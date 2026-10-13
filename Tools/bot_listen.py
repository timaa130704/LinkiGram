"""Listens for the owner's direct messages to the release bot.

Runs until the owner sends a message, prints it and exits. The agent runs this
as a background command: its completion is the wake-up signal, after which the
message is answered and the listener is armed again. The last processed
update_id lives in .release-bot.offset so nothing is skipped while offline.
"""
import json
import os
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENV = os.path.join(ROOT, ".release-bot.env")
STATE = os.path.join(ROOT, ".release-bot.offset")
USER_ID = 8006327885

token = None
with open(ENV, encoding="utf-8") as fh:
    for line in fh:
        line = line.strip()
        if line.startswith("TELEGRAM_BOT_TOKEN="):
            token = line.split("=", 1)[1]
if not token:
    print("no token in .release-bot.env")
    sys.exit(1)


def call(method, params=None, timeout=90):
    data = json.dumps(params or {}).encode("utf-8")
    request = urllib.request.Request(
        "https://api.telegram.org/bot{0}/{1}".format(token, method),
        data=data,
        headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


# Polling and a webhook exclude each other; make sure polling works.
call("deleteWebhook")

offset = 0
if os.path.exists(STATE):
    try:
        offset = int(open(STATE).read().strip()) + 1
    except ValueError:
        offset = 0

messages = []
while not messages:
    try:
        updates = call("getUpdates", {
            "timeout": 30,
            "offset": offset,
            "allowed_updates": ["message"],
        })
    except Exception:
        time.sleep(5)
        continue
    for update in updates.get("result", []):
        offset = update["update_id"] + 1
        message = update.get("message")
        if not message:
            continue
        chat = (message.get("chat") or {}).get("id")
        if chat != USER_ID:
            continue
        name = (message.get("from") or {}).get("first_name") or "?"
        text = message.get("text") or "(без текста)"
        messages.append("{0}: {1}".format(name, text))

with open(STATE, "w") as fh:
    fh.write(str(offset - 1))

print("MESSAGES:\n" + "\n".join(messages), flush=True)
