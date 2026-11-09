/*
This file is part of LinkiGram Desktop,
a desktop client based on Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "linkigram/linkigram_config.h"

#include "settings.h"

#include <QtCore/QFile>
#include <QtCore/QJsonDocument>

namespace LinkiGram {
namespace {

constexpr auto kFileName = "tdata/linkigram.json";

} // namespace

Config &Config::Instance() {
	static Config instance;
	return instance;
}

Config::Config() {
	load();
}

QString Config::filePath() {
	return cWorkingDir() + kFileName;
}

QString Config::path() {
	return filePath();
}

void Config::import(const QString &fromFile) {
	auto file = QFile(fromFile);
	if (!file.open(QIODevice::ReadOnly)) {
		return;
	}
	const auto document = QJsonDocument::fromJson(file.readAll());
	if (document.isObject()) {
		_values = document.object();
		save();
	}
}

void Config::load() {
	auto file = QFile(filePath());
	if (!file.open(QIODevice::ReadOnly)) {
		return;
	}
	const auto document = QJsonDocument::fromJson(file.readAll());
	if (document.isObject()) {
		_values = document.object();
	}
}

void Config::save() const {
	auto file = QFile(filePath());
	if (!file.open(QIODevice::WriteOnly | QIODevice::Truncate)) {
		return;
	}
	file.write(QJsonDocument(_values).toJson());
}

bool Config::getBool(const QString &key, bool fallback) const {
	const auto value = _values.value(key);
	return value.isBool() ? value.toBool() : fallback;
}

void Config::setBool(const QString &key, bool value) {
	if (getBool(key) == value && _values.contains(key)) {
		return;
	}
	_values.insert(key, value);
	save();
}

int Config::getInt(const QString &key, int fallback) const {
	const auto value = _values.value(key);
	return value.isDouble() ? value.toInt() : fallback;
}

void Config::setInt(const QString &key, int value) {
	if (getInt(key) == value && _values.contains(key)) {
		return;
	}
	_values.insert(key, value);
	save();
}

QString Config::getString(const QString &key, const QString &fallback) const {
	const auto value = _values.value(key);
	return value.isString() ? value.toString() : fallback;
}

void Config::setString(const QString &key, const QString &value) {
	if (getString(key) == value && _values.contains(key)) {
		return;
	}
	_values.insert(key, value);
	save();
}

} // namespace LinkiGram
