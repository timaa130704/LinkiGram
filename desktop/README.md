# LinkiGram Desktop for Windows

The Windows client is based on the open-source [Telegram Desktop](https://github.com/telegramdesktop/tdesktop) codebase. Reusing Telegram Desktop provides the mature Qt UI, MTProto implementation, media pipeline, calls, notifications and update infrastructure that the Android project already relies on.

The desktop target deliberately does **not** include LinkiGram's Android plugin runtime (Chaquopy/Python/Pine/DEX). Android-specific activities, services and JNI integrations remain Android-only. LinkiGram desktop features should be ported as native Qt/C++ modules under the Telegram Desktop tree.

## Build

Windows builds run in GitHub Actions using `desktop-windows.yml`. The workflow is manual initially and accepts a Telegram Desktop revision, making upgrades reproducible. Required Telegram API credentials are read from repository secrets and are never committed.

## Porting order

1. Branding, theme and appearance preferences.
2. Chat list, folders, filtering and profile customisation.
3. Media tools, stories and clipboard workflows.
4. Privacy, translation and network settings.
5. Calls and platform integrations.

Each feature must have a native desktop implementation and tests; Android plugin APIs are not part of the desktop contract.
