# Liner-compatible lyrics engine

The player consumes Liner's authenticated `/v1/tracks/{id}/lyrics` SSE contract.
It resolves Telegram track metadata through YouTube Music's WEB_REMIX catalog,
which is the client catalog used by `tryliner/desktop`, instead of the removed
`/v1/search` backend endpoint. Unmatched tracks retain the metadata-hint fallback.

`engine.js` is our offline adapter around **@braccato/parsers 0.2.1**, the parser
version used by the inspected Liner desktop source. It supports its original
TTML/LRC/QRC/SRT/plain parsers and the richsync/Musixmatch subtitle response
shapes. The compiled asset is checked in; normal Android builds need no Node.
No Liner account token, provider credential, or private server code is embedded.
Provider availability is controlled by Liner's server and account access.

Rebuild with Node and pnpm from this directory:

```
pnpm install --frozen-lockfile --ignore-scripts
pnpm run build
pnpm test
```

The Android wrapper runs the bundled parser in an offline WebView JavaScript
context with networking, file access, and content access disabled. It uses no
JavaScript interfaces and quotes all input data; provider text is never code.
Only background provider threads wait for parser callbacks. The native player
renders the returned line/word timings. A native-parser fallback is retained for
devices whose WebView is unavailable.

`THIRD_PARTY_LICENSES.txt` in the asset directory preserves licenses of every
bundled runtime package. The adapter and application code follow this repository's
license. Upstream API reference: https://github.com/tryliner/desktop .
