/*
This file is part of LinkiGram Desktop,
a desktop client based on Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace LinkiGram {

[[nodiscard]] bool GhostEnabled();
[[nodiscard]] bool HideIncomingTyping();
[[nodiscard]] bool HideOutgoingTyping();
[[nodiscard]] bool HideOutgoingRecording();
[[nodiscard]] bool HideReadReceipts();
[[nodiscard]] bool HideStoryViews();

} // namespace LinkiGram
