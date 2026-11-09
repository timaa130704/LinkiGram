#!/usr/bin/env python3
"""Regenerate desktop/linkigram/.../settings_data.cpp from DESKTOP_FEATURES.md.

The settings reference in DESKTOP_FEATURES.md lists every Android settings
screen and record. This script parses the 28 non-Android screens (all screens
except PluginsActivity, which is Android-only) and emits the C++ data table the
desktop settings hub renders. Run it after editing the inventory:

    python3 desktop/tools/gen_settings.py
"""

import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
SRC = os.path.join(REPO, "DESKTOP_FEATURES.md")
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


def cpp(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def main():
    lines = open(SRC, encoding="utf-8").read().splitlines()
    screens = []  # list of (title, [(key, title), ...])
    cur = None
    bullet = re.compile(
        r"^-\s+(.*?)\s*\(`R\.string\.([A-Za-z0-9_]+)`\)\s*$"
    )
    for ln in lines:
        if ln.startswith("# 7."):
            break
        m = re.match(r"^##\s+([A-Za-z0-9_]+)\s*$", ln)
        if m:
            name = m.group(1)
            if name in TITLES:
                cur = (TITLES[name], [])
                screens.append(cur)
            else:
                cur = None
            continue
        if cur is not None:
            bm = bullet.match(ln.strip())
            if bm:
                title, key = bm.group(1), bm.group(2)
                title = title.replace("\\\\'", "'").replace("\\'", "'")
                cur[1].append((key, title))

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
    for title, entries in screens:
        out.write("\t\t{ u%s_q, {\n" % cpp(title))
        for key, etitle in entries:
            total += 1
            out.write("\t\t\t{ u%s_q, u%s_q },\n" % (cpp(key), cpp(etitle)))
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
