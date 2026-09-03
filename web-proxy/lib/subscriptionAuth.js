'use strict';

const fs = require('fs');
const crypto = require('crypto');

/**
 * Проверка доступа по общему JSON-хранилищу, которое пишет telegram-bot/
 * (см. telegram-bot/lib/store.js). Файл читается заново при каждой попытке
 * подключения — так подписка реально включает и отключает доступ.
 */

function timingSafeEqualStr(a, b) {
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  if (bufA.length !== bufB.length) {
    crypto.timingSafeEqual(bufA, bufA);
    return false;
  }
  return crypto.timingSafeEqual(bufA, bufB);
}

function loadStore(filePath) {
  if (!fs.existsSync(filePath)) return { users: {} };
  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch {
    return { users: {} };
  }
}

function hasActiveSubscription(user) {
  const now = Date.now();
  return (user.subscriptions || []).some(
    (s) => s.status === 'active' && new Date(s.expiresAt).getTime() > now
  );
}

function createSubscriptionAuthenticator(storePath) {
  return function checkCredentials(username, password) {
    if (typeof username !== 'string' || typeof password !== 'string') return false;

    const data = loadStore(storePath);
    const user = Object.values(data.users || {}).find((u) => u.proxyLogin === username);

    if (!user) {
      timingSafeEqualStr(password, password);
      return false;
    }
    if (!timingSafeEqualStr(password, user.proxyPassword)) return false;

    return hasActiveSubscription(user);
  };
}

module.exports = { createSubscriptionAuthenticator };
