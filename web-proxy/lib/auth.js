'use strict';

const crypto = require('crypto');

function timingSafeEqualStr(a, b) {
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  if (bufA.length !== bufB.length) {
    // Сравниваем всё равно (с чужим буфером той же длины), чтобы не выдавать
    // длину пароля через тайминг раннего выхода.
    crypto.timingSafeEqual(bufA, bufA);
    return false;
  }
  return crypto.timingSafeEqual(bufA, bufB);
}

function createAuthenticator(users) {
  return function checkCredentials(username, password) {
    if (typeof username !== 'string' || typeof password !== 'string') return false;
    const expected = users[username];
    if (expected === undefined) {
      // Всё равно тратим время на сравнение, чтобы не палить существование логина.
      timingSafeEqualStr(password, password);
      return false;
    }
    return timingSafeEqualStr(password, expected);
  };
}

module.exports = { createAuthenticator };
