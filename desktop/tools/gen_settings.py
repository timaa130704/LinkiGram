#!/usr/bin/env python3
"""Regenerate desktop/linkigram/.../settings_data.cpp from DESKTOP_FEATURES.md.

The settings reference in DESKTOP_FEATURES.md lists every Android settings
screen and record. This script parses the 28 non-Android screens (all screens
except PluginsActivity, which is Android-only) and emits the C++ data table the
desktop settings hub renders. Run it after editing the inventory:

    python3 desktop/tools/gen_settings.py

Each record also gets a kind (Header, Toggle or Button) so the desktop screen
can render section titles and action rows like the Android one. The kind is
recovered from the Android preference sources (the `UItem.asHeader`,
`SettingsHelper.asSwitchCG`, `UItem.asButton` factories); records the sources do
not classify default to Toggle. The screen's own title key (from `getTitle()`)
is dropped because the desktop section already shows it.
"""

import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
SRC = os.path.join(REPO, "DESKTOP_FEATURES.md")
JAVA = os.path.join(REPO, "TMessagesProj", "src", "main", "java")
DEST = os.path.join(
    REPO,
    "desktop",
    "linkigram",
    "Telegram",
    "SourceFiles",
    "linkigram",
    "settings",
    "settings_data.cpp",
)

# Activity class -> display title (desktop hub entry).
TITLES = {
    "MainPreferencesActivity": "LinkiGram",
    "GeneralPreferencesActivity": "General",
    "AppearancePreferencesActivity": "Appearance",
    "ChatsPreferencesActivity": "Chats",
    "CameraPreferencesActivity": "Camera",
    "PrivacyPreferencesActivity": "Privacy",
    "AyuMomentsPreferencesActivity": "AyuMoments (ghost)",
    "MessagesPreferencesActivity": "Messages",
    "RecentEmojisStickersPreferencesActivity": "Recent emojis/stickers",
    "FoldersPreferencesActivity": "Folders",
    "BottomTabsPreferencesActivity": "Bottom tabs",
    "NimarkoMediaPreferencesActivity": "Media downloader",
    "BannerPreferencesActivity": "Banners",
    "DebugPreferencesActivity": "Debug",
    "ExperimentalPreferencesActivity": "Experimental",
    "NimarkoTextAnimPreferencesActivity": "Text animation",
    "BackgroundPreferencesActivity": "Background",
    "MessageMenuItemsPreferencesActivity": "Message menu items",
    "DrawerPreferencesActivity": "Side menu",
    "ScreenAppearanceActivity": "Side menu appearance",
    "AiSettingsActivity": "AI Assistant",
    "InfoCardsPreferencesActivity": "Quick info",
    "WsBypassPreferencesActivity": "Bypass",
    "MessageMenuPreferencesActivity": "Message menu",
    "MessageFiltersPreferencesActivity": "Message filtering",
    "MessageMenuOrderPreferencesActivity": "Reorder menu items",
    "MessagesAndProfilesPreferencesActivity": "Messages and profiles",
    "IconPackSelectorActivity": "Icon pack",
}

CALL_RE = re.compile(
    r"\b(?:UItem\.|SettingsHelper\.)?"
    r"(asHeader|asAnimatedHeader|asLargeHeader|asBlackHeader|"
    r"asSwitchCG|asSwitch|asCheck|asRippleCheck|"
    r"asButtonCheck|asButton|asTextDetail|asRadio2|asRadio|"
    r"asStickerButton|asButtonWithSubtext)\s*\("
)
KEY_RE = re.compile(r"R\.string\.([A-Za-z0-9_]+)")
TITLE_RE = re.compile(
    r"String\s+getTitle\s*\(\s*\)\s*\{.*?R\.string\.([A-Za-z0-9_]+)", re.S
)
FILE_RE = re.compile(r"Текущий файл:\s*`([^`]+)`")
BULLET_RE = re.compile(r"^-\s+(.*?)\s*\(`R\.string\.([A-Za-z0-9_]+)`\)\s*$")

HEADER_TYPES = {"asHeader", "asAnimatedHeader", "asLargeHeader", "asBlackHeader"}
TOGGLE_TYPES = {"asSwitchCG", "asSwitch", "asCheck", "asRippleCheck"}
BUTTON_TYPES = {
    "asButton",
    "asTextDetail",
    "asRadio",
    "asRadio2",
    "asButtonCheck",
    "asStickerButton",
    "asButtonWithSubtext",
}

# Explicit fixes for records the Android factories do not classify.
OVERRIDES = {
    "NM_CAM_Improvements": "Header",
}

KINDS = ("Header", "Toggle", "Button")


def cpp(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def classify(call):
    if call in HEADER_TYPES:
        return "Header"
    if call in TOGGLE_TYPES:
        return "Toggle"
    if call in BUTTON_TYPES:
        return "Button"
    return None


def scan_source(text):
    """Map R.string key -> kind inside one Java translation unit."""
    found = {}
    for match in CALL_RE.finditer(text):
        call = match.group(1)
        kind = classify(call)
        if kind is None:
            continue
        depth = 0
        i = match.end() - 1
        j = i
        while j < len(text):
            if text[j] == "(":
                depth += 1
            elif text[j] == ")":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        km = KEY_RE.search(text[i:j + 1])
        if km:
            found.setdefault(km.group(1), kind)
    return found


def scan_tree():
    kinds = {}
    for dirpath, _dirs, files in os.walk(JAVA):
        for name in files:
            if not name.endswith(".java"):
                continue
            try:
                text = open(
                    os.path.join(dirpath, name),
                    encoding="utf-8",
                    errors="replace",
                ).read()
            except OSError:
                continue
            for key, kind in scan_source(text).items():
                kinds.setdefault(key, kind)
    return kinds


def read_screen_file(path):
    try:
        text = open(path, encoding="utf-8", errors="replace").read()
    except OSError:
        return {}, None
    tm = TITLE_RE.search(text)
    return scan_source(text), (tm.group(1) if tm else None)


def parse_inventory():
    screens = []
    current = None
    for line in open(SRC, encoding="utf-8").read().splitlines():
        if line.startswith("# 7."):
            break
        header = re.match(r"^##\s+([A-Za-z0-9_]+)\s*$", line)
        if header:
            name = header.group(1)
            if name in TITLES:
                current = {
                    "title": TITLES[name],
                    "file": None,
                    "entries": [],
                }
                screens.append(current)
            else:
                current = None
            continue
        if current is None:
            continue
        fm = FILE_RE.search(line)
        if fm and current["file"] is None:
            current["file"] = fm.group(1)
            continue
        bm = BULLET_RE.match(line.strip())
        if bm:
            title = bm.group(1).replace("\\\\'", "'").replace("\\'", "'")
            current["entries"].append((bm.group(2), title))
    return screens


def main():
    global_kinds = scan_tree()

    out = io.StringIO()
    out.write(
        """/*
This file is part of LinkiGram Desktop,
a desktop client based on Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "linkigram/settings/settings_data.h"

#include "base/basic_types.h"

namespace LinkiGram {

const std::vector<ScreenData> &ScreensData() {
\tstatic const std::vector<ScreenData> result = {
"""
    )

    total = 0
    screens = parse_inventory()
    for screen in screens:
        local, title_key = ({}, None)
        if screen["file"]:
            local, title_key = read_screen_file(os.path.join(JAVA, screen["file"]))
        records = []
        for key, etitle in screen["entries"]:
            if title_key and key == title_key:
                continue
            kind = local.get(key) or global_kinds.get(key) or OVERRIDES.get(key)
            if kind is None:
                kind = "Header" if key.endswith("Header") else "Toggle"
            records.append((key, etitle, kind))
        if not records and title_key:
            for key, etitle in screen["entries"]:
                if key == title_key:
                    records.append((key, etitle, "Header"))
                    break
        out.write("\t\t{ u%s_q, {\n" % cpp(screen["title"]))
        for key, etitle, kind in records:
            total += 1
            out.write(
                "\t\t\t{ u%s_q, u%s_q, EntryKind::%s },\n"
                % (cpp(key), cpp(etitle), kind)
            )
        out.write("\t\t} },\n")
    out.write(
        """\t};
\treturn result;
}

} // namespace LinkiGram
"""
    )

    with open(DEST, "w", encoding="utf-8") as f:
        f.write(out.getvalue())
    sys.stderr.write(
        "screens=%d entries=%d -> %s\n" % (len(screens), total, DEST)
    )


if __name__ == "__main__":
    main()
