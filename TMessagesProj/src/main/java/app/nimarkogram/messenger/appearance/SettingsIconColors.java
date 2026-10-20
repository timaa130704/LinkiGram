package app.nimarkogram.messenger.appearance;

import app.nimarkogram.messenger.NimarkoConfig;

/** Shared style for settings section badges. Colors follow the active theme at draw time. */
public final class SettingsIconColors {
    public static final int NEUTRAL = 0, ACCENT = 1, ORIGINAL = 2, CUSTOM = 3;
    private SettingsIconColors() {}
    public static int mode() {
        int value = NimarkoConfig.getPreferences().getInt("settingsIconColorMode", NEUTRAL);
        return value >= NEUTRAL && value <= CUSTOM ? value : NEUTRAL;
    }
    public static int customColor() {
        return NimarkoConfig.getPreferences().getInt("settingsIconCustomColor", 0xFF7E72DE) | 0xFF000000;
    }
    public static void setMode(int mode) {
        NimarkoConfig.getEditor().putInt("settingsIconColorMode", mode).apply();
    }
    public static void setCustomColor(int color) {
        NimarkoConfig.getEditor().putInt("settingsIconCustomColor", color | 0xFF000000)
                .putInt("settingsIconColorMode", CUSTOM).apply();
    }
}
