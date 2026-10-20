package app.nimarkogram.messenger;

/** Local display preferences; these do not change Telegram privacy settings. */
public final class LinkiPrivacy {
    private LinkiPrivacy() {}
    public static boolean hidePhone() { return NimarkoConfig.getPreferences().getBoolean("linkiHidePhone", false); }
    public static boolean relativeLastSeen() { return NimarkoConfig.getPreferences().getBoolean("linkiRelativeLastSeen", false); }
    public static void toggle(String key) {
        NimarkoConfig.getEditor().putBoolean(key, !NimarkoConfig.getPreferences().getBoolean(key, false)).apply();
    }
}
