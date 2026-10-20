# Chat automation

Local repeats are chosen in the normal schedule picker, under Repeat → Local repeat · minutes/hours.
The selected date/time is the first send. The negative period is an internal picker sentinel;
SendMessagesHelper captures it and never includes it in Telegram RPCs. Existing server repeats
keep their normal Premium checks. Local repeats support new text messages, including text
entities and reply/topic context. Media, forwards, paid messages, secret chats and editing a
server scheduled message are rejected. Jobs survive restarts/reboots in dedicated preferences,
are bound to Telegram user ID and backend (not account slot), and can be cancelled in the chat
menu. Exact alarms are used when Android permits them; the interval dialog offers the Android
permission screen when needed. Doze/network restrictions can delay delivery. Missed intervals
are skipped rather than replayed in a burst. Jobs from signed-out accounts are removed when due.

The chat assistant is enabled explicitly in the chat menu. It uses the shared exteraless AI
service configuration, with a separate prompt and bounded transient conversation per chat.
Only incoming recent text from non-bot users is considered. Groups default to mentions only.
Message IDs are persisted before generation to prevent retries/duplicates; there is at most one
request per chat and a 30-second cooldown. Disabling clears context and cancels generation.
Callbacks recheck identity/backend and enabled state before sending. No shared manual-assistant
history is transmitted. Text is sent to the chosen provider, described in the settings screen.

Edit history is persisted by the existing AyuGram database hook before overwriting a message.
The sender-id gate is removed (channel edits may lack it), and null text is compared safely.
The new message-menu action reads that existing database on the global worker queue and shows
saved versions plus current text. It cannot recover changes not received while saving was on.
