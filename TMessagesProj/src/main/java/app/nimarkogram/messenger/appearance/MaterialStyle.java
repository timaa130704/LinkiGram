package app.nimarkogram.messenger.appearance;

import app.nimarkogram.messenger.NimarkoConfig;

/** Shared Material list metrics; all settings lists use the same geometry. */
public final class MaterialStyle {
    private MaterialStyle() {}

    public static boolean listItems() { return LinkiGramYouTheme.isEnabled() || NimarkoConfig.m3ListItems(); }
    public static int dividerStyle() {
        if (LinkiGramYouTheme.isEnabled()) return 2;
        return Math.max(0, Math.min(2, NimarkoConfig.getPreferences().getInt("materialDividerStyle", NimarkoConfig.disableDividers ? 0 : 1)));
    }
    public static boolean segments() { return listItems() || dividerStyle() == 2; }
    public static boolean separatedHeaders() {
        return segments() || NimarkoConfig.getPreferences().getBoolean("materialSeparateHeaders", false);
    }
    public static boolean hideDividers() { return listItems() || dividerStyle() != 1; }
    public static int rowHeight(int height) { return listItems() && height == 50 ? 56 : height; }
    public static int detailHeight(int height) { return listItems() && height == 64 ? 72 : height; }
    public static int shadowHeight(boolean beforeHeader) { return listItems() ? (beforeHeader ? 10 : 16) : 12; }
}
