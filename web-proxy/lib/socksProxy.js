'use strict';

const net = require('net');

const VERSION = 0x05;
const AUTH_USERPASS = 0x02;
const AUTH_NO_ACCEPTABLE = 0xff;

const CMD_CONNECT = 0x01;

const ATYP_IPV4 = 0x01;
const ATYP_DOMAIN = 0x03;
const ATYP_IPV6 = 0x04;

const REP_SUCCESS = 0x00;
const REP_HOST_UNREACHABLE = 0x04;
const REP_COMMAND_NOT_SUPPORTED = 0x07;
const REP_ADDRESS_TYPE_NOT_SUPPORTED = 0x08;

// Буферизует входящие байты сокета и отдаёт их порциями по запросу —
// нужно, потому что TCP не гарантирует, что один "пакет" SOCKS5-рукопожатия
// придёт одним `data`-событием.
function createByteReader(socket) {
  let buffer = Buffer.alloc(0);
  let waiting = null;
  let detached = false;

  function tryResolve() {
    if (waiting && buffer.length >= waiting.n) {
      const { n, resolve } = waiting;
      const result = buffer.subarray(0, n);
      buffer = buffer.subarray(n);
      waiting = null;
      resolve(result);
    }
  }

  function onData(chunk) {
    buffer = Buffer.concat([buffer, chunk]);
    tryResolve();
  }

  function onEnd(err) {
    if (waiting) {
      waiting.reject(err || new Error('Соединение закрыто до завершения SOCKS5-рукопожатия'));
      waiting = null;
    }
  }

  socket.on('data', onData);
  socket.once('close', () => onEnd());
  socket.once('error', onEnd);

  return {
    readBytes(n) {
      return new Promise((resolve, reject) => {
        if (buffer.length >= n) {
          const result = buffer.subarray(0, n);
          buffer = buffer.subarray(n);
          resolve(result);
          return;
        }
        waiting = { n, resolve, reject };
      });
    },
    // Отключает буферизацию и возвращает то, что уже успело прийти сверх
    // прочитанного — эти байты нужно переслать в туннель до pipe().
    detach() {
      if (detached) return Buffer.alloc(0);
      detached = true;
      socket.removeListener('data', onData);
      const leftover = buffer;
      buffer = Buffer.alloc(0);
      return leftover;
    },
  };
}

function ipv6BufferToString(buf) {
  const groups = [];
  for (let i = 0; i < 16; i += 2) {
    groups.push(buf.readUInt16BE(i).toString(16));
  }
  return groups.join(':');
}

function sendReply(socket, rep) {
  return new Promise((resolve, reject) => {
    // BND.ADDR/BND.PORT для CONNECT практически все клиенты игнорируют,
    // поэтому не тратим усилия на настоящий локальный адрес.
    const buf = Buffer.from([VERSION, rep, 0x00, ATYP_IPV4, 0, 0, 0, 0, 0, 0]);
    socket.write(buf, (err) => (err ? reject(err) : resolve()));
  });
}

async function handleConnection(socket, checkCredentials, log) {
  const reader = createByteReader(socket);

  const greeting = await reader.readBytes(2);
  if (greeting[0] !== VERSION) {
    socket.destroy();
    return;
  }
  const methods = await reader.readBytes(greeting[1]);

  if (!methods.includes(AUTH_USERPASS)) {
    socket.end(Buffer.from([VERSION, AUTH_NO_ACCEPTABLE]));
    return;
  }
  socket.write(Buffer.from([VERSION, AUTH_USERPASS]));

  const authHeader = await reader.readBytes(2); // VER, ULEN
  if (authHeader[0] !== 0x01) {
    socket.destroy();
    return;
  }
  const username = (await reader.readBytes(authHeader[1])).toString('utf8');
  const plen = (await reader.readBytes(1))[0];
  const password = (await reader.readBytes(plen)).toString('utf8');

  const authOk = checkCredentials(username, password);
  await new Promise((resolve, reject) =>
    socket.write(Buffer.from([0x01, authOk ? 0x00 : 0x01]), (err) => (err ? reject(err) : resolve()))
  );
  if (!authOk) {
    socket.end();
    return;
  }

  const reqHeader = await reader.readBytes(4); // VER, CMD, RSV, ATYP
  if (reqHeader[0] !== VERSION) {
    socket.destroy();
    return;
  }
  const cmd = reqHeader[1];
  const atyp = reqHeader[3];

  let address;
  if (atyp === ATYP_IPV4) {
    address = Array.from(await reader.readBytes(4)).join('.');
  } else if (atyp === ATYP_DOMAIN) {
    const len = (await reader.readBytes(1))[0];
    address = (await reader.readBytes(len)).toString('utf8');
  } else if (atyp === ATYP_IPV6) {
    address = ipv6BufferToString(await reader.readBytes(16));
  } else {
    await sendReply(socket, REP_ADDRESS_TYPE_NOT_SUPPORTED);
    socket.end();
    return;
  }

  const port = (await reader.readBytes(2)).readUInt16BE(0);

  if (cmd !== CMD_CONNECT) {
    await sendReply(socket, REP_COMMAND_NOT_SUPPORTED);
    socket.end();
    return;
  }

  const leftover = reader.detach();
  const target = net.connect(port, address);

  target.once('connect', async () => {
    await sendReply(socket, REP_SUCCESS);
    if (leftover.length) target.write(leftover);
    target.pipe(socket);
    socket.pipe(target);
  });

  target.on('error', async (err) => {
    log(`Ошибка SOCKS5-подключения к ${address}:${port}: ${err.message}`);
    if (!socket.destroyed) {
      try {
        await sendReply(socket, REP_HOST_UNREACHABLE);
      } catch {
        // сокет уже недоступен — игнорируем
      }
    }
    socket.destroy();
  });

  socket.on('error', () => target.destroy());
}

function createSocksServer({ checkCredentials, idleTimeoutMs, log }) {
  const server = net.createServer((socket) => {
    socket.setTimeout(idleTimeoutMs, () => socket.destroy());
    handleConnection(socket, checkCredentials, log).catch((err) => {
      log(`Ошибка SOCKS5-соединения: ${err.message}`);
      socket.destroy();
    });
  });

  server.on('error', (err) => log(`Ошибка SOCKS5-сервера: ${err.message}`));

  return server;
}

module.exports = { createSocksServer };
