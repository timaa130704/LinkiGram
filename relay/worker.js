/**
 * LinkiGram relay for Cloudflare Workers + Durable Objects.
 *
 * Same job as relay/server.js: terminate the WebSocket that WsBypassCore
 * speaks, read the 64-byte obfuscated2 header off it, derive AES-256-CTR from
 * bytes 8..40 (key) and 40..56 (iv) with the header's own 64 bytes of
 * keystream discarded, and pipe the result to the Telegram DC named by dc=.
 * One binary message in, one chunk of ciphertext out; there is no length
 * framing because the client forwards the proxy stream in whatever chunks it
 * reads.
 *
 * The Durable Object holds the state. Workers isolates are ephemeral and
 * requests are load-balanced, so the TCP socket to the DC and the two cipher
 * states have to live in something that outlives a single request.
 *
 * AES note: crypto.subtle has no streaming mode and its only async primitive
 * here is a whole-block call, so each 16-byte counter block is produced with
 * AES-ECB and the keystream is consumed in order. CTR is its own inverse, so
 * the same construction encrypts towards the DC and decrypts back; the two
 * directions keep separate counters because they advance independently.
 *
 * Deploy with a Durable Object binding named RELAY, then route the hostname at
 * this worker.
 */

import { DurableObject } from 'cloudflare:workers';

const INIT_LEN = 64;
const KEY_OFF = 8;
const KEY_LEN = 32;
const IV_OFF = 40;
const IV_LEN = 16;
const BLOCK = 16;

/** Telegram DC addresses, matching DIRECT_DC_IP in the client. */
const DC_IPS = {
  1: '149.154.175.50',
  2: '149.154.167.51',
  3: '149.154.175.100',
  4: '149.154.167.91',
  5: '149.154.171.5',
  203: '91.105.192.100',
};

const MAX_MESSAGE_BYTES = 1024 * 1024;
const IDLE_TIMEOUT_MS = 10 * 60 * 1000;

/** All-zero IV: makes single-block AES-CBC identical to ECB. */
const ZERO_IV = new Uint8Array(BLOCK);

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === '/healthz') {
      return Response.json({ ok: true, edge: true });
    }

    if (url.pathname !== '/apiws') {
      return new Response(
        'relay\n  websocket: /apiws?dc=N\n  health:      /healthz\n',
        { status: 200, headers: { 'content-type': 'text/plain' } }
      );
    }

    if (request.headers.get('Upgrade') !== 'websocket') {
      return new Response('expected a websocket upgrade\n', {
        status: 426,
        headers: { upgrade: 'websocket' },
      });
    }

    const dc = normaliseDc(url.searchParams.get('dc'));

    // A fresh object per connection: unrelated clients must never share a
    // socket, and objects are cheap enough for this.
    const id = env.RELAY.idFromName(crypto.randomUUID());
    const stub = env.RELAY.get(id);

    return stub.fetch('https://relay.internal/apiws', { dc });
  },
};

function normaliseDc(value) {
  const parsed = parseInt(value ?? '2', 10);
  return DC_IPS[parsed] ? parsed : 2;
}

export class Relay extends DurableObject {
  constructor(ctx, state) {
    super(ctx, state);
    this.ctx = ctx;
    this.state = state;

    this.client = null;
    this.tcp = null;
    this.writer = null;

    this.up = null;
    this.down = null;
    this.headerReady = false;
    this.closed = false;
    this.dc = 2;
    this.idleTimer = null;

    /** Serialises the whole session: WebSocket messages, TCP reads and the
     *  writes back out all have to keep their order and share one cipher
     *  position per direction. */
    this.queue = Promise.resolve();
  }

  onMessage(message) {
    this.enqueue(() => this.handleClientMessage(message));
  }

  enqueue(job) {
    this.queue = this.queue.then(job).catch((err) => {
      this.close('session error: ' + describe(err));
    });
    return this.queue;
  }

  async fetch(request) {
    this.dc = normaliseDc(new URL(request.url).searchParams.get('dc'));

    const pair = new WebSocketPair();
    this.client = pair[1];
    this.armIdle();

    this.client.addEventListener('message', (event) => this.onMessage(event.data));
    this.client.addEventListener('close', () => this.close('client closed'));
    this.client.addEventListener('error', () => this.close('client error'));

    this.ctx.acceptWebSocket(pair[0]);
    return new Response(null, { status: 101, webSocket: pair[0] });
  }

  /* Hibernation can return only the socket, so the state that matters -- the
   * header, and therefore both cipher positions -- is re-derived from it. */
  async webSocketMessage(socket, message) {
    this.client = socket;
    this.armIdle();
    this.onMessage(message);
    await this.queue;
  }

  async webSocketClose(socket, code, reason) {
    this.close('client closed: ' + reason);
  }

  async webSocketError(socket, error) {
    this.close('client socket error');
  }

  async handleClientMessage(data) {
    if (this.closed || typeof data === 'string') return;
    this.touch();

    const payload = toBytes(data);
    if (!payload || payload.length > MAX_MESSAGE_BYTES) {
      this.close('bad frame');
      return;
    }

    if (!this.headerReady) {
      if (payload.length < INIT_LEN) {
        this.close('short header');
        return;
      }
      this.up = await Counter.create(
        payload.slice(KEY_OFF, KEY_OFF + KEY_LEN),
        payload.slice(IV_OFF, IV_OFF + IV_LEN)
      );
      this.down = await Counter.create(
        payload.slice(KEY_OFF, KEY_OFF + KEY_LEN),
        payload.slice(IV_OFF, IV_OFF + IV_LEN)
      );
      this.headerReady = true;
      await this.openDc();
      return;
    }

    if (!this.writer) return;
    await this.writer.write(await this.up.apply(payload));
  }

  async openDc() {
    const host = DC_IPS[this.dc];
    let socket;
    try {
      socket = connect({ hostname: host, port: 443 });
    } catch (err) {
      this.close('dc connect failed: ' + describe(err));
      return;
    }

    this.tcp = socket;
    this.writer = socket.writable.getWriter();

    const pump = async () => {
      const reader = socket.readable.getReader();
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        this.touch();
        await this.client.send(await this.down.apply(value));
      }
    };

    pump().catch((err) => this.close('dc read ended: ' + describe(err)));
  }

  armIdle() {
    if (this.idleTimer) clearTimeout(this.idleTimer);
    this.idleTimer = setTimeout(() => this.close('idle timeout'), IDLE_TIMEOUT_MS);
  }

  touch() {
    this.armIdle();
  }

  close(reason) {
    if (this.closed) return;
    this.closed = true;
    if (this.idleTimer) clearTimeout(this.idleTimer);
    try { this.writer?.abort(); } catch (err) { /* already gone */ }
    try { this.tcp?.close(); } catch (err) { /* already gone */ }
    try { this.client?.close(1000, 'closing'); } catch (err) { /* already gone */ }
  }
}

/* ------------------------------------------------------------------ *
 * AES-256-CTR over a block-at-a-time AES-ECB primitive
 * ------------------------------------------------------------------ */

class Counter {
  /**
   * The header itself is encrypted with the same keystream, so the stream has
   * to start 64 bytes in -- the Node relay does this with update(ZERO_64) and
   * the client does update(ZERO_64) on its own side. Skipping it here would
   * desynchronise the very first byte and the DC would drop the connection.
   */
  static async create(key, iv) {
    const cryptoKey = await crypto.subtle.importKey(
      'raw',
      key,
      { name: 'AES-CBC' },
      false,
      ['encrypt']
    );
    const counter = new Counter(cryptoKey, iv.slice());
    for (let skipped = 0; skipped < INIT_LEN / BLOCK; skipped++) {
      counter.increment();
    }
    return counter;
  }

  constructor(cryptoKey, counter) {
    this.cryptoKey = cryptoKey;
    this.counter = counter;
    this.keystream = new Uint8Array(0);
    this.offset = 0;
  }

  async apply(bytes) {
    const out = new Uint8Array(bytes.length);
    for (let i = 0; i < bytes.length; i++) {
      if (this.offset >= this.keystream.length) {
        this.keystream = await this.nextBlock();
        this.offset = 0;
      }
      out[i] = bytes[i] ^ this.keystream[this.offset++];
    }
    return out;
  }

  /**
   * Workers has no ECB primitive, so a counter block is produced by encrypting
   * it as a single CBC block with an all-zero IV: C = E(0 XOR counter) =
   * E(counter), which is exactly the CTR keystream. Passing the key as the IV
   * instead would give E(key XOR counter) and silently produce a different, and
   * wrong, stream.
   *
   * CBC also PKCS7-pads, so the result is 32 bytes and only the first block is
   * ciphertext.
   */
  async nextBlock() {
    const input = new Uint8Array(BLOCK);
    input.set(this.counter);
    const encrypted = new Uint8Array(
      await crypto.subtle.encrypt({ name: 'AES-CBC', iv: ZERO_IV }, this.cryptoKey, input)
    );
    this.increment();
    return encrypted.subarray(0, BLOCK);
  }

  increment() {
    for (let i = this.counter.length - 1; i >= 0; i--) {
      this.counter[i] = (this.counter[i] + 1) & 0xff;
      if (this.counter[i] !== 0) break;
    }
  }
}

function toBytes(data) {
  if (data instanceof ArrayBuffer) return new Uint8Array(data);
  if (ArrayBuffer.isView(data)) {
    return new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
  }
  return null;
}

function describe(err) {
  if (!err) return 'unknown';
  return err.message ? err.message : String(err);
}
