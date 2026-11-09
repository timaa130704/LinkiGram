# LinkiGram — Agent Instructions

## Telegram base upgrade audit (2026-11-07)

- User requested upgrading the Telegram base to 13.2.1, not changing the
  LinkiGram version name. Official DrKLO/Telegram commit
  `7be672ead283d022812d75c729d3a04dd4c2a2c6` is 13.2.1 (7291); the parent
  reference `1266006d887e75b70674b6b085822c8a315091ba` is 13.1.1 (7237).
- Compared these official snapshots against the current checkout without
  overwriting local work. The upstream diff contains 4,730 files; tree audit
  found 4,732 changed paths including renames as add/delete. 873 local paths
  match the older upstream version, 3,741 additions are absent locally, and
  118 paths diverge. Of 116 diverged paths with both upstream blobs, 86 merged
  cleanly in a dry run and 30 need manual conflict resolution. This is only a
  textual audit; clean merges still need semantic review and CI/device tests.
- `Tools/audit_telegram_update_tmp.py` reproduces the audit. Conflict previews
  are under the local ignored reference checkout's `merge-previews` directory.
  No Telegram base files have been updated, and no upgrade build was run.
  Preserve local modifications; reconcile the 30 conflicts and adapt upstream
  changes to LinkiGram, including plugins, player, privacy and build rules.
- `Tools/resolve_telegram_previews_tmp.py` prepares draft resolutions for 10 of
  the 30 conflicting files under `resolved-previews` in that reference checkout;
  these are unverified drafts, not applied to the project. In particular,
  `ChatMessageCell.java` has very large structural conflicts and requires
  careful integration of Telegram widget/poll changes with LinkiGram hooks.
- Superseded by the applied upgrade below (2026-11-08). The audit numbers stay
  useful as the record of what the merge contained.

## Telegram base upgraded to 13.2.1 (2026-11-08)

- The Telegram base files in `TMessagesProj` were updated from 13.1.1
  (`1266006d…`) to 13.2.1 (`7be672ea…`). The LinkiGram identity was not touched:
  `APP_VERSION_NAME=2.8.1`, `APP_VERSION_CODE=20260211`,
  `APP_PACKAGE=app.linkigram.messenger`.
- Reference trees are local and ignored (`/ci-apk-*/` in `.gitignore`,
  including `tree-13.1.1`, `tree-13.2.1`, `merge-previews` and
  `upgrade-backup`, the pre-upgrade copy of every changed file).
  4,700 upstream changes were applied on top of the local tree; all 30 conflicting
  files were reconciled by hand. No `<<<<<<<`/`>>>>>>>` markers remain.
- New upstream API adopted: `org.telegram.utils.glass.*` (`GlassSource*`,
  `GlassEngine`), `ThemeBind`, `PipCoordinator`, the `blur3` render-node
  drawables and the `setSections`/`cachedIsViewTypeShadow` signature. Removed:
  `Components/blur3/source/*`, `BlurredBackgroundDrawableRenderNode`,
  `Cells/LocationPoweredCell`, `main_tabs_glass_shader`. Local code was ported to
  the new API instead of restoring the deleted classes:
  - `MainTabsActivity` builds the tabs drawable from
    `createTabSource(glass/frosted)` + `BlurredBackgroundProviderImpl.mainTabs`;
    the local Material/ColorOS/LinkiGram-You styling and
    `refreshTabsLiquidGlass()` are kept.
  - `MainTabsLayout.draw()` no longer sets a shader light position; the moving
    rim light is `BlurredBackgroundDrawable.setLightAngle(...)`, quantised to
    4° because the rim nine-patch is baked per angle.
  - `GlassTabView` keeps `applyMaterialMainTab()`, the Material indicator palette
    and the stacked-label alpha, now driven by `applyColors()` /
    `updateNavigationColors()`.
  - `ChatActivityFadeView` uses `GlassSourceColor`.
- Dependency/build decisions: upstream Firebase BoM 34.19.0 (`firebase-messaging`,
  `firebase-pnv`, `firebase-appindexing`), the local `firebase-config` dependency
  removed (no code used it); `minSdkVersion 24` kept; ML Kit `translate` kept
  enabled; Chaquopy/Kotlin/Vosk and the media3 module exclusions kept.
- Checks done (static only): tree-sitter parse of all 2,805 Java files (one
  pre-existing false failure in `MediaController.java`, a literal NUL byte inside
  the XMP string), `Tools/check_braces.py` `+0/+0` on every resolved file, all
  1,143 `res/**/*.xml` files parse, no duplicate resource names, every
  `R.<type>.<name>`, `Theme.key_*`, `ThemeBind.*`, `ClientTheme.*` and
  `BlurredBackgroundProviderImpl.*` reference resolves, no reference to a deleted
  glass class remains.
- Not verified: no Gradle compile, no CI run and no device test. The next step is
  an ARM64 `build.yml` run, then signature/package verification and
  `adb install -r`. Behaviour that still needs a device: glass/frosted tab
  background (moving rim light, Material and ColorOS styles), glass action bars
  and sheets, chat menu (topics panel, 9876/9878 items), the new
  `UniversalFragment`/`UniversalRecyclerView` section drawing, stories cell
  header, PIP (`PipCoordinator`), plugins and the music player.
- There is no Git repository in this checkout and no JDK/Android SDK on the
  machine, so nothing was committed and no build could be started locally.
  Do not describe this state as a finished, verified upgrade.

## LinkiGram 2.9 built and installed (2026-11-08)

- The checkout was turned into a Git repository on `main` (origin
  `https://github.com/timaa130704/LinkiGram.git`, shallow clone base
  `f6c3bb24`). The upgrade and the API fixes were pushed as `80dd94a8`,
  `a11f9fde`, `736b6f49`, `5eccd13b`, and the version bump as `f58adc54`
  (its commit message contains a literal `\n`; history was not rewritten).
- The 13.2.1 prisma failure had nothing to do with the Java merge: the local
  gitlink `TMessagesProj/jni/third_party/libprisma` was stale (`31a5d6fb`),
  while upstream 13.1.1 and 13.2.1 both pin `94640c63`, which is the first
  libprisma with `TokenList(std::string_view)`, `TokenList::swap` and
  `LanguageTree::order()/language()`. `5eccd13b` fixes the pin. Other gitlinks
  that differ from upstream are harmless: ffmpeg, libvpx and wallet engine are
  linked from prebuilt `jni/prebuild/lib`, `third_party/pine` is a local plugin
  dependency, and `tgcalls` was not changed between 13.1.1 and 13.2.1.
- CI runs **42417988725** (2.8.1 on 13.2.1) and **42421295770** (2.9) both
  succeeded, ARM64 only. The 2.9 artifact `linkigram-apk` was verified offline:
  package `app.linkigram.messenger`, version `2.9`, code `202611089`, ZIP
  integrity OK, v2 signature digest and RSA signature verified against
  certificate SHA-256
  `627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4`,
  APK SHA-256
  `368fc50d80f7eae4d857a55e12ee1039a85976a53d8bce5c77e80e1288129aa6`.
  No Java-side apksigner was available, so verification used a local script
  that reimplements the v2 chunked digest and checks the RSA signature with
  OpenSSL (the digest must be computed with the EOCD central-directory offset
  rewritten to the signing-block start).
- The 2.8.1-on-13.2.1 build was installed over the existing app with
  `adb install -r` and checked on a Galaxy S10 (Android 16): no crash on start,
  chat list with the glass tabs and story header, chat screen with drafts,
  replies and the admin tag, Settings, LinkiGram settings (shows the version
  number), and the plugin screen with the engine on and the installed plugins
  listed. Lyrics, player, PIP, widgets, translation and the 9876/9878 menu
  items were not exercised.
- The 2.9 APK was not installed: the device disappeared from `adb devices`
  between the 2.8.1 check and the 2.9 install. Reconnect the phone and run
  `adb install -r` with the 2.9 artifact before claiming the device runs 2.9.
- The 2.9 changelog the user asked for lists only generic "Telegram base
  updated to 13.2.1" plus the LinkiGram-side player, lyrics and translation
  changes; base feature details and the native build fix were explicitly
  removed at the user's request. Release **v2.9** was published on
  2026-11-08 from `504907c5` with the ARM64 asset
  `LinkiGram-2.9-arm64.apk` (SHA-256
  `0f05a390c55f3ef8ce7f08bf0d5029e1fefe9c82c8631f8b2113d4e1ca7c7b9b`),
  verified the same way as the CI artifact and marked Latest.

## Linki Glass over LinkiGram You (2026-11-08)

- Requested behaviour: with LinkiGram You and Linki Glass both on, Linki Glass
  applies on the LinkiGram You screens instead of the solid You surfaces.
  Condition is `ClientTheme.glassEnabled()` = LinkiGram You + `LiteMode.FLAG_LIQUID_GLASS`
  + SDK >= 33; `ClientTheme.glassColor(role)` is the same You role at alpha
  0.66 (dark) / 0.78 (light).
- Removed the You-specific glass suppressions: chat header (`setupGlass`),
  message menu (`scrimBlur3Factory` / `liquidMessageMenu`), input panel
  background (`ChatInputViewsContainer` no longer paints the opaque You rect),
  message-menu background provider, input-panel glass color, filter tabs
  (`FilterTabsView.setBlurredBackground`), search field
  (`FragmentSearchField`, translucent `bg`), floating/sub buttons
  (`FragmentFloatingButton`), and the docked main tab bar
  (`MainTabsActivity.useGlassTabs()` and the three background paths).
- Glass headers for LinkiGram settings: `BasePreferencesActivity`,
  `MessageFiltersPreferencesActivity` and `MessageMenuPreferencesActivity` now
  return `listView` from `getListViewForSimpleGlass()` and skip
  `setAdaptiveBackground` when `glassEnabled()`; `onInsets` keeps the padding
  that `BaseFragmentSimpleGlass.setListViewParams` sets. `UniversalFragment`
  does the same for every screen that does not already override it
  (`UserInfoActivity` and `BusinessIntroActivity` already did).
- `ClientTheme.roundedSurface` (AlertDialog, BottomSheet, popups) uses the
  glass alpha; `UniversalRecyclerView.drawBackgroundRect` fills You section
  cards with `glassColor(SURFACE)`.
- The docked You bottom bar was still solid in practice: `onApplyWindowInsets`
  (and `cgTabsUpdated`) reset `tabsView` to a transparent background over the
  solid `tabsViewWrapper`, undoing the glass set in `updateLayout`.
  `MainTabsActivity` now paints `tabsViewBackground` in every path
  (`updateLayout`, `onApplyWindowInsets`, `refreshTabsLiquidGlass`,
  `blur3_updateColors`, `cgTabsUpdated`) through `glassTabsBackground()`; the
  wrapper keeps the solid color only for the system navigation strip. The
  `MainTabsPreviewCell` in the bottom-tabs settings uses the glass fill too.
- Checks done: `Tools/check_braces.py` `+0/+0` on all 13 changed Java files.
  Committed as `3b5c2499` and pushed to `main`.
- CI run **42428023278** (`3b5c2499`, ARM64) succeeded. Artifact
  `linkigram-apk` (ID 13596654304) verified offline: package
  `app.linkigram.messenger`, version `2.9`, code `202611089`, ZIP and v2
  digest/RSA signature OK against certificate SHA-256
  `627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4`;
  APK SHA-256
  `1cbba2c67e93606df61162b4e9f524cea99fac12d286761e8ef50b2d4dee4dce`.
- Not verified on a device: the phone was absent from `adb devices` and
  `lsusb` during this work, so the APK was not installed. Needs `adb install -r`
  and a visual pass over You + Linki Glass: docked tab bar, chat
  header/menu/input, filter tabs, search field, LinkiGram settings headers,
  dialogs/sheets/popups, and the same screens with Linki Glass off to compare.
  Treat the feature as built but unverified until that pass is done.

- Device pass on the You + Linki Glass layer (`78101bd8`, `90c7cdd5`): the
  docked/floating You bar turned out to be transparent but flat — see the
  section below. Also found and fixed a `StackOverflowError` on entering any
  chat: the 13.2.1 merge had replaced `SharedConfig.bubbleRadius` with a call to
  the enclosing `ChatMessageCell.getBubbleRadius()`, so the bubble radius
  lookup recursed whenever LinkiGram You was off (`90c7cdd5`).

## Why the Linki Glass tab bar still looked flat (2026-11-08)

- Three separate causes, each of which alone was enough to hide the glass:
  1. Automatic power saving (battery below `BATTERY_*`, i.e. 10%) kept
     `FLAG_LIQUID_GLASS` but dropped `FLAG_CHAT_BLUR`. Without blur
     `BlurredBackgroundDrawable.pickSource()` returns the colour-only source, so
     the bar is a flat translucent rectangle with a rim and no page behind it.
     `LiteMode.powerSaverValue()` now keeps `FLAG_CHAT_BLUR` together with the
     glass flag.
  2. `BlurredBackgroundProviderImpl.mainTabs()` solved its fill against the solid
     glass target at 0.85 alpha, which over the You surface is an almost opaque
     slab. With `ClientTheme.glassEnabled()` the fill is now the You glass colour
     at 0.42 alpha (dark) / 0.34 (light). Note the liquid glass shader composites
     the tint over the captured page, so this alpha controls the tint strength,
     not transparency: the page showing through the bar depends on the source,
     i.e. on cause 1.
  3. `cgTabsUpdated` reset the floating bar to an opaque round-rect drawable;
     that path now uses the glass background like every other one, and the glass
     capture band follows the bar that is actually drawn (the docked bar is
     taller than the floating pill).
- Committed as `45259bbb`. CI run **42515040780** (ARM64) succeeded; artifact
  `app.apk` SHA-256
  `2cd2ae030066c815a3d59c4c30a0e14a6e378aeff0815ecad2fb0a4094d9c084`,
  package `app.linkigram.messenger`, version `2.9`, code `202611089`,
  certificate SHA-256
  `627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4`
  (checked offline with androguard; no device install).
- Not verified on a device: whether the bar now reads as glass at low battery
  and with power saving on, the docked variant, and the same screens with Linki
  Glass off. The phone was in use by the user, so nothing was installed or
  touched during this change.

## Material settings sections lost their shape after the 13.2.1 merge (2026-11-09)

- Symptom reported on the device: in the Material style the settings sections no
  longer look like one card — rows get their own rounded corners and gaps.
- Cause: the 13.2.1 merge replaced the second `setSections` callback in
  `UniversalRecyclerView.setSections(int, float, boolean)` with upstream's
  `UniversalAdapter::isShadow`, while the pre-upgrade code passed
  `type -> !isShadow(type) && !(isHeader(type) && MaterialStyle.separatedHeaders())`
  and `this::drawBackgroundRect`. The new `RecyclerListView` uses that callback
  to ask whether a *neighbour* is a section item (`sectionNeighbor`,
  `hasAbove`/`hasBelow`), so the inverted predicate made every row its own pill
  and added the 2 dp segment gap above the first row too. Restored the local
  lambda; the 13.2.1 five-argument overload already defaults to
  `this::drawBackgroundRect`.
- Checked by diffing `80dd94a8^` with the merge and with the 13.2.1 reference
  tree; `Tools/check_braces.py` `+0/+0`. Every settings screen built on
  `UniversalFragment`/`UniversalRecyclerView` is affected, not only the main
  settings list.
- Fixed in `7330d87b`; CI run **42524006442** (ARM64) succeeded. Artifact
  `app.apk` SHA-256
  `c33d8f9dad346492672ecb3fbdffd5b8bdf8e392f2419620ec18ee77098ffc05`,
  package `app.linkigram.messenger`, version `2.9`, code `202611089`,
  certificate SHA-256
  `627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4`
  (androguard, offline). Not installed: `adb devices` was empty, so the card
  shape is still unverified on the device.

## Selected tab, message-menu blur and session diagnostics (2026-11-09)

- The selected-tab capsule of the glass bar was painted with the opaque You
  `CONTAINER` colour. In `GlassTabView.dispatchDraw` it is now multiplied to
  0.34 (dark) / 0.42 (light) alpha when `ClientTheme.glassEnabled()`, so the
  blurred page and the bar itself show through it.
- The message context menu dimmed the chat without blurring it, because
  `createMenu` called the dim-only `dimBehindView(v, true)`. It now uses the
  blurred scrim on Android 13+ with liquid glass, like the reaction menu. The
  popup content is made invisible during the synchronous capture so the menu
  surface is not blurred into its own background.
- Session import failures were opaque. `SessionTransfer.error` now accepts a
  detail and `login` names the refusing stage (native import, `users.getUsers`
  error text, missing/mismatched user, 45 s timeout) and logs it under the
  `SessionTransfer` logcat tag. This is temporary diagnostics; it also explains
  the by-design rejection of this app's own exported session, whose
  authorization is still used by the active account.
- Committed as `9db10d6f`; the settings-section fix is a separate commit. No
  device verification: the phone was not touched on request.

## Message menu anchor, plugin AI agent and OpenCode provider (2026-11-09)

- Message context menu placement: `ChatActivity.createMenu` used to open the
  popup at the touch point (`v.getTop() + y`), so it covered the message. It now
  anchors to the cell: `v.getBottom() + 8dp` when the popup fits below, otherwise
  `v.getTop() - 8dp - popupHeight` above, otherwise the side with more free
  space, then clamped to the list bounds. The `isInsideContainer` (chat in a
  dialog container) path and the full-height path are unchanged.
- Plugin AI agent: new `PluginAgentActivity` (chat UI built on `BaseFragment`)
  and `PluginAgentPrompt` (static SDK description, live installed-plugin
  catalogue, crash report + source assembly, code/id extraction). Entry points:
  the `ai_chat` ActionBar icon in `PluginsActivity` and a per-plugin kebab item
  that opens the agent and immediately reports that plugin. Answers that contain
  a fenced Python file with `__id__` get an "install" action; the file is
  written to `files/plugin-agent/<id>.plugin` and passed through
  `PluginsController.showInstallDialog(fragment, path, false)`, so the normal
  metadata/dependency validation and the update flow apply. The report path uses
  the new `PluginCrashReports.reportText()` plus the plugin source. The
  in-memory conversation is capped at 16 messages / 120k characters and is kept
  for the app session.
- OpenCode provider: `Provider.PRESETS` now starts with a keyless
  `opencode` preset (`OpenCode Zen`, `https://opencode.ai/zen/v1`, model
  `big-pickle`). `Provider.isKeylessUrl`, `Service.isKeyless()` and
  `Service.hasCredentials()` allow it to be used without a key;
  `Client.request` omits the `Authorization` header when the key is empty;
  `AiController.canUseAI` accepts a keyless service; `AiSettingsActivity` hides
  the key row for the preset. Because it is the first preset it also becomes
  `AiConfig.DEFAULT_SERVICE`.
- Verified against the live gateway from the PC: `GET /zen/v1/models` returns
  200 without a key (list includes `big-pickle`); `POST /zen/v1/chat/completions`
  without a key answers 429 `{"type":"error","error":{"type":"FreeUsageLimitError",
  "message":...}}` for this IP, which the existing `readErrorMessage` surfaces as
  the menu/test error text. No key, login or token is stored for this provider.
- Checks: `Tools/check_braces.py` `+0/+0` on all changed Java files, both
  `strings.xml` parse with no duplicate names, every new `NM_PluginAgent*`
  reference resolves. No local Gradle/Android SDK, so this was not compiled
  locally; CI must build it.
- Not verified: the menu flip above/below on a device, the agent chat, the
  install of a generated plugin, the crash-report flow, and an actual OpenCode
  answer from the device IP (free-tier limits are per IP; the test IP is
  already limited). Treat the whole change as built-but-unverified until the CI
  APK is installed and those screens are exercised.
- Committed as `a2fa9af6`. The first CI run **42533251422** failed to compile:
  in `PluginsActivity` the new ActionBar item chained `setContentDescription`
  (a `void` `View` method), fixed in `681983ee`.
- CI run **42533855263** (`681983ee`, ARM64) succeeded. Artifact `linkigram-apk`
  (ID from run download) verified offline: package `app.linkigram.messenger`,
  versionName `2.9`, versionCode `202611089`, ZIP integrity OK, v2 RSA signature
  over the signed data verified with the certificate public key, certificate
  SHA-256
  `627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4`;
  APK SHA-256
  `6b285049f4f0763b4a680ea53839fda1f0c0cd531a4fc55c68b4a027d8bafdf9`.
  The local chunked-digest reimplementation reports a mismatch even for the
  already-installed older APK, so it is the script that is wrong; Android's own
  v2 verification happens at install.
- Not installed: `adb devices` was empty and no phone was visible in `lsusb`,
  so the APK was not installed and the new screens remain unverified on a
  device.
- Free models in the picker (2026-11-09, after the build above): the gateway
  serves several free models (`big-pickle`, `*-free`, ...) next to paid ones.
  `Client.listModelsInfo` parses the endpoint metadata (`name`,
  `cost.text.input/output.price`, `limit.context`,
  `capabilities.reasoning.supported`) into `RemoteModel`; `AiModelPicker` sorts
  free models first, shows a green `Free`/`Бесплатно` badge, prefers the endpoint
  display names (models.dev is the fallback) and, for keyless presets, lists
  only free models. A keyless endpoint that publishes no prices (local servers)
  keeps the full list, and a typed model id is always accepted.
  `Client.listModels` stays as an id-only wrapper. `Tools/check_braces.py`
  `+0/+0`, both strings files parse. Not built or installed yet; the next CI run
  must cover it.

## Session export fix (2026-11-08)

- Exporting to the SQLite formats produced a 0-byte document. `SessionFiles.write`
  opened the SAF stream first and built the database afterwards, swallowed the
  exception, and ran `PRAGMA journal_mode` through `execSQL`. It now builds the
  whole payload (zip / database / text) and reads the database back before the
  document is opened, turns write-ahead logging off explicitly, copies the file
  whole, logs failures with `FileLog.e`, and the export error dialog uses the
  export title and shows the reason so a device test is diagnosable
  (`78101bd8`). The exception text in that dialog is temporary and should be
  removed once the export is confirmed working on a device.

## Telegram account session import/export (2026-11-08)

- Ported the session portability feature from `skb8/Telegram` commits
  `bb3bdf5b` and `0ac0817b` onto the 13.2.1 base as `4b2b4095`. Format
  interop comes from that fork; this tree does not include its QR login,
  APK workflow or API-credential helper.
- Native: `ConnectionsManager::importSession`/`exportSession` run on the
  account network thread. Import refuses an activated account, a test
  backend, DC outside 1–5 or a key that is not 256 bytes, clears pending
  phone/QR login state, clears every DC auth key, installs `authKeyPerm`
  with the SHA-1 key id, marks the DC authorized and saves the config.
  Export refuses the test backend and an unactivated account.
  `TgNetWrapper.cpp` adds `native_importSession`/`native_exportSession` with
  a `SessionCallback` (`onResult(byte[])`); import returns a zero-length
  array on success, export returns 4-byte big-endian DC id + 256-byte key.
- Java: new `org.telegram.messenger.sessions` package (`SessionData`,
  `SessionFiles`, `SessionTransfer`, `StringSessionCodec`, `TdataCodec`).
  Formats: Telethon/Pyrogram SQLite `.session`, Desktop `tdata` ZIP, Telethon
  and Pyrogram String Session, Auth Key + DC ID. Imports reject bots, test
  sessions and malformed keys; tdata uses the Desktop TDF$ container and
  MTProto 1 AES-IGE with an optional local passcode.
- Entry points: a “Log in with a session” button on the phone-number screen
  (confirmation, then native import, then `users.getUsers(inputUserSelf)`,
  then the existing `onAuthSuccess` path), and “Export account session” under
  Settings → Devices → This device. Failed or timed-out imports roll the
  imported key back locally; existing accounts are never overwritten.
- ProGuard keeps `ConnectionsManager$SessionCallback` and its `onResult`.
- Checked locally: `Tools/check_braces.py` `+0/+0` on all changed Java files,
  both strings files parse, no duplicate resource names and every
  `R.string.SessionTransfer*` reference resolves. Not yet compiled or run on
  a device; CI run **42504035400** (`4b2b4095`) was started for that.
- Device validation still required: import each format (at least a real
  Telethon/Pyrogram `.session`, tdata ZIP, and Auth Key + DC), reject a bot or
  expired session, export in every format, reopen the exported files in
  Telethon/Pyrogram, and confirm that a failed import leaves the account
  selectable and does not leave a half-authorized DC. Do not use the primary
  account for destructive checks without a backup of its session.
- `63f8415b` (glass bottom bar) was not installed separately: CI run
  **42502345696** was cancelled by the newer push because the workflow uses
  `cancel-in-progress`. Its Java smoke test had passed; the newer build
  includes both commits.

## exDesktop fork handoff (2026-11-05)

- Fork `timaa130704/exDesktop` contains an initial, unverified ghost privacy
  implementation. Deleted-message preservation has not been ported.
- The original Windows workflow was taken from upstream branch `ci-fix-builds`.
  Upstream Windows run 40503901564 succeeded; this does not establish that the
  fork builds or that its privacy controls work. Check the fork's Actions result.
- Telegram Desktop test API credentials are limited and are not suitable for
  distributing the client. Do not publish releases without explicit approval.

## Правила работы агента

- Пиши по делу, без пустых вступлений и рассуждений. Выполняй запрошенную
  работу, а не ограничивайся описанием плана. Итоговый отчёт делай коротким:
  что изменено, как проверено и что осталось незавершённым.
- Сложные задачи разбивай на 3–10 конкретных шагов, выводи их одним списком в
  консоль и последовательно выполняй, проверяя результаты. Не останавливайся
  только из-за объёма или первой ошибки.
- При ошибках изучай причину и проверяй доступную документацию. Самостоятельно
  пробуй альтернативы; где это возможно и разумно, испробуй три разных подхода
  прежде чем обращаться за уточнением. Не повторяй одинаковые действия, если
  они не могут изменить результат.
- Сначала быстро изучи необходимые файлы и окружение, затем внеси изменения и
  запусти подходящие проверки, тесты или сборку. Исправляй обнаруженные ошибки.
  Не выдавай непроверенную или незавершённую работу за выполненную; прямо
  сообщай о фактических препятствиях и ограничениях проверки.
- Самостоятельно принимай обычные технические решения и доводи задачу до
  результата. Если действительно не хватает критических данных, задай один
  короткий конкретный вопрос с вариантами, когда варианты уместны.
- Все агенты без исключения обязаны вести этот AGENTS.md: после каждой задачи,
  существенного этапа и перед передачей работы записывайте выполненные действия,
  изменения, установленные факты, результаты проверок, ошибки, ограничения и
  оставшиеся задачи. При продолжении работы уточняйте устаревшие записи, не
  удаляя чужие инструкции и важный контекст. Не добавляйте личные данные,
  учётные данные и приватные пути; используйте AGENTS.local.md для приватного.

## Working workflow

- Continue on the user's PC in its existing checkout, directly on `main`.
  Do not create feature branches, worktrees or PRs unless explicitly requested.
- Read `git status` and recent commits before editing. Preserve all existing
  user changes. Fetch and fast-forward only when local work can be preserved;
  never reset, overwrite or discard it to match the remote.
- Discover the actual local paths and tools. Cloud-only `/workspace` paths and
  dependencies are not expected to exist on the user's PC. If no checkout is
  present, clone `https://github.com/timaa130704/LinkiGram.git` locally.
- Build Android APKs through `.github/workflows/build.yml` in GitHub Actions.
  Do not run local Gradle builds; the project workflow uses CI for them.
- Before pushing Java changes, check delimiters and referenced symbols. Check
  XML well-formedness after resource edits. Preserve the ProGuard keep rules
  required by Python plugins, particularly `org.telegram.**` names.
- Diagnose Android behavior using logcat. A successful build or install does
  not establish that a feature works on-device.
- When APK installation is requested, download the relevant CI artifact, verify
  package/version/signature and use local `adb install -r` to preserve app data.
  Check the actual device connection; cloud ADB limitations do not apply to a PC.
- Keep X-CFG, wsbypass and landing outside the Liner task.
- Do not publish releases or send announcements without explicit authorization.

## Latest handoff: lyrics presentation and Liner (2026-11-05)

- On 2026-11-06 the public lyrics aggregator was updated to request word-level
  results and query Deezer, KuGou, YouTube Music and Musixmatch separately via
  lrcmux. Deezer and KuGou returned actual word timestamps for a public test
  track; YouTube Music returned line timing and Musixmatch plain text. Results
  depend on the track and provider. The cache version was incremented.
  `Tools/test_lrcmux_words.py --json-jar <org-json.jar>` checks production
  parsing against offline word-timing and invalid-token fixtures. Device and CI
  validation remain required.
- The sources change is commit `f6c3bb24`; CI run **42256941863** was in
  progress at handoff and its result must be checked before installation.
- CI run **42256941863** succeeded. Its ARM64 LinkiGram 2.8.1 APK signature
  was verified and it was installed with `adb install -r` on 2026-11-07.
  Runtime availability and word highlighting have not been verified on device.
- On 2026-11-06 a subtle cover-color radial glow was added to the player, and
  optional ML Kit on-device translation was added to the message translation
  sheet. It is enabled in chat tools, downloads language models when first used,
  and does not fall back to network translation when local translation fails.
  Rich-message and summary translation are not supported by the local path.
  Java syntax and resource XML were checked; CI and real-device behavior,
  including model download, translation and glow appearance, need validation.
- These changes are committed as `c1bf4f94`; CI run **42218083284** was in
  progress at handoff. This run must pass before installing its artifact.
- CI run **42218083284** succeeded; its signed LinkiGram 2.8.1 ARM64 APK was
  installed with `adb install -r`. On-device translation and glow appearance
  have not yet been verified.
- The upstream exteraless style editor and mini-player remain unported. These
  are explicitly requested and must be adapted without losing LinkiGram lyrics,
  playlists, local themes and other existing functionality.
- At the user's request, Liner integration is being removed. The updated player
  keeps the public lyrics providers and centered lyrics UI. Earlier Liner notes
  below are historical; do not restore the integration without a new request.
- The exteraless reference was checked at `c83765c4`. Its queue drag-and-drop
  implementation and frozen seek-bar behavior were adapted to LinkiGram. The
  larger upstream style editor, mini-player and full-screen player changes have
  not been ported. Host checks covered Java syntax, resource XML, moving the
  current and other tracks, shuffle mode and invalid indices. CI and on-device
  queue behavior still need verification.
- Commit `16612b07` removes the Liner integration and adds those queue/player
  changes; CI run **42031148785** was queued at handoff. Do not describe this
  partial adaptation as a complete exteraless player port.
- CI run **42031148785** succeeded, and its signed ARM64 LinkiGram 2.8.1 APK
  was installed with `adb install -r` on 2026-11-06. Queue behavior and the
  remaining public lyrics providers have not yet been verified on the device.
- CI run **41924704336** at `a0a547c6` succeeded. Its signed ARM64 APK was
  installed with `adb install -r`; it includes the new lyrics presentation and
  still includes Liner. Visual behavior on the device has not been verified.
- Work to remove Liner was started but interrupted when the user requested this
  earlier APK. The working tree contains partially staged deletions and partial
  edits for that removal; they are not included in the installed APK. Preserve
  this state and finish or reverse it only according to the user's direction.

- `1a481ec2` centers synced, word-timed and plain lyrics horizontally, adds
  side margins and larger spacing, and reduces blur on surrounding lines.
  Existing seek and word timing behavior is retained. Device visual validation
  and CI results must be checked separately before claiming they passed.
- Offline Liner checks: `Tools/test_liner.py` passed 28 checks using JDK 17 and
  org.json; `node tools/liner-lyrics/check.cjs` passed its parser checks.
  These use fixtures and do not verify live services.
- On-device Liner requests returned HTTP 403 for account history, library and
  lyrics after the catalog lookup failed. A separate unauthenticated request
  from the PC received a Cloudflare block page. Do not assume the phone's 403
  has the same cause, or that provider responses are working. No credentials
  were inspected. Live authenticated provider recovery remains unresolved.
- The official `tryliner/desktop` client now requests `/v1/auth/challenge`
  before registration and login; challenges may require Argon2id proof of work
  and/or an interactive Turnstile check. LinkiGram's registration supports the
  unchallenged case only and explicitly reports unsupported verification. Login
  does not yet implement this challenge flow. Implement and test the official
  flow before claiming registration and authentication are fully compatible.
- `a0a547c6` hides seek, time and playback controls in lyrics mode and expands
  the lyrics area; the lyrics button remains available to return. It also adds
  the partial registration flow described above. CI run **41924704336** was
  pending at handoff. Validate transitions, smaller screens, center alignment,
  and registration on a real device after a successful build.

## Handoff: LinkiGram 2.8.1 (2026-11-05)

- Plugin files remained on the investigated device even though the plugin list
  was empty after restarting the app. Their enabled preferences were retained.
  The application was not calling `PluginsController.init()` at startup, leaving
  the in-memory registry empty. This explains the observed case; it does not
  establish the cause of every reported disappearance.
- `0a5e8c5d` improves plugin recovery after a crash. `f0f93a25` initializes
  the plugin engine during application startup and rescans installed plugin
  files when the plugin screen resumes. The rescan preserves existing instances
  and settings, and skips pending deletion markers.
- Host checks covered startup process selection, list refresh and rescan, crash
  inventory, and metadata publication. CI run **41906023326**, built from
  `f0f93a25902e1321c1e302818b8b4eeeaa617386`, succeeded. Its ARM64 APK has
  package `app.linkigram.messenger`, version `2.8.1`, code `202602119`, and
  signing certificate SHA-256
  `627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4`.
- This APK was installed successfully over the existing app with `adb install -r`
  and the app was launched. The restored plugin list and plugin execution after
  restart have **not** yet been verified on the device. Test them before claiming
  that persistence is fixed for all users. Full exteraless compatibility remains
  incomplete and needs real-plugin testing as described below.
- GitHub release `v2.8.1` is published with the same CI APK:
  https://github.com/timaa130704/LinkiGram/releases/tag/v2.8.1 . The APK SHA-256
  is `07a4a33aa91aaea2c1e22d56b4b511e4474bd1cc8d3933bf32b14843ea18e41c`.
  Release `v2.8.0` was also published previously.
- Other recent committed changes include `537b3e4d` (reusable plugin Android
  runnables; the user confirmed Clawd Pet moves and responds to touch),
  `2ae363f5` (ghost privacy on outgoing requests), `78660304` (plugin
  quarantine recovery), and `d91c15d8` (deleted-message history and 2.8).
  Ghost privacy and deleted-message behavior still require device validation.

## Earlier handoff: plugin compatibility (2026-11-04)

The user wants plugins which work in `exteraless/exteraless` to work in
LinkiGram. **This is not complete.** Do not report full compatibility based on
the SDK additions or a successful build. Continue the implementation and verify
real plugins on a device, including installation, activation, restart, hooks,
callbacks and unloading. Preserve existing uncommitted work when doing so.

Recent committed work on `main`:

- `88315370`: fix a plugin permission prompt localization crash.
- `387d3162`: invoke plugin send-message hooks from
  `SendMessagesHelper.sendMessage`; previously the controller implemented the
  hook but the actual sending path did not call it. Cancellation now prevents
  sending and a modified `SendMessageParams` is passed onward.
- `456fbec9` and `edbc71a0`: add some exteraless-compatible Android callback,
  context, bulletin, account and plugin data/cache directory helpers, with
  runtime-owned callbacks and tests. These do not constitute a full engine port.

CI run **41538249771** built `387d3162`; its LinkiGram 2.7 APK was installed
successfully on the connected device with `adb install -r`, preserving app data.
An isolated test of the example Tiktokframe plugin's `.f` command showed that
its Python handler starts the frame job and cancels the command; the complete
Android flow has not been verified. The user also reported that the plugin
disappeared after restarting the app and reinstalled it. Later investigation of
an empty plugin list found the startup initialization issue described above;
distinguish missing files from disabled or quarantined plugins in other cases.

CI run **41713334571** succeeded at
`edbc71a0689af1005dd1dd94e9b99454d84a42c0`. This newer build includes the
additional SDK helpers. Its installation and runtime behavior have not been
verified. The previous installed APK's certificate SHA-256 was
`627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4`.

The current LinkiGram plugin implementation already derives from NimarkoGram.
The upstream NimarkoGram `extera_utils.classes` has the same limitation as the
current LinkiGram shim: it supports Java interfaces but not runtime subclassing
of ordinary Java classes. Simply copying it does not add that capability.
The exteraless implementation uses a dexmaker-backed class generator, a
different Python loader and SDK, hooks, Java services and UI integrations.
Their dependencies and behavior must be adapted and tested; copying individual
files without wiring them does not make plugins work. A universal claim about
all plugins requires evidence from an adequate plugin corpus and real devices.

At this handoff there are uncommitted plugin migration files: `JsonUtils.java`,
`utils/ClassProxyFactory.java`, the `elyx_runtime` Python package, the metadata
parser changes and capability scanner with its test. They are incomplete and
are not included in the committed CI builds above. The checked-out exteraless
source used for comparison was commit
`4771c553fee84618f8fa8dfc034c9e85317c2c72`; the local clone and the example
Tiktokframe plugin are included as handoff materials in the project archive.
The user's uncommitted blurred-background change and `promo-2.7/` project must
also be preserved.

For the latest committed SDK additions, 13 targeted host checks passed. Some
older source-inspection tests failed because their expected Java comments or
literal code shapes are absent in the pre-existing implementation; do not
describe those failures as successful checks. Host checks do not prove Android
runtime compatibility.

## Current handoff: Liner lyrics

The Liner fixes are on `main`. Implementation commits:

- `9ad6a922`: separate bounded Liner/public-provider workers; invalidate player
  callbacks on cancellation/dismissal; align UI and coordinator timeouts; treat
  provider errors/truncated SSE as incomplete results rather than complete cache
  entries; invalidate older disk caches; remove the obsolete `/v1/search` step.
- `1d5dbb09`: release the catalog visitor-cache monitor before network I/O so a
  cancelled request cannot block the next track's lookup.

Validation already completed:

- 24 offline checks in `Tools/test_liner.py`, compiling the production API,
  catalog, aggregation, cache and workers against platform/network fakes.
- Existing lyrics-parser checks in `tools/liner-lyrics/check.cjs`.
- CI run **40085356687** at source
  `1d5dbb09981f8160d0c8e9f821e8323d45c4ba75` passed Java/Kotlin compilation,
  ARM64 APK assembly and artifact upload.
- Artifact **linkigram-apk**, ID **12557427588**. APK ZIP integrity and
  apksigner cryptographic signature verification passed.
- Package `app.linkigram.messenger`, version `2.6.5beta`, code `202602089`,
  ARM64. APK SHA-256:
  `8c97fa17304e230ab84d4d592b7c84783adae5bc629b1d0e35988b4894e26d09`.
- Signing certificate SHA-256:
  `627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4`.

Historical next steps on the PC (the older APK below has since been superseded;
do not install it over the newer LinkiGram build):

1. Download the CI artifact above and install its verified APK through local ADB.
   Installation was requested but could not be performed from the cloud machine.
2. With the existing Liner login and online lyrics enabled, check Liner providers
   for `9mice, Егор Крид, тёмный принц, madk1d — Jealous`.
3. Switch tracks rapidly and inspect `LinkiLyrics` logs. Confirm searches do not
   stall behind old requests and previous-track callbacks do not replace new text.
4. Investigate actual HTTP/SSE/provider failures if Liner sources remain absent.
   `/v1/search` was confirmed to return 404. Official client request signing
   differs from this integration, but enforcement on the authenticated lyrics
   request has not been established. Do not assume it is the cause without logs.

Real-account provider recovery and on-device track switching remain unverified.
The offline harness does not exercise Android WebView parsing or encrypted token
storage. No release was published for these changes.

Offline checks with JDK 17 and an existing org.json JAR:

```sh
python3 Tools/test_liner.py --json-jar /path/to/org-json.jar
node tools/liner-lyrics/check.cjs
```

## Plugin agent attachments (2026-11-09)

- `PluginAgentActivity` now accepts attachments. An attach button in the input
  panel opens `ItemOptions` with “Photo” and “File”, both through
  `ACTION_GET_CONTENT` and `startActivityForResult`, so no runtime permission is
  requested.
- Images are decoded, downsampled to 1600 px and re-encoded as JPEG (raw pick is
  capped at 16 MB); they are sent as
  `Message(role, text, bytes, mime)` → `image_url` data URL. Files must be text
  (≤256 KB, UTF-8, NUL scan rejects binaries) and are pasted into the prompt as
  a fenced block; other binaries are rejected with a bulletin.
- The attachment chip sits above the input row and shows the file name plus a
  remove button. Sending with neither text nor attachment does nothing. Old
  pictures are dropped from `SESSION_HISTORY` beyond the newest two
  (`Message.clearImage`) so base64 payloads do not accumulate.
- New strings: `NM_PluginAgentAttach`, `...AttachPhoto`, `...AttachFile`,
  `...AttachRemove`, `...Attachment`, `...AttachTooBig`, `...AttachBinary`,
  `...AttachFailed`, `...PhotoPrompt`, `...FilePrompt`, in both locale files.
- Checks: `Tools/check_braces.py` `+0/+0`, every `R.string` reference resolves,
  both strings files parse with no duplicate names, javac reports no syntax
  errors (there is no Android SDK here, so this is not a compile). Not built or
  installed: a device pass for photo/file attach, vision-model answers and
  history replay is still required.
- Committed as `21a7fe7b`; CI run **42537791017** (ARM64) succeeded. Artifact
  `linkigram-apk` verified offline with androguard: package
  `app.linkigram.messenger`, versionName `2.9`, versionCode `202611089`, ZIP
  integrity OK, APK Signature Scheme v2 present, certificate SHA-256
  `627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4`; APK
  SHA-256
  `432be0dfad980c911268c0c32c778035be35dde40efba13ff12f4af124be9129`.
- Not installed: `adb devices` was empty and `lsusb` showed no phone. The
  attachment UI, provider-side image support and the gallery/file pickers remain
  unverified on a device.

## OpenCode Zen rejected a leftover key (2026-11-09)

- Symptom reported by the user: the OpenCode Zen preset answered “Invalid
  credential” / an invalid-key error. Verified against the live gateway: `GET
  https://opencode.ai/zen/v1/models` returns 200 without a key, but returns 401
  `{"error":{"type":"server_error","message":"Invalid credential"}}` when a
  `Bearer` token is sent. The keyless preset was inheriting the API key of the
  previously selected provider (custom `api.reformboss.com`), and
  `Service.isKeyless()` only reported keyless while the stored key was empty.
- `Service.isKeyless()` is now derived from the URL (`Provider.isKeylessUrl`)
  and a new `Service.sendsKey()` decides the `Authorization` header, so both
  `Client.listModels` and `Client.request` never attach a key to a keyless
  preset, even if old state still holds one. `AiSettingsActivity` clears the key
  when the provider URL actually changes, so a key no longer leaks across
  providers. `hasCredentials()` and the hidden key row keep using the same rule.
- The user's own `reformboss` key was verified working from the PC (`/v1/models`
  200, `/v1/chat/completions` 200 for `deepseek-v4.1-flash-1017`); it is the key
  present in the opencode app config (`REFORMBOSS_API_KEY` in
  `~/.config/opencode/service.json`). No key was read into the repository.
- Checks: `Tools/check_braces.py` `+0/+0` on the three changed Java files, javac
  reports no syntax errors. Committed as `4dfad06d`; CI run **42633870052**
  (ARM64) succeeded. Artifact `linkigram-apk` verified offline with androguard:
  package `app.linkigram.messenger`, versionName `2.9`, versionCode `202611089`,
  v2 signature and certificate SHA-256
  `627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4`; APK
  SHA-256
  `830dc994e33aa5232f1ee5e93b2924c160816497cfaa8ba787934b24a7d495b7`.
- Installed on `RF8MA188NFZ` with `adb install -r` (app data preserved). On the
  installed build the AI settings screen shows “OpenCode Zen”,
  `https://opencode.ai/zen/v1`, no “Ключ API” row, and the model picker lists
  the gateway's free models with the “Бесплатно” badge (`beam-free`,
  `big-pickle`, `crouton-alpha-free`, `deepseek-v4.1-flash-free`,
  `flint-40b-free`, `glm-5.3-flash-free`, `iceman-free`, `inkling-2-small-free`,
  …). That list can only be fetched when the request carries no `Authorization`
  header, so the invalid-credential case is fixed. A real chat answer on the
  free tier was not exercised (the PC's IP is already rate-limited).
- Note for the next session: the `gh` token became invalid in the middle of this
  work (`gh run download` → HTTP 401), so artifacts could not be fetched until
  the user ran `gh auth login` again. `git` operations kept working because the
  repository is public.

## Privacy

This file is public on GitHub. Include only project instructions and technical
handoffs. Never put credentials, private machine paths, device identifiers or
personal chat history here. Keep private notes in ignored `AGENTS.local.md`.
Do not rewrite Git history as part of this task.
