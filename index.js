#!/usr/bin/env node
'use strict';

/**
 * UDP-прокси для SA-MP / open.mp сервера Harribel Online.
 *
 * Игроки подключаются к прокси (публичный IP), прокси прозрачно
 * пересылает UDP-пакеты на реальный игровой сервер и обратно.
 * Реальный IP/порт сервера при этом не раскрывается игрокам.
 */

const dgram = require('dgram');
const fs = require('fs');
const path = require('path');

function loadConfig() {
  const defaults = {
    listenHost: '0.0.0.0',
    listenPort: 7777,
    targetHost: '127.0.0.1',
    targetPort: 7777,
    idleTimeoutMs: 60000,
    maxClients: 500,
  };

  const configPath = process.env.PROXY_CONFIG || path.join(__dirname, 'config.json');
  let fileConfig = {};
  if (fs.existsSync(configPath)) {
    try {
      fileConfig = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    } catch (err) {
      console.error(`Не удалось прочитать ${configPath}: ${err.message}`);
    }
  }

  const envOverrides = {
    listenHost: process.env.PROXY_LISTEN_HOST,
    listenPort: process.env.PROXY_LISTEN_PORT,
    targetHost: process.env.PROXY_TARGET_HOST,
    targetPort: process.env.PROXY_TARGET_PORT,
    idleTimeoutMs: process.env.PROXY_IDLE_TIMEOUT_MS,
    maxClients: process.env.PROXY_MAX_CLIENTS,
  };

  const numericKeys = new Set(['listenPort', 'targetPort', 'idleTimeoutMs', 'maxClients']);
  const merged = { ...defaults, ...fileConfig };

  for (const [key, raw] of Object.entries(envOverrides)) {
    if (raw === undefined || raw === '') continue;
    merged[key] = numericKeys.has(key) ? Number(raw) : raw;
  }

  return merged;
}

function log(...args) {
  console.log(new Date().toISOString(), ...args);
}

function main() {
  const config = loadConfig();

  log('Запуск прокси Harribel Online (SA-MP / open.mp)');
  log(`  Приём подключений: ${config.listenHost}:${config.listenPort}`);
  log(`  Реальный сервер:   ${config.targetHost}:${config.targetPort}`);
  log(`  Таймаут простоя:   ${config.idleTimeoutMs} мс`);
  log(`  Лимит клиентов:    ${config.maxClients}`);

  // key `${address}:${port}` -> { socket, timer, address, port }
  const clients = new Map();

  const frontSocket = dgram.createSocket('udp4');

  const clientKey = (rinfo) => `${rinfo.address}:${rinfo.port}`;

  function touch(entry) {
    clearTimeout(entry.timer);
    entry.timer = setTimeout(() => removeClient(entry.key), config.idleTimeoutMs);
  }

  function removeClient(key) {
    const entry = clients.get(key);
    if (!entry) return;
    clearTimeout(entry.timer);
    entry.socket.close();
    clients.delete(key);
    log(`Клиент отключён: ${key} (осталось: ${clients.size})`);
  }

  function createClientEntry(rinfo) {
    const key = clientKey(rinfo);
    // Отдельный сокет на каждого клиента: ответы сервера приходят именно
    // на него, поэтому легко понять, какому игроку их пересылать.
    const backSocket = dgram.createSocket('udp4');

    backSocket.on('message', (msg) => {
      frontSocket.send(msg, rinfo.port, rinfo.address, (err) => {
        if (err) log(`Ошибка отправки клиенту ${key}: ${err.message}`);
      });
    });

    backSocket.on('error', (err) => {
      log(`Ошибка сокета клиента ${key}: ${err.message}`);
      removeClient(key);
    });

    const entry = { key, socket: backSocket, timer: null };
    touch(entry);
    clients.set(key, entry);
    log(`Новый клиент: ${key} (всего: ${clients.size})`);
    return entry;
  }

  frontSocket.on('message', (msg, rinfo) => {
    const key = clientKey(rinfo);
    let entry = clients.get(key);

    if (!entry) {
      if (clients.size >= config.maxClients) {
        log(`Лимит клиентов (${config.maxClients}) достигнут, пакет от ${key} отброшен`);
        return;
      }
      entry = createClientEntry(rinfo);
    } else {
      touch(entry);
    }

    entry.socket.send(msg, config.targetPort, config.targetHost, (err) => {
      if (err) log(`Ошибка отправки на сервер (от ${key}): ${err.message}`);
    });
  });

  frontSocket.on('error', (err) => {
    log(`Критическая ошибка прокси: ${err.message}`);
    process.exit(1);
  });

  frontSocket.bind(config.listenPort, config.listenHost, () => {
    log('Прокси запущен и готов принимать подключения игроков');
  });

  function shutdown() {
    log('Остановка прокси...');
    for (const key of Array.from(clients.keys())) {
      removeClient(key);
    }
    frontSocket.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 2000).unref();
  }

  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

main();
