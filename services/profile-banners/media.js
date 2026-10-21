'use strict';
const fs = require('node:fs/promises');
const sharp = require('sharp');
sharp.cache({memory: 16, files: 0, items: 16});
sharp.concurrency(1);
const LIMIT = 32 * 1024 * 1024;
const allowed = new Set(['jpeg', 'png', 'webp', 'gif', 'avif', 'heif', 'tiff']);
function image(input) {
  return sharp(input, {animated: false, limitInputPixels: LIMIT, failOn: 'error', sequentialRead: true}).timeout({seconds: 10});
}
function rasterSignature(data) {
  return data.subarray(0, 3).equals(Buffer.from([255, 216, 255]))
    || data.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
    || /^GIF8[79]a/.test(data.subarray(0, 6).toString('ascii'))
    || (data.subarray(0, 4).toString() === 'RIFF' && data.subarray(8, 12).toString() === 'WEBP')
    || data.subarray(0, 4).equals(Buffer.from([73, 73, 42, 0]))
    || data.subarray(0, 4).equals(Buffer.from([77, 77, 0, 42]))
    || (data.subarray(4, 8).toString() === 'ftyp' && /avif|avis|heic|heix|mif1|msf1/.test(data.subarray(8, 64).toString('ascii')));
}
async function inspect(input, previewPath) {
  const data = await fs.readFile(input);
  if (!rasterSignature(data)) throw new Error('unsupported_image');
  const meta = await image(data).metadata();
  if (!allowed.has(meta.format) || !meta.width || !meta.height) throw new Error('unsupported_image');
  const frames = meta.pages || 1;
  const height = meta.pageHeight || meta.height;
  if (meta.width * height > LIMIT || meta.width > 16384 || height > 16384) {
    throw new Error('image_too_complex');
  }
  // Private first-frame JPEG preview only. The approved WebP does not exist yet.
  await image(data).autoOrient().resize({width: 900, height: 500, fit: 'inside', withoutEnlargement: true})
    .flatten({background: '#303030'}).jpeg({quality: 80}).toFile(previewPath);
  return {format: meta.format, width: meta.width, height, frames, animated: false};
}
async function convert(input, output, metadata) {
  const data = await fs.readFile(input);
  for (const quality of [82, 68, 52]) {
    const result = await image(data).autoOrient()
      .resize({width: 1200, height: 400, fit: 'cover', position: 'centre'})
      .webp({quality, effort: 3}).toBuffer();
    if (result.length <= 1024 * 1024) {
      await fs.writeFile(output, result, {flag: 'wx', mode: 0o600});
      return result.length;
    }
  }
  throw new Error('webp_too_large');
}
module.exports = {inspect, convert};
