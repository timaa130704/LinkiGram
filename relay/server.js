#!/usr/bin/env node
'use strict';

/*
 * LinkiGram relay.
 *
 * Terminates the WebSocket transport that WsBypassCore speaks and pipes the
 * payload into a Telegram DC over MTProto.
 *
 * The client sends a 64-byte obfuscated2 header as its first binary message.
 * Bytes 8..40 of that header are the AES-256 key and 40..56 the IV for the
 * stream with the DC; both directions run AES-256-CTR from the same key and IV
 * and skip the first 64 bytes, because those bytes are the header itself.
 * After that it is a plain byte pipe: one WebSocket binary message in, one
 * chunk of ciphertext out.
 *
 * Nothing here is NimarkoGram-specific. There is no credential, no install id
 * and no rate limit, which is the point -- the upstream relays reject clients
 * they do not recognise, and that is what stopped the client from holding a
 * tunnel open.
 *
 * Usage: node server.js --port 443 --cert cert.pem --key key.pem
 */

const net = require('net');
const tls = require('tls');
const http = require('http');
const https = require('https');
const crypto = require('crypto');
const fs = require('fs');

const WS_GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';
const INIT_LEN = 64;
const KEY_OFF = 8;
const KEY_LEN = 32;
const IV_OFF = 40;
const IV_LEN = 16;

/** Telegram DC address per the dc= parameter the client sends. */
const DC_IPS = {
  1: '149.154.175.50',
  2: '149.154.167.51',
  3: '149.154.175.100',
  4: '149.154.167.91',
  5: '149.154.171.5',
  203: '91.105.192.100',
};

const args = parseArgs(process.argv.slice(2));
const PORT = args.port || 8443;
const HOST = args.host || '0.0.0.0';
const VERBOSE = !!args.verbose;
const IDLE_TIMEOUT_MS = num(args['idle-timeout'], 10 * 60 * 1000);
const MAX_MESSAGE_BYTES = num(args['max-message'], 1024 * 1024);

const stats = {
  started: new Date().toISOString(),
  connections: 0,
  established: 0,
  rejected: 0,
  failed: 0,
  bytesUp: 0,
  bytesDown: 0,
  live: 0,
};

function num(value, fallback) {
  const parsed = parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function parseArgs(list) {
  const out = {};
  for (let i = 0; i < list.length; i++) {
    const item = list[i];
    if (!item.startsWith('--')) continue;
    const eq = item.indexOf('=');
    if (eq >= 0) {
      out[item.slice(2, eq)] = item.slice(eq + 1);
    } else if (i + 1 < list.length && !list[i + 1].startsWith('--')) {
      out[item.slice(2)] = list[++i];
    } else {
      out[item.slice(2)] = true;
    }
  }
  return out;
}

function log(...parts) {
  console.log(new Date().toISOString(), ...parts);
}

function debug(...parts) {
  if (VERBOSE) log('  ', ...parts);
}

/* ------------------------------------------------------------------ *
 * AES-256-CTR stream cipher, offset past the 64-byte header
 * ------------------------------------------------------------------ */

function newStream(key, iv) {
  // CTR is its own inverse, so the same construction both encrypts towards the
  // DC and decrypts back towards the client. One instance per direction: they
  // advance independently and must not share a counter.
  const cipher = crypto.createCipheriv('aes-256-ctr', key, iv);
  cipher.update(Buffer.alloc(INIT_LEN)); // discard the header's keystream
  return cipher;
}

function readHeader(init) {
  if (!Buffer.isBuffer(init) || init.length < INIT_LEN) return null;
  return {
    key: Buffer.from(init.subarray(KEY_OFF, KEY_OFF + KEY_LEN)),
    iv: Buffer.from(init.subarray(IV_OFF, IV_OFF + IV_LEN)),
  };
}

/* ------------------------------------------------------------------ *
 * WebSocket framing (server side: read masked, write unmasked)
 * ------------------------------------------------------------------ */

function encodeFrame(payload, opcode) {
  const type = opcode === undefined ? 0x2 : opcode;
  const length = payload.length;
  let header;

  if (length < 126) {
    header = Buffer.alloc(2);
    header[1] = length;
  } else if (length < 65536) {
    header = Buffer.alloc(4);
    header[1] = 126;
    header.writeUInt16BE(length, 2);
  } else {
    header = Buffer.alloc(10);
    header[1] = 127;
    header.writeBigUInt64BE(BigInt(length), 2);
  }
  header[0] = 0x80 | type; // FIN + opcode
  return Buffer.concat([header, payload]);
}

function encodeClose(code, reason) {
  const body = Buffer.alloc(2 + Buffer.byteLength(reason));
  body.writeUInt16BE(code, 0);
  body.write(reason, 2);
  return encodeFrame(body, 0x8);
}

/**
 * Incremental frame reader. TCP hands us arbitrary chunks, so a frame header
 * and its payload can arrive split across reads.
 */
class FrameReader {
  constructor(onMessage, onClose, onError) {
    this.buffer = Buffer.alloc(0);
    this.onMessage = onMessage;
    this.onClose = onClose;
    this.onError = onError;
    this.fragments = [];
    this.fragmentOpcode = 0;
    this.closed = false;
  }

  push(chunk) {
    this.buffer = this.buffer.length ? Buffer.concat([this.buffer, chunk]) : chunk;
    try {
      for (;;) {
        const frame = this.readOne();
        if (!frame) break;
        this.dispatch(frame);
        if (this.closed) break;
      }
    } catch (err) {
      this.onError(err);
    }
  }

  readOne() {
    const buf = this.buffer;
    if (buf.length < 2) return null;

    const first = buf[0];
    const second = buf[1];
    const fin = (first & 0x80) !== 0;
    const opcode = first & 0x0f;
    const masked = (second & 0x80) !== 0;
    let length = second & 0x7f;
    let offset = 2;

    if (length === 126) {
      if (buf.length < offset + 2) return null;
      length = buf.readUInt16BE(offset);
      offset += 2;
    } else if (length === 127) {
      if (buf.length < offset + 8) return null;
      const big = buf.readBigUInt64BE(offset);
      if (big > BigInt(MAX_MESSAGE_BYTES)) throw new Error('frame too large');
      length = Number(big);
      offset += 8;
    }

    if (length > MAX_MESSAGE_BYTES) throw new Error('frame too large');

    let mask = null;
    if (masked) {
      if (buf.length < offset + 4) return null;
      mask = buf.subarray(offset, offset + 4);
      offset += 4;
    }

    if (buf.length < offset + length) return null;

    const payload = Buffer.from(buf.subarray(offset, offset + length));
    if (mask) {
      for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
    }
    this.buffer = buf.subarray(offset + length);
    return { fin, opcode, payload };
  }

  dispatch(frame) {
    switch (frame.opcode) {
      case 0x0: // continuation
      case 0x1: // text
      case 0x2: // binary
        if (frame.opcode !== 0x0) this.fragmentOpcode = frame.opcode;
        this.fragments.push(frame.payload);
        if (this.fragments.reduce((n, b) => n + b.length, 0) > MAX_MESSAGE_BYTES) {
          throw new Error('message too large');
        }
        if (frame.fin) {
          const whole = Buffer.concat(this.fragments);
          this.fragments = [];
          this.onMessage(this.fragmentOpcode, whole);
        }
        break;
      case 0x8: // close
        this.closed = true;
        this.onClose();
        break;
      case 0x9: // ping -> pong
        break;
      case 0xa: // pong
        break;
      default:
        throw new Error('bad opcode ' + frame.opcode);
    }
  }
}

/* ------------------------------------------------------------------ *
 * Session: one client tunnel onto one DC
 * ------------------------------------------------------------------ */

class Session {
  constructor(socket, dc, id) {
    this.socket = socket;
    this.dc = dc;
    this.id = id;
    this.up = null;      // client -> DC
    this.down = null;    // DC -> client
    this.tcp = null;
    this.headerReady = false;
    this.closed = false;
    this.lastActivity = Date.now();
    this.idleTimer = null;
  }

  start() {
    this.socket.setNoDelay(true);
    this.socket.on('error', (err) => {
      debug('client socket error', err.message);
      this.close();
    });
    this.socket.on('close', () => this.close());
    this.armIdle();
  }

  armIdle() {
    if (this.idleTimer) clearTimeout(this.idleTimer);
    this.idleTimer = setTimeout(() => {
      log('session', this.id, 'idle timeout');
      this.close();
    }, IDLE_TIMEOUT_MS);
  }

  touch() {
    this.lastActivity = Date.now();
    this.armIdle();
  }

  send(payload) {
    if (this.closed || this.socket.destroyed) return;
    this.socket.write(encodeFrame(payload));
  }

  onMessage(opcode, payload) {
    if (opcode !== 0x2) return; // the stream is binary only
    this.touch();

    if (!this.headerReady) {
      const header = readHeader(payload);
      if (!header) {
        stats.rejected++;
        log('session', this.id, 'rejected: first message was', payload.length,
            'bytes, need a', INIT_LEN, 'byte header');
        this.close(1002, 'bad header');
        return;
      }
      this.headerReady = true;
      this.up = newStream(header.key, header.iv);
      this.down = newStream(header.key, header.iv);
      this.openDc();
      return;
    }

    if (!this.tcp || !this.up) return;
    const cipherText = this.up.update(payload);
    this.tcp.write(cipherText);
    stats.bytesUp += cipherText.length;
  }

  openDc() {
    const target = DC_IPS[this.dc] || DC_IPS[2];
    debug('session', this.id, 'opening DC', this.dc, target);
    this.tcp = net.connect({ host: target, port: 443 });
    this.tcp.setNoDelay(true);

    this.tcp.on('connect', () => {
      stats.established++;
      stats.live++;
      log('session', this.id, 'established to DC', this.dc, target);
    });

    this.tcp.on('data', (chunk) => {
      if (this.closed) return;
      this.touch();
      const plain = this.down.update(chunk);
      stats.bytesDown += plain.length;
      this.send(plain);
    });

    this.tcp.on('error', (err) => {
      stats.failed++;
      log('session', this.id, 'DC error:', err.message);
      this.close();
    });

    this.tcp.on('close', () => {
      debug('session', this.id, 'DC closed');
      this.close();
    });
  }

  close(code, reason) {
    if (this.closed) return;
    this.closed = true;
    stats.live = Math.max(0, stats.live - 1);
    if (this.idleTimer) clearTimeout(this.idleTimer);
    try { if (this.tcp) this.tcp.destroy(); } catch (err) { /* ignore */ }
    try {
      if (code && !this.socket.destroyed) this.socket.write(encodeClose(code, reason));
      this.socket.end();
    } catch (err) { /* ignore */ }
    try { this.socket.destroy(); } catch (err) { /* ignore */ }
  }
}

/* ------------------------------------------------------------------ *
 * HTTP + upgrade handling
 * ------------------------------------------------------------------ */

let sessionSeq = 0;

function onUpgrade(req, socket, head) {
  const key = req.headers['sec-websocket-key'];
  if (!key || (req.headers.upgrade || '').toLowerCase() !== 'websocket') {
    socket.write('HTTP/1.1 400 Bad Request\r\n\r\n');
    socket.destroy();
    return;
  }

  const accept = crypto
    .createHash('sha1')
    .update(key + WS_GUID)
    .digest('base64');

  const url = new URL(req.url, 'https://placeholder');
  const dc = parseInt(url.searchParams.get('dc') || '2', 10);

  stats.connections++;

  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
    'Upgrade: websocket\r\n' +
    'Connection: Upgrade\r\n' +
    'Sec-WebSocket-Accept: ' + accept + '\r\n' +
    '\r\n'
  );

  if (socket.setNoDelay) socket.setNoDelay(true);

  const id = ++sessionSeq;
  const session = new Session(socket, Number.isFinite(dc) ? dc : 2, id);
  session.start();

  const reader = new FrameReader(
    (opcode, payload) => session.onMessage(opcode, payload),
    () => session.close(),
    (err) => {
      debug('session', id, 'protocol error:', err.message);
      session.close(1002, 'protocol error');
    }
  );

  socket.on('data', (chunk) => reader.push(chunk));
  if (head && head.length) reader.push(head);

  log('session', id, 'open from', req.socket.remoteAddress, 'dc=' + dc,
      'x-install=' + (req.headers['x-install'] || '-'),
      'x-cred=' + (req.headers['x-cred'] ? 'yes' : 'no'));
}

function statusJson() {
  return JSON.stringify({
    ...stats,
    uptimeSeconds: Math.round(process.uptime()),
    memoryMB: Math.round(process.memoryUsage().rss / 1048576),
  });
}

function onRequest(req, res) {
  if (req.url === '/healthz') {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(statusJson());
    return;
  }
  res.writeHead(404, { 'content-type': 'text/plain' });
  res.end('relay: websocket endpoint is /apiws?dc=N\n');
}

function start() {
  let server;
  if (args.cert && args.key) {
    server = https.createServer(
      { cert: fs.readFileSync(args.cert), key: fs.readFileSync(args.key) },
      onRequest
    );
    log('listening on https://' + HOST + ':' + PORT);
  } else {
    server = http.createServer(onRequest);
    log('listening on http://' + HOST + ':' + PORT + ' (no TLS: put a terminating proxy in front)');
  }

  server.on('upgrade', onUpgrade);
  server.on('clientError', (err, socket) => {
    if (socket.writable) socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');
  });

  server.listen(PORT, HOST, () => {
    log('relay ready. dc map:', JSON.stringify(DC_IPS));
  });

  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.on(signal, () => {
      log('shutting down');
      stats.established = stats.established; // keep the summary intact
      log('stats', statusJson());
      process.exit(0);
    });
  }
}

start();
