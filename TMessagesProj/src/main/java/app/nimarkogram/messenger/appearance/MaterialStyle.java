package app.nimarkogram.messenger.appearance;

import app.nimarkogram.messenger.NimarkoConfig;

/** Shared Material list metrics; all settings lists use the same geometry. */
public final class MaterialStyle {
    private MaterialStyle() {}

    public static boolean listItems() {
        return LinkiGramYouTheme.isEnabled() || ColorOs17Theme.isEnabled()
                || Material3Theme.isEnabled() || NimarkoConfig.m3ListItems();
    }
    public static int dividerStyle() {
        if (LinkiGramYouTheme.isEnabled() || ColorOs17Theme.isEnabled()
                || Material3Theme.isEnabled()) return 2;
        return Math.max(0, Math.min(2, NimarkoConfig.getPreferences().getInt("materialDividerStyle", NimarkoConfig.disableDividers ? 0 : 1)));
    }
    public static boolean segments() { return listItems() || dividerStyle() == 2; }
    public static boolean separatedHeaders() {
        return segments() || NimarkoConfig.getPreferences().getBoolean("materialSeparateHeaders", false);
    }
    public static boolean hideDividers() { return listItems() || dividerStyle() != 1; }
    public static int rowHeight(int height) {
        if (!listItems() || height != 50) return height;
        return ColorOs17Theme.isEnabled() ? 60 : 56;
    }
    public static int detailHeight(int height) { return listItems() && height == 64 ? 72 : height; }
    public static int shadowHeight(boolean beforeHeader) { return listItems() ? (beforeHeader ? 10 : 16) : 12; }
    public static int switchTextInset() { return ClientTheme.isEnabled() ? switchWidth() + 38 : 70; }
    public static int switchWidth() {
        if (ColorOs17Theme.isEnabled()) return 44;
        if (Material3Theme.isEnabled() || LinkiGramYouTheme.isEnabled()) return 52;
        return 37;
    }
    public static int switchHeight() {
        if (ColorOs17Theme.isEnabled()) return 26;
        if (Material3Theme.isEnabled() || LinkiGramYouTheme.isEnabled()) return 32;
        return 20;
    }
}
