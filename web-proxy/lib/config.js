'use strict';

const fs = require('fs');
const path = require('path');

function loadConfig() {
  const defaults = {
    listenHost: '0.0.0.0',
    httpPort: 8080,
    socksPort: 1080,
    idleTimeoutMs: 120000,
    users: {},
    subscriptionsFile: null,
  };

  const configPath = process.env.PROXY_CONFIG || path.join(__dirname, '..', 'config.json');
  let fileConfig = {};
  if (fs.existsSync(configPath)) {
    try {
      fileConfig = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    } catch (err) {
      console.error(`Не удалось прочитать ${configPath}: ${err.message}`);
    }
  }

  const merged = { ...defaults, ...fileConfig, users: { ...fileConfig.users } };

  const numericEnv = {
    listenHost: process.env.PROXY_LISTEN_HOST,
    httpPort: process.env.PROXY_HTTP_PORT,
    socksPort: process.env.PROXY_SOCKS_PORT,
    idleTimeoutMs: process.env.PROXY_IDLE_TIMEOUT_MS,
  };
  for (const [key, raw] of Object.entries(numericEnv)) {
    if (raw === undefined || raw === '') continue;
    merged[key] = key === 'listenHost' ? raw : Number(raw);
  }

  // PROXY_USER / PROXY_PASSWORD — быстрый способ задать одного пользователя
  // через переменные окружения, без config.json.
  if (process.env.PROXY_USER && process.env.PROXY_PASSWORD) {
    merged.users[process.env.PROXY_USER] = process.env.PROXY_PASSWORD;
  }

  // SUBSCRIPTIONS_FILE — путь к общему хранилищу telegram-bot/lib/store.js.
  // Когда задан, доступ дополнительно разрешается пользователям с активной
  // подпиской из этого файла (в дополнение к статическим "users").
  if (process.env.SUBSCRIPTIONS_FILE) {
    merged.subscriptionsFile = process.env.SUBSCRIPTIONS_FILE;
  }

  if (Object.keys(merged.users).length === 0 && !merged.subscriptionsFile) {
    throw new Error(
      'Не задано ни одного пользователя. Заполните "users" в config.json ' +
        '(см. config.example.json), задайте PROXY_USER/PROXY_PASSWORD, ' +
        'или подключите SUBSCRIPTIONS_FILE (см. telegram-bot/README.md).'
    );
  }

  return merged;
}

module.exports = { loadConfig };
