'use strict';
const http = require('node:http');
const {loadConfig} = require('./config');
const {Telegram} = require('./telegram');
const {createService} = require('./app');
async function main() {
  const config = loadConfig();
  const telegram = new Telegram(config.botToken);
  const me = await telegram.call('getMe');
  if (!me.supports_inline_queries) throw new Error('Enable inline mode with @BotFather /setinline first');
  if ((await telegram.call('getWebhookInfo')).url) throw new Error('This bot already has a webhook. Use a separate bot or remove that webhook first.');
  const service = createService(config, telegram, me.username);
  await service.recoverFiles(); await service.maintenance();
  const server = http.createServer(service.handle);
  server.requestTimeout = 65000; server.headersTimeout = 15000;
  server.listen(config.port, '0.0.0.0', () => console.log('LinkiGram banner API listening on port ' + config.port));
  const pendingTimer = setInterval(() => { void service.notifyPending().catch(service.log); }, 20000);
  const cleanupTimer = setInterval(() => { void service.maintenance().catch(service.log); }, 3600000);
  let stopping = false;
  for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => {
    stopping = true; clearInterval(pendingTimer); clearInterval(cleanupTimer);
    server.close(); setTimeout(() => process.exit(0), 5000).unref();
  });
  console.log('Moderation bot @' + me.username + ' started. Owner must send /start before receiving cards.');
  while (!stopping) {
    try {
      const offset = Number(service.store.get("SELECT value FROM state WHERE key='offset'")?.value || 0);
      const updates = await telegram.call('getUpdates', {offset, timeout: 30, allowed_updates: ['message', 'inline_query', 'callback_query']});
      for (const update of updates) {
        await service.handleUpdate(update);
        service.store.run("INSERT INTO state(key,value) VALUES('offset',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", String(update.update_id + 1));
      }
    } catch (error) { service.log(error); await new Promise(resolve => setTimeout(resolve, 3000)); }
  }
}
main().catch(error => { console.error(String(error.message).replaceAll(process.env.BOT_TOKEN || '\0', '[redacted]')); process.exitCode = 1; });
