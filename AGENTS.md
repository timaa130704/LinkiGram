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

## NimarkoGram DEX plugins and hook guards

Ported the four files that make plugin-generated DEX classes attributable and stop
plugin hooks from recursing into the plugin engine itself (Ettacent's
`PluginDexRegistry`, `PluginDexTracking`, `PluginHookBypassTarget`,
`PluginHookGuard`, ~217 lines; credits kept).

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

- DialogsActivity shows "Telegram" in the collapsed state while the stories
  header shows "LinkiGram" — a second title path that bypasses `getStringV2`.
  `resolveMainTitle` is called from exactly one place, so the other title is set
  somewhere else, most likely on scroll.
- Camera module switching (0.6x / 1.0x / 1.9x) was implemented and then removed
  at the user's request: the button never became reliably usable on SM-G973F,
  which reports each physical lens as its own camera id rather than one logical
  multi-camera.
- Not yet ported from exteraless: `nowplaying` (1365 lines), `feed` (5649),
  `drawer` (3734), `pillstack` (5081). Those are screen rewrites against a
  different base, not drop-in ports — decide per module what to take rather than
  copying wholesale.
- Now ported from NimarkoGram: DEX plugins and hook guards (see the section below).
- Release 2.5.1 was published on GitHub with an announcement to the channel.
  Whatever ships next needs its own release entry, or users never get it.
- The bot token was pasted into chat once and should be rotated via @BotFather.