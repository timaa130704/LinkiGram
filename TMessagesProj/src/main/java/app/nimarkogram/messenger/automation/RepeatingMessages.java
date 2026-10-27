package app.nimarkogram.messenger.automation;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import java.util.ArrayList;
import java.util.UUID;

/** Device-local recurring text messages. Negative picker periods never reach Telegram RPCs. */
public final class RepeatingMessages {
    private static final String ACTION = "app.linkigram.REPEAT_MESSAGE";
    private RepeatingMessages() {}
    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("linki_repeating_messages", Context.MODE_PRIVATE);
    }
    private static JSONArray read() {
        try { return new JSONArray(prefs().getString("jobs", "[]")); }
        catch (Exception e) { FileLog.e(e); return new JSONArray(); }
    }
    private static void save(JSONArray jobs) {
        if (!prefs().edit().putString("jobs", jobs.toString()).commit()) throw new IllegalStateException("Cannot persist repeating messages");
        AndroidUtilities.runOnUIThread(() -> {
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                if (UserConfig.getInstance(i).isClientActivated())
                    NotificationCenter.getInstance(i).postNotificationName(NotificationCenter.linkiRepeatsUpdated);
            }
        });
    }
    private static String encode(TLRPC.Message message) throws Exception {
        if (message == null) return "";
        NativeByteBuffer buffer = new NativeByteBuffer(message.getObjectSize());
        try {
            message.serializeToStream(buffer);
            byte[] bytes = new byte[buffer.length()];
            buffer.position(0); buffer.readBytes(bytes, true);
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } finally { buffer.reuse(); }
    }
    private static MessageObject decode(int account, String encoded) throws Exception {
        if (encoded.isEmpty()) return null;
        byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
        NativeByteBuffer buffer = new NativeByteBuffer(bytes.length);
        try {
            buffer.writeBytes(bytes); buffer.position(0);
            TLRPC.Message message = TLRPC.Message.TLdeserialize(buffer, buffer.readInt32(true), true);
            return new MessageObject(account, message, false, false);
        } finally { buffer.reuse(); }
    }
    public static boolean capture(int account, SendMessagesHelper.SendMessageParams params) {
        if (params.scheduleRepeatPeriod >= 0) return false;
        if (TextUtils.isEmpty(params.message) || params.photo != null || params.document != null
                || params.location != null || params.user != null || params.poll != null || params.todo != null
                || params.game != null || params.invoice != null || params.payStars > 0
                || DialogObject.isEncryptedDialog(params.peer)) {
            reject(); return true;
        }
        try {
            int seconds = -params.scheduleRepeatPeriod;
            if (seconds < 60 || seconds > 31 * 86400 || params.scheduleDate <= 0) { reject(); return true; }
            TLRPC.TL_message template = new TLRPC.TL_message();
            template.message = params.message;
            template.peer_id = MessagesController.getInstance(account).getPeer(params.peer);
            if (params.entities != null && !params.entities.isEmpty()) {
                template.entities = params.entities; template.flags |= 128;
            }
            JSONObject job = new JSONObject();
            job.put("id", UUID.randomUUID().toString());
            job.put("user", UserConfig.getInstance(account).getClientUserId());
            job.put("test", ConnectionsManager.getInstance(account).isTestBackend());
            job.put("dialog", params.peer); job.put("period", seconds);
            job.put("next", Math.max(System.currentTimeMillis() + 1000, params.scheduleDate * 1000L));
            job.put("message", encode(template)); job.put("text", params.message);
            job.put("reply", encode(params.replyToMsg == null ? null : params.replyToMsg.messageOwner));
            job.put("topic", encode(params.replyToTopMsg == null ? null : params.replyToTopMsg.messageOwner));
            job.put("reply_main", params.replyToMsg != null && params.replyToMsg.isTopicMainMessage);
            job.put("topic_main", params.replyToTopMsg != null && params.replyToTopMsg.isTopicMainMessage);
            job.put("same_target", params.replyToMsg != null && params.replyToMsg == params.replyToTopMsg);
            job.put("notify", params.notify); job.put("mono", params.monoForumPeer);
            JSONArray jobs = read(); jobs.put(job); save(jobs); arm(job);
            toast(R.string.LinkiRepeatSaved);
        } catch (Exception e) { FileLog.e(e); toast(R.string.LinkiAutomationError); }
        return true;
    }
    public static void reject() { toast(R.string.LinkiRepeatTextOnly); }
    private static void toast(int id) {
        AndroidUtilities.runOnUIThread(() -> android.widget.Toast.makeText(ApplicationLoader.applicationContext,
                LocaleController.getString(id), android.widget.Toast.LENGTH_LONG).show());
    }
    private static PendingIntent pending(JSONObject job) {
        Context context = ApplicationLoader.applicationContext;
        Intent intent = new Intent(context, Receiver.class).setAction(ACTION)
                .setData(Uri.parse("linkigram-repeat://job/" + job.optString("id")));
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static void arm(JSONObject job) {
        AlarmManager alarms = (AlarmManager) ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE);
        long date = Math.max(System.currentTimeMillis() + 1000, job.optLong("next"));
        try {
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date, pending(job)); return;
            }
        } catch (SecurityException ignored) {}
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date, pending(job));
    }
    public static void restore() {
        AndroidUtilities.runOnUIThread(() -> {
            JSONArray jobs = read();
            for (int i = 0; i < jobs.length(); i++) {
                try { arm(jobs.getJSONObject(i)); } catch (Exception e) { FileLog.e(e); }
            }
        });
    }
    private static int account(JSONObject job) {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (UserConfig.getInstance(i).isClientActivated()
                    && UserConfig.getInstance(i).getClientUserId() == job.optLong("user")
                    && ConnectionsManager.getInstance(i).isTestBackend() == job.optBoolean("test")) return i;
        }
        return -1;
    }
    private static void fire(String id) {
        JSONArray jobs = read();
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job == null || !id.equals(job.optString("id"))) continue;
            int account = account(job);
            if (account < 0) { remove(id); return; }
            if (job.optLong("next") > System.currentTimeMillis() + 1000) { arm(job); return; }
            try {
                // Advance before enqueueing; missing intervals never generate a burst of messages.
                job.put("next", System.currentTimeMillis() + job.getLong("period") * 1000);
                save(jobs); arm(job);
                ConnectionsManager.getInstance(account).resumeNetworkMaybe();
                deliver(job, account, 0);
            } catch (Exception e) { FileLog.e(e); remove(id); toast(R.string.LinkiAutomationError); }
            return;
        }
    }
    private static void deliver(JSONObject job, int account, int attempt) {
        if (account(job) != account) return;
        boolean present = false;
        JSONArray jobs = read();
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject current = jobs.optJSONObject(i);
            if (current != null && job.optString("id").equals(current.optString("id"))) { present = true; break; }
        }
        if (!present) return;
        int state = ConnectionsManager.getInstance(account).getConnectionState();
        if (state != ConnectionsManager.ConnectionStateConnected && state != ConnectionsManager.ConnectionStateUpdating) {
            if (attempt < 2) AndroidUtilities.runOnUIThread(() -> deliver(job, account, attempt + 1), 2000);
            return; // No offline backlog of repeated messages.
        }
        try {
            MessageObject template = decode(account, job.getString("message"));
            MessageObject reply = decode(account, job.optString("reply"));
            MessageObject topic = decode(account, job.optString("topic"));
            long dialogId = job.getLong("dialog");
            TLRPC.Chat chat = dialogId < 0 ? MessagesController.getInstance(account).getChat(-dialogId) : null;
            // Old jobs did not persist the forum marker. Do not guess while the chat is unavailable.
            if (topic != null && !job.has("topic_main") && dialogId < 0 && chat == null) return;
            boolean forum = ChatObject.isForum(chat);
            if (topic != null) {
                topic.isTopicMainMessage = forum || job.optBoolean("topic_main");
            }
            if (reply == null && topic != null && topic.isTopicMainMessage) reply = topic;
            if (reply != null) {
                boolean sameMessage = topic != null && reply.getId() == topic.getId()
                        && reply.getDialogId() == topic.getDialogId();
                reply.isTopicMainMessage = job.has("reply_main") ? job.optBoolean("reply_main")
                        : forum && sameMessage;
                if (sameMessage && (job.optBoolean("same_target") || !job.has("same_target"))) reply = topic;
            }
            SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                    template.messageOwner.message, job.getLong("dialog"),
                    reply, topic,
                    null, true, template.messageOwner.entities, null, null, job.optBoolean("notify", true), 0, 0, null, false);
            params.monoForumPeer = job.optLong("mono");
            SendMessagesHelper.getInstance(account).sendMessage(params);
        } catch (Exception e) { FileLog.e(e); remove(job.optString("id")); toast(R.string.LinkiAutomationError); }
    }
    private static void remove(String id) {
        JSONArray jobs = read(), kept = new JSONArray();
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job != null && id.equals(job.optString("id"))) {
                ((AlarmManager) ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE)).cancel(pending(job));
            } else if (job != null) kept.put(job);
        }
        save(kept);
    }
    private static ArrayList<JSONObject> matching(int account, long dialog, long topicId) {
        ArrayList<JSONObject> selected = new ArrayList<>();
        JSONArray jobs = read();
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job != null && job.optLong("dialog") == dialog && account(job) == account) {
                if (topicId != 0) {
                    try {
                        MessageObject topic = decode(account, job.optString("topic"));
                        if (topic == null || topic.getId() != topicId) continue;
                    } catch (Exception e) { FileLog.e(e); continue; }
                }
                selected.add(job);
            }
        }
        selected.sort((a, b) -> Long.compare(a.optLong("next"), b.optLong("next")));
        return selected;
    }
    public static int count(int account, long dialog, long topicId) {
        return matching(account, dialog, topicId).size();
    }
    private static CharSequence label(JSONObject job) {
        String text = job.optString("text");
        return (text.length() > 200 ? text.substring(0, 200) + "…" : text) + "\n"
                + LocaleController.getString(R.string.LinkiRepeatLocalLabel) + " · "
                + LocaleController.formatDateTime(job.optLong("next") / 1000, true)
                + " · " + job.optInt("period") / 60 + " " + LocaleController.getString(R.string.LinkiMinutes);
    }
    private static void confirmRemove(BaseFragment fragment, JSONObject job) {
        if (fragment.getParentActivity() == null) return;
        AlertDialog.Builder confirm = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        confirm.setTitle(LocaleController.getString(R.string.LinkiRepeatCancel));
        confirm.setMessage(job.optString("text"));
        confirm.setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> remove(job.optString("id")));
        confirm.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(confirm.create());
    }
    public static void bindScheduled(LinearLayout container, BaseFragment fragment, int account, long dialog, long topicId) {
        container.removeAllViews();
        for (JSONObject job : matching(account, dialog, topicId)) {
            TextView row = new TextView(container.getContext());
            row.setText(label(job));
            row.setTextSize(15);
            row.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, fragment.getResourceProvider()));
            row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
            row.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                    Theme.getColor(Theme.key_windowBackgroundWhite, fragment.getResourceProvider())));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));
            container.addView(row, lp);
            row.setOnClickListener(v -> confirmRemove(fragment, job));
        }
    }
    public static void show(BaseFragment fragment, int account, long dialog, long topicId) {
        ArrayList<JSONObject> selected = matching(account, dialog, topicId);
        ArrayList<CharSequence> labels = new ArrayList<>();
        for (JSONObject job : selected) labels.add(label(job));
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.LinkiRepeatingMessages));
        if (selected.isEmpty()) builder.setMessage(LocaleController.getString(R.string.LinkiRepeatEmpty));
        else builder.setItems(labels.toArray(new CharSequence[0]), (dialogInterface, which) -> confirmRemove(fragment, selected.get(which)));
        builder.setNegativeButton(LocaleController.getString(R.string.Close), null);
        fragment.showDialog(builder.create());
    }
    public static class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            ApplicationLoader.postInitApplication();
            PendingResult result = goAsync();
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (ACTION.equals(intent.getAction()) && intent.getData() != null) fire(intent.getData().getLastPathSegment());
                    else restore();
                } finally { AndroidUtilities.runOnUIThread(result::finish, 5000); }
            });
        }
    }
}
