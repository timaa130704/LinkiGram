# LinkiGram — Agent Notes

Unofficial Telegram Android fork. Upstream base: **Telegram Android 12.10.5**.
Published as `app.linkigram.messenger`. Version in `gradle.properties`
(`APP_VERSION_CODE`, `APP_VERSION_NAME`).

## Layout that matters

- `TMessagesProj/src/main/java/org/telegram/...` — upstream Telegram, lightly touched.
- `TMessagesProj/src/main/java/app/nimarkogram/messenger/...` — the fork itself:
  config (`NimarkoConfig`), updater (`updater/NimarkoUpdater`), plugins
  (`plugins/`), media (`media/`), preferences.
- `TMessagesProj/src/main/python/` — Python runtime for plugins (Chaquopy 17).
- `TMessagesProj_AppStandalone/` — app variant, launcher icons live here.
- `TMessagesProj/proguard-rules.pro` — **critical**, see "Release-only failures".

## Branding

App name lives in `values/strings.xml` and `values-ru/strings.xml` (`AppName`).
Both packs — the bundled localisation `.bin` and the Telegram cloud pack — are
built from upstream, so `AppName` comes back as "Telegram" no matter which one
answers. `LocaleController.keepForkBranding()` guards the way out of
`getStringV2` for every branch and falls back to the compiled string resource.

Launcher icon: `icon_background_sa.xml` (adaptive) plus legacy
`ic_launcher_sa.png` in all five densities. Current default is dark grey
`#424242`. Sixteen baked presets plus a HEX picker live in
`org/telegram/ui/LauncherIconController.java`.

## Release-only failures

These only show up in the **release** build. R8 is the usual culprit.

- `-keep class org.telegram.** { *; }` is required. Python plugins look classes
  up by their original names; without it `org.telegram.ui.MainTabsActivity` gets
  renamed and plugin `on_load` dies with `ClassNotFoundException`, so the plugin
  loads with no UI at all. Same rule and same reason exists in
  `Ettacent/NimarkoGram`.
- Verify every API name against the tree before pushing. A build cycle costs
  ~25 minutes and a wrong guess costs a full cycle.

## Startup stall (fixed in 2.5.1)

Cold start used to sit silent ~11.6s before the chat list appeared.
Measured on device:

```
awaitLoaded: waited 5015ms (loaded=false)
openDatabase ENTER
openDatabase DONE t=16        <- the database itself takes 16ms
```

The fork loaded its native library by unpacking ~20 MB out of the APK into
`files/lib/` and calling `System.load()` on it. Android refuses to load
libraries from a writable directory, so the load never succeeded and every
caller waited out the full timeout. Fixed by loading straight from the APK's
native library directory (`NativeLoader.initNativeLibs`) and shortening the
wait to 400ms as a backstop. Cold start is now ~1.3s.

To diagnose similar stalls: instrument, build, read `logcat`. Do not guess.

## Plugins

Entrypoint files are `<pluginId>.plugin`; the installer publishes them as
`<pluginId>.py` in `pluginsDir`.

Fixed in 2.5.1, all found by reading device logs:

1. Tapping a `.plugin` file did nothing — three call sites into
   `PluginsController.maybeShowInstallDialog` were lost during the 12.10.2
   upstream update. Restored in `ChatActivity`.
2. `ModuleNotFoundError: No module named '<id>'` on enable — the entry was
   imported by name, which depends on sys.path and a stale directory cache.
   Now loaded by file path through `plugin_imports.load_plugin`
   (`plugin_imports.py`, ported from Ettacent/NimarkoGram, GPL, credits kept).
3. No button in the UI — R8 had renamed `MainTabsActivity`, the class plugins
   hook. See "Release-only failures".
4. Plugins vanished after a restart — a dead end in the quarantine logic: only a
   successful load cleared `plugin_crashed_<id>`, but a quarantined plugin is
   skipped before loading, so it could never clear its own flag. Successful
   loads and crashes now record a timestamp and a stale marker is ignored.
5. `on_update_hook` / `on_updates_hook` existed in `PluginsController` but
   `MessagesController` never called them, so plugins received no updates at
   all. Both call sites wired in `processUpdates` and `processUpdateArray`,
   ported from exteraless.

Known remaining issue: X-CFG loops on expired login codes
(`код получен (история)` → `Код не найден или устарел`). The bot replies fine
and the messages do arrive in the app; the plugin re-reads history instead of
consuming the fresh code. That is plugin-side logic.

Diagnostic logging still in the tree: `LinkiPluginHook` tag in
`MessagesController.processUpdateArray` and in
`PluginsController.executeUpdateHook`. Remove before shipping a stable build.

## Upstream forks worth consulting

- `Ettacent/NimarkoGram` — 12.10.6, closer to our base than exteraless. Has
  `plugin_imports.py`, the `-keep class org.telegram.**` rule, DEX plugin
  support and crash reports. Difference from us: ~1167 lines in
  `PythonPluginsEngine`, ~467 in `PluginsController`, 5 files we do not have.
- `exteraless/exteraless` — plugin update-hook call sites, ProGuard rules,
  feature directories worth porting: `pills`, `pillstack`, `feed`, `drawer`,
  `nowplaying`, `ghost`, `appicons`, `glyph`, `picker`, `proxy`, `backup`.

## Release

Builds come from CI only, ~25 min per run. Local Gradle is broken; do not try.

```
gh workflow run build.yml --ref main
gh run watch <id> --exit-status
gh run download <id> -n linkigram-apk -D <dir>
```

The device is SM-G973F (Android 16), `RF8MA188NFZ`, package
`app.linkigram.messenger`, launch with
`am start -n app.linkigram.messenger/org.telegram.ui.LaunchActivity`. adb drops
often; `adb kill-server` + retry usually recovers it. `/data/data` is not
readable without root, so plugin files on disk cannot be inspected directly.

Publish with `gh release create v<X.Y.Z> <apk> --title ... --notes-file ...`.
`prerelease=false` matters: the in-app updater reads `/releases/latest` and will
not see a pre-release.

## Release announcement bot

`tools/release_bot.py` posts to the announcement channel. Token lives in
`.release-bot.env` (gitignored, never commit it). Verify:

```
python tools/release_bot.py --dry-run --version 2.5.1 --notes "..."
python tools/release_bot.py --check          # token + channel access
python tools/release_bot.py --channel @linkireleases --version 2.5.1 --notes-file notes.md
```

The bot must be an admin with posting rights in the target channel; it cannot
promote itself. Two traps already hit:

- The caption of a document needs `parse_mode=HTML` sent explicitly, otherwise
  the markup arrives as literal `<b>` text. Plain text messages do not have
  this problem, so it only shows up with `--file`.
- Bot API refuses uploads over **50 MB** and the APK is ~105 MB, so the
  announcement goes out as text with a download link. `--file` works for small
  files only.
- In PowerShell, `--channel @name` is parsed as splatting. Quote it:
  `--channel="@name"`.

## Working rules learned the hard way

Seven builds were started on this work and five failed, every time on my own
mistake. The three checks below catch nearly all of them, and a build costs
25 minutes, so run them before every push:

1. Brace balance in any Java file you appended to.
2. XML well-formedness after editing `strings.xml` (and put new strings **inside**
   `</resources>`, not after it).
3. Every external symbol the new code references actually exists in this tree —
   including strings and methods in neighbouring classes of the same port.

Other rules:

- Porting upstream code means porting its dependencies too. NimarkoGram had
  seven string keys and `PluginsWatchdog.getKnownPlugin()` that we did not; the
  compiler found each one, one build at a time.
- Prefer reading device logs over reasoning about symptoms; every real root
  cause here was found in `logcat`, none were guessed correctly.
- The user's device is their own: unlock it before screenshots, do not tap
  blindly, cancel anything started by accident.
- `landing/index.html` and `landing/.gitignore` often hold the user's own
  uncommitted work. Never sweep them into a commit.

## Where we stopped — tell the user this first

**Do this before anything else.** The user comes from a long session in another
agent and will ask "what were we doing". Briefly reconstruct the story for them
before touching code. Do not wait to be asked twice.

The session was one long debugging day. Roughly what happened, in order:

1. AyuMoments ghost mode, custom launcher icon colours, the creator plane icon
   and a 2.5 OTA release had already shipped before this day started.
2. **The chat list was empty for ~11.6 s on every cold start.** First attempts
   were wrong guesses — a stale ProGuard hook, then the WS-bypass proxy, then
   the database. The real cause only appeared once we added timestamped logging
   inside the app: `awaitLoaded: waited 5015ms (loaded=false)`, while the
   database itself opened in 16 ms. The fork loaded its native library by
   unpacking ~20 MB out of the APK into `files/lib/`, which Android refuses to
   load from. Fixed; startup is now ~1.3 s and was verified on the device.
3. **Plugins did nothing when tapped.** The three call sites into
   `maybeShowInstallDialog` were lost during the 12.10.2 upstream update.
   Restored and verified — the install panel opens.
4. **Plugins failed to enable** with `ModuleNotFoundError`. They were imported by
   module name instead of by file path. Ported `plugin_imports.py` from
   NimarkoGram. Verified: the plugin enables and draws its button.
5. **The button was missing anyway** — R8 had renamed
   `org.telegram.ui.MainTabsActivity`, the class plugins hook into. Added the
   keep rule that NimarkoGram already documents for this exact failure.
6. **Plugins vanished after every restart.** A dead end: only a successful load
   cleared `plugin_crashed_<id>`, but a quarantined plugin is skipped *before*
   loading, so it could never clear its own flag. Now timestamped.
7. **Plugins received no updates at all** — the update hooks existed in
   `PluginsController` but `MessagesController` never called them. Wired in.
8. **X-CFG then looped on expired login codes.** Still open: the bot does reply
   and the messages do arrive in the app, but the plugin re-reads chat history
   instead of consuming the fresh code. Last measurement: 190 hook calls, 48
   with the engine available, **zero** with any plugin interested.
9. Camera module switching was implemented, never became reliably usable, and
   was removed at the user's request.
10. Release **2.5.1 was published**, with an announcement bot posting to
    `@linkireleases`. Bot tooling is described further down.

**Immediately pending:** the MD3 player port build `38466359729` **passed** (commits
`b37849b6` + `288e2a36`, 33 files: 19 player sources, 2 string files, 12 integration
edits across MediaController/MusicPlayerService/NotificationCenter/VideoPlayer/
FragmentContextView/ChatActivity/LaunchActivity). The APK is downloaded to `.tmp-apk/`
but **not installed** — the phone was disconnected. Plug it in and install, then verify:
no crashes on launch, the player sheet/mini-bar opens, the queue and online lyrics work.
Also still unverified: the nowplaying music card on a profile with saved music.

**Lesson (cost us a near-miss):** a subagent whose session `outcome` is empty is
**still running**, not dead. Do not relaunch "dead-looking" subagents — that put two
agents on one working tree; one committed while the other was mid-edit, and the commit
left `clearMusicPlaylistState()` private while `FragmentContextView` called it (would
not compile). Keep exactly one feature agent per area and wait for its explicit
completion notification.

The nowplaying port: 7 files in `app.exteraless.*` (original packages, credits kept),
hunks in `MessageObject.getArtworkUrl` (zero-width strip), `ProfileMusicView` (long
press), `ProfileActivity` (card row + section shadow, creation/bind/getViewType/
fillPositions, `openSavedMusic()` extracted from the inline click listener,
`showProfileMusicDesignDialog()`), `NimarkoConfig.profileMusicCard/lastfmNick`,
strings in `values/strings_oe_nowplaying.xml` + `values-ru`. Their `AppearanceConfig`
was mapped onto NimarkoConfig; section radius fixed at 20dp (their default).
**The write side is not surfaced yet:** `ProfileMusicStamp.apply` exists but no
settings row calls it — a "Last.fm nick" input is still needed (their UI lives in
`OpenExteraGeneralActivity`, ~90 lines: progress dialog + stage callbacks + bulletins).
After that: DEX plugins + hook guards from NimarkoGram, then `feed`/`drawer`/`pillstack`.

Two things the user should be reminded of: the bot token was pasted into chat
and should be rotated via @BotFather, and `landing/index.html` +
`landing/.gitignore` usually hold the user's own uncommitted work.

## exteraless MD3 player port

Ported the exteraless 12.10.6 MD3 music player (19 files, ~5.5k lines) into
LinkiGram 12.10.5. The files live in `app/exteraless/player/` with the original
`app.exteraless` package and credits kept.

Wiring (all against our base — nothing copied blindly):

- `Md3Player.create(context, provider)` replaces `new AudioPlayerAlert(...)` at the
  three open points (`ChatActivity`, `ProfileActivity`, `LaunchActivity`) and the
  context-bar click in `FragmentContextView`. It returns the MD3 `PlayerSheet` for
  music when enabled, else the stock `AudioPlayerAlert`.
- `PlayerBarView` replaces the stock context audio bar when the mini player is on
  (`FragmentContextView`: `getStyleHeight`/`updateStyle`/`checkMd3Bar`/`dispatchDraw`
  and the `hidesContextPlayer` hide path).
- `PlayerMiniView` floats above the tab bar in `MainTabsActivity`.
- `SeekableFragmentsExtractorsFactory` wraps the progressive source in `VideoPlayer`
  so unseekable fragmented-MP4 audio can seek.
- `PlayerSheet.onModesChanged()` keeps repeat/shuffle in sync from `MusicPlayerService`.
- Config on `NimarkoConfig`: `md3Player` / `md3MiniPlayer` (both default **on**) and
  `lrclibAllowed` (default **off** — online lyrics send the track name off-device),
  mapped from their `AppearanceConfig` as the nowplaying port did (`b82e9aaa`).

Adaptations for symbols our base lacks:

- Their `app.exteraless.appearance.AppearanceConfig` → `NimarkoConfig`.
- NagramX `NaConfig.getSilentMessageByDefault()` → `PlayerActions.silent()` is always `true`.
- `tw.nekomimi...MonetHelper` (no Monet here) → `PlayerColors.noCoverSeed` falls back to
  the themed accent.
- `tw.nekomimi...TypefaceHelper.lyricsTypeface()` → `AndroidUtilities.bold()`.
- Added `NotificationCenter.audioInfoLoaded` and the async `MediaController.loadAudioInfo`
  port that posts it (12.10.6 had it; 12.10.5 did not).
- Added inert `BottomSheet.onCustomBackStarted/-Progressed/-Cancelled` hooks so the
  `PlayerSheet` overrides compile. Our `BottomSheet` has no OnBackInvoked plumbing, so the
  predictive-back "drag to morph" preview is not live; the morph still runs on dismissal.

Added strings in `values/strings_oe_player.xml` and `values-ru/strings_oe_player.xml`
(33 keys, EN/RU parity).

Known limitation: the mini player is positioned above the tab bar, but the last list
item is not inset for it (their `setMiniPlayerInset`/`TabFragmentDelegate` propagation
was not ported), so it can overlap the bottom of a long list.

Checks run before push: brace balance on every touched `.java` (+0/+0), XML
well-formedness on both new string files, EN/RU key parity, and a sweep of every
external symbol the new code references against this tree. Build only in CI; do not
push while a build runs.

## MD3 player online lyrics

The auto-search the user asked for was already inside the port; this pass verified
it end to end and surfaced it in the UI.

`app/exteraless/player/OnlineLyrics.java` is not a stub — it already ships two
providers and a cache:

- Primary: **LRCLIB** (`https://lrclib.net/api/`, no key). First the exact `get`
  (artist + track + album + duration), then `search`, scored by the synced flag and
  by duration closeness.
- Fallback: **lrcmux** (`https://api.lrcmux.dev/get`). When LRCLIB returns a plain
  text and lrcmux returns timing, `punctuate()` grafts LRCLIB's casing and
  punctuation onto the synced lines.
- Cache: in-memory `LruCache` (24 entries) plus a disk cache under
  `cacheDir/lrclib/<md5>.json` (versioned); a `missing` set skips known-empty
  repeats.
- States: `LyricsView` has LOADING / OFFER / NOT_FOUND / ERROR / INSTRUMENTAL /
  LYRICS, and embedded tag lyrics (`SOURCE_FILE`) win over the network.

Wiring that was already in the port: `PlayerSheet.loadLyrics()` runs on track
change (`bind`), when the lyrics panel opens, and again on `audioInfoLoaded` /
`fileLoaded`. The gate is `NimarkoConfig.lrclibAllowed()` (default **off** — the
track name leaves the device); it was reachable only through the small "Search
online" button in the empty state.

Added this pass:

- A checked overflow-menu row in the player (`PlayerSheet.showMenu`),
  `OEPlayerOnlineLyrics`, backed by `NimarkoConfig.setLrclibAllowed` — the setting
  can now be turned on **and off** from the player itself.
- Auto-search now runs on every track change when the setting is on, even with the
  lyrics panel closed (`bind`: `lyricsMode || NimarkoConfig.lrclibAllowed()`), so
  opening the panel shows text instead of a spinner.
- Strings `OEPlayerOnlineLyrics` in `values/strings_oe_player.xml` and `values-ru`
  (EN/RU parity kept).

Verified with a real request: `lrclib.net/api/get` for Queen / Bohemian Rhapsody
returns the JSON object whose fields `OnlineLyrics` reads (`syncedLyrics`,
`plainLyrics`, `instrumental`, `duration`); `search` returns the same shape as an
array. Network and parsing are fine.

Not done on purpose: no formal `LyricsProvider` interface. The two providers
already sit behind one fallback path in a working 508-line file; extracting an
interface would refactor live code for no behaviour change and risk a 25-minute
build cycle. If the owner wants it, it becomes a real interface.

Remaining: no settings screen hosts the player toggles — `OEAppearanceMd3Player` /
`OEAppearanceMd3MiniPlayer` exist as strings but no row uses them, and the player's
`md3Player` / `md3MiniPlayer` switches are still config-only.

## NimarkoGram DEX plugins and hook guards

Ported the four files that make plugin-generated DEX classes attributable and stop
plugin hooks from recursing into the plugin engine itself (Ettacent's
`PluginDexRegistry`, `PluginDexTracking`, `PluginHookBypassTarget`,
`PluginHookGuard`, ~217 lines; credits kept). Build `38539717232` passed
(commit `54ad9552`).

What they do and how they were wired onto our base:

- `PluginDexTracking.install()` hooks every `BaseDexClassLoader` constructor and
  `findClass` through Pine and records which plugin's runtime created each
  generated class loader / class (`PluginDexRegistry`). Our engine already compiles
  bridge classes with dexmaker (`ClassProxy` + `InMemoryDexClassLoader`), so this
  is the missing attribution half, not a new loader. Installed from
  `PythonPluginsEngine.initPython()` right after `ensurePineReady()`, before
  `Python.start` — same point and order as NimarkoGram, and after the
  `HiddenApiBypass.addHiddenApiExemptions("L")` call.
- `PluginsWatchdog` gained `activeWatchdog`, `currentExecutingPluginId()` (reads
  the runtime scope, then the per-thread execution stack) and
  `findCrashingPlugin(...)`. `getCrashingPluginId` now tries the recorded callback
  failure first, then DEX ownership, and only then falls back to the plugin
  currently executing on the thread — otherwise that generic fallback masked every
  DEX attribution.
- `PluginHookGuard` is a per-thread re-entrancy guard. `PineAdapter` marks hooks
  whose target class is `PluginsController`, `PluginsWatchdog` or `FileLog` as
  "support" hooks and skips them while the guard scope is active, so the bridge's
  own calls into the controller/watchdog/FileLog can never re-enter a plugin's
  hook. Its constructor also refuses to hook the bridge implementations
  (`PluginHookGuard`, `PineAdapter`, `PythonBoundarySanitizer`,
  `PythonInterfaceProxy`).
- `PyMethodHook` / `PyMethodReplacement` now route `PluginsController.getInstance`,
  runtime enter/exit/decision and the watchdog started/finished/failed callbacks
  through the guard, and wrap their error logging in it. `started` is recorded
  before the hook filters run so a filter that aborts still gets a matching
  `finished`.
- `PluginHookBypassTarget` lets a receiver opt out of plugin hooks
  (`shouldBypassPluginHooks()`); `PineAdapter` honours it in `beforeCall`.
  `ChatMessageCell` implements it and returns `false`. The one place NimarkoGram
  returns `true` is inside the 12.10.6 static-presentation subclass in
  `MessageEntityView`, which our 12.10.5 base does not have, so that override is
  intentionally not ported — the extension point is live, the opt-out is inert.

Adaptations for symbols our base lacks (nothing copied blindly):

- NimarkoGram's `PineAdapter` carries a large crash-diagnostics subsystem built on
  `NimarkoCrashContext` and a `PythonBoundarySanitizer.ScanResult` API. Our base
  has neither (our sanitizer is the `containsRawPythonProxy` generation), so the
  diagnostic phases, `unverifiedBaseline`, the `scan`/`ScanResult` refactor and the
  `diagnosticPluginId`/`diagnosticRuntimeOwner` accessors were dropped. The
  guard/bypass/support-method protections, the point of the port, are all kept.
- Added `PluginsController.captureInitializedPluginRuntime()` (plus the
  `diagnosticInstance` field set in `SingletonHolder`) that `PluginHookGuard`'s
  `captureRuntime()` needs; it reads the already-created controller without forcing
  `getInstance()`.

No new user-facing strings, so no `strings_*.xml` changes.

## exteraless pill stack → LinkiGram infocards

LinkiGram **already had** the equivalent of the exteraless pill stack: the
info-cards subsystem (`app.nimarkogram.messenger.infocards`, prefs file
`nm_pillstack`, `InfoCardStripView`, `InfoCardRegistry`, `InfoCardType`,
`BaseInfoCard`, and the `NM_CARDS_Title` settings screen). Porting the exteraless
pill-stack layer wholesale would have duplicated a working system, so only the
extra pills it ships were added, as `BaseInfoCard` subclasses.

New files live in `app/exteraless/pillstack/pills/` with the original
`app.exteraless` package and credits kept:

- `TelemetryCard` — base for local telemetry (measure on `Utilities.globalQueue`,
  apply the text on the UI thread), plus Refresh/Settings long-press.
- `RamCard` (ActivityManager), `CpuCard` (`/proc/stat` deltas),
  `NetSpeedCard` (TrafficStats deltas), `DcPingCard`
  (`ConnectionsManager.native_getCurrentPingTime`) — adapted from `RamPill`,
  `CpuPill`, `NetSpeedPill`, `DcPingPill`.
- `GhostCard` (tap toggles, long-press opens ghost settings) and `LastSeenCard`
  (own status via `users.getUsers`), adapted from `GhostPill` / `LastSeenPill`.

Wiring:

- `InfoCardType` ids match the exteraless `PillType` for the shared ones and add
  `GHOST(7)`, `RAM(8)`, `CPU(9)`, `NET_SPEED(10)`, `DC_PING(11)`, `LAST_SEEN(15)`.
- `InfoCardRegistry.ensureRegistered()` registers all six; the existing settings
  screen picks them up automatically (toggle/reorder/colour modes already worked).
- Icons: reused `ghost_solar`, `msg_speed_solar`, `msg_online_solar`; drew our own
  `pill_ram` / `pill_cpu` / `pill_ping` vectors (no upstream artwork copied).

Adaptations for symbols our base lacks (nothing copied blindly):

- Their `app.exteraless.ghost.OwnLastSeen` → `LocaleController.formatUserStatus`
  with `R.string.Online` for the online state.
- `NekoConfig.isGhostModeActive`/`toggleGhostMode`/`GhostModeActivity` →
  `NimarkoConfig.isGhostModeActive`/`setGhostMode` and `GeneralPreferencesActivity`.
- `PillStackSettingsActivity` → `InfoCardsPreferencesActivity`.

Strings: `values/strings_oe_pillstack.xml` + `values-ru/` (EN/RU parity).

**Not ported: `feed` and `drawer`.** They are deep host rewrites against a
different base (ChatActivity ~150 call sites, `FeedChatIntegration.Host`,
`MessagesStorage`/`ConnectionsManager` edits) and depend on infrastructure
LinkiGram does not have (`tw.nekomimi`, `NekoConfig`/`NaConfig`/`ConfigItem`,
`BaseNekoSettingsActivity`, `AppearanceConfig`, `FeedController`,
`MainMenuHelper`/`DrawerContainer`). Drawer additionally depends on feed. Both
are documented as out of scope rather than half-wired.

**Also not ported: the `GOLD` / `ETH` / `EUR` pills (ids 12-14).** LinkiGram's
`InfoCardRates` has no gold/ETH source, so they would need new network backends;
the rest of the port is self-contained.

Checks run before push: brace balance on every touched `.java` (+0/+0), XML
well-formedness on the new string files and drawables, EN/RU key parity, and a
sweep of every external symbol the new code references against this tree.

- DialogsActivity shows "Telegram" in the collapsed state while the stories
  header shows "LinkiGram" — a second title path that bypasses `getStringV2`.
  `resolveMainTitle` is called from exactly one place, so the other title is set
  somewhere else, most likely on scroll.
- Camera module switching (0.6x / 1.0x / 1.9x) was implemented and then removed
  at the user's request: the button never became reliably usable on SM-G973F,
  which reports each physical lens as its own camera id rather than one logical
  multi-camera.
- Not yet ported from exteraless: `nowplaying` (1365 lines) is done; `pillstack`
  is now merged into infocards (see the pill-stack section above); `feed` (5649)
  and `drawer` (3734) are deliberately **not** ported — they need infrastructure
  and host rewrites this base lacks (documented above). Decide per module what to
  take rather than copying wholesale.
- Now ported from NimarkoGram: DEX plugins and hook guards (see the section below).
- Release 2.5.1 was published on GitHub with an announcement to the channel.
  Whatever ships next needs its own release entry, or users never get it.
- The bot token was pasted into chat once and should be rotated via @BotFather.

## X-CFG — built-in VPN bypass (2.5.4)

Ported the X-CFG plugin (author @x_vare, GPL, kept) as a built-in bypass
method, mutually exclusive with tg-ws-linki. Six files in
`app/nimarkogram/messenger/xcfg`: `XcfgPrefs`, `XcfgApi`, `XcfgCore`,
`XcfgVless`, `XcfgController` (~1.2k lines), `XcfgCabinet`, plus the cabinet
tab injection, autostart and settings rows. Log tag is `xcfg`; file log via
`XcfgController.fileLog` → `files/log.txt`.

How it works: login through the bot `xvpnoff_bot` (code is read from chat
history/updates), the subscription URL comes back from `/me`, links are
`vless://` and go into an Xray config. The core is AndroidLibXrayLite shipped
by the X-VPN server as DEX + gzipped `.so` and loaded with `DexClassLoader`
from `files/xcfgcore/` (`libgojni.so` 33.6 MB unpacked, `classes.dex` 12 KB).
The core listens as a local SOCKS5 proxy that Telegram is then pointed at
through the normal proxy path.

Things that were real bugs, in order found:

- **Server stalls.** x-vpn.x-vare.com (Cloudflare, 188.114.97.1) serves any
  response larger than ~16 KB as ~16–18 KB and then dead-stalls the socket.
  Reproduced from a PC with curl and python on several paths. Small files and
  `Range: bytes=0-16383` requests answer in ~0.4 s. Fixed with
  `XcfgApi.downloadChunked`: sequential 16 KB ranges, 4 retries per chunk,
  SHA-256 check, direct download first with a 45 s read timeout then the
  chunked fallback (`XcfgCore.downloadWithRetry` logs each stage). The 11.75 MB
  `.so.gz` needs ~5 min on device; nothing is logged per chunk, only at the end,
  so silence in logcat is normal. Hashes live in `XcfgPrefs` and matched the
  copies exteraless had unpacked on the phone.
- **Cabinet spinner froze** because `vpnEnable` ran on the single-thread pool
  that also served the UI callbacks. It now runs on its own `xcfg-enable`
  thread and is guarded by an `AtomicBoolean` (the "VPN уже включается…" toast
  is that guard, not an error).
- **Two bypass radios checked at once.** The X-CFG radio row read live
  `XcfgController.vpnActive()` while the other two read
  `NimarkoWsBypassConfig.routeMode`, so after the cabinet enabled the VPN both
  rows showed selected. All three now read `routeMode`; `ROUTE_XCFG = 2` was
  added (load/save accept it), enabling from the cabinet/radio/autostart sets
  it, disabling returns to `ROUTE_TGWS`, and a failed enable rolls back to
  tg-ws so the bypass never silently disappears.
- **Subscription servers.** The cabinet can now re-fetch the server list from
  the API (`refreshServers`, restarts the core on the fresh list if the VPN is
  running) and pick one server (`showServerPicker` → `pickServer`). Auto (−1)
  keeps the leastPing balancer; a chosen index pins the config to that single
  outbound. Names come from the vless `#remark`. Choice persists as
  `tg_server` in the same prefs file.

Root access on this device (KernelSU Next) is the fastest way to stage the
core: `su -c cp` into another app's data dir is blocked by SELinux categories
and the denials are intermittent — `sha256sum`/`dd` reads sometimes work while
`cat >` does not. `su 10416 -c` does not help (its context stays `ksu:s0`).
What worked in the end: copy into `/data/local/tmp` as root, then
`su -c` copy the files into `files/xcfgcore/` during a window when the denials
were gone. Alternative if that fails: MT Manager on the phone.

## Where this session stopped (2.5.4, before release)

- 2.5.3 is the published release. 2.5.4 is the current
  `gradle.properties` version (`APP_VERSION_CODE=20250400`) and is meant to be
  the last of the 2.5.x line. Builds that passed and are on the phone:
  `38786191857` (X-CFG port), `38793111331` (mutual exclusion), `38810598498`
  (radio fix), `38813793105` (server refresh + picker, commit `a8b817c2`).
  APKs are in `.tmp-apk-2.5.4d/` and `.tmp-apk-2.5.4e/`.
- Verified on device: X-CFG login completes, the core starts
  (`vpn: ядро: Started successfully, running`), 10 servers, `прокси Telegram
  включён`, notifications keep coming. Not yet verified by the user: the new
  server buttons and that the bypass radios now show a single selection.
- Before the 2.5.4 release commit: remove the `LinkiPluginHook` diagnostic
  logging in `MessagesController.processUpdateArray` and
  `PluginsController.executeUpdateHook`.
- Publishing needs the user's explicit go-ahead every time: GitHub release
  `prerelease=false` (the in-app updater reads `/releases/latest`), channel
  post, landing deploy.
- `landing/index.html` was updated for 2.5.3 (the stale "Новое в 2.21" proxy
  banner label was dropped) but **is not deployed**: `vercel --prod` returns
  "Not authorized" and the interactive `vercel login` has to be completed by
  the user in a browser. The file is the user's own work — do not commit it.
- 2.6 is planned as the Telegram base update to **13.0.2** plus **Liquid Glass
  for the bottom tab bar** (one shared sliding pill with spring + squash-and-
  stretch, draggable by finger, moving specular highlight). The glass package
  and per-tab selector already exist in this tree
  (`org/telegram/ui/Components/glass/`, `MainTabsActivity`, `ChatAttachAlert`,
  `StarGiftPreviewSheet`, `StatisticActivity`), so the work is a shared
  selector view plus a drag controller, not a new subsystem. Do not build it
  on 12.10.5 — it goes in with the 13.0.2 merge.
- Phone: SM-G973F, Android 16 (Project Infinity X 3.12), root via
  FrEeRuNnEr kernel 4.14.356 v3.8 + KernelSU Next 3.3.0 (manager upped from
  3.2.0 to match the kernel). `su` works from adb and is not visible to the
  app in the usual way — KernelSU Next hides it from `adb shell` by default.
  adb drops constantly; `adb kill-server` + retry usually recovers it.

## Current handoff — 2.6 Liquid Glass (2026-10-16)

Read this section first when continuing in a new chat. Then check `git status`,
the current branch and recent commits; this file can lag the checkout. Keep the
user's standing boundaries: do not change X-CFG, `wsbypass`, or `landing`.

- Current checkout is branch `codex/2.6`, commit
  `3f847505366fa330d73139fc73765261dd62e444` (`Restore solid tab material when
  Liquid Glass is off`), based on Telegram **13.0.2**. App version is
  `2.6.0-dev` (`APP_VERSION_CODE=20260001`).
- **Build 2.6 only from branch `codex/2.6`.** Before starting Actions, verify
  `git branch --show-current` is exactly `codex/2.6` and
  `gradle.properties` contains `APP_VERSION_NAME=2.6.0-dev` (and the expected
  2.6 version code). Do not build this work from `main` or take an APK from
  another chat/run just because it is newer. After downloading, verify the APK
  metadata (`versionName=2.6.0-dev`) before installing or handing it over; a
  2.5.4 APK is the wrong artifact.
- Liquid Glass setting reported OFF on the user's phone. Root-only read of
  `mainconfig.xml` showed `lite_mode6=262143`: the Liquid Glass bit
  (`1<<18`) was clear, while legacy chat blur (`1<<8`) remained on. This was
  initially mistaken for the reason the tab bar still looked glassy.
- Global appearance switch was wired to existing blur surfaces in commit
  `cf8e1ba22`; commit `3f8475053` additionally switches the main tab bar to
  `BlurredBackgroundProviderImpl.mainTabs(...)` when Liquid Glass is OFF.
  Actions build **38951695984** passed, and its `2.6.0-dev` APK (92,365,360
  bytes) was installed with `adb install -r`, preserving app data.
- The user still reports that switching Liquid Glass OFF leaves the visual
  effect on. The post-install screenshot attempt was inconclusive: the device
  went to sleep and showed a black screen. Therefore this regression is **not
  verified fixed**; diagnose the actual rendered UI when the device is awake,
  trace all surfaces that continue to use blur/material after the flag changes,
  and fix the underlying behavior before reporting completion. ADB device is
  `RF8MA188NFZ`; USB debugging is enabled, though ADB may drop.
- User also requested that the message long-press/context menu use the Liquid
  Glass style. This has not been implemented yet. Inspect the menu presentation
  path and existing glass components before changing it; respect the global
  toggle so OFF removes the effect there too.
- User wants to use a fresh chat for each change. In every new chat, read this
  handoff and the relevant parts of `AGENTS.md`, summarize the current state
  briefly, then proceed without asking them to repeat project history. Update
  this section after meaningful work (commit/build/install/verification),
  recording what is confirmed and what remains open. Never store credentials,
  tokens, or other secrets here.


### Pending validation — 2026-10-17

The installed build 39019267465 was a rebuild of 3f8475053; the user confirms
that OFF still leaves glass and the message context menu has no Liquid Glass.
New fixes in this checkout address the actual paths: main-tab creation no longer
unconditionally applies its shader; disabling a RenderNode drawable explicitly
clears `renderNodeFill.setRenderEffect(null)` (clearing the Java wrapper did not
remove the GPU effect). Existing drawables also check the global flag on draw.
The OFF tab/menu backgrounds are opaque. Bitmap-backed menus gain a RenderNode
refraction path on API 33+, and the message menu uses a more detailed background
capture, lighter tint, and rounded refracting edges when enabled. These changes
require a fresh CI build and on-device ON/OFF + message-menu validation before
claiming the regression is fixed. The previously installed APK lacks these fixes.

The shared selector in `MainTabsLayout` also drew its own specular gradient and
stretch effect independently of the global flag. These are now gated by the same
Liquid Glass flag; OFF retains a flat sliding selection indicator. Build
39025563690 was cancelled during checkout to include this additional fix.


## Linki Glass switches — 2026-10-17

User confirmed build 39025681460 works: global OFF and message menu are fixed.
New request: rename visible Liquid Glass labels to Linki Glass and style switches.
Switch.java now uses the existing refraction shader for the moving thumb over its
colored track, with surface reflections and animated stretch. Global OFF restores
the selected normal/MD3 style; color override transitions retain their original path.
Visible EN/RU labels renamed; internal resource and preference keys preserved.
Implementation pending CI build/device review. Build only codex/2.6, 2.6.0-dev.


## Linki Glass installation update (2026-10-17)

Build 39027613487 succeeded and its APK was installed with `adb install -r` on SM-G973F. Data preserved. Verify switch rendering on device; active branch codex/2.6, version 2.6.0-dev.


## Sticker sizing and switch follow-up (2026-10-17)

User reported the previous build wasted a 20-minute build cycle: the sticker slider did not resize ordinary chat stickers, and the preview used an unrelated sticker. Root cause: prior scaling was added only to the link-preview branch; regular sticker messages size themselves in `ChatMessageCell`'s `isAnyKindOfSticker()` path. Current edits scale `maxWidth` and `maxHeight` there and rebuild fragments on slider release. The preview now constructs the exact document from the user's Bot API file ID fields. Glass rendering was removed from the shared `Switch`; retain the separate global Linki Glass setting and glass surfaces. Changes require CI compile and on-device verification of the exact preview/download and slider in a real chat. Build from codex/2.6, version 2.6.0-dev only.


## Latest correction installed (2026-10-17)

Commit c0dbe334c removed the glass treatment from shared switches and fixed sticker scaling in the normal message layout. It also puts the exact decoded sticker document from the user's Bot API file_id in the size preview. CI rerun 39041526461 succeeded; artifact was installed with adb install -r. Verified package app.linkigram.messenger versionName 2.6.0-dev, versionCode 202600019, install time 2026-10-17 18:08:33. Device screen was asleep at last screenshot, so visual preview/file-reference loading and real-chat slider behavior still need verification. Ask user to wake/unlock and open Appearance > Sticker size; inspect before any UI interaction. Continue on codex/2.6, do not touch X-CFG/wsbypass/landing.

## Sticker fix device verification (2026-10-17)

User opened Appearance > Sticker size on the installed build. The exact supplied
sticker loaded in the preview (black dancing silhouette with “а что звучит
хайпово”). Moving the slider to maximum visibly enlarged it; releasing updated
the preview immediately. Slider was returned to the user's original value 10.
Shared switch styling is reverted. No further build needed for this request.

## Install successful CI builds

User preference (2026-10-17): after every successful APK CI build, download the
artifact, verify package/version against the intended branch, and install it on
the connected phone with `adb install -r` to preserve app data. If the phone is
not visible or artifact download is temporarily unavailable, keep going where
possible and report the exact blocker; resume installation when connectivity
returns without requiring the user to repeat this preference.

## AI auto-reply build installed (2026-10-17)

CI run 39047948894 succeeded for commit c3ae2b5aaf5dbb68045e12fde2f6152621fb2b2f.
APK artifact `linkigram-apk` was verified with aapt2: package
`app.linkigram.messenger`, versionCode 202600019, versionName `2.6.0-dev`.
Installed on SM-G973F (`RF8MA188NFZ`) with `adb install -r`; install succeeded
and preserved app data. Install time: 2026-10-17 21:15:54. User preference is to
automatically install every successful CI APK build after verifying branch and
manifest version (see install rule above).

## AI auto-reply follow-up (2026-10-17)

User requested replies only to Telegram replies, access from forum topics, and
speaker-aware group context. Commit cda067bb4 on codex/2.6 implements: require a
nonzero reply_to_msg_id; include the referenced message and author when locally
available, current author, and author-labeled context from the same forum topic;
show the setting in forum/topic menus and permit forum groups through eligibility.
EN/RU notice now discloses the reply/author/context data sent to the provider.
`git diff --check`, XML parsing, and EN/RU key parity passed. CI run
39058525005 is queued for 2.6.0-dev; install it with `adb install -r` after
successful build and verify its manifest first. Do not touch X-CFG/wsbypass/landing.

CI follow-up: run 39058525005 caught missing `senderName(MessageObject)` overloads
before APK assembly. Fixed in commit 4d73757fd; rerun 39058939550 is queued for
that exact branch commit. Install only after smoke compile/build succeeds and APK
manifest matches 2.6.0-dev.

## AI auto-reply queue and memory follow-up (2026-10-17)

User reported that a second reply was not answered and asked for persistent
memory. The previous controller silently dropped requests while busy or inside
its one-minute per-chat cooldown. Commit `381e279a7` removes that cooldown and
queues requests on the single worker, retaining the cap of three requests per
chat per ten minutes. Generated outgoing messages are excluded from the manual
send cancellation revision, so they do not cancel the next queued answer.

The previous-message context now defaults to six and persists in the existing
AES-GCM Android Keystore protected, no-backup settings file, scoped by chat and
forum topic. The setting can be 0–6; a chat settings row clears stored memory.
AI replies are included in subsequent context. EN/RU disclosure explains local
encrypted retention and provider transfer. Commits `381e279a7` and
`7c6467aad` are pushed to `codex/2.6`. CI run `39059497890` failed on an uncaught
`JSONException`; run `39060053152` was cancelled at the user's request. Run
`39061238264` passed on `7c6467aad3e12a477f62a31dc2a9ba55093bd9cb`. Artifact
`.tmp-apk-2.6.0-dev-39061238264/app.apk` has package `app.linkigram.messenger`,
versionCode `202600019`, versionName `2.6.0-dev`, SHA-256
`63C1805364EFF91F6449D699D3926D8545A288D377C90894D6790DA89EEB1267`; installed
successfully with `adb install -r` on SM-G973F at 2026-10-17 22:37:53. Verify on
device that a second queued reply sends and encrypted per-topic memory survives an
app restart; these functional checks remain pending. The user confirmed the lyrics
search issue was caused by their VPN disconnecting; no lyrics-search code changed.

## 2.6.0 promotion to main — 2026-10-17

The user authorized promoting the 2.6 work to `main` and setting the stable app
version. Telegram base is 13.0.2. `gradle.properties` now has
`APP_VERSION_NAME=2.6.0` and `APP_VERSION_CODE=20260002`.

Commit `5dbb551d6` merges the 2.6 work with main's Linki Glass refresh fix and
was pushed successfully to `origin/main`. The push output confirmed
`4c3e680b8..5dbb551d6 HEAD -> main`. Run 39063854026 succeeded on `main` at commit `1b6cfc6d4ea701af7cdb58d4f11d828b9fb7b5ad`. Its APK was verified as package `app.linkigram.messenger`, versionName `2.6.0`, versionCode `202600029`; APK signature verification passed. SHA-256: `9c82710bac4d9505a124230333d9d86e2edc6b1a910f688df45b2a367a3a46a1`. Installed with `adb install -r` on SM-G973F.

Stable GitHub release `v2.6.0` is published at https://github.com/timaa130704/LinkiGram/releases/tag/v2.6.0 with `prerelease=false` and asset `LinkiGram-2.6.0.apk`. The user will post the Telegram announcement.

The separate local `C:/Users/dolbaeb/LinkiGram` checkout contains uncommitted
user edits, including `landing/` files and artifacts. Do not reset, stash,
overwrite, or include them in commits. Do not change X-CFG, `wsbypass`, or
`landing/` as part of 2.6 release follow-up.

## 2.6.1 follow-up (2026-10-18)

Continue from development worktree branch `codex/2.6.1`, based on stable 2.6.0.
Current app version is `2.6.1-dev` (`APP_VERSION_CODE=20260100`); keep package
`app.linkigram.messenger`. The user's accepted scope is link tracking cleanup,
plugin installation safety/permissions, custom SVG icon packs, and all Material
3 items from exteraless. Do not touch X-CFG, `wsbypass`, or `landing/`; do not
publish without fresh explicit authorization.

Audit before implementation: AyuGram deleted-message DB import/export already
exists in `AyuData` and `AyuMomentsPreferencesActivity`. The Material 3 group
already exposes eight options in `AppearancePreferencesActivity`, with code
integrations for loaders, slider, switch, chat header, navigation, list cards,
MD3 player and mini-player. Do not duplicate these. Link tracking cleanup is
implemented centrally in `Browser.openUrl` and text paste via
`LinkTrackingCleaner`; a custom SVG ZIP importer is implemented through
`CustomSvgIconPack` and the icon-pack selector. Plugin installation now has a
heuristic source review, and the user selected active permission blocking:
enable prompts persist per-plugin Telegram API and file permissions, and the
Python SDK gates account/connection, message/request, media-file and edit
helpers through `PluginPermissions`. This is **not an OS sandbox**: plugins run
in-process and can bypass SDK checks through direct Python/Java imports. Do not
describe this as complete isolation; a real sandbox needs a separate design.
Material 3 list rows are now separated on the main Settings screen when the
existing `Material 3 > List items` option is enabled. Adjacent account/settings
rows get 4dp spacing; card radius remains controlled by `Section radius`.
Commit `c36733fe2` was built successfully in Actions run `39074433830` on
`codex/2.6.1`; APK `.tmp-apk-m3-list-39074433830/app.apk` has manifest
`versionName=2.6.1-dev`, package `app.linkigram.messenger`, SHA-256
`6915FA0CA16DBC1FD9EBC3A5CCB73C9DB903FB8FE66E7D12E271B2970B27F7E6`.
APK was installed with `adb install -r` on SM-G973F; data was preserved and
`dumpsys package` confirmed `versionName=2.6.1-dev`, `versionCode=202601009`.
Visual confirmation of the separate Material 3 cards is still pending; do not
claim visual verification until the user opens the screen and checks it.

Licensing: project `LICENSE` is GPL-2.0-only while exteraless is GPL-3.0.
Implement compatible behavior independently; do not copy GPL-3.0 source into
this repository without an explicit license decision.


## Material parity correction — 2026-10-18

User rejected the settings-only 4dp spacing change. Work now targets the shared
Material component behavior using exteraless commit
`1ad6a60a6834ffca722b1a22f4fba27ecb29a061` as a visual/behavior reference;
no GPL-3 source files are vendored. Independent implementation in `MaterialStyle`
and `MaterialProgress` drives shared RecyclerListView segments, 2dp gaps,
4dp inner corners, section radius, separated headers, standard row heights,
neutral circular settings icons and pill master rows. The previous UItem spacer
hack is removed. Material switches/sliders use the reference metrics; radial
spinners use the already-installed Material 1.13 LoadingIndicator (Apache-2.0),
determinate progress uses a track gap and expressive wave. Material navigation
uses equal destinations, individual selected indicators and 64dp height with
system insets; the chat-header option now also affects the middle pill and fade.
Appearance exposes enable-all plus independent component toggles, divider style
and separated-header controls. Preserve the existing Linki Glass mode when
Material options are off. CI compilation and real-device ON/OFF navigation,
settings and chat checks are required before claiming parity. X-CFG, wsbypass
and landing remain outside this task.


### Material build ready for manual installation — 2026-10-18

Shared Material changes are in `30cd9bd9b`; `9e9f8eabc` fixes the header type
reference found by CI and preserves top-fade visibility. Actions run
`39077496616` succeeded on `codex/2.6.1` at
`9e9f8eabcd6686f3b0a2f82a6d047ee1fdc7a77a`. Verified ZIP integrity and binary
manifest: package `app.linkigram.messenger`, versionName `2.6.1-dev`,
versionCode `202601009`. APK size 92,774,049 bytes; SHA-256
`daf7e0511b632eaf6536bcc178fd6708a45c4d6f87f17eef21b99edecf0820ad`.
Artifact: `.tmp-material-build-39077496616/app.apk`; convenient copy in this
DEV checkout: `LinkiGram-2.6.1-dev-Material-39077496616.apk`.
The user explicitly requested building only and will install it in the morning;
do not auto-install this artifact until requested. On-device visual parity and
ON/OFF behavior are not yet verified. Enable the full preset through Appearance
> Material 3 > Enable all Material 3 styles, then check standard settings pages,
nav destinations, chat header, toggles, sliders and loading states. Do not claim
pixel-identical parity before that check. No release was created. An automatic
approval review rejected saving the artifact in the primary checkout, so all
new source and APK output remain in the dev checkout.


### Material APK installed — 2026-10-18

User explicitly requested installation. Verified APK SHA-256 against the build
record above, then installed `LinkiGram-2.6.1-dev-Material-39077496616.apk` on
`RF8MA188NFZ` using `adb install -r`: Success, data preserved. Device package
metadata confirms `versionName=2.6.1-dev`, `versionCode=202601009`, update time
2026-10-18 12:49:18. Visual Material parity and ON/OFF checks remain unverified.


### Navigation and icon-pack corrections — 2026-10-18

User reported a black-edged rectangular bottom panel and stock chat receipt icons
after choosing Plumpy. Device preferences confirmed Material navigation OFF,
floating mode OFF, Plumpy selected. Material no longer overwrites the user
navigation mode; liquid refraction is restricted to floating tabs to avoid
zero-radius refraction on a docked rectangle. Icon selection now reloads Theme
resource caches before fragment rebuild; Theme drawable creation uses the app
icon resource wrapper even when an Activity retains stock Resources. Build and
device verification pending. Existing overwritten navigation preference is not
silently migrated because explicit docked selections cannot be distinguished.


### Panel/icon-pack APK built — 2026-10-18

Actions run 39117724865 succeeded on codex/2.6.1 at
18e8d8ebef09b8375c44ac1ad71e9dd337e463e2. Downloaded artifact
.tmp-panel-icons-39117724865/app.apk, convenient copy
LinkiGram-2.6.1-dev-panel-icons.apk. Verified binary manifest: package
app.linkigram.messenger, versionName 2.6.1-dev, versionCode 202601009,
arm64-v8a. ZIP integrity passed. SHA-256:
471dc30faac5172b371ebde5e16f60a87a9409a9b967bf1f143fffbd775d60bb.
This turn requested building; this artifact has not been installed and the
panel/icon fixes remain unverified on device. No release created.


### Panel/icon-pack APK installed — 2026-10-18

At the user request, installed LinkiGram-2.6.1-dev-panel-icons.apk from
Actions run 39117724865 on RF8MA188NFZ using adb install -r: Success.
SHA-256 matched the build record; data preserved. Visual confirmation of
the panel and chat icon-pack behavior is pending user feedback.


### Optional beta OTA channel — 2026-10-18

User requested a beta toggle in automatic updates. Existing LaunchActivity
onResume schedules checkOnLaunch after 2500ms; enabled by default, once per
process (with existing hourly throttle). Added persisted betaUpdates, OFF by
default, in LinkiUpdateSheet. Switching channel immediately rechecks and clears
stale release selection; unavailable while checking or a download is active/paused.
Stable mode keeps releases/latest. Beta mode scans paginated GitHub releases,
skips drafts and entries without a valid HTTPS APK, chooses highest version
across stable and prerelease. Stable beats a prerelease of the same base; version
comparison handles dev/alpha/beta/rc and numeric suffixes without integer overflow.
Equal/older installed versions are not offered. No APK build or device verification
for this change yet. No release published.


### Beta OTA APK built and installed — 2026-10-18

Actions 39119970505 succeeded on codex/2.6.1 at
953d8013fc2ea0cdf5277fedd4cb8a2bb2edac1c. Artifact
.tmp-beta-ota-39119970505/app.apk verified ZIP integrity and manifest:
app.linkigram.messenger, 2.6.1-dev, versionCode 202601009, arm64-v8a.
SHA-256 f369d5ae38389185115c204b799e400d2fbe0861a30bebc7e620cc8a34ab110b.
Installed at user request on RF8MA188NFZ with adb install -r: Success,
data preserved. Beta toggle remains OFF by default. On-device interaction
and real prerelease selection have not been verified. No release published.


### Ghost exceptions, recording control and chat indicator — 2026-10-18

User accepted suggestions 2/3/6. Local implementation adds explicit normal-chat
vs global ghost policy via chat header ghost icon and existing overflow entry.
Normal-chat overrides use client user ID plus signed dialog ID, independent
across accounts/users/groups. Legacy read/typing exclusions remain fallback until
an explicit per-chat choice is saved. ConnectionsManager passes its actual account
to the interceptor (old public overload retained). Content-read requests without
a peer carry local weak-map dialog context from MessagesController/LocationController;
encrypted peer IDs now use makeEncryptedDialogId. Recording has its own persisted
switch (defaults to previous typing behavior), included in presets/master. Cancel
typing passes through. Stories and typing already had independent toggles.
Header icon tint indicates active hidden actions; tap lists exact effective flags
and chat exception choices, plus a link to settings. Refresh on chat resume.
Online suppression remains account-wide; explicit hidden-story sessions remain
hidden despite the exception. Secret-chat typing packets lack an action subtype,
so separate recording detection is possible only for ordinary setTyping packets.
No APK build/device verification for these local changes yet.

User instruction: after implementing requested changes, build through GitHub
Actions and install the verified APK on their phone automatically. Do not stop
at local edits or ask again; report a disconnected device if installation fails.


### Ghost features APK installed — 2026-10-18

Initial Actions run 39130851618 failed on a missing NimarkoConfig import in
ChatActivity; fixed in 60047d550. Run 39131315330 succeeded on codex/2.6.1 at
60047d55044d32c2ea7ef034c17492f8130c9cf8. Artifact
.tmp-ghost-39131315330/app.apk: ZIP integrity checked; manifest
app.linkigram.messenger, 2.6.1-dev, versionCode 202601009, arm64-v8a.
SHA-256 1137e71cc9e533214cafb9841fdb8d348aa4d8f4ab789d34e4e4febb803183ee.
Installed on RF8MA188NFZ using adb install -r: Success, data preserved.
Ghost indicator and per-chat exceptions still need user interaction checks;
do not claim their on-device behavior verified merely from successful install.


### Feed, menu editor and icon sets — 2026-10-19

User requested implementing the combined channel/chat feed, side-menu customization,
launcher icon sets, per-screen appearance and building an APK. The user cancelled
phone push notifications explicitly; do not send a bot message or notification.

Working checkout is the Linux copy on codex/2.6.1, now version 2.6.2-dev
(APP_VERSION_CODE=20260200). Source base remains Telegram 13.0.2. This supersedes
historical Windows checkout/version references above for this task.

Implementation uses an independent FeedActivity rather than the Externaless synthetic
ChatActivity host rewrite: selected ordinary dialogs per client user ID, bounded
history requests (3 concurrent), per-source cursors, deduplication and chronological
pagination boundary; text/captions and image thumbnails, PhotoViewer for photos/video,
source-message navigation. Feed itself sends no read receipts; opening an original
chat or media uses normal Telegram behavior. Spoiler text is concealed; protected
media previews are omitted. Refresh is manual; secret chats are excluded. Full chat
interactions (reactions/replies/etc.) remain in the original chat.

Existing drawer/icon infrastructure was reused. Added menu visibility/order editor
(long press moves visible items), folder shortcuts, independent feed/drawer accents,
compact spacing/detail labels, and new Mono/Neon/Sunset native vector launcher icons
with aliases/adaptive variants. Existing 16-color HEX picker remains quantized to
installed icon colors; the UI states this explicitly.

Initial build 39247139087 was cancelled during environment preparation so media-viewer
and color-persistence fixes can ship in the same APK. Final build still pending.
APK installation is already authorized by earlier instructions, but adb devices was
empty. SDK platform tools downloaded to sibling work/android-tools/platform-tools.
Do not claim device UI verified from compilation or APK installation alone.


### Feed/menu/icon build completed — 2026-10-19

Actions run 39247497048 succeeded at source commit ff6f59b17160c6dc1d8c9f67bf6bd8bbd92c615d on
codex/2.6.1. Java/Kotlin smoke compile and ARM64 APK assembly passed. Artifact
linkigram-apk (id 12185940238), downloaded using parallel byte ranges after the
regular gh download was slow. Both outer artifact and APK ZIP integrity passed.
Binary manifest: app.linkigram.messenger, 2.6.2-dev, versionCode 202602009,
ABI arm64-v8a. APK size 92790600 bytes, SHA-256:
f0e7b397d3077acd885262ef6a5ab4fdf7817b43801abd992205c025c86b215b.
Signing certificate SHA-256 matches the published 2.6.1 certificate:
627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4.
This confirms package/version/certificate compatibility; no separate apksigner
cryptographic signature verification was run. Verified APK is in the task outputs
as LinkiGram-2.6.2-dev.apk with notes and machine-readable verification metadata.

ADB devices remained empty after download, so automatic installation could not
run. No device UI checks performed. Install the verified artifact with adb install
-r when the phone becomes available; installation was already authorized. No
release published and no push notification sent (user cancelled notifications).


### 2.6.2-dev installed — 2026-10-19

User authorized USB debugging and requested installation. Verified APK SHA-256
f0e7b397d3077acd885262ef6a5ab4fdf7817b43801abd992205c025c86b215b.
adb -s RF8MA188NFZ install -r completed with Success, preserving application data.
dumpsys package confirms app.linkigram.messenger, versionName 2.6.2-dev,
versionCode 202602009, lastUpdateTime 2026-10-19 16:58:11 (device clock).
No device UI/functionality checks performed during installation.


### Feed removal requested — 2026-10-19

User requested removing the feed and explaining the remaining additions. Removed
FeedActivity/FeedSourcesActivity and feed-source preferences, navigation/menu entries,
feed appearance entry and feed-only strings. Saved menu item id 109 is discarded
by the existing MainMenuLayout parser as unknown. Shared appearance preferences
were moved out of the feed package to appearance.CustomizationPreferences; existing
drawer colors/density/labels and folder shortcut keys are preserved. Retained menu
editor, folder shortcuts, Mono/Neon/Sunset launcher icons and launcher picker.
Version remains 2.6.2-dev, APP_VERSION_CODE=20260201 for an in-place update.
Build and auto-install still pending. Do not send phone push notifications.


### Feed removal APK verified — 2026-10-19

Actions run 39265130383 succeeded at source commit d85cf4d112122391e96563a17eb61a8c77a45209.
Artifact linkigram-apk id 12193713934 was downloaded and ZIP integrity checked.
APK package app.linkigram.messenger, versionName 2.6.2-dev,
versionCode 202602019, arm64-v8a; code is higher than installed 202602009.
Signing certificate matches the preceding APK. APK SHA-256:
d059339a9a94f26e30dc849a281889b863da239e4f5cc9d925b6923b3983e5f8.
DEX files were checked: no app.nimarkogram.messenger.feed classes remain.
Verified deliverable: task outputs/LinkiGram-2.6.2-dev-no-feed.apk.

Phone RF8MA188NFZ was authorized and connected when this task started but was
absent from adb after the build. Installation pending reconnection; already
authorized, use adb install -r without asking again. No release or notification.


### Feed removal APK installed — 2026-10-19

User reconnected RF8MA188NFZ. Verified no-feed APK SHA-256
d059339a9a94f26e30dc849a281889b863da239e4f5cc9d925b6923b3983e5f8.
adb install -r completed with Success, preserving application data.
Installed artifact is LinkiGram-2.6.2-dev-no-feed.apk from Actions 39265130383;
expected versionCode 202602019. No device UI checks performed.


### Remove new icons and duplicate picker — 2026-10-19

User requested removing Mono/Neon/Sunset and explicitly confirmed removal of the
duplicate icon-selection screen because the original picker exists in chat settings.
Removed new icon enum entries, aliases, vectors/adaptive resources, labels and
LauncherIconsActivity plus its Appearance entry. Original ThemeActivity icon picker
and the pre-existing icons/colors remain. AppStartReceiver now handles
MY_PACKAGE_REPLACED by running the existing launcher-alias repair: if a removed
icon was the only enabled alias, it restores DefaultIcon automatically.
Version 2.6.2-dev, APP_VERSION_CODE=20260202. Build and auto-install pending.


### Icon/window removal built and installed — 2026-10-19

Actions 39272821097 succeeded on 157fc6626cbb3d90175cb1e0a0a5870707389675, artifact
linkigram-apk id 12197387273. Outer ZIP and APK ZIP integrity passed. Binary
manifest: app.linkigram.messenger, 2.6.2-dev, versionCode 202602029, arm64-v8a.
Certificate matches preceding APK. SHA-256:
da645698d4aa962eb9d260be3defff02d92e3801bea5d20a96b00fa32f6bdc67.
Verified removed icon aliases absent from manifest; original DefaultIcon and
CustomIcon16 retained. DEX files contain neither the removed launcher picker nor
the feed package. Deliverable: task outputs/LinkiGram-2.6.2-dev-menu.apk.

Installed with adb -s RF8MA188NFZ install -r: Success, data preserved.
dumpsys confirms versionCode 202602029, versionName 2.6.2-dev, device-clock
lastUpdateTime 2026-10-19 18:12:59. Launcher query resolves DefaultIcon after
installation. No UI navigation/visual checks performed. No release or push sent.

### Standard settings search integration — 2026-10-19

User requested LinkiGram settings in the existing ordinary settings search, not a
separate screen. ProfileActivity.SearchAdapter.onCreateSearchArray now appends the
static LinkiSettingsCatalog, shared by SettingsActivity and the legacy profile
settings search. Catalog: 400 localized labels/section entries in 29 sections,
generated from settings declarations by Tools/generate_settings_search.py. Run it
after changing settings labels. Generation checks stable GUID collisions; GUIDs
hash screen name + resource name, preserving recent-search identity across builds.
English base-resource labels are also indexed alongside the current locale.
Index construction reads no setting values, credentials, plugin data or live UI.

Linki results match labels and section/root names, case/yo normalization and
transliteration. Native Telegram/FAQ matching remains in its existing branch.
Opening a result instantiates its settings page for the originating account;
universal settings pages scroll/highlight the localized row. Appearance expands
its Material/iOS groups for search without toggling styles. Conditional rows stay
conditional and show an explanatory bulletin if not visible. Message menu/filter legacy lists use their existing row fields for direct scroll;
message/profile appearance selects the right page and highlights its control.
Choice-only custom lists (icon pack selection, reorder) are indexed as sections. No result invokes a setting action directly.

Version 2.6.2-dev, APP_VERSION_CODE=20260203. XML, resource-key existence, Java brace
balance and generated stable ID uniqueness passed; CI/device checks pending.
Build and automatically install the verified APK as previously authorized.
No release or phone push notification requested.

First Actions run 39277563153 was cancelled during checkout to complete direct
legacy-list and message/profile-page targets in the same APK. Final build pending.

### Standard settings search APK built — 2026-10-19

Final source commit 06ab941b7560acbd7819f097660d8426a9d7ee73 built successfully
in Actions run 39278524695 (job 118148499642). Java/Kotlin smoke compile,
ARM64 standalone APK build and artifact upload all succeeded. Artifact
linkigram-apk id 12201310157. APK: task outputs/LinkiGram-2.6.2-dev-settings-search.apk.
Outer ZIP and APK ZIP integrity passed. Binary manifest: app.linkigram.messenger,
2.6.2-dev, versionCode 202602039, arm64-v8a. Signing certificate matches the
prior installed APK. SHA-256:
26fa9a5c6f7878d67c8aeec2440354b1b08ecf37a2c6705e9558d78a9d7fc387.
DEX contains LinkiSettingsSearch, and contains neither removed feed classes nor
the removed duplicate launcher icon picker.

Install attempt is pending: adb did not list RF8MA188NFZ after starting the
server. The device was authorized earlier; retry adb install -r when it reconnects.
No UI smoke check yet. No release or push notification sent.

### Settings crash, Glass and local features — 2026-10-20

Search APK 202602039 installed on RF8MA188NFZ, then Settings entry reproduced a
Resources.NotFoundException from LinkiSettingsSearch.englishLabel (alternate EN
resource context cannot resolve the synthetic string IDs). English labels are now
generated into Java from default XML resources. First fix build 39424202650 failed
because R IDs are non-final and cannot be Java switch cases; replaced that switch
with a SparseArray table. Generator includes 402 entries in 29 sections now.

Glass toggles now use the stored setting even during power saving. Automatic
power saving preserves the saved Glass bit. Appearance and Background screens
share LiteMode.FLAG_LIQUID_GLASS; the legacy linkiAss flag no longer independently
forces the message menu style. Legacy toggleLinkiAss plugin API delegates to it.

User clarified local Premium means device-only. LocalPremium stores a per-user,
per-backend cosmetic badge flag and enables the existing local emoji encoding;
it never edits TLRPC.User.premium or server entitlement checks. Toggle under
LinkiGram General. Badge wired to own profile/settings/drawer account displays.

LocalChatPins stores unlimited pin IDs/order per Telegram user and backend,
independent of account slot reuse. Both native comparators sort these AFTER cloud
pins, preserving the contiguous cloud pin block used by sync/reorder algorithms.
No protocol pinned fields or cloud filter lists are mutated. Separate local
pin/unpin commands in preview and selected-chat menus. Pin icon displayed in list.

Java account slots increased to 15 (native already supports 16); default and max
slot counts agree. Drawer account picker no longer reserves half the slots;
account profile info reflects 15 slots. Version code 20260204 (APK 202602049).
Static Java delimiters/new resource symbols/EN-RU XML/native capacity checked.
Build completed successfully in Actions run 39425808447 for d3bcf78d53d44cbedde01beb4cf3628984f0269e. APK versionCode 202602049 installed on RF8MA188NFZ; signature SHA-256 627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4; APK SHA-256 c0182028fb05a4a42ecfb6fdf1503f4324d55085e6e766ed3638e973af9665f9. Device UI check: normal settings opens, search results for LinkiGram and Local premium appear, and selecting Local premium opens the General page with its local-only description. No post-install Settings crash appeared in logcat. Low-battery Glass behavior and real account sign-ins were not manually exercised. No release or push notification.
