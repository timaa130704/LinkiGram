package app.nimarkogram.messenger.utils;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;

import java.util.Map;

import app.nimarkogram.messenger.NimarkoConfig;

/**
 * LinkiGram: автоочистка истории чата по своему таймеру.
 *
 * Таймер задаётся для каждого чата отдельно и хранится в общих настройках
 * форка (ключ autoClean_&lt;account&gt;_&lt;dialogId&gt;, значение — число дней).
 * Чистка запускается при старте приложения не чаще раза в 12 часов и удаляет
 * сообщения старше выбранного срока только для текущего пользователя
 * ({@code forAll = false}), поэтому чужие копии не трогаются.
 */
public final class NimarkoAutoClean {

    /** Доступные интервалы в днях; 0 — выключено. */
    public static final int[] INTERVALS = {0, 1, 3, 7, 30, 90};

    private static final String KEY_PREFIX = "autoClean_";
    private static final String LAST_RUN_KEY = "autoCleanLastRun";
    private static final long MIN_INTERVAL_MS = 12 * 3600_000L;

    private NimarkoAutoClean() {
    }

    public static int getDays(int account, long dialogId) {
        return NimarkoConfig.getPreferences().getInt(KEY_PREFIX + account + "_" + dialogId, 0);
    }

    public static void setDays(int account, long dialogId, int days) {
        SharedPreferences.Editor editor = NimarkoConfig.getEditor();
        final String key = KEY_PREFIX + account + "_" + dialogId;
        if (days <= 0) {
            editor.remove(key);
        } else {
            editor.putInt(key, days);
        }
        editor.apply();
    }

    /** Запускается при старте приложения; сама решает, пора ли чистить. */
    public static void run() {
        final SharedPreferences preferences = NimarkoConfig.getPreferences();
        if (System.currentTimeMillis() - preferences.getLong(LAST_RUN_KEY, 0) < MIN_INTERVAL_MS) {
            return;
        }
        preferences.edit().putLong(LAST_RUN_KEY, System.currentTimeMillis()).apply();
        Utilities.globalQueue.postRunnable(() -> AndroidUtilities.runOnUIThread(NimarkoAutoClean::runInternal));
    }

    private static void runInternal() {
        final SharedPreferences preferences = NimarkoConfig.getPreferences();
        final int now = (int) (System.currentTimeMillis() / 1000);
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            final String key = entry.getKey();
            if (!key.startsWith(KEY_PREFIX) || !(entry.getValue() instanceof Integer)) {
                continue;
            }
            final int days = (Integer) entry.getValue();
            if (days <= 0) {
                continue;
            }
            final int separator = key.indexOf('_', KEY_PREFIX.length());
            if (separator < 0) {
                continue;
            }
            final int account;
            final long dialogId;
            try {
                account = Integer.parseInt(key.substring(KEY_PREFIX.length(), separator));
                dialogId = Long.parseLong(key.substring(separator + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (account < 0 || account >= org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT) {
                continue;
            }
            try {
                final MessagesController controller = MessagesController.getInstance(account);
                final long channelId = controller.getChat(-dialogId) != null ? dialogId : 0;
                controller.deleteMessagesRange(dialogId, channelId, 0, now - days * 86400, false, () -> {});
            } catch (Throwable t) {
                FileLog.e("nimarko: auto clean failed for " + dialogId, t);
            }
        }
    }
}