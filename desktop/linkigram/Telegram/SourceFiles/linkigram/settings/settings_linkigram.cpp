/*
This file is part of LinkiGram Desktop,
a desktop client based on Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "linkigram/settings/settings_linkigram.h"

#include "linkigram/linkigram_config.h"
#include "linkigram/settings/settings_data.h"

#include "settings/settings_common.h"
#include "settings/settings_common_session.h"
#include "core/click_handler_types.h"
#include "core/version.h"
#include "ui/vertical_list.h"
#include "ui/wrap/vertical_layout.h"
#include "ui/widgets/buttons.h"
#include "ui/rp_widget.h"
#include "ui/qt_object_factory.h"
#include "window/window_session_controller.h"
#include "styles/style_settings.h"
#include "styles/style_menu_icons.h"

#include <QtCore/QFile>
#include <QtWidgets/QFileDialog>

namespace Settings {
namespace {

using namespace LinkiGram;

const auto kSourceUrl = u"https://github.com/timaa130704/LinkiGram"_q;

[[nodiscard]] int ScreensCount() {
	return int(ScreensData().size());
}

class LinkiGramScreen;

[[nodiscard]] Type LinkiGramScreenIdImpl(int index);

class ScreenFactory final : public AbstractSectionFactory {
public:
	explicit ScreenFactory(int index) : _index(index) {
	}

	object_ptr<AbstractSection> create(
		not_null<QWidget*> parent,
		not_null<Window::SessionController*> controller,
		not_null<Ui::ScrollArea*> scroll,
		rpl::producer<Container> containerValue) const final override;

private:
	const int _index;

};

class LinkiGramScreen final : public AbstractSection {
public:
	LinkiGramScreen(
		QWidget *parent,
		not_null<Window::SessionController*> controller,
		int index);

	[[nodiscard]] Type id() const final override;
	[[nodiscard]] rpl::producer<QString> title() final override;

private:
	void setupContent();

	const int _index;

};

object_ptr<AbstractSection> ScreenFactory::create(
		not_null<QWidget*> parent,
		not_null<Window::SessionController*> controller,
		not_null<Ui::ScrollArea*> scroll,
		rpl::producer<Container> containerValue) const {
	return object_ptr<LinkiGramScreen>(parent, controller, _index);
}

Type LinkiGramScreenIdImpl(int index) {
	static auto cache = std::vector<Type>();
	if (cache.empty()) {
		const auto count = ScreensCount();
		cache.reserve(count);
		for (auto i = 0; i != count; ++i) {
			cache.push_back(std::make_shared<ScreenFactory>(i));
		}
	}
	return cache[index];
}

LinkiGramScreen::LinkiGramScreen(
	QWidget *parent,
	not_null<Window::SessionController*> controller,
	int index)
: AbstractSection(parent, controller)
, _index(index) {
	setupContent();
}

Type LinkiGramScreen::id() const {
	return LinkiGramScreenIdImpl(_index);
}

rpl::producer<QString> LinkiGramScreen::title() {
	return rpl::single(ScreensData()[_index].title);
}

void LinkiGramScreen::setupContent() {
	const auto content = Ui::CreateChild<Ui::VerticalLayout>(this);
	const auto config = &Config::Instance();
	const auto &screen = ScreensData()[_index];

	auto first = true;
	for (const auto &entry : screen.entries) {
		if (entry.kind == EntryKind::Header) {
			Ui::AddSkip(content);
			Ui::AddSubsectionTitle(content, rpl::single(entry.title));
			first = false;
			continue;
		}
		if (first) {
			Ui::AddSkip(content);
			first = false;
		}
		const auto key = screen.title + u"/"_q + entry.key;
		const auto button = AddButtonWithIcon(
			content,
			rpl::single(entry.title),
			st::settingsButtonNoIcon);
		if (entry.kind == EntryKind::Toggle) {
			button->toggleOn(rpl::single(config->getBool(key)));
			button->toggledValue(
			) | rpl::on_next([config, key](bool value) {
				config->setBool(key, value);
			}, button->lifetime());
		}
	}
	Ui::AddSkip(content);

	Ui::ResizeFitChild(this, content);
}

class LinkiGramHub final : public Section<LinkiGramHub> {
public:
	LinkiGramHub(
		QWidget *parent,
		not_null<Window::SessionController*> controller);

	[[nodiscard]] rpl::producer<QString> title() override;

private:
	void setupContent();

};

LinkiGramHub::LinkiGramHub(
	QWidget *parent,
	not_null<Window::SessionController*> controller)
: Section(parent, controller) {
	setupContent();
}

rpl::producer<QString> LinkiGramHub::title() {
	return rpl::single(u"LinkiGram"_q);
}

void LinkiGramHub::setupContent() {
	const auto content = Ui::CreateChild<Ui::VerticalLayout>(this);

	Ui::AddSkip(content);
	Ui::AddSubsectionTitle(content, rpl::single(u"About"_q));
	const auto version = u"LinkiGram Desktop "_q
		+ QString::fromLatin1(AppVersionStr)
		+ u" (Telegram Desktop)"_q;
	const auto about = AddButtonWithIcon(
		content,
		rpl::single(version),
		st::settingsButton,
		{ &st::menuIconExperimental });
	about->setClickedCallback([=] {
		HiddenUrlClickHandler::Open(kSourceUrl);
	});

	Ui::AddSkip(content);
	Ui::AddSubsectionTitle(content, rpl::single(u"Settings"_q));

	const auto &screens = ScreensData();
	for (auto i = 1, count = ScreensCount(); i != count; ++i) {
		const auto button = AddButtonWithIcon(
			content,
			rpl::single(screens[i].title),
			st::settingsButtonNoIcon);
		button->setClickedCallback([=] {
			showOther(LinkiGramScreenIdImpl(i));
		});
	}

	Ui::AddSkip(content);
	Ui::AddSubsectionTitle(content, rpl::single(u"Configuration"_q));

	const auto exportButton = AddButtonWithIcon(
		content,
		rpl::single(u"Export settings"_q),
		st::settingsButtonNoIcon);
	exportButton->setClickedCallback([=] {
		const auto path = QFileDialog::getSaveFileName(
			exportButton,
			u"Export LinkiGram settings"_q,
			QString(),
			u"JSON (*.json)"_q);
		if (!path.isEmpty()) {
			QFile::copy(Config::path(), path);
		}
	});

	const auto importButton = AddButtonWithIcon(
		content,
		rpl::single(u"Import settings"_q),
		st::settingsButtonNoIcon);
	importButton->setClickedCallback([=] {
		const auto path = QFileDialog::getOpenFileName(
			importButton,
			u"Import LinkiGram settings"_q,
			QString(),
			u"JSON (*.json)"_q);
		if (!path.isEmpty()) {
			Config::Instance().import(path);
		}
	});

	Ui::ResizeFitChild(this, content);
}

} // namespace

Type LinkiGramId() {
	return LinkiGramHub::Id();
}

Type LinkiGramScreenId(int index) {
	return LinkiGramScreenIdImpl(index);
}

} // namespace Settings
