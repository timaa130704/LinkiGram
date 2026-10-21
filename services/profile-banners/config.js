'use strict';
const path = require('node:path');
const fs = require('node:fs');
function loadConfig() {
  if (fs.existsSync('.env')) process.loadEnvFile('.env');
  const botToken = process.env.BOT_TOKEN || '';
  if (!/^\d+:[\w-]{20,}$/.test(botToken)) throw new Error('Set BOT_TOKEN in .env or server environment');
  const adminIds = (process.env.ADMIN_IDS || '').split(',').map(x => x.trim()).filter(Boolean);
  if (!adminIds.length || adminIds.some(x => !/^\d{1,16}$/.test(x))) throw new Error('Set numeric ADMIN_IDS');
  const adminChat = process.env.ADMIN_CHAT_ID || adminIds[0];
  if (!/^-?\d{1,16}$/.test(adminChat)) throw new Error('Invalid ADMIN_CHAT_ID');
  const url = new URL(process.env.PUBLIC_BASE_URL || '');
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('PUBLIC_BASE_URL must be a public HTTPS origin, without a path');
  }
  const integer = (name, fallback, min, max) => {
    const value = Number(process.env[name] || fallback);
    if (!Number.isSafeInteger(value) || value < min || value > max) throw new Error('Invalid ' + name);
    return value;
  };
  return {
    botToken, adminIds, adminChat, baseUrl: url.origin,
    port: integer('PORT', process.env.SERVER_PORT || 8080, 1, 65535),
    dataDir: path.resolve(process.env.DATA_DIR || './data'),
    maxStorage: integer('MAX_STORAGE_MB', 600, 32, 100000) * 1024 * 1024,
    maxPending: integer('MAX_PENDING', 50, 1, 1000),
    trustProxy: process.env.TRUST_PROXY === 'true',
    maxUpload: 8 * 1024 * 1024,
  };
}
module.exports = {loadConfig};
