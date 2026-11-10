/*
This file is part of LinkiGram Desktop,
a desktop client based on Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

#include <QtCore/QString>
#include <vector>

namespace LinkiGram {

enum class EntryKind {
	Header,
	Toggle,
	Button,
};

struct EntryData {
	QString key;
	QString title;
	EntryKind kind;
};

struct ScreenData {
	QString title;
	std::vector<EntryData> entries;
};

[[nodiscard]] const std::vector<ScreenData> &ScreensData();

} // namespace LinkiGram
