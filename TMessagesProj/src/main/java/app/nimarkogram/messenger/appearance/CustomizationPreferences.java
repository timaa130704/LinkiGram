package app.nimarkogram.messenger.appearance;

import android.content.SharedPreferences;
import app.nimarkogram.messenger.NimarkoConfig;

/** Appearance settings shared by the side-menu editor and drawer views. */
public final class CustomizationPreferences {
    private CustomizationPreferences() {}
    public static SharedPreferences prefs() { return NimarkoConfig.getPreferences(); }
    public static boolean compact(String screen) { return prefs().getBoolean("linki_" + screen + "_compact", false); }
    public static boolean labels(String screen) { return prefs().getBoolean("linki_" + screen + "_labels", true); }
    public static int accent(String screen, int fallback) { return prefs().getInt("linki_" + screen + "_accent", fallback); }
}
