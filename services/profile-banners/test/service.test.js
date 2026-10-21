'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const sharp = require('sharp');
const {createService} = require('../app');
async function fixture(t) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'linki-banners-test-'));
  const calls = []; let nextMessage = 1;
  const telegram = {call: async (method, payload) => { calls.push({method, payload}); return {message_id: nextMessage++}; }};
  const config = {dataDir: dir, maxStorage: 64 * 1024 * 1024, maxPending: 10, maxUpload: 8 * 1024 * 1024,
    adminIds: ['100'], adminChat: '100', baseUrl: 'https://banners.example.com', botToken: 'fake-token', notificationDelay: 0};
  const service = createService(config, telegram, 'banner_test_bot');
  const server = http.createServer(service.handle);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = 'http://127.0.0.1:' + server.address().port;
  t.after(async () => { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); service.store.close(); await fs.rm(dir, {recursive: true, force: true}); });
  async function call(route, options = {}) {
    const response = await fetch(origin + route, options);
    return {status: response.status, body: await response.json()};
  }
  async function auth(user) {
    const registered = await call('/auth/register?user_id=' + user, {method: 'POST'});
    assert.equal(registered.status, 200);
    const code = registered.body.code;
    await service.handleUpdate({inline_query: {id: 'auth', from: {id: user}, query: code}});
    const polled = await call('/auth/poll?user_id=' + user + '&code=' + code, {method: 'POST'});
    assert.equal(polled.status, 200); assert.ok(polled.body.token);
    return polled.body.token;
  }
  async function submit(token, user, data) {
    const form = new FormData();
    form.set('file', new Blob([data]), 'arbitrary.extension'); form.set('user_id', String(user)); form.set('file_size', String(data.length));
    return call('/submit', {method: 'POST', headers: {'x-auth-token': token}, body: form});
  }
  async function card(id) {
    const until = Date.now() + 2000;
    while (Date.now() < until) {
      const row = service.store.get('SELECT * FROM submissions WHERE id=?', id);
      if (row?.admin_message) return row;
      await new Promise(resolve => setTimeout(resolve, 10));
    }
    throw new Error('Moderation card was not sent');
  }
  async function decide(id, action, actor = 100) {
    const row = await card(id);
    await service.handleUpdate({callback_query: {id: 'callback', from: {id: actor}, data: action + ':' + id,
      message: {message_id: row.admin_message, chat: {id: 100}}}});
  }
  return {dir, service, call, auth, submit, card, decide, origin, calls};
}
async function image(format = 'png') {
  return sharp({create: {width: 1300, height: 500, channels: 3, background: '#4581bc'}}).toFormat(format).toBuffer();
}

test('inline authentication binds the challenge to the actual Telegram sender', async t => {
  const f = await fixture(t);
  const reg = await f.call('/auth/register?user_id=200', {method: 'POST'});
  const route = '/auth/poll?user_id=200&code=' + reg.body.code;
  await f.service.handleUpdate({inline_query: {id: 'wrong', from: {id: 201}, query: reg.body.code}});
  assert.equal((await f.call(route, {method: 'POST'})).body.pending, true);
  await f.service.handleUpdate({inline_query: {id: 'right', from: {id: 200}, query: reg.body.code}});
  assert.ok((await f.call(route, {method: 'POST'})).body.token);
  assert.equal((await f.call(route, {method: 'POST'})).status, 404);
  assert.equal((await f.call('/settings', {method: 'POST', headers: {'content-type': 'application/json'}, body: JSON.stringify({user_id: 200, hide_avatar: true})})).status, 401);
});

test('only the owner can upload/change settings and only the moderator can publish', async t => {
  const f = await fixture(t); const token = await f.auth(200); const data = await image();
  assert.equal((await f.submit(token, 201, data)).status, 403);
  assert.equal((await f.call('/settings', {method: 'POST', headers: {'x-auth-token': token}, body: JSON.stringify({user_id: 201, hide_avatar: true})})).status, 403);
  const submission = await f.submit(token, 200, data); assert.equal(submission.status, 200);
  const id = submission.body.submission_id;
  assert.deepEqual(await fs.readFile(path.join(f.dir, 'pending', id)), data);
  assert.deepEqual(await fs.readdir(path.join(f.dir, 'approved')), []);
  assert.equal((await f.call('/get/200')).body.has_banner, false);
  assert.equal((await fetch(f.origin + '/banners/' + id + '.webp')).status, 404);
  assert.equal((await fetch(f.origin + '/pending/' + id)).status, 404);
  await f.decide(id, 'approve', 201);
  assert.equal((await f.call('/status/200')).body.status, 'pending');
  await f.decide(id, 'approve');
  const approved = (await f.call('/get/200')).body;
  assert.equal(approved.type, 'webp'); assert.equal(approved.has_banner, true);
  const webp = await fs.readFile(path.join(f.dir, 'approved', id + '.webp'));
  const meta = await sharp(webp).metadata();
  assert.equal(meta.format, 'webp'); assert.equal(meta.width, 1200); assert.equal(meta.height, 400);
  assert.equal(meta.pages || 1, 1);
  await assert.rejects(fs.stat(path.join(f.dir, 'pending', id)), {code: 'ENOENT'});
  const publicResponse = await fetch(f.origin + '/banners/' + id + '.webp');
  assert.equal(publicResponse.status, 200); assert.equal(publicResponse.headers.get('content-type'), 'image/webp');
  await publicResponse.arrayBuffer();
  await f.decide(id, 'approve'); // Replayed callbacks cannot publish a second time.
  assert.equal((await f.call('/get/200')).body.version, id);
});

test('rejecting a replacement keeps the last approved banner and private bytes are deleted', async t => {
  const f = await fixture(t); const token = await f.auth(200); const data = await image('jpeg');
  const first = await f.submit(token, 200, data); await f.decide(first.body.submission_id, 'approve');
  const second = await f.submit(token, 200, data); const id = second.body.submission_id;
  assert.equal((await f.call('/status/200')).body.has_banner, true);
  assert.equal((await f.call('/get/200')).body.version, first.body.submission_id);
  await f.decide(id, 'reject');
  assert.equal((await f.call('/status/200')).body.status, 'rejected');
  assert.equal((await f.call('/get/200')).body.version, first.body.submission_id);
  await assert.rejects(fs.stat(path.join(f.dir, 'pending', id)), {code: 'ENOENT'});
});

test('SVG, invalid bytes and files over 8 MB never enter moderation', async t => {
  const f = await fixture(t); const token = await f.auth(200);
  for (const data of [Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"/>'), Buffer.from('not an image')]) {
    assert.equal((await f.submit(token, 200, data)).status, 422);
  }
  assert.equal((await f.submit(token, 200, Buffer.alloc(8 * 1024 * 1024 + 1))).status, 413);
  assert.equal(f.service.store.get('SELECT COUNT(*) AS n FROM submissions').n, 0);
  assert.deepEqual(await fs.readdir(path.join(f.dir, 'pending')), []);
});

test('concurrent uploads create at most one pending submission for the same user', async t => {
  const f = await fixture(t); const token = await f.auth(200); const data = await image();
  const results = await Promise.all([f.submit(token, 200, data), f.submit(token, 200, data)]);
  assert.deepEqual(results.map(r => r.status).sort(), [200, 409]);
  assert.equal(f.service.store.get("SELECT COUNT(*) AS n FROM submissions WHERE status='pending'").n, 1);
  const successful = results.find(r => r.status === 200); await f.card(successful.body.submission_id);
});

test('PNG, JPEG, WebP, GIF, TIFF and AVIF sources produce static WebP after approval', async t => {
  const f = await fixture(t);
  let user = 300;
  for (const format of ['png', 'jpeg', 'webp', 'gif', 'tiff', 'avif']) {
    const token = await f.auth(user);
    const submission = await f.submit(token, user, await image(format));
    assert.equal(submission.status, 200, format);
    await f.decide(submission.body.submission_id, 'approve');
    const meta = await sharp(path.join(f.dir, 'approved', submission.body.submission_id + '.webp')).metadata();
    assert.equal(meta.format, 'webp'); assert.equal(meta.pages || 1, 1);
    user++;
  }
});

test('animated source is reduced to its first frame, not published as animation', async t => {
  const f = await fixture(t); const token = await f.auth(200);
  const red = await sharp({create: {width: 40, height: 20, channels: 3, background: '#ff0000'}}).png().toBuffer();
  const blue = await sharp({create: {width: 40, height: 20, channels: 3, background: '#0000ff'}}).png().toBuffer();
  const animated = await sharp([red, blue], {join: {animated: true}}).gif({delay: [100, 100]}).toBuffer();
  assert.equal((await sharp(animated, {animated: true}).metadata()).pages, 2);
  const submitted = await f.submit(token, 200, animated);
  assert.equal(submitted.status, 200);
  await f.decide(submitted.body.submission_id, 'approve');
  const output = path.join(f.dir, 'approved', submitted.body.submission_id + '.webp');
  const metadata = await sharp(output, {animated: true}).metadata();
  assert.equal(metadata.pages || 1, 1);
  const color = await sharp(output).resize(1, 1).raw().toBuffer();
  assert.ok(color[0] > 200 && color[2] < 30, 'The published image must use the first (red) frame');
});
