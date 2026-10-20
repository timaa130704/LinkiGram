package app.nimarkogram.messenger.local;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

/** Cosmetic client state; never changes the server's user object or entitlement checks. */
public final class LocalPremium {
    private LocalPremium() {}

    public static boolean isEnabled(int account) {
        String scope = LocalAccountSettings.scope(account);
        return scope != null && MessagesController.getGlobalMainSettings()
                .getBoolean("linki_local_premium_" + scope, false);
    }

    public static void toggle(int account) {
        String scope = LocalAccountSettings.scope(account);
        if (scope != null) MessagesController.getGlobalMainSettings().edit()
                .putBoolean("linki_local_premium_" + scope, !isEnabled(account)).apply();
    }

    public static boolean showBadge(int account, TLRPC.User user) {
        return user != null && (MessagesController.getInstance(account).isPremiumUser(user)
                || user.id == UserConfig.getInstance(account).getClientUserId() && isEnabled(account));
    }
}
