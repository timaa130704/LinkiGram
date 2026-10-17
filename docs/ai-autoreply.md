# AI auto-replies (2.6 development)

Configure Settings > General > AI auto-reply. Enter a provider label, HTTPS API base URL, key and model ID, or load `/models`. Base URLs may end in `/v1`, `/models` or `/chat/completions`; they are normalized. Redirects are rejected to protect the key.

Enable each conversation from its overflow menu after reading the data-sharing notice. Supported conversations are ordinary private chats and non-forum groups. Secret chats, bots, Saved Messages, broadcast channels, forum topics and channel direct-message inboxes are excluded. Nothing is enabled by default.

Only accepted live incoming text/captions newer than enable time and less than 60 seconds old qualify. History, difference replay, outgoing, service and bot messages are ignored. Batched updates select the newest eligible message. No catch-up queue is kept. One generation can run globally, with at most one attempt per minute and three per ten minutes per chat. Up to three models can be tried for one attempt, with a total request budget of 60 seconds. A persistent message watermark prevents duplicate processing across restart. Failures are not automatically retried.

The optional context is at most six prior incoming texts seen during the current enable/settings generation, held only in memory. The default is zero. Each input is capped at 3000 characters. An answer is capped at the configured 50–4000 characters. The client sends through `SendMessagesHelper`, replying to the triggering message. The user can continue sending normally. Pending responses are discarded after disable, relevant configuration changes, account changes, or observed manual outgoing updates.

Changing the endpoint disables previously allowed conversations. Enable them again to consent to the new destination. API key and all feature settings are AES-GCM encrypted with an Android Keystore key, stored under `noBackupFilesDir`; portable settings export does not include them. Credentials, chat text and raw provider error bodies are never logged.

Automatic model choice asks permission to send synthetic generation probes, which may incur provider charges. It ranks successful short generation measurements, never `/models` latency. Cache lifetime is seven days, scoped to normalized endpoint/model, with manual reset. Each test measures up to twelve new models within 90 seconds; repeat for remaining models. Unknown models follow measured successes; failed/incompatible models sort last. Authorization/rate errors stop fallback. Manual model choice disables auto selection.

Checks (no local Gradle):

- `python Tools/test_ai_autoreply.py` compiles the real controller with deterministic platform/network stubs and exercises gates, cancellation, rate, context, duplicate and send routing behavior.
- `powershell -NoProfile -File Tools/tests/ai_client/run.ps1` compiles the real client and exercises fake HTTPS requests, JSON/errors, URL joining, ranking, expiry and benchmark batches.
- Real Android compilation uses `gh workflow run build.yml --ref codex/2.6` only.

The tests substitute platform/network services; hardware Keystore, UI appearance and a live provider exchange require Android device verification. The app must be running to process updates; no new background service is introduced.
