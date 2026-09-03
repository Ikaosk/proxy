#!/usr/bin/env node
'use strict';

/**
 * Личный прокси-сервер: HTTP(S) forward-proxy + SOCKS5, оба с обязательной
 * авторизацией по логину/паролю. Для повседневного использования — браузер,
 * мессенджеры, консольные утилиты и т.п.
 */

const { loadConfig } = require('./lib/config');
const { createAuthenticator } = require('./lib/auth');
const { createSubscriptionAuthenticator } = require('./lib/subscriptionAuth');
const { createHttpProxyServer } = require('./lib/httpProxy');
const { createSocksServer } = require('./lib/socksProxy');

function log(...args) {
  console.log(new Date().toISOString(), ...args);
}

function main() {
  const config = loadConfig();

  const checkStaticUser = Object.keys(config.users).length
    ? createAuthenticator(config.users)
    : () => false;
  const checkSubscriptionUser = config.subscriptionsFile
    ? createSubscriptionAuthenticator(config.subscriptionsFile)
    : () => false;
  const checkCredentials = (username, password) =>
    checkStaticUser(username, password) || checkSubscriptionUser(username, password);

  log('Запуск личного прокси (HTTP/HTTPS + SOCKS5)');
  if (Object.keys(config.users).length) {
    log(`  Статические пользователи: ${Object.keys(config.users).join(', ')}`);
  }
  if (config.subscriptionsFile) {
    log(`  Пользователи по подписке: ${config.subscriptionsFile}`);
  }
  log(`  HTTP(S)-прокси: ${config.listenHost}:${config.httpPort}`);
  log(`  SOCKS5-прокси:  ${config.listenHost}:${config.socksPort}`);

  const httpServer = createHttpProxyServer({
    checkCredentials,
    idleTimeoutMs: config.idleTimeoutMs,
    log,
  });
  const socksServer = createSocksServer({
    checkCredentials,
    idleTimeoutMs: config.idleTimeoutMs,
    log,
  });

  httpServer.listen(config.httpPort, config.listenHost, () => {
    log('HTTP(S)-прокси запущен');
  });
  socksServer.listen(config.socksPort, config.listenHost, () => {
    log('SOCKS5-прокси запущен');
  });

  function shutdown() {
    log('Остановка...');
    httpServer.close();
    socksServer.close();
    setTimeout(() => process.exit(0), 2000).unref();
  }

  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

main();
