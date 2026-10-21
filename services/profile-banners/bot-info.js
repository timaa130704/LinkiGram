'use strict';
const fs = require('node:fs');
const {Telegram} = require('./telegram');
if (fs.existsSync('.env')) process.loadEnvFile('.env');
async function main() {
  if (!process.env.BOT_TOKEN) throw new Error('Set BOT_TOKEN first');
  const api = new Telegram(process.env.BOT_TOKEN);
  const me = await api.call('getMe');
  console.log('Bot: @' + me.username + '; inline: ' + !!me.supports_inline_queries);
  console.log('Send /start to this bot now. The server must not be running during setup.');
  const updates = await api.call('getUpdates', {timeout: 30, allowed_updates: ['message']});
  const people = new Map();
  for (const update of updates) {
    const message = update.message;
    if (message?.chat?.type === 'private') people.set(String(message.from.id), message.from.username || message.from.first_name || '');
  }
  for (const [id, name] of people) console.log('Telegram user ID: ' + id + ' (' + name + ')');
  if (!people.size) console.log('No private messages yet. Send /start and run bot-info again.');
}
main().catch(() => { console.error('Cannot query Telegram. Check BOT_TOKEN, connectivity and whether another process uses this bot.'); process.exitCode = 1; });
