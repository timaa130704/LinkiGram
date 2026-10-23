import {detectParser, PlainParser} from '@braccato/parsers';

// Offline adapter from Liner's raw lyrics contract to the Android player model.
export function parse(raw, format, durationMs) {
  let rows;
  let synced = true;
  const trimmed = raw.trim();
  if (trimmed.startsWith('[')) {
    try {
      const data = JSON.parse(trimmed);
      if (Array.isArray(data) && data.length && typeof data[0].ts === 'number') {
        rows = data.map(row => {
          const start = Math.round(row.ts * 1000);
          const tokens = [];
          for (const token of (Array.isArray(row.l) ? row.l : [])) {
            if (!token || typeof token.c !== 'string') continue;
            if (!token.c.trim() && tokens.length) tokens[tokens.length - 1].c += token.c;
            else tokens.push({...token});
          }
          return {startTimeMs:start, words:tokens.map(t => t.c).join(''), parts:tokens.map(token => {
            const offset = Math.round((token.o || 0) * 1000);
            return {startTimeMs:offset >= start ? offset : start + offset, words:token.c};
          })};
        });
      } else if (Array.isArray(data) && data.length && typeof data[0].time?.total === 'number') {
        rows = data.filter(row => typeof row.text === 'string' && row.text.trim())
          .map(row => ({startTimeMs:Math.round(row.time.total * 1000), words:row.text.trim()}));
      }
    } catch (_) { /* Not JSON: enhanced LRC also starts with a bracket. */ }
  }
  if (!rows) {
    const parser = detectParser(trimmed);
    synced = parser !== PlainParser;
    rows = parser.parse(trimmed, durationMs > 0 ? durationMs : undefined);
  }
  return {synced, lines:(rows || []).map(row => {
    const text = typeof row.words === 'string' ? row.words : '';
    let cursor = 0;
    const words = [];
    for (const part of row.parts || []) {
      if (typeof part.words !== 'string' || !part.words.length || !Number.isFinite(part.startTimeMs)) continue;
      const start = text.indexOf(part.words, cursor);
      if (start < 0) continue;
      const end = start + part.words.length;
      words.push({time:Math.max(0, Math.round(part.startTimeMs)), start, end});
      cursor = end;
    }
    return {time:Math.max(0, Math.round(row.startTimeMs || 0)), text, words};
  })};
}
