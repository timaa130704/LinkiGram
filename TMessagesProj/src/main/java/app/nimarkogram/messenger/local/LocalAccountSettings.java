package app.nimarkogram.messenger.local;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

/** Device-only keys follow the Telegram user, never a reusable account slot. */
final class LocalAccountSettings {
    private LocalAccountSettings() {}

    static String scope(int account) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) return null;
        long uid = UserConfig.getInstance(account).getClientUserId();
        if (uid == 0) return null;
        return (ConnectionsManager.getInstance(account).isTestBackend() ? "test_" : "main_") + uid;
    }
}
