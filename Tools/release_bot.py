#!/usr/bin/env python3
"""Posts a LinkiGram release into the announcement channel.

Reads the newest GitHub release, formats it as

    LinkiGram 2.5.1
    <blockquote expandable>the changelog</blockquote>

and sends it to the configured channel. The changelog can also be supplied
directly so a draft can be posted exactly as written, before it goes into the
release notes.

Usage
-----
    python release_bot.py --token TOKEN --channel @linkireleases --dry-run
    python release_bot.py --token TOKEN --channel @linkireleases
    python release_bot.py --token TOKEN --channel @linkireleases \
        --version 2.5.1 --notes notes.md
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request

TELEGRAM_API = "https://api.telegram.org/bot{token}/{method}"
USER_AGENT = "LinkiGram-release-bot/1.0"


class TelegramError(RuntimeError):
    pass


def call_api(token, method, payload):
    url = TELEGRAM_API.format(token=token, method=method)
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url, data=body, headers={"Content-Type": "application/json",
                                 "User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")
        raise TelegramError("{0} failed: HTTP {1} {2}".format(
            method, error.code, detail))
    except urllib.error.URLError as error:
        raise TelegramError("{0} failed: {1}".format(method, error.reason))
    if not data.get("ok"):
        raise TelegramError("{0} failed: {1}".format(method,
                                                      data.get("description")))
    return data["result"]


def escape(text):
    """Telegram HTML parse mode treats these as markup, so neutralise them."""
    return (text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;"))


def format_message(version, notes, url=None):
    parts = ["<b>LinkiGram {0}</b>".format(escape(version))]
    if url:
        parts.append('<a href="{0}">Скачать</a>'.format(url))
    if notes:
        # The changelog is the payload, so it goes in a collapsed quote instead of
        # pushing the download link off the first screen.
        parts.append("<blockquote expandable>{0}</blockquote>".format(escape(notes)))
    return "\n".join(parts)


def read_release(repo, token):
    """Fetches the newest release from GitHub, or None when there is none."""
    request = urllib.request.Request(
        "https://api.github.com/repos/{0}/releases/latest".format(repo),
        headers={"Accept": "application/vnd.github+json",
                 "User-Agent": USER_AGENT})
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            release = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise TelegramError("GitHub release lookup failed: HTTP {0} {1}".format(
            error.code, error))
    return {
        "version": release.get("tag_name") or release.get("name") or "",
        "notes": release.get("body") or "",
        "url": release.get("html_url") or "",
    }


def send_preview(token, channel, text):
    """Asks the bot to echo the message back to us, so formatting can be checked
    without posting into the channel."""
    me = call_api(token, "getMe", {})
    chat = call_api(token, "getChat", {"chat_id": channel})
    return {"bot": me.get("username"), "chat": chat.get("title"),
            "chat_type": chat.get("type")}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", default=os.environ.get("TELEGRAM_BOT_TOKEN"))
    parser.add_argument("--channel", default=os.environ.get("TELEGRAM_CHANNEL"))
    parser.add_argument("--repo", default="timaa130704/LinkiGram")
    parser.add_argument("--github-token", default=os.environ.get("GITHUB_TOKEN"))
    parser.add_argument("--version")
    parser.add_argument("--notes")
    parser.add_argument("--notes-file")
    parser.add_argument("--dry-run", action="store_true",
                        help="print the message and exit without sending")
    parser.add_argument("--check", action="store_true",
                        help="verify the token and channel access, then exit")
    args = parser.parse_args()

    version = args.version
    notes = args.notes or ""
    url = ""
    if not version or not notes:
        release = read_release(args.repo, args.github_token)
        version = version or release["version"]
        notes = notes or release["notes"]
        url = release["url"]

    if args.notes_file:
        with open(args.notes_file, "r", encoding="utf-8") as handle:
            notes = handle.read()

    if not version:
        print("no version to announce", file=sys.stderr)
        return 1

    text = format_message(version.strip(), notes.strip(), url or None)

    if args.dry_run:
        print("--- would send to {0} ---".format(args.channel or "<channel>"))
        print(text)
        return 0

    if not args.token:
        print("no bot token: pass --token or set TELEGRAM_BOT_TOKEN", file=sys.stderr)
        return 1
    if not args.channel:
        print("no channel: pass --channel or set TELEGRAM_CHANNEL", file=sys.stderr)
        return 1

    if args.check:
        info = send_preview(args.token, args.channel, text)
        print("bot: @{0}".format(info["bot"]))
        print("channel: {0} ({1})".format(info["chat"], info["chat_type"]))
        return 0

    call_api(args.token, "sendMessage", {
        "chat_id": args.channel,
        "text": text,
        "parse_mode": "HTML",
        "link_preview_options": {"is_disabled": True},
        "disable_web_page_preview": True,
    })
    print("posted {0} to {1}".format(version, args.channel))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except TelegramError as error:
        print("error: {0}".format(error), file=sys.stderr)
        sys.exit(2)