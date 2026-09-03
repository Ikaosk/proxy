'use strict';

/**
 * Небольшой JSON HTTP API для клиентских приложений (Android и т.п.).
 * Написан на встроенном `http`, без веб-фреймворка.
 *
 * Эндпоинты:
 *   POST /api/link/start           -> { code, deepLink, expiresAt }
 *   GET  /api/link/status?code=... -> { valid, linked, sessionToken? }
 *   GET  /api/plans                -> [{ id, title, days, price, currency }]
 *   GET  /api/me                   -> профиль + подписка + креды прокси (Bearer)
 *   GET  /api/subscriptions        -> история подписок (Bearer)
 *   POST /api/logout               -> отзыв текущего токена (Bearer)
 *
 * В проде эндпоинт стоит прятать за HTTPS-реверс-прокси (nginx/Caddy) —
 * сам сервер сырой HTTP.
 */

const http = require('http');
const { URL } = require('url');
const store = require('./store');
const { PLANS, findPlan } = require('./plans');

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > 1024 * 1024) {
        reject(new Error('Тело запроса слишком большое'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

function bearerToken(req) {
  const header = req.headers.authorization || '';
  if (!header.startsWith('Bearer ')) return null;
  return header.slice('Bearer '.length).trim();
}

function planPublicView(plan, currency) {
  return {
    id: plan.id,
    title: plan.title,
    days: plan.days,
    price: currency === 'XTR' ? plan.priceStars : plan.priceRub,
    currency,
  };
}

function userPublicView(user, config) {
  const sub = store.getActiveSubscription(user);
  const plan = sub ? findPlan(sub.planId) : null;
  return {
    telegramId: user.telegramId,
    username: user.username,
    firstName: user.firstName,
    createdAt: user.createdAt,
    subscription: sub
      ? { planId: sub.planId, planTitle: plan ? plan.title : sub.planId, expiresAt: sub.expiresAt, active: true }
      : { active: false },
    proxy: {
      login: user.proxyLogin,
      password: user.proxyPassword,
      host: config.proxyHost,
      httpPort: config.proxyHttpPort,
      socksPort: config.proxySocksPort,
    },
  };
}

function createApiServer(config, getBotUsername, log) {
  const loadStore = () => store.load(config.storePath);
  const saveStore = (data) => store.save(config.storePath, data);

  const server = http.createServer(async (req, res) => {
    try {
      if (req.method === 'OPTIONS') {
        sendJson(res, 204, {});
        return;
      }

      // Ни один из маршрутов ниже не читает тело — вычитываем и отбрасываем
      // его сразу, иначе keep-alive соединение может зависнуть в ожидании.
      if (req.method === 'POST') {
        await readBody(req).catch(() => {});
      }

      const url = new URL(req.url, 'http://localhost');

      if (req.method === 'POST' && url.pathname === '/api/link/start') {
        const data = loadStore();
        const code = store.createLinkCode(data);
        saveStore(data);

        const botUsername = getBotUsername();
        const deepLink = botUsername ? `https://t.me/${botUsername}?start=link_${code}` : null;
        sendJson(res, 200, { code, deepLink, expiresIn: 600 });
        return;
      }

      if (req.method === 'GET' && url.pathname === '/api/link/status') {
        const code = url.searchParams.get('code') || '';
        const data = loadStore();
        const status = store.getLinkStatus(data, code);
        sendJson(res, 200, status);
        return;
      }

      if (req.method === 'GET' && url.pathname === '/api/plans') {
        sendJson(res, 200, PLANS.map((p) => planPublicView(p, config.currency)));
        return;
      }

      if (req.method === 'GET' && url.pathname === '/api/me') {
        const token = bearerToken(req);
        const data = loadStore();
        const user = store.findUserByToken(data, token);
        if (!user) {
          sendJson(res, 401, { error: 'unauthorized' });
          return;
        }
        sendJson(res, 200, userPublicView(user, config));
        return;
      }

      if (req.method === 'GET' && url.pathname === '/api/subscriptions') {
        const token = bearerToken(req);
        const data = loadStore();
        const user = store.findUserByToken(data, token);
        if (!user) {
          sendJson(res, 401, { error: 'unauthorized' });
          return;
        }
        const now = Date.now();
        const subscriptions = user.subscriptions
          .slice()
          .reverse()
          .map((s) => {
            const plan = findPlan(s.planId);
            return {
              planId: s.planId,
              planTitle: plan ? plan.title : s.planId,
              startedAt: s.startedAt,
              expiresAt: s.expiresAt,
              active: s.status === 'active' && new Date(s.expiresAt).getTime() > now,
            };
          });
        sendJson(res, 200, subscriptions);
        return;
      }

      if (req.method === 'POST' && url.pathname === '/api/logout') {
        const token = bearerToken(req);
        const data = loadStore();
        store.revokeToken(data, token);
        saveStore(data);
        sendJson(res, 200, { ok: true });
        return;
      }

      sendJson(res, 404, { error: 'not_found' });
    } catch (err) {
      log(`Ошибка API-запроса ${req.method} ${req.url}: ${err.message}`);
      sendJson(res, 500, { error: 'internal_error' });
    }
  });

  return server;
}

module.exports = { createApiServer };
