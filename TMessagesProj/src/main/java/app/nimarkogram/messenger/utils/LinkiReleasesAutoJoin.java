package app.nimarkogram.messenger.utils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import android.content.SharedPreferences;

/**
 * Joins @linkireleases once when the user starts using the client.
 *
 * Fire-and-forget: runs at most once per process and records success in prefs,
 * so it never spams the API. Any failure (no account yet, no connection,
 * resolve error) simply leaves the flag unset and retries on a later launch.
 * An "already a participant" answer counts as success.
 */
public final class LinkiReleasesAutoJoin {

    private static final String CHANNEL = "linkireleases";
    private static final String PREFS = "nimarkoconfig";
    private static final String KEY_DONE = "linkireleases_autojoin_done";

    private static volatile boolean attempted;

    private LinkiReleasesAutoJoin() {}

    public static void ensureAsync() {
        if (attempted) return;
        attempted = true;
        try {
            if (getPrefs().getBoolean(KEY_DONE, false)) return;
        } catch (Throwable ignored) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                doJoin();
            } catch (Throwable ignored) {}
        }, "linki-autojoin");
        t.setDaemon(true);
        try {
            t.start();
        } catch (Throwable ignored) {}
    }

    private static void doJoin() {
        int account = UserConfig.selectedAccount;
        UserConfig uc;
        try {
            uc = UserConfig.getInstance(account);
        } catch (Throwable ignored) {
            return;
        }
        if (uc == null || !uc.isClientActivated()) return;

        TLRPC.TL_contacts_resolveUsername resolve = new TLRPC.TL_contacts_resolveUsername();
        resolve.username = CHANNEL;
        try {
            ConnectionsManager.getInstance(account).sendRequest(resolve, (response, error) -> {
                try {
                    if (!(response instanceof TLRPC.TL_contacts_resolvedPeer)) return;
                    TLRPC.TL_contacts_resolvedPeer peer = (TLRPC.TL_contacts_resolvedPeer) response;
                    TLRPC.Chat chat = null;
                    if (peer.chats != null) {
                        for (TLRPC.Chat c : peer.chats) {
                            if (c != null && (c.id == peer.peer.channel_id || c.id == peer.peer.chat_id)) {
                                chat = c;
                                break;
                            }
                        }
                        if (chat == null && !peer.chats.isEmpty()) chat = peer.chats.get(0);
                    }
                    if (chat == null) return;
                    TLRPC.InputChannel input = MessagesController.getInputChannel(chat);
                    if (input == null || input instanceof TLRPC.TL_inputChannelEmpty) return;
                    TLRPC.TL_channels_joinChannel join = new TLRPC.TL_channels_joinChannel();
                    join.channel = input;
                    ConnectionsManager.getInstance(account).sendRequest(join, (response2, error2) -> {
                        try {
                            if (response2 != null || (error2 != null && "USER_ALREADY_PARTICIPANT".equals(error2.text))) {
                                markDone();
                            }
                        } catch (Throwable ignored) {}
                    });
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static void markDone() {
        try {
            getPrefs().edit().putBoolean(KEY_DONE, true).apply();
        } catch (Throwable ignored) {}
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
