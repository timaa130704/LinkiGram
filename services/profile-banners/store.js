'use strict';
const fs = require('node:fs');
const path = require('node:path');
const {DatabaseSync} = require('node:sqlite');
class Store {
  constructor(dir) {
    fs.mkdirSync(dir, {recursive: true, mode: 0o700});
    for (const subdir of ['pending', 'previews', 'approved', 'tmp']) fs.mkdirSync(path.join(dir, subdir), {recursive: true, mode: 0o700});
    this.db = new DatabaseSync(path.join(dir, 'banners.sqlite'));
    fs.chmodSync(path.join(dir, 'banners.sqlite'), 0o600);
    this.db.exec(`PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL; PRAGMA cache_size=-2048;
      CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY, current_id TEXT, hide_avatar INTEGER NOT NULL DEFAULT 0, blocked INTEGER NOT NULL DEFAULT 0);
      CREATE TABLE IF NOT EXISTS challenges (hash TEXT PRIMARY KEY, user_id TEXT NOT NULL, expires INTEGER NOT NULL, verified INTEGER NOT NULL DEFAULT 0);
      CREATE TABLE IF NOT EXISTS sessions (hash TEXT PRIMARY KEY, user_id TEXT NOT NULL, expires INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS submissions (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, status TEXT NOT NULL, created INTEGER NOT NULL,
        metadata TEXT NOT NULL, input_bytes INTEGER NOT NULL, output_bytes INTEGER NOT NULL DEFAULT 0,
        admin_message INTEGER, notified INTEGER NOT NULL DEFAULT 0, reason TEXT);
      CREATE INDEX IF NOT EXISTS submissions_user ON submissions(user_id, created);
      CREATE UNIQUE INDEX IF NOT EXISTS one_pending_per_user ON submissions(user_id) WHERE status IN ('pending','processing');
      CREATE TABLE IF NOT EXISTS state (key TEXT PRIMARY KEY, value TEXT NOT NULL);`);
    this.db.exec("UPDATE submissions SET status='pending' WHERE status='processing'");
  }
  get(sql, ...params) { return this.db.prepare(sql).get(...params); }
  all(sql, ...params) { return this.db.prepare(sql).all(...params); }
  run(sql, ...params) { return this.db.prepare(sql).run(...params); }
  ensureUser(id) { this.run('INSERT OR IGNORE INTO users(id) VALUES(?)', id); }
  close() { this.db.close(); }
}
module.exports = {Store};
