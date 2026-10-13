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

## Working rules learned the hard way

- Check API names in the tree before pushing; five of six builds failed on my
  own invented names.
- Prefer reading device logs over reasoning about symptoms; every real root
  cause here was found in `logcat`, none were guessed correctly.
- The user's device is their own: unlock it before screenshots, do not tap
  blindly, cancel anything started by accident.

## Still open

- DialogsActivity shows "Telegram" in the collapsed state while the stories
  header shows "LinkiGram" — a second title path that bypasses `getStringV2`.
- Camera module switching (0.6x / 1.0x / 1.9x) was implemented and then removed
  at the user's request: the button never became reliably usable on SM-G973F,
  which reports each physical lens as its own camera id rather than one logical
  multi-camera.
- Release 2.5.1 had not been published as of the last session.