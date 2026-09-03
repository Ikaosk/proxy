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

  if (Object.keys(merged.users).length === 0) {
    throw new Error(
      'Не задано ни одного пользователя. Заполните "users" в config.json ' +
        '(см. config.example.json) или задайте PROXY_USER/PROXY_PASSWORD.'
    );
  }

  return merged;
}

module.exports = { loadConfig };
