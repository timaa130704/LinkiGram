# Telegram 13.1.1 integration

Status: integration candidate; runtime validation is pending.

## Source revisions

- LinkiGram starting point: `499338f775d9f0dc976e724c7148b5fc3a72026b`.
- Upstream baseline for the squashed import: `f2908b14133bbffbf7ab04f641ecb5bfaf533242` (12.10.6).
- Target: `3de37c8cfb039452be7398909461da6288babf15` (13.1.1, build 7237).
- Current release recovery point: `v2.7.0-pre.3`.

The fork has a squashed upstream import, so ordinary unrelated-history merging
does not identify the correct baseline. A non-mutating three-way preview using
the baseline above produces 266 changed paths and conflicts in protocol,
controllers, UI, resources, build configuration and two submodules.
The preview conflicts have been resolved into an integration candidate. The
custom InstantCameraView implementation is retained for its CameraX and round
video controls; its compatibility still requires CI and device validation.

Validation of the candidate: Java syntax parsing passed for 140 changed files;
modified XML parses; 28 Liner checks, lyrics parser checks and 45 appearance
checks passed. The legacy AI autoreply harness references a deleted controller
and cannot validate the current ChatAssistant implementation.

## Integration work

- [ ] Review TLRPC, bot/wallet schemas and generated protocol tests together.
- [ ] Preserve LinkiGram hooks in MessagesController, ConnectionsManager,
  ChatActivity, ProfileActivity, SettingsActivity and PhotoViewer.
- [ ] Resolve wallet add/add conflicts against the fork's existing wallet.
- [ ] Reconcile tgcalls and media module revisions and native CMake sources.
- [ ] Preserve application IDs, signing configuration, Python plugin keep rules
  and the ARM64 CI workflow while reconciling Gradle configuration.
- [ ] Validate Java symbols, XML resources and localization generator inputs.
- [ ] Run offline lyrics, parser, AI and appearance checks.
- [ ] Complete GitHub Actions Java/Kotlin compilation and APK assembly.
- [ ] Verify APK identity and signature; install with data preserved.

## Device regression record

All entries below are pending for the upgraded base. Compilation is not proof
of runtime behavior. Record tested scenarios and failures before claiming a pass.

- [ ] Existing login, account switching and restart with retained data.
- [ ] Private chats, groups, channels, forums and individual topics.
- [ ] Text, reply, quote, edit, delete, saved deletion and edit history.
- [ ] Photos, video, files, voice, round video, stickers and playback.
- [ ] Scheduled messages and local repeat isolation between two topics.
- [ ] Local repeat cancellation, restart recovery and scheduled-list display.
- [ ] Chat search, animated cursor and mark-all-read with folder isolation.
- [ ] Chat center, mentions, AI configuration and summary success/failure.
- [ ] Chat statistics for ordinary groups and individual topics.
- [ ] Liner login, provider selection and rapid track changes.
- [ ] Material You and ColorOS: navigation, drawers, settings, message menus.
- [ ] Bottom-tab organizer, hidden navigation and access to settings.
- [ ] Calls, notifications, background delivery and media permissions.
- [ ] Privacy settings, one-time media archive and protected screenshots.
- [ ] Python plugin load/hooks and proxy modes already present in the client.

Use private ignored notes for account, device and test-chat details. Do not put
credentials, message content or device identifiers in this public record.
