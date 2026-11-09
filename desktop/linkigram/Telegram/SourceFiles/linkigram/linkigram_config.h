/*
This file is part of LinkiGram Desktop,
a desktop client based on Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

#include <QtCore/QJsonObject>
#include <QtCore/QString>

namespace LinkiGram {

class Config final {
public:
	static Config &Instance();

	[[nodiscard]] bool getBool(const QString &key, bool fallback = false) const;
	void setBool(const QString &key, bool value);

	[[nodiscard]] int getInt(const QString &key, int fallback = 0) const;
	void setInt(const QString &key, int value);

	[[nodiscard]] QString getString(
		const QString &key,
		const QString &fallback = QString()) const;
	void setString(const QString &key, const QString &value);

	void save() const;

	[[nodiscard]] static QString path();

	void import(const QString &fromFile);

private:
	Config();

	[[nodiscard]] static QString filePath();
	void load();

	QJsonObject _values;

};

} // namespace LinkiGram
