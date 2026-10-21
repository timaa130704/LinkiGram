'use strict';
const fs = require('node:fs/promises');
class Telegram {
  constructor(token) { this.token = token; }
  async call(method, payload = {}, file) {
    let body, headers;
    if (file) {
      body = new FormData();
      for (const [key, value] of Object.entries(payload)) body.set(key, typeof value === 'object' ? JSON.stringify(value) : String(value));
      body.set(file.field, new Blob([await fs.readFile(file.path)], {type: file.mime}), file.name);
    } else { body = JSON.stringify(payload); headers = {'content-type': 'application/json'}; }
    const timeout = method === 'getUpdates' ? 45000 : 20000;
    const response = await fetch('https://api.telegram.org/bot' + this.token + '/' + method,
      {method: 'POST', headers, body, signal: AbortSignal.timeout(timeout)});
    const data = await response.json();
    if (!response.ok || !data.ok) throw new Error('Telegram ' + method + ' failed: ' + (data.error_code || response.status));
    return data.result;
  }
}
module.exports = {Telegram};
