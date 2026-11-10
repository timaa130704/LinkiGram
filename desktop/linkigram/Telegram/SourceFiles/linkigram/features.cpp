/*
This file is part of LinkiGram Desktop,
a desktop client based on Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "linkigram/features.h"

#include "base/basic_types.h"
#include "linkigram/linkigram_config.h"

namespace LinkiGram {
namespace {

const auto kGhostMaster = u"AyuMoments (ghost)/NM_GhostMaster"_q;
const auto kGhostTyping = u"AyuMoments (ghost)/NM_GhostTyping"_q;
const auto kGhostRecording = u"AyuMoments (ghost)/NM_GhostRecording"_q;
const auto kGhostReadReceipts = u"AyuMoments (ghost)/NM_GhostReadReceipts"_q;
const auto kGhostStories = u"AyuMoments (ghost)/NM_GhostStories"_q;
const auto kGhostOnline = u"AyuMoments (ghost)/NM_GhostOnline"_q;
const auto kHideIncomingTyping = u"General/NM_HideIncomingTyping"_q;
const auto kHideOwnPhone = u"General/LinkiHidePhone"_q;

[[nodiscard]] bool GhostOption(const QString &key) {
	return Config::Instance().getBool(kGhostMaster)
		&& Config::Instance().getBool(key);
}

} // namespace

bool GhostEnabled() {
	return Config::Instance().getBool(kGhostMaster);
}

bool HideIncomingTyping() {
	return Config::Instance().getBool(kHideIncomingTyping);
}

bool HideOutgoingTyping() {
	return GhostOption(kGhostTyping);
}

bool HideOutgoingRecording() {
	return GhostOption(kGhostRecording);
}

bool HideReadReceipts() {
	return GhostOption(kGhostReadReceipts);
}

bool HideStoryViews() {
	return GhostOption(kGhostStories);
}

bool HideOnline() {
	return GhostOption(kGhostOnline);
}

bool HideOwnPhone() {
	return Config::Instance().getBool(kHideOwnPhone);
}

} // namespace LinkiGram
