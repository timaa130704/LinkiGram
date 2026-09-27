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
// connect() is not a global in Workers -- without this import every attempt to
// reach a DC throws "connect is not defined" and the tunnel carries bytes one
// way only.
import { connect } from 'cloudflare:sockets';

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

/** How much keystream may be regenerated to restore a position. Past this the
 *  session is treated as dead rather than replayed. */
const MAX_REPLAY_BYTES = 4 * 1024 * 1024;

/** All-zero IV: makes single-block AES-CBC identical to ECB. */
const ZERO_IV = new Uint8Array(BLOCK);

/** First bytes a DC must not see, or it treats the connection as noise. */
const RESERVED_FIRST_BYTES = new Set([0xef]);

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === '/healthz') {
      return Response.json({ ok: true, edge: true });
    }

    if (url.pathname === '/dctest') {
      return Response.json(await dcTest(url.searchParams.get('dc')));
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

    // The upgrade request has to be forwarded with its headers intact. A
    // synthetic request without "Upgrade: websocket" makes the runtime reject
    // the 101 the object returns, and the dc has to travel in the URL because a
    // stub's second fetch argument is a RequestInit, where extra properties are
    // dropped.
    const forwarded = new Request('https://relay.internal/apiws?dc=' + dc, {
      method: 'GET',
      headers: request.headers,
    });
    return stub.fetch(forwarded);
  },
};

/**
 * Build a fresh obfuscated2 header for the DC, the way the client's
 * generateRelayInit does: random bytes with the reserved prefixes avoided, the
 * key and IV taken from bytes 8..40 and 40..56, and the protocol tag plus DC
 * index encrypted into the last 8 bytes with the same keystream the payload
 * uses.
 */
async function makeDcHeader(dc) {
  const rnd = new Uint8Array(INIT_LEN);
  for (let guard = 0; guard < 64; guard++) {
    crypto.getRandomValues(rnd);
    if (RESERVED_FIRST_BYTES.has(rnd[0])) continue;
    let reserved = false;
    for (let i = 0; i < 4; i++) {
      if (rnd[i] === 0x16 && rnd[i + 1] === 0x03) reserved = true;
    }
    if (rnd[0] === 0x48 && rnd[1] === 0x45 && rnd[2] === 0x41 && rnd[3] === 0x44) reserved = true;
    if (rnd[0] === 0x50 && rnd[1] === 0x4f && rnd[2] === 0x53 && rnd[3] === 0x54) reserved = true;
    if (rnd[0] === 0x47 && rnd[1] === 0x45 && rnd[2] === 0x54 && rnd[3] === 0x20) reserved = true;
    if (reserved) continue;
    if (rnd[4] === 0 && rnd[5] === 0 && rnd[6] === 0 && rnd[7] === 0) continue;
    break;
  }

  const key = rnd.slice(KEY_OFF, KEY_OFF + KEY_LEN);
  const iv = rnd.slice(IV_OFF, IV_OFF + IV_LEN);

  // The whole 64 bytes are encrypted from counter zero; the payload that
  // follows continues from byte 64, which is why every Counter skips 64 bytes.
  const stream = await Counter.create(key, iv);
  const head = await stream.apply(rnd.subarray(0, 56));

  const tail = new Uint8Array(8);
  tail.set(new Uint8Array([0xee, 0xee, 0xee, 0xee]), 0); // intermediate
  tail[4] = dc & 0xff;
  tail[5] = (dc >> 8) & 0xff;
  crypto.getRandomValues(tail.subarray(6, 8));
  const tailCipher = await stream.apply(tail);

  return {
    header: new Uint8Array([...head, ...tailCipher]),
    key,
    iv,
  };
}

function normaliseDc(value) {
  const parsed = parseInt(value ?? '2', 10);
  return DC_IPS[parsed] ? parsed : 2;
}

/**
 * Open a bare TCP connection to a DC and report what came back.
 *
 * This isolates the one thing that decides whether a Workers relay can work at
 * all: whether the runtime is allowed to reach the Telegram DCs on 443, and
 * whether the DC answers a connection it has not been given a handshake for.
 */
async function dcTest(value) {
  const dc = normaliseDc(value);
  const host = DC_IPS[dc];
  const started = Date.now();

  let socket;
  try {
    socket = connect({ hostname: host, port: 443, secureTransport: 'off' });
  } catch (err) {
    return { dc, host, connectThrew: describe(err), ms: Date.now() - started };
  }

  try {
    const reader = socket.readable.getReader();
    const writer = socket.writable.getWriter();

    // Build a header the way the client does, then a real req_pq_multi, and
    // see whether the DC answers. auth_key_id is int128, not int32 -- getting
    // that wrong produces a stream the DC silently ignores, which looks
    // exactly like a dead relay.
    const init = new Uint8Array(INIT_LEN);
    crypto.getRandomValues(init);
    const key = init.slice(KEY_OFF, KEY_OFF + KEY_LEN);
    const iv = init.slice(IV_OFF, IV_OFF + IV_LEN);

    // Tag the header as intermediate and stamp the dc index, as the client's
    // generateRelayInit does, so the DC recognises the stream.
    const header = await stampHeader(init, key, dc);
    await writer.write(header);

    const up = await Counter.create(key, iv);
    const down = await Counter.create(key, iv);

    const msg = new Uint8Array(52);
    const view = new DataView(msg.buffer);
    // auth_key_id int128 -> left as zero
    view.setBigInt64(16, 0x7abe77ecn, true);  // req_pq_multi constructor
    view.setInt32(24, 0, true);               // seq_no
    view.setInt32(28, 20, true);              // payload length
    view.setUint32(32, 0x7abe77ec, true);     // constructor
    // bytes 36..52 stay zero: the 16-byte nonce
    await writer.write(await up.apply(msg));

    const read = await Promise.race([
      reader.read().then((r) => ({ read: r })),
      new Promise((resolve) => setTimeout(() => resolve({ timeout: true }), 8000)),
    ]);

    writer.abort().catch(() => {});
    socket.close();

    if (read.timeout) {
      return {
        dc, host, ok: true, ms: Date.now() - started, replyBytes: 0,
        note: 'connected, sent header+req_pq_multi, no reply in 8s',
      };
    }

    const chunk = read.read.value || new Uint8Array(0);
    const plain = await down.apply(chunk);
    return {
      dc, host, ok: true, ms: Date.now() - started, replyBytes: chunk.length,
      constructor: plain.length >= 20 ? hex32(plain, 16) : null,
      firstBytes: hex(plain.subarray(0, 24)),
    };
  } catch (err) {
    try { socket.close(); } catch (e) { /* ignore */ }
    return { dc, host, ok: false, err: describe(err), ms: Date.now() - started };
  }
}

/** Encrypt the 64-byte header and stamp the protocol tag and dc index in it. */
async function stampHeader(init, key, dc) {
  const proto = new Uint8Array([0xee, 0xee, 0xee, 0xee]); // intermediate
  const fromZero = await Counter.create(key, init.slice(IV_OFF, IV_OFF + IV_LEN));
  const encrypted = await fromZero.apply(init);

  // Re-encrypt the last 8 bytes, which is where the tag and index live.
  const stream = await Counter.create(key, init.slice(IV_OFF, IV_OFF + IV_LEN));
  const head = await stream.apply(init.subarray(0, 56));
  const tail = new Uint8Array(8);
  tail.set(proto, 0);
  tail[4] = dc & 0xff;
  tail[5] = (dc >> 8) & 0xff;
  const tailCipher = await stream.apply(tail);
  return new Uint8Array([...head, ...tailCipher]);
}

function hex(bytes) {
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('');
}

function hex32(bytes, at) {
  const v = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  return '0x' + v.getUint32(at, true).toString(16).padStart(8, '0');
}

export class Relay extends DurableObject {
  constructor(ctx, state) {
    super(ctx, state);
    this.ctx = ctx;
    this.state = state;

    this.client = null;
    this.tcp = null;
    this.writer = null;

    this.headerReady = false;
    this.closed = false;
    this.dc = 2;
    this.echo = false;
    this.idleTimer = null;

    /** Restored from storage when the object is reconstructed. A hibernating
     *  Durable Object comes back as a fresh instance with only the WebSocket
     *  carried over, so anything kept in a field is gone: the key material,
     *  which DC we picked, and how far each cipher has advanced. That is why
     *  the tunnel came up and then reported down=0 -- the ciphers were missing
     *  by the time the first payload arrived. */
    this.restored = null;
    this.restoring = null;

    /** Serialises the whole session: WebSocket messages, TCP reads and the
     *  writes back out all have to keep their order and share one cipher
     *  position per direction. */
    this.queue = Promise.resolve();
  }

  /**
   * Read persisted session state once per object lifetime.
   *
   * A storage read is a round trip, so it is memoised behind `restoring` and
   * cleared whenever the session state changes, which keeps the fast path -- a
   * connection that never hibernates -- free of storage traffic.
   */
  async loadState() {
    if (this.restored) return this.restored;
    if (!this.restoring) {
      this.restoring = (async () => {
        const stored = await this.ctx.storage.get('session');
        this.restored = stored || null;
        return this.restored;
      })();
    }
    return this.restoring;
  }

  async saveState(patch) {
    this.restored = { ...(this.restored || {}), ...patch };
    this.restoring = null;
    await this.ctx.storage.put('session', this.restored);
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
    const here = new URL(request.url);
    this.dc = normaliseDc(here.searchParams.get('dc'));

    // ?echo=1 sends the client's own bytes straight back, encrypted with the
    // client's own key. It touches no DC, so it isolates the client-facing
    // half: if the client reports bytes flowing in that mode, the WebSocket
    // framing and the client-side cipher are right and only the DC session is
    // wrong.
    this.echo = here.searchParams.get('echo') === '1';

    const pair = new WebSocketPair();
    this.client = pair[1];
    this.armIdle();

    // The server end has to be handed to acceptWebSocket -- the one that is
    // returned in the Response is the client end. Doing both to the same socket
    // is rejected outright ("Can't return WebSocket in a Response after calling
    // acceptWebSocket()"), and returning the socket without accepting it means
    // nothing is listening: the client's bytes arrived and were dropped, which
    // is why the tunnel reported down=0.
    //
    // With the socket accepted, messages arrive in webSocketMessage rather than
    // through the 'message' listener.
    this.ctx.acceptWebSocket(pair[1]);
    return new Response(null, { status: 101, webSocket: pair[0] });
  }

  /* Hibernation returns only the socket, so the key material and the cipher
   * positions come back out of storage. */
  async webSocketMessage(socket, message) {
    this.client = socket;
    this.armIdle();
    // Anything thrown here is swallowed by the runtime: the message is dropped
    // and the socket is simply left open, which is indistinguishable from a
    // relay that never received anything. So failures are turned into a close
    // frame that says what went wrong.
    try {
      await this.restoreIfNeeded();
    } catch (err) {
      this.close('restore failed: ' + describe(err));
      return;
    }
    this.onMessage(message);
    await this.queue;
  }

  async restoreIfNeeded() {
    if (this.headerReady) return;
    const state = await this.loadState();
    if (!state || !state.clientKey) return;
    this.dc = state.dc || 2;
    this.echo = !!state.echo;
    const clientKey = fromBase64(state.clientKey);
    const clientIv = fromBase64(state.clientIv);
    const dcKey = fromBase64(state.dcKey);
    const dcIv = fromBase64(state.dcIv);
    this.fromClient = await Counter.create(clientKey, clientIv);
    this.toClient = await Counter.create(clientKey, clientIv);
    this.toDc = await Counter.create(dcKey, dcIv);
    this.fromDc = await Counter.create(dcKey, dcIv);
    await this.skipTo(this.fromClient, state.upPos || 0);
    await this.skipTo(this.toDc, state.upPos || 0);
    await this.skipTo(this.fromDc, state.downPos || 0);
    await this.skipTo(this.toClient, state.downPos || 0);
    this.upBytes = state.upBytes || 0;
    this.bytesDown = state.downBytes || 0;
    this.header = fromBase64(state.header);
    this.headerReady = true;
    if (!this.echo) await this.openDc();
  }

  /**
   * Advance a cipher to a byte offset without keeping the output.
   *
   * A gap this large means the object was evicted for a while; the session is
   * not worth reconstructing, so it is closed rather than replayed. A gap that
   * big would also mean the TCP socket is long gone anyway.
   */
  async skipTo(counter, offset) {
    if (!counter || counter.consumed <= offset) return;
    if (offset - counter.consumed > MAX_REPLAY_BYTES) {
      this.close('cipher gap too large to replay');
      return;
    }
    await counter.skip(offset - counter.consumed);
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
      // This is a proxy, not a pipe. The client and the DC each get their own
      // obfuscated2 session with its own key and IV, and the relay translates
      // between them: four cipher states, not two.
      //
      // Reusing the client's key and IV towards the DC gets the tunnel up but
      // never a byte back, because the client's header describes the local
      // connection rather than being a DC-side handshake.
      const clientKey = payload.slice(KEY_OFF, KEY_OFF + KEY_LEN);
      const clientIv = payload.slice(IV_OFF, IV_OFF + IV_LEN);

      const dcSide = await makeDcHeader(this.dc);

      this.fromClient = await Counter.create(clientKey, clientIv);
      this.toDc = await Counter.create(dcSide.key, dcSide.iv);
      this.fromDc = await Counter.create(dcSide.key, dcSide.iv);
      this.toClient = await Counter.create(clientKey, clientIv);

      this.upBytes = 0;
      this.bytesDown = 0;
      this.header = dcSide.header;
      this.headerReady = true;

      await this.saveState({
        dc: this.dc,
        echo: this.echo,
        clientKey: toBase64(clientKey),
        clientIv: toBase64(clientIv),
        dcKey: toBase64(dcSide.key),
        dcIv: toBase64(dcSide.iv),
        header: toBase64(dcSide.header),
        upPos: 0,
        downPos: 0,
        upBytes: 0,
        downBytes: 0,
      });

      if (!this.echo) await this.openDc();
      return;
    }

    if (this.echo) {
      // No DC involved: reflect the payload back under the client's own cipher.
      const back = await this.toClient.apply(await this.fromClient.apply(payload));
      this.bytesDown += back.length;
      await this.client.send(back);
      await this.persistCounters();
      return;
    }

    if (!this.writer) return;
    const plain = await this.fromClient.apply(payload);
    const cipherText = await this.toDc.apply(plain);
    this.upBytes = (this.upBytes || 0) + cipherText.length;
    await this.writer.write(cipherText);
    await this.persistCounters();
  }

  /** Counters are written after each message so a woken object can catch up. */
  async persistCounters() {
    await this.saveState({
      upPos: this.toDc ? this.toDc.consumed : 0,
      downPos: this.toClient ? this.toClient.consumed : 0,
      upBytes: this.upBytes || 0,
      downBytes: this.bytesDown || 0,
    });
  }

  async openDc() {
    const host = DC_IPS[this.dc];
    let socket;
    try {
      socket = connect({ hostname: host, port: 443, secureTransport: 'off' });
    } catch (err) {
      console.log('relay: connect threw', describe(err));
      this.close('dc connect failed: ' + describe(err));
      return;
    }

    // The DC side is already TLS: connect() with secureTransport 'on' does the
    // handshake as part of the connect, and there is no startTls() call. Using
    // 'starttls' plus startTls() is rejected by the runtime here -- it insists
    // the option was not set even though it was.
    //
    // No expectedServerHostname: the DC is reached by IP and its certificate
    // names domains, so pinning one would fail. What protects this leg is that
    // the payload it carries is already end-to-end encrypted with the key from
    // the client's own handshake, which this relay never sees in the clear.

    this.tcp = socket;
    this.writer = socket.writable.getWriter();

    // Hand the DC its header before any payload.
    if (this.header) {
      await this.writer.write(this.header);
      console.log('relay: forwarded', this.header.length, 'byte header to DC');
    }

    const pump = async () => {
      const reader = socket.readable.getReader();
      let first = true;
      for (;;) {
        const { value, done } = await reader.read();
        if (done) {
          console.log('relay: dc stream ended');
          break;
        }
        this.touch();
        // The first thing the DC says is the only diagnostic that matters here:
        // a few bytes back means it accepted the header, and what those bytes
        // are says whether it is speaking our protocol at all.
        if (first) {
          first = false;
          console.log('relay: first from DC', value.length, 'bytes:', hex(value.subarray(0, 24)));
        }
        const plain = await this.fromDc.apply(value);
        this.bytesDown += plain.length;
        const cipherText = await this.toClient.apply(plain);
        await this.client.send(cipherText);
        await this.persistCounters();
      }
    };

    this.upBytes = this.upBytes || 0;
    this.bytesDown = this.bytesDown || 0;
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
    console.log('relay: closing', reason,
      'up=' + (this.upBytes || 0), 'down=' + (this.bytesDown || 0));
    if (this.idleTimer) clearTimeout(this.idleTimer);
    try { this.writer?.abort(); } catch (err) { /* already gone */ }
    try { this.tcp?.close(); } catch (err) { /* already gone */ }
    // The reason goes into the close frame so a failing session explains
    // itself to the client instead of just vanishing.
    try { this.client?.close(1011, String(reason).slice(0, 120)); } catch (err) { /* gone */ }
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
    // Payload bytes consumed, not counting the 64-byte header skip. This is the
    // value persisted so a hibernated object can be put back where it was.
    this.consumed = 0;
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
    this.consumed += bytes.length;
    return out;
  }

  /** Burn keystream without keeping it, to reach a saved position. */
  async skip(count) {
    let left = count;
    while (left > 0) {
      if (this.offset >= this.keystream.length) {
        this.keystream = await this.nextBlock();
        this.offset = 0;
      }
      const take = Math.min(left, this.keystream.length - this.offset);
      this.offset += take;
      left -= take;
    }
    this.consumed += count;
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

/** Key material is stored as base64 so it survives a round trip through
 *  storage as plain JSON-compatible values. */
function toBase64(bytes) {
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary);
}

function fromBase64(text) {
  const binary = atob(text);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

function describe(err) {
  if (!err) return 'unknown';
  return err.message ? err.message : String(err);
}
