package app.nimarkogram.messenger.appearance;

import android.content.Context;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

/** Global Material You layer for Telegram's existing native views. */
public final class LinkiGramYouTheme {
    private static final String PREF_ENABLED = "linkigramYouEnabled";
    public static final int NAV_COLOR_SURFACE = 0;
    public static final int NAV_COLOR_INDICATOR = 1;
    public static final int NAV_COLOR_SELECTED = 2;
    public static final int NAV_COLOR_UNSELECTED = 3;
    private static volatile boolean enabled;
    private static volatile boolean loaded;

    private LinkiGramYouTheme() { }

    public static boolean isEnabled() {
        if (loaded) return enabled;
        try {
            synchronized (LinkiGramYouTheme.class) {
                if (!loaded) {
                    enabled = app.nimarkogram.messenger.NimarkoConfig.getPreferences().getBoolean(PREF_ENABLED, true);
                    loaded = true;
                }
                return enabled;
            }
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        if (enabled) ColorOs17Theme.setEnabled(false);
        LinkiGramYouTheme.enabled = enabled;
        loaded = true;
        app.nimarkogram.messenger.NimarkoConfig.getPreferences().edit().putBoolean(PREF_ENABLED, enabled).apply();
    }

    /** Applies Android's wallpaper-derived Material palette to Telegram's semantic color roles. */
    public static int resolveColor(int key, int original) {
        if (ColorOs17Theme.isEnabled()) return ColorOs17Theme.resolveColor(key, original);
        if (!isEnabled() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return original;
        Context context = ApplicationLoader.applicationContext;
        if (context == null) return original;
        final boolean dark = Theme.isCurrentThemeDark();
        try {
            if (key == Theme.key_actionBarDefault || key == Theme.key_actionBarDefaultArchived
                    || key == Theme.key_windowBackgroundWhite || key == Theme.key_dialogBackground
                    || key == Theme.key_actionBarDefaultSubmenuBackground) return color(context, dark,
                    android.R.color.system_neutral1_0, android.R.color.system_neutral1_900, original);
            if (key == Theme.key_windowBackgroundGray || key == Theme.key_dialogBackgroundGray
                    || key == Theme.key_dialogInputField || key == Theme.key_windowBackgroundWhiteInputField)
                return color(context, dark,
                    android.R.color.system_neutral1_50, android.R.color.system_neutral1_800, original);
            if (key == Theme.key_actionBarDefaultIcon || key == Theme.key_actionBarDefaultTitle
                    || key == Theme.key_actionBarDefaultArchivedIcon || key == Theme.key_actionBarDefaultArchivedTitle
                    || key == Theme.key_actionBarDefaultSubmenuItem || key == Theme.key_dialogTextBlack
                    || key == Theme.key_dialogButton || key == Theme.key_chats_name
                    || key == Theme.key_windowBackgroundWhiteBlackText) return color(context, dark,
                    android.R.color.system_neutral1_900, android.R.color.system_neutral1_100, original);
            if (key == Theme.key_actionBarDefaultSubtitle || key == Theme.key_windowBackgroundWhiteGrayText
                    || key == Theme.key_windowBackgroundWhiteGrayText2 || key == Theme.key_windowBackgroundWhiteGrayIcon
                    || key == Theme.key_dialogTextGray || key == Theme.key_dialogTextGray2
                    || key == Theme.key_actionBarDefaultArchivedSearchPlaceholder
                    || key == Theme.key_actionBarDefaultSearchPlaceholder || key == Theme.key_actionBarDefaultSearch
                    || key == Theme.key_actionBarDefaultSubmenuItemIcon || key == Theme.key_chats_message
                    || key == Theme.key_chats_actionMessage)
                return color(context, dark, android.R.color.system_neutral2_700, android.R.color.system_neutral2_200, original);
            if (key == Theme.key_windowBackgroundWhiteBlueText || key == Theme.key_windowBackgroundWhiteBlueText2
                    || key == Theme.key_windowBackgroundWhiteBlueText3 || key == Theme.key_windowBackgroundWhiteBlueText4
                    || key == Theme.key_windowBackgroundWhiteBlueText5 || key == Theme.key_windowBackgroundWhiteBlueText6
                    || key == Theme.key_windowBackgroundWhiteBlueText7 || key == Theme.key_windowBackgroundWhiteLinkText
                    || key == Theme.key_windowBackgroundWhiteBlueIcon || key == Theme.key_windowBackgroundWhiteBlueHeader
                    || key == Theme.key_dialogTextBlue || key == Theme.key_dialogTextBlue2 || key == Theme.key_dialogTextLink
                    || key == Theme.key_dialogInputFieldActivated || key == Theme.key_dialogCheckboxSquareBackground
                    || key == Theme.key_dialogRadioBackgroundChecked || key == Theme.key_dialogRoundCheckBoxCheck
                    || key == Theme.key_dialogButtonSelector || key == Theme.key_actionBarDefaultArchivedSearch
                    || key == Theme.key_telegram_color || key == Theme.key_telegram_color_text
                    || key == Theme.key_checkboxSquareBackground || key == Theme.key_checkboxCheck
                    || key == Theme.key_radioBackgroundChecked || key == Theme.key_chats_unreadCounter
                    || key == Theme.key_chats_pinnedIcon || key == Theme.key_chats_onlineCircle)
                return color(context, dark, android.R.color.system_accent1_600, android.R.color.system_accent1_200, original);
            if (key == Theme.key_windowBackgroundWhiteBlueButton || key == Theme.key_actionBarDefaultSelector)
                return color(context, dark, android.R.color.system_accent1_100, android.R.color.system_accent1_700, original);
            if (key == Theme.key_chats_unreadCounterText || key == Theme.key_checkboxSquareCheck)
                return color(context, dark, android.R.color.system_accent1_0, android.R.color.system_accent1_900, original);
            if (key == Theme.key_chat_outBubble || key == Theme.key_chat_outBubbleSelected)
                return color(context, dark, android.R.color.system_accent1_100, android.R.color.system_accent1_800, original);
            if (key == Theme.key_chat_messageTextOut)
                return color(context, dark, android.R.color.system_accent1_900, android.R.color.system_accent1_100, original);
            if (key == Theme.key_chat_inBubble || key == Theme.key_chat_inBubbleSelected)
                return color(context, dark, android.R.color.system_neutral1_100, android.R.color.system_neutral1_800, original);
            if (key == Theme.key_chat_messageTextIn)
                return color(context, dark, android.R.color.system_neutral1_900, android.R.color.system_neutral1_100, original);
        } catch (Throwable ignore) {
            // Keep the selected Telegram theme on devices without a usable system palette.
        }
        return original;
    }

    /** Material 3 color roles used by the main NavigationBar. */
    public static int navigationBarColor(Context context, int role, int fallback) {
        if (ColorOs17Theme.isEnabled()) return ColorOs17Theme.navigationBarColor(role, fallback);
        if (!isEnabled() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context == null) return fallback;
        final boolean dark = Theme.isCurrentThemeDark();
        final int colorId;
        switch (role) {
            case NAV_COLOR_SURFACE:
                colorId = dark ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_50;
                break;
            case NAV_COLOR_INDICATOR:
                colorId = dark ? android.R.color.system_accent2_800 : android.R.color.system_accent2_100;
                break;
            case NAV_COLOR_SELECTED:
                colorId = dark ? android.R.color.system_accent2_100 : android.R.color.system_accent2_900;
                break;
            case NAV_COLOR_UNSELECTED:
                colorId = dark ? android.R.color.system_neutral2_200 : android.R.color.system_neutral2_700;
                break;
            default:
                return fallback;
        }
        try {
            return context.getColor(colorId);
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    private static int color(Context context, boolean dark, int lightId, int darkId, int fallback) {
        return context.getColor(dark ? darkId : lightId);
    }
}
