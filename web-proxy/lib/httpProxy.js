'use strict';

const http = require('http');
const net = require('net');
const { URL } = require('url');

function parseBasicAuth(header) {
  if (!header || !header.startsWith('Basic ')) return null;
  const decoded = Buffer.from(header.slice(6), 'base64').toString('utf8');
  const sep = decoded.indexOf(':');
  if (sep === -1) return null;
  return { username: decoded.slice(0, sep), password: decoded.slice(sep + 1) };
}

const AUTH_REALM = 'web-proxy';

function requireAuth(headers, checkCredentials) {
  const creds = parseBasicAuth(headers['proxy-authorization']);
  if (!creds) return false;
  return checkCredentials(creds.username, creds.password);
}

function sendProxyAuthRequired(res) {
  res.writeHead(407, {
    'Proxy-Authenticate': `Basic realm="${AUTH_REALM}"`,
    Connection: 'close',
  });
  res.end('Proxy Authentication Required');
}

function createHttpProxyServer({ checkCredentials, idleTimeoutMs, log }) {
  const server = http.createServer((req, res) => {
    if (!requireAuth(req.headers, checkCredentials)) {
      sendProxyAuthRequired(res);
      return;
    }

    let target;
    try {
      target = new URL(req.url);
    } catch {
      res.writeHead(400);
      res.end('Bad Request: expected absolute URL in a forward-proxy request');
      return;
    }

    const options = {
      protocol: target.protocol,
      hostname: target.hostname,
      port: target.port || 80,
      method: req.method,
      path: `${target.pathname}${target.search}`,
      headers: { ...req.headers },
    };
    delete options.headers['proxy-authorization'];
    delete options.headers['proxy-connection'];

    const upstreamReq = http.request(options, (upstreamRes) => {
      res.writeHead(upstreamRes.statusCode, upstreamRes.headers);
      upstreamRes.pipe(res);
    });

    upstreamReq.setTimeout(idleTimeoutMs, () => upstreamReq.destroy());
    upstreamReq.on('error', (err) => {
      log(`Ошибка запроса к ${target.hostname}: ${err.message}`);
      if (!res.headersSent) {
        res.writeHead(502);
      }
      res.end('Bad Gateway');
    });

    req.pipe(upstreamReq);
  });

  // HTTPS через CONNECT: пробрасываем TCP-туннель до целевого хоста.
  server.on('connect', (req, clientSocket, head) => {
    if (!requireAuth(req.headers, checkCredentials)) {
      clientSocket.write(
        `HTTP/1.1 407 Proxy Authentication Required\r\n` +
          `Proxy-Authenticate: Basic realm="${AUTH_REALM}"\r\n` +
          `Connection: close\r\n\r\n`
      );
      clientSocket.end();
      return;
    }

    const [hostname, portStr] = req.url.split(':');
    const port = Number(portStr) || 443;

    const serverSocket = net.connect(port, hostname, () => {
      clientSocket.write('HTTP/1.1 200 Connection Established\r\n\r\n');
      if (head && head.length) serverSocket.write(head);
      serverSocket.pipe(clientSocket);
      clientSocket.pipe(serverSocket);
    });

    serverSocket.setTimeout(idleTimeoutMs, () => serverSocket.destroy());
    clientSocket.setTimeout(idleTimeoutMs, () => clientSocket.destroy());

    serverSocket.on('error', (err) => {
      log(`Ошибка CONNECT-туннеля к ${hostname}:${port}: ${err.message}`);
      clientSocket.destroy();
    });
    clientSocket.on('error', () => serverSocket.destroy());
  });

  return server;
}

module.exports = { createHttpProxyServer };
