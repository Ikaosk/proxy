'use strict';

const path = require('path');

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`Переменная окружения ${name} обязательна`);
  return value;
}

function loadConfig() {
  // Если задан PROVIDER_TOKEN (платёжный провайдер, например ЮKassa) —
  // оплата в рублях через классический Telegram Payments API.
  // Если нет — оплата встроенной валютой Telegram Stars (XTR), работает
  // "из коробки" для любого бота без подключения платёжного провайдера.
  const providerToken = process.env.PROVIDER_TOKEN || null;

  return {
    botToken: required('BOT_TOKEN'),
    providerToken,
    currency: providerToken ? 'RUB' : 'XTR',
    storePath: process.env.STORE_PATH || path.join(__dirname, '..', 'data', 'store.json'),
    proxyHost: process.env.PROXY_HOST || '127.0.0.1',
    proxyHttpPort: Number(process.env.PROXY_HTTP_PORT || 8080),
    proxySocksPort: Number(process.env.PROXY_SOCKS_PORT || 1080),
    adminIds: (process.env.ADMIN_IDS || '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
      .map(Number),
    supportUsername: process.env.SUPPORT_USERNAME || null,
    apiHost: process.env.API_HOST || '0.0.0.0',
    apiPort: Number(process.env.API_PORT || 3000),
    // Если не задан — определяется автоматически через bot.telegram.getMe().
    botUsername: process.env.BOT_USERNAME || null,
  };
}

module.exports = { loadConfig };
