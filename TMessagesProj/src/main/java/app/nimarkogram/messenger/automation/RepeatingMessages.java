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
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
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
                MessageObject template = decode(account, job.getString("message"));
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                        template.messageOwner.message, job.getLong("dialog"),
                        decode(account, job.optString("reply")), decode(account, job.optString("topic")),
                        null, true, template.messageOwner.entities, null, null, job.optBoolean("notify", true), 0, 0, null, false);
                params.monoForumPeer = job.optLong("mono");
                ConnectionsManager.getInstance(account).resumeNetworkMaybe();
                SendMessagesHelper.getInstance(account).sendMessage(params);
            } catch (Exception e) { FileLog.e(e); remove(id); toast(R.string.LinkiAutomationError); }
            return;
        }
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
    public static void show(BaseFragment fragment, int account, long dialog) {
        ArrayList<JSONObject> selected = new ArrayList<>(); ArrayList<CharSequence> labels = new ArrayList<>();
        JSONArray jobs = read();
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job != null && job.optLong("dialog") == dialog && account(job) == account) {
                selected.add(job);
                String text = job.optString("text");
                labels.add((text.length() > 70 ? text.substring(0, 70) + "…" : text) + "\n"
                        + LocaleController.formatDateTime(job.optLong("next") / 1000, true)
                        + " · " + job.optInt("period") / 60 + " " + LocaleController.getString(R.string.LinkiMinutes));
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.LinkiRepeatingMessages));
        if (selected.isEmpty()) builder.setMessage(LocaleController.getString(R.string.LinkiRepeatEmpty));
        else builder.setItems(labels.toArray(new CharSequence[0]), (dialogInterface, which) -> {
            AlertDialog.Builder confirm = new AlertDialog.Builder(fragment.getParentActivity());
            confirm.setTitle(LocaleController.getString(R.string.LinkiRepeatCancel));
            confirm.setMessage(selected.get(which).optString("text"));
            confirm.setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> remove(selected.get(which).optString("id")));
            confirm.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            fragment.showDialog(confirm.create());
        });
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
