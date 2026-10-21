'use strict';
const crypto = require('node:crypto');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const {pipeline} = require('node:stream/promises');
const Busboy = require('busboy');
const {Store} = require('./store');
const media = require('./media');
const DAY = 86400000;
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const uid = value => /^\d{1,16}$/.test(String(value || '')) && BigInt(value) > 0n ? String(value) : null;
const jobId = value => /^[a-f0-9]{32}$/.test(value || '');
class HttpError extends Error {
  constructor(status, error) { super(error); this.status = status; }
}
class Queue {
  constructor() { this.tail = Promise.resolve(); this.count = 0; }
  run(task) {
    if (this.count >= 4) throw new HttpError(503, 'Сервер занят обработкой изображений. Повторите позже.');
    this.count++;
    const result = this.tail.then(task);
    this.tail = result.catch(() => {}).finally(() => this.count--);
    return result;
  }
}
function createService(config, telegram, botUsername) {
  const store = new Store(config.dataDir);
  const queue = new Queue();
  const limits = new Map();
  let notifying = false, receiving = 0;
  const file = (folder, id, suffix = '') => path.join(config.dataDir, folder, id + suffix);
  const cleanupFile = async filename => { await fsp.unlink(filename).catch(error => { if (error.code !== 'ENOENT') throw error; }); };
  const tell = (chat, text) => telegram.call('sendMessage', {chat_id: chat, text});
  const log = error => console.error(String(error.message || 'Service error').replaceAll(config.botToken || '\0', '[redacted]').slice(0, 300));
  function rate(key, limit, period) {
    const now = Date.now();
    if (limits.size > 10000) for (const [id, bucket] of limits) if (bucket.reset <= now) limits.delete(id);
    if (limits.size > 10000 && !limits.has(key)) throw new HttpError(429, 'Too many requests');
    let bucket = limits.get(key);
    if (!bucket || bucket.reset <= now) { bucket = {count: 0, reset: now + period}; limits.set(key, bucket); }
    if (++bucket.count > limit) throw new HttpError(429, 'Слишком много запросов. Повторите позже.');
  }
  function json(response, status, data) {
    response.writeHead(status, {'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff'});
    response.end(JSON.stringify(data));
  }
  async function readJson(request) {
    let bytes = 0; const chunks = [];
    for await (const chunk of request) {
      bytes += chunk.length;
      if (bytes > 4096) throw new HttpError(413, 'Request too large');
      chunks.push(chunk);
    }
    try { return JSON.parse(Buffer.concat(chunks).toString()); } catch { throw new HttpError(400, 'Invalid JSON'); }
  }
  function session(request) {
    const token = request.headers['x-auth-token'];
    if (typeof token !== 'string' || !/^[a-f0-9]{64}$/.test(token)) throw new HttpError(401, 'Unauthorized');
    const row = store.get('SELECT user_id FROM sessions WHERE hash=? AND expires>?', hash(token), Date.now());
    if (!row) throw new HttpError(401, 'Unauthorized');
    return row.user_id;
  }
  function userAllowed(id) {
    store.ensureUser(id);
    if (store.get('SELECT blocked FROM users WHERE id=?', id).blocked) throw new HttpError(403, 'Загрузка баннеров заблокирована модератором.');
  }
  function status(id) {
    const user = store.get('SELECT * FROM users WHERE id=?', id);
    const latest = store.get('SELECT status,reason FROM submissions WHERE user_id=? ORDER BY created DESC,rowid DESC LIMIT 1', id);
    return {status: user?.blocked ? 'blocked' : latest?.status === 'processing' ? 'pending' : latest?.status === 'revoked' ? 'none' : latest?.status || 'none',
      has_banner: !!user?.current_id, hide_avatar: !!user?.hide_avatar, has_sound: false, reason: latest?.reason || null};
  }
  function capacity() {
    const used = store.get('SELECT COALESCE(SUM(input_bytes+output_bytes),0) AS bytes FROM submissions').bytes;
    const pending = store.get("SELECT COUNT(*) AS n FROM submissions WHERE status IN ('pending','processing')").n;
    const disk = fs.statfsSync(config.dataDir);
    if (used + (receiving + 1) * config.maxUpload + 1024 * 1024 > config.maxStorage || pending >= config.maxPending
        || disk.bavail * disk.bsize < config.maxUpload + 32 * 1024 * 1024) {
      throw new HttpError(507, 'На сервере пока нет места. Обратитесь к администратору.');
    }
  }
  async function multipart(request, destination) {
    return new Promise((resolve, reject) => {
      let parser;
      try { parser = Busboy({headers: request.headers, limits: {fileSize: config.maxUpload + 1, files: 1, fields: 2, fieldSize: 1024, parts: 4}}); }
      catch { reject(new HttpError(400, 'Expected multipart form')); return; }
      const fields = {}; let seen = false, invalid = false, write;
      parser.on('file', (name, stream) => {
        if (seen || name !== 'file') { invalid = true; stream.resume(); return; }
        seen = true;
        stream.on('limit', () => { invalid = true; });
        write = pipeline(stream, fs.createWriteStream(destination, {flags: 'wx', mode: 0o600}));
        write.catch(() => {});
      });
      parser.on('field', (name, value, info) => { if (info.valueTruncated || !['user_id', 'file_size'].includes(name)) invalid = true; fields[name] = value; });
      for (const event of ['filesLimit', 'fieldsLimit', 'partsLimit']) parser.on(event, () => { invalid = true; });
      request.once('aborted', () => { parser.destroy(new Error('Aborted upload')); });
      parser.once('error', reject);
      parser.once('close', async () => {
        try { await write; if (!seen || invalid) throw new HttpError(413, 'Недопустимая загрузка или файл больше 8 МБ.'); resolve(fields); }
        catch (error) { reject(error); }
      });
      request.pipe(parser);
    });
  }
  function buttons(id) {
    return {inline_keyboard: [[{text: '✅ Одобрить → WebP', callback_data: 'approve:' + id}, {text: '❌ Отклонить', callback_data: 'reject:' + id}],
      [{text: '📎 Оригинал', callback_data: 'original:' + id}]]};
  }
  async function notifyPending() {
    if (notifying) return; notifying = true;
    try {
      const rows = store.all("SELECT * FROM submissions WHERE status='pending' AND notified=0 ORDER BY created LIMIT 2");
      for (const row of rows) {
        const metadata = JSON.parse(row.metadata);
        const caption = 'Баннер на модерации\nПользователь: ' + row.user_id + '\nЗаявка: ' + row.id
          + '\n' + metadata.format.toUpperCase() + ' · ' + metadata.width + '×' + metadata.height
          + ' · кадров: ' + metadata.frames + '\n' + Math.ceil(row.input_bytes / 1024) + ' КБ\n'
          + (metadata.frames > 1 ? 'Используется первый кадр/страница. Баннер будет статичным.\n' : '')
          + 'После одобрения: WebP 1200×400. При замене прежний баннер останется до одобрения нового.';
        try {
          const result = await telegram.call('sendPhoto', {chat_id: config.adminChat, caption, reply_markup: buttons(row.id)},
            {field: 'photo', path: file('previews', row.id, '.jpg'), name: 'preview.jpg', mime: 'image/jpeg'});
          store.run("UPDATE submissions SET notified=1,admin_message=? WHERE id=? AND status='pending'", result.message_id, row.id);
        } catch (error) { log(error); break; }
        if (config.notificationDelay !== 0) await new Promise(resolve => setTimeout(resolve, 1100));
      }
    } finally { notifying = false; }
  }
  async function notifyDecision(row, text) {
    await telegram.call('editMessageReplyMarkup', {chat_id: config.adminChat, message_id: row.admin_message, reply_markup: {inline_keyboard: []}}).catch(log);
    await tell(config.adminChat, text + '\nПользователь: ' + row.user_id + '\nЗаявка: ' + row.id).catch(log);
  }
  async function moderate(callback) {
    const answer = text => telegram.call('answerCallbackQuery', {callback_query_id: callback.id, text}).catch(log);
    if (!config.adminIds.includes(String(callback.from?.id)) || String(callback.message?.chat?.id) !== String(config.adminChat)) {
      await answer('Недостаточно прав'); return;
    }
    const [action, id] = (callback.data || '').split(':');
    if (!['approve', 'reject', 'original'].includes(action) || !jobId(id)) { await answer('Неизвестное действие'); return; }
    const row = store.get('SELECT * FROM submissions WHERE id=?', id);
    if (!row || row.status !== 'pending') { await answer('Эта заявка уже обработана'); return; }
    if (row.admin_message !== callback.message.message_id) { await answer('Используйте последнюю карточку заявки'); return; }
    await answer(action === 'approve' ? 'Конвертирую в WebP…' : action === 'reject' ? 'Отклоняю…' : 'Отправляю оригинал…');
    if (action === 'original') {
      await telegram.call('sendDocument', {chat_id: config.adminChat, caption: 'Оригинал заявки ' + id},
        {field: 'document', path: file('pending', id), name: 'banner.' + JSON.parse(row.metadata).format, mime: 'application/octet-stream'});
      return;
    }
    const locked = store.run("UPDATE submissions SET status='processing' WHERE id=? AND status='pending'", id).changes;
    if (!locked) return;
    if (action === 'reject') {
      store.run("UPDATE submissions SET status='rejected',reason='Отклонено модератором',input_bytes=0 WHERE id=?", id);
      await cleanupFile(file('pending', id)); await cleanupFile(file('previews', id, '.jpg'));
      await notifyDecision(row, '❌ Отклонено. Прежний одобренный баннер не изменён.'); return;
    }
    let outputSize;
    try {
      // Only the moderator's approval enters this conversion path.
      outputSize = await queue.run(async () => {
        await cleanupFile(file('approved', id, '.partial'));
        return media.convert(file('pending', id), file('approved', id, '.partial'), JSON.parse(row.metadata));
      });
      await fsp.rename(file('approved', id, '.partial'), file('approved', id, '.webp'));
      const previous = store.get('SELECT current_id FROM users WHERE id=?', row.user_id)?.current_id;
      store.db.exec('BEGIN IMMEDIATE');
      try {
        store.run("UPDATE submissions SET status='approved',output_bytes=?,input_bytes=0,reason=NULL WHERE id=?", outputSize, id);
        store.run('UPDATE users SET current_id=? WHERE id=?', id, row.user_id);
        if (previous && previous !== id) store.run("UPDATE submissions SET status='revoked',output_bytes=0 WHERE id=?", previous);
        store.db.exec('COMMIT');
      } catch (error) { store.db.exec('ROLLBACK'); throw error; }
      await cleanupFile(file('pending', id)); await cleanupFile(file('previews', id, '.jpg'));
      if (previous && previous !== id) await cleanupFile(file('approved', previous, '.webp'));
      await notifyDecision(row, '✅ Одобрено и опубликовано в WebP · ' + Math.ceil(outputSize / 1024) + ' КБ');
    } catch (error) {
      store.run("UPDATE submissions SET status='pending',reason='Не удалось конвертировать. Модератор может повторить попытку.' WHERE id=? AND status='processing'", id);
      if (store.get('SELECT status FROM submissions WHERE id=?', id)?.status !== 'approved') {
        await cleanupFile(file('approved', id, '.partial')); await cleanupFile(file('approved', id, '.webp'));
      }
      log(error); await tell(config.adminChat, 'Не удалось обработать заявку ' + id + '. Повторите одобрение или отклоните её.').catch(log);
    }
  }
  async function handleUpdate(update) {
    const query = update.inline_query;
    if (query) {
      const id = uid(query.from?.id), code = query.query || '';
      if (id && /^[a-f0-9]{64}$/.test(code)) {
        store.run('UPDATE challenges SET verified=1 WHERE hash=? AND user_id=? AND expires>?', hash(code), id, Date.now());
      }
      await telegram.call('answerInlineQuery', {inline_query_id: query.id, results: [], cache_time: 0, is_personal: true});
      return;
    }
    if (update.callback_query) { await moderate(update.callback_query); return; }
    const message = update.message;
    if (!message?.text || message.chat?.type !== 'private') return;
    const id = String(message.from.id);
    const command = message.text.trim().split(/\s+/)[0].split('@')[0];
    if (command === '/start' || command === '/id') {
      await tell(message.chat.id, 'LinkiGram: модерация баннеров. Ваш Telegram ID: ' + id
        + '\nБаннер отправляется из настроек LinkiGram. Авторизация выполняется через inline-запрос приложения.');
    } else if (config.adminIds.includes(id) && command === '/pending') {
      store.run("UPDATE submissions SET notified=0 WHERE status='pending'"); await notifyPending();
    } else if (config.adminIds.includes(id) && command === '/stats') {
      const summary = store.all('SELECT status,COUNT(*) AS count FROM submissions GROUP BY status');
      await tell(message.chat.id, summary.map(row => row.status + ': ' + row.count).join('\n') || 'Пока нет заявок');
    }
  }
  async function handle(request, response) {
    try {
      const url = new URL(request.url, 'http://local');
      const address = config.trustProxy ? String(request.headers['x-forwarded-for'] || request.socket.remoteAddress).split(',')[0].trim() : request.socket.remoteAddress;
      rate('http:' + address, 300, 60000);
      if (request.method === 'GET' && url.pathname === '/healthz') { json(response, 200, {ok: true}); return; }
      let match = url.pathname.match(/^\/(get|status)\/(\d{1,16})$/);
      if (request.method === 'GET' && match) {
        const id = uid(match[2]); if (!id) throw new HttpError(400, 'Invalid user ID');
        if (match[1] === 'status') { json(response, 200, status(id)); return; }
        const row = store.get("SELECT s.id,u.hide_avatar FROM users u JOIN submissions s ON s.id=u.current_id WHERE u.id=? AND s.status='approved'", id);
        json(response, 200, row ? {has_banner: true, url: config.baseUrl + '/banners/' + row.id + '.webp', type: 'webp', version: row.id,
          has_sound: false, hide_avatar: !!row.hide_avatar} : {has_banner: false}); return;
      }
      match = url.pathname.match(/^\/banners\/([a-f0-9]{32})\.webp$/);
      if (request.method === 'GET' && match) {
        const id = match[1];
        if (!store.get("SELECT s.id FROM submissions s JOIN users u ON u.current_id=s.id WHERE s.id=? AND s.status='approved'", id)) throw new HttpError(404, 'Not found');
        const filename = file('approved', id, '.webp');
        const stat = await fsp.stat(filename);
        response.writeHead(200, {'content-type': 'image/webp', 'content-length': stat.size, 'cache-control': 'public,max-age=3600', 'x-content-type-options': 'nosniff'});
        await pipeline(fs.createReadStream(filename), response); return;
      }
      if (request.method === 'POST' && url.pathname === '/auth/register') {
        rate('register-ip:' + address, 20, 60000);
        const id = uid(url.searchParams.get('user_id')); if (!id) throw new HttpError(400, 'Invalid user ID');
        rate('register-user:' + id, 5, 60000); userAllowed(id);
        const code = crypto.randomBytes(32).toString('hex');
        store.run('INSERT INTO challenges(hash,user_id,expires) VALUES(?,?,?)', hash(code), id, Date.now() + 120000);
        json(response, 200, {code, bot_username: botUsername}); return;
      }
      if (request.method === 'POST' && url.pathname === '/auth/poll') {
        const id = uid(url.searchParams.get('user_id')), code = url.searchParams.get('code') || '';
        if (!id || !/^[a-f0-9]{64}$/.test(code)) throw new HttpError(404, 'Expired');
        const challenge = store.get('SELECT * FROM challenges WHERE hash=? AND user_id=? AND expires>?', hash(code), id, Date.now());
        if (!challenge) throw new HttpError(404, 'Expired');
        if (!challenge.verified) { json(response, 200, {pending: true}); return; }
        const token = crypto.randomBytes(32).toString('hex');
        store.db.exec('BEGIN IMMEDIATE');
        try {
          store.run('DELETE FROM challenges WHERE hash=?', hash(code));
          store.run('INSERT INTO sessions(hash,user_id,expires) VALUES(?,?,?)', hash(token), id, Date.now() + 30 * DAY);
          store.db.exec('COMMIT');
        } catch (error) { store.db.exec('ROLLBACK'); throw error; }
        json(response, 200, {token}); return;
      }
      if (request.method === 'POST' && url.pathname === '/settings') {
        const owner = session(request); userAllowed(owner); const body = await readJson(request);
        if (uid(body.user_id) !== owner) throw new HttpError(403, 'Not your profile');
        if (typeof body.hide_avatar !== 'boolean') throw new HttpError(400, 'Invalid setting');
        store.run('UPDATE users SET hide_avatar=? WHERE id=?', Number(body.hide_avatar), owner);
        json(response, 200, {success: true}); return;
      }
      if (request.method === 'POST' && url.pathname === '/submit') {
        const owner = session(request); userAllowed(owner); rate('upload:' + owner, 5, DAY);
        if (store.get("SELECT id FROM submissions WHERE user_id=? AND status IN ('pending','processing')", owner)) throw new HttpError(409, 'Баннер уже ожидает модерации.');
        if (receiving >= 3) throw new HttpError(503, 'Сервер занят, повторите позже.');
        if (Number(request.headers['content-length'] || 0) > config.maxUpload + 65536) throw new HttpError(413, 'Файл больше 8 МБ.');
        capacity(); receiving++;
        const id = crypto.randomBytes(16).toString('hex');
        const original = file('tmp', id), preview = file('previews', id, '.jpg');
        let registered = false;
        try {
          const fields = await multipart(request, original);
          if (uid(fields.user_id) !== owner) throw new HttpError(403, 'Not your profile');
          const stat = await fsp.stat(original);
          if (stat.size <= 0 || stat.size > config.maxUpload) throw new HttpError(413, 'Файл пуст или больше 8 МБ.');
          let metadata;
          try { metadata = await queue.run(() => media.inspect(original, preview)); }
          catch (error) {
            if (error instanceof HttpError) throw error;
            throw new HttpError(422, 'Этот формат не поддерживается, файл повреждён или изображение слишком сложное. Используйте JPEG, PNG, WebP, GIF, AVIF или TIFF.');
          }
          // Recheck after asynchronous upload/preview so concurrent requests cannot replace a pending submission.
          if (store.get("SELECT id FROM submissions WHERE user_id=? AND status IN ('pending','processing')", owner)) throw new HttpError(409, 'Баннер уже ожидает модерации.');
          await fsp.rename(original, file('pending', id));
          try {
            store.run('INSERT INTO submissions(id,user_id,status,created,metadata,input_bytes) VALUES(?,?,\'pending\',?,?,?)', id, owner, Date.now(), JSON.stringify(metadata), stat.size);
          } catch (error) {
            if (store.get("SELECT id FROM submissions WHERE user_id=? AND status IN ('pending','processing')", owner)) throw new HttpError(409, 'Баннер уже ожидает модерации.');
            throw error;
          }
          registered = true;
          json(response, 200, {success: true, status: 'pending', submission_id: id});
          void notifyPending().catch(log);
        } finally {
          receiving--;
          await cleanupFile(original);
          if (!registered) { await cleanupFile(preview); await cleanupFile(file('pending', id)); }
        }
        return;
      }
      throw new HttpError(404, 'Not found');
    } catch (error) {
      if (response.headersSent) { response.destroy(); return; }
      const statusCode = error instanceof HttpError ? error.status : error.code === 'ENOENT' ? 404 : 500;
      if (statusCode === 500) log(error);
      request.resume(); json(response, statusCode, {success: false, error: statusCode === 500 ? 'Ошибка сервера' : statusCode === 404 ? 'Not found' : error.message});
    }
  }
  async function maintenance() {
    store.run('DELETE FROM challenges WHERE expires<?', Date.now());
    store.run('DELETE FROM sessions WHERE expires<?', Date.now());
    for (const row of store.all("SELECT id FROM submissions WHERE status='pending' AND created<?", Date.now() - 7 * DAY)) {
      store.run("UPDATE submissions SET status='rejected',reason='Истёк срок модерации',input_bytes=0 WHERE id=?", row.id);
      await cleanupFile(file('pending', row.id)); await cleanupFile(file('previews', row.id, '.jpg'));
    }
    store.run("DELETE FROM submissions WHERE status IN ('rejected','revoked') AND created<?", Date.now() - 90 * DAY);
  }
  async function recoverFiles() {
    for (const name of fs.readdirSync(path.join(config.dataDir, 'tmp'))) await cleanupFile(file('tmp', name));
    for (const folder of ['pending', 'previews', 'approved']) {
      for (const name of fs.readdirSync(path.join(config.dataDir, folder))) {
        const id = name.split('.')[0];
        const row = jobId(id) && store.get('SELECT status FROM submissions WHERE id=?', id);
        const keep = folder === 'approved' ? row?.status === 'approved' && name.endsWith('.webp') : row?.status === 'pending';
        if (!keep) await cleanupFile(file(folder, name));
      }
    }
  }
  return {handle, handleUpdate, notifyPending, maintenance, recoverFiles, store, log};
}
module.exports = {createService};
