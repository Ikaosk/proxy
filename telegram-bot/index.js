#!/usr/bin/env node
'use strict';

require('dotenv').config(); // подхватывает .env, если он есть; переменные окружения имеют приоритет

const { loadConfig } = require('./lib/config');
const createBot = require('./lib/bot');
const { createApiServer } = require('./lib/apiServer');

function log(...args) {
  console.log(new Date().toISOString(), ...args);
}

async function main() {
  const config = loadConfig();
  const bot = createBot(config);

  log('Запуск Telegram-бота');
  log(`  Оплата: ${config.currency === 'XTR' ? 'Telegram Stars' : 'платёжный провайдер (RUB)'}`);
  log(`  Хранилище: ${config.storePath}`);
  log(`  Прокси: ${config.proxyHost}:${config.proxyHttpPort} (HTTP), ${config.proxyHost}:${config.proxySocksPort} (SOCKS5)`);

  let botUsername = config.botUsername;

  await bot.launch();
  log('Бот запущен и принимает сообщения (long polling)');

  if (!botUsername) {
    try {
      const me = await bot.telegram.getMe();
      botUsername = me.username;
      log(`  Юзернейм бота определён автоматически: @${botUsername}`);
    } catch (err) {
      log(`  Не удалось определить юзернейм бота автоматически: ${err.message}. Задайте BOT_USERNAME.`);
    }
  }

  const apiServer = createApiServer(config, () => botUsername, log);
  apiServer.listen(config.apiPort, config.apiHost, () => {
    log(`API для приложения запущен: ${config.apiHost}:${config.apiPort}`);
  });

  function shutdown() {
    log('Остановка...');
    bot.stop('SIGTERM');
    apiServer.close();
    setTimeout(() => process.exit(0), 2000).unref();
  }

  process.once('SIGINT', shutdown);
  process.once('SIGTERM', shutdown);
}

main().catch((err) => {
  console.error('Не удалось запустить бота:', err);
  process.exit(1);
});
