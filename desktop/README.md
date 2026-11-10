# LinkiGram Desktop for Windows

The Windows client is based on the open-source [Telegram Desktop](https://github.com/telegramdesktop/tdesktop) codebase. Reusing Telegram Desktop provides the mature Qt UI, MTProto implementation, media pipeline, calls, notifications and update infrastructure that the Android project already relies on.

The desktop target deliberately does **not** include LinkiGram's Android plugin runtime (Chaquopy/Python/Pine/DEX). Android-specific activities, services and JNI integrations remain Android-only. LinkiGram desktop features should be ported as native Qt/C++ modules under the Telegram Desktop tree.

## Source layout and overlay

The Telegram Desktop base is never vendored. It is cloned into the
git-ignored `desktop/tdesktop/` by developers and CI, and the LinkiGram code
is applied on top of it:

- `desktop/linkigram/` — the tracked LinkiGram desktop sources, laid out under
  the same `Telegram/SourceFiles/...` paths they take inside the base:
  `linkigram/linkigram_config.{h,cpp}` (JSON settings store at
  `tdata/linkigram.json`), `linkigram/features.{h,cpp}` (the native feature
  facade that upstream hooks call), `linkigram/settings/settings_data.{h,cpp}`
  (the full settings inventory: all 28 non-Android screens / 407 records parsed
  from `DESKTOP_FEATURES.md`; each record is tagged `Header`, `Toggle` or
  `Button` from the Android preference sources) and
  `linkigram/settings/settings_linkigram.{h,cpp}` (the LinkiGram hub reached
  from the main Settings page plus one generic screen per Android settings
  screen that renders section titles and persisted toggle/action rows).
- `desktop/patches/` — the edits LinkiGram makes to upstream files
  (`Telegram/CMakeLists.txt` source list, `settings/sections/settings_main.cpp`
  section button, `core/version.h` branding, `api/api_send_progress.cpp`
  outgoing typing suppression, `history/view/history_view_send_action.cpp`
  incoming typing suppression, `data/data_histories.cpp` read-receipt
  suppression, `data/data_stories.cpp` story-view suppression).
- `desktop/apply-linkigram.sh` — copies the overlay onto a checkout and applies
  the patches idempotently:

  ```sh
  desktop/apply-linkigram.sh [path-to-tdesktop]
  ```

  With no argument it uses `desktop/tdesktop`. The CI workflow should run this
  after checking out Telegram Desktop and before configuring the build.

## Build

Windows builds run in GitHub Actions using `desktop-windows.yml`. The workflow is manual initially and accepts a Telegram Desktop revision, making upgrades reproducible. Required Telegram API credentials are read from repository secrets and are never committed.

## Porting order

1. Branding, theme and appearance preferences.
2. Chat list, folders, filtering and profile customisation.
3. Media tools, stories and clipboard workflows.
4. Privacy, translation and network settings.
5. Calls and platform integrations.

Each feature must have a native desktop implementation and tests; Android plugin APIs are not part of the desktop contract.

## Feature inventory

The complete LinkiGram feature list, with names, types and where every feature
lives today, is in [`../DESKTOP_FEATURES.md`](../DESKTOP_FEATURES.md).
