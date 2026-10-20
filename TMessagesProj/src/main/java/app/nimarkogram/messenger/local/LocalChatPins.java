package app.nimarkogram.messenger.local;

import org.json.JSONArray;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;

import java.util.LinkedHashMap;

/** Unlimited device-only pins. Cloud pinned flags and ordering are never modified. */
public final class LocalChatPins {
    private LocalChatPins() {}
    private static final State[] states = new State[UserConfig.MAX_ACCOUNT_COUNT];

    private static final class State {
        final String scope;
        final LinkedHashMap<Long, Long> ranks = new LinkedHashMap<>();
        long sequence;
        State(String scope) { this.scope = scope; }
    }

    private static State state(int account) {
        String scope = LocalAccountSettings.scope(account);
        if (scope == null) return null;
        State state = states[account];
        if (state != null && state.scope.equals(scope)) return state;
        state = new State(scope);
        try {
            JSONArray ids = new JSONArray(MessagesController.getGlobalMainSettings()
                    .getString("linki_local_pins_" + scope, "[]"));
            for (int i = 0; i < ids.length(); i++) {
                long id = Long.parseLong(ids.getString(i));
                if (id != 0) state.ranks.put(id, ++state.sequence);
            }
        } catch (Exception e) { FileLog.e(e); }
        states[account] = state;
        return state;
    }

    public static synchronized boolean isPinned(int account, long dialogId) {
        State state = state(account);
        return state != null && state.ranks.containsKey(dialogId);
    }

    public static synchronized void setPinned(int account, long dialogId, boolean pinned) {
        State state = state(account);
        if (state == null || dialogId == 0) return;
        if (pinned) {
            if (state.ranks.containsKey(dialogId)) return;
            state.ranks.put(dialogId, ++state.sequence);
        } else if (state.ranks.remove(dialogId) == null) return;
        JSONArray ids = new JSONArray();
        for (Long id : state.ranks.keySet()) ids.put(Long.toString(id));
        MessagesController.getGlobalMainSettings().edit()
                .putString("linki_local_pins_" + state.scope, ids.toString()).apply();
    }

    /** Call after the cloud pin comparison, preserving its contiguous leading block. */
    public static synchronized int compare(int account, long first, long second) {
        State state = state(account);
        if (state == null) return 0;
        Long a = state.ranks.get(first), b = state.ranks.get(second);
        return Long.compare(b == null ? 0L : b, a == null ? 0L : a);
    }
}
