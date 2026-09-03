'use strict';

/**
 * Общее хранилище пользователей/подписок в JSON-файле. Читается и ботом,
 * и (только на чтение) web-proxy — так подписка реально включает и
 * отключает доступ к прокси.
 *
 * Запись атомарна (пишем во временный файл и делаем rename), поэтому
 * читатель никогда не увидит "половину" файла. Внутри одного обработчика
 * бота между load() и save() не должно быть `await` — тогда race conditions
 * между параллельными апдейтами Telegram исключены (Node однопоточный).
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

function ensureDir(filePath) {
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

function load(filePath) {
  if (!fs.existsSync(filePath)) return { users: {} };
  try {
    const parsed = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    if (!parsed.users) parsed.users = {};
    return parsed;
  } catch (err) {
    throw new Error(`Повреждён файл хранилища ${filePath}: ${err.message}`);
  }
}

function save(filePath, data) {
  ensureDir(filePath);
  const tmpPath = `${filePath}.${process.pid}.tmp`;
  fs.writeFileSync(tmpPath, JSON.stringify(data, null, 2));
  fs.renameSync(tmpPath, filePath);
}

function generatePassword() {
  return crypto.randomBytes(9).toString('base64url'); // ~12 символов, без спецсимволов
}

function generateLogin(telegramId) {
  return `tg${telegramId}`;
}

function getOrCreateUser(store, telegramId, profile = {}) {
  const key = String(telegramId);
  if (!store.users[key]) {
    store.users[key] = {
      telegramId,
      username: profile.username || null,
      firstName: profile.firstName || null,
      proxyLogin: generateLogin(telegramId),
      proxyPassword: generatePassword(),
      createdAt: new Date().toISOString(),
      subscriptions: [],
      payments: [],
    };
  } else {
    if (profile.username) store.users[key].username = profile.username;
    if (profile.firstName) store.users[key].firstName = profile.firstName;
  }
  return store.users[key];
}

function getActiveSubscription(user) {
  const now = Date.now();
  return (
    user.subscriptions
      .filter((s) => s.status === 'active' && new Date(s.expiresAt).getTime() > now)
      .sort((a, b) => new Date(b.expiresAt) - new Date(a.expiresAt))[0] || null
  );
}

function addSubscription(user, plan) {
  const now = Date.now();
  const current = getActiveSubscription(user);
  // Продление добавляется к остатку текущей подписки, а не поверх "сейчас".
  const startFrom = current ? new Date(current.expiresAt).getTime() : now;
  const expiresAt = new Date(startFrom + plan.days * 24 * 60 * 60 * 1000).toISOString();
  const subscription = {
    planId: plan.id,
    startedAt: new Date(now).toISOString(),
    expiresAt,
    status: 'active',
  };
  user.subscriptions.push(subscription);
  return subscription;
}

function addPayment(user, payment) {
  user.payments.push({ ...payment, createdAt: new Date().toISOString() });
}

module.exports = {
  load,
  save,
  getOrCreateUser,
  getActiveSubscription,
  addSubscription,
  addPayment,
};
