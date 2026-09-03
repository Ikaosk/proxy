#!/usr/bin/env node
'use strict';

const { loadConfig } = require('./lib/config');
const createBot = require('./lib/bot');

function log(...args) {
  console.log(new Date().toISOString(), ...args);
}

function main() {
  const config = loadConfig();
  const bot = createBot(config);

  log('Запуск Telegram-бота');
  log(`  Оплата: ${config.currency === 'XTR' ? 'Telegram Stars' : 'платёжный провайдер (RUB)'}`);
  log(`  Хранилище: ${config.storePath}`);
  log(`  Прокси: ${config.proxyHost}:${config.proxyHttpPort} (HTTP), ${config.proxyHost}:${config.proxySocksPort} (SOCKS5)`);

  bot.launch().then(() => log('Бот запущен и принимает сообщения (long polling)'));

  process.once('SIGINT', () => bot.stop('SIGINT'));
  process.once('SIGTERM', () => bot.stop('SIGTERM'));
}

main();
