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
  if (!fs.existsSync(filePath)) return { users: {}, linkCodes: {} };
  try {
    const parsed = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    if (!parsed.users) parsed.users = {};
    if (!parsed.linkCodes) parsed.linkCodes = {};
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

function generateToken() {
  return crypto.randomBytes(32).toString('base64url');
}

function generateLinkCode() {
  // Короткий код для отображения в приложении, но по-прежнему случайный.
  return crypto.randomBytes(6).toString('base64url');
}

const LINK_CODE_TTL_MS = 10 * 60 * 1000;

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

// --- Связка приложения с Telegram через одноразовый код ---
//
// 1. Приложение вызывает createLinkCode() -> получает код и открывает бота
//    по deep link t.me/<bot>?start=link_<код>.
// 2. Пользователь нажимает Start в Telegram — бот вызывает consumeLinkCode()
//    с своим telegramId, получает сессионный токен и привязывает код к нему.
// 3. Приложение опрашивает getLinkStatus(code) и, получив токен, дальше
//    ходит в API с ним как с Bearer-токеном.

function cleanupLinkCodes(store) {
  const now = Date.now();
  for (const [code, entry] of Object.entries(store.linkCodes)) {
    if (new Date(entry.expiresAt).getTime() < now) delete store.linkCodes[code];
  }
}

function createLinkCode(store) {
  cleanupLinkCodes(store);
  const code = generateLinkCode();
  store.linkCodes[code] = {
    createdAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + LINK_CODE_TTL_MS).toISOString(),
    telegramId: null,
    sessionToken: null,
  };
  return code;
}

// Вызывается ботом при получении /start link_<code> от пользователя.
// Возвращает сессионный токен или null, если код неизвестен/просрочен.
function consumeLinkCode(store, code, telegramId, profile = {}) {
  const entry = store.linkCodes[code];
  if (!entry) return null;
  if (new Date(entry.expiresAt).getTime() < Date.now()) {
    delete store.linkCodes[code];
    return null;
  }

  const user = getOrCreateUser(store, telegramId, profile);
  const token = generateToken();
  if (!user.appSessions) user.appSessions = [];
  user.appSessions.push({ token, createdAt: new Date().toISOString() });

  entry.telegramId = telegramId;
  entry.sessionToken = token;
  return token;
}

// Вызывается приложением при поллинге — не мутирует стор.
function getLinkStatus(store, code) {
  const entry = store.linkCodes[code];
  if (!entry) return { valid: false, linked: false };
  if (new Date(entry.expiresAt).getTime() < Date.now()) return { valid: false, linked: false };
  if (entry.sessionToken) return { valid: true, linked: true, sessionToken: entry.sessionToken };
  return { valid: true, linked: false };
}

function findUserByToken(store, token) {
  if (!token) return null;
  return (
    Object.values(store.users).find((u) => (u.appSessions || []).some((s) => s.token === token)) || null
  );
}

function revokeToken(store, token) {
  const user = findUserByToken(store, token);
  if (!user) return false;
  user.appSessions = (user.appSessions || []).filter((s) => s.token !== token);
  return true;
}

module.exports = {
  load,
  save,
  getOrCreateUser,
  getActiveSubscription,
  addSubscription,
  addPayment,
  createLinkCode,
  consumeLinkCode,
  getLinkStatus,
  findUserByToken,
  revokeToken,
};
