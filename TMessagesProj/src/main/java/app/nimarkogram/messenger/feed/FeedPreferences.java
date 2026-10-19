package app.nimarkogram.messenger.feed;

import android.content.SharedPreferences;
import app.nimarkogram.messenger.NimarkoConfig;
import java.util.HashSet;
import java.util.Set;

/** Account-specific source selection; appearance preferences never contain messages. */
public final class FeedPreferences {
    private FeedPreferences() {}
    public static SharedPreferences prefs() { return NimarkoConfig.getPreferences(); }
    public static Set<String> sources(int account) {
        return new HashSet<>(prefs().getStringSet("linki_feed_sources_" + org.telegram.messenger.UserConfig.getInstance(account).getClientUserId(), new HashSet<>()));
    }
    public static void toggle(int account, long id) {
        Set<String> selected = sources(account);
        String key = Long.toString(id);
        if (!selected.remove(key)) selected.add(key);
        prefs().edit().putStringSet("linki_feed_sources_" + org.telegram.messenger.UserConfig.getInstance(account).getClientUserId(), selected).apply();
    }
    public static boolean compact(String screen) { return prefs().getBoolean("linki_" + screen + "_compact", false); }
    public static boolean labels(String screen) { return prefs().getBoolean("linki_" + screen + "_labels", true); }
    public static int accent(String screen, int fallback) { return prefs().getInt("linki_" + screen + "_accent", fallback); }
}
