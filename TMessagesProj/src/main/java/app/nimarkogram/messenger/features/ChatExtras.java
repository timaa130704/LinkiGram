package app.nimarkogram.messenger.features;

import app.nimarkogram.messenger.NimarkoConfig;

public final class ChatExtras {
    public static boolean enabled(String key) { return NimarkoConfig.getPreferences().getBoolean("extras_" + key, key.equals("cleanOpen") || key.equals("cleanPaste")); }
    public static void toggle(String key) { NimarkoConfig.getEditor().putBoolean("extras_" + key, !enabled(key)).apply(); }
    private ChatExtras() { }
}
