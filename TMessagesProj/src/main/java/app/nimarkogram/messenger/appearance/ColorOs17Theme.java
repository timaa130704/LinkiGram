package app.nimarkogram.messenger.appearance;

import app.nimarkogram.messenger.NimarkoConfig;
import org.telegram.ui.ActionBar.Theme;

/** ColorOS-inspired palette for LinkiGram's native Android views. */
public final class ColorOs17Theme {
    private static final String PREF_ENABLED = "colorOs17ThemeEnabled";
    private static volatile boolean loaded;
    private static volatile boolean enabled;

    private ColorOs17Theme() {}

    public static boolean isEnabled() {
        if (!loaded) {
            synchronized (ColorOs17Theme.class) {
                if (!loaded) {
                    enabled = NimarkoConfig.getPreferences().getBoolean(PREF_ENABLED, false);
                    loaded = true;
                }
            }
        }
        return enabled;
    }

    public static void setEnabled(boolean value) {
        if (value) LinkiGramYouTheme.setEnabled(false);
        enabled = value;
        loaded = true;
        NimarkoConfig.getPreferences().edit().putBoolean(PREF_ENABLED, value).apply();
    }

    public static int resolveColor(int key, int original) {
        if (!isEnabled()) return original;
        final boolean dark = Theme.isCurrentThemeDark();
        final int accent = dark ? 0xFF65AEFF : 0xFF0080FF;
        if (key == Theme.key_windowBackgroundGray || key == Theme.key_dialogBackgroundGray
                || key == Theme.key_actionBarDefault || key == Theme.key_actionBarDefaultArchived)
            return dark ? 0xFF101010 : 0xFFF5F5F5;
        if (key == Theme.key_windowBackgroundWhite || key == Theme.key_dialogBackground
                || key == Theme.key_actionBarDefaultSubmenuBackground)
            return dark ? 0xFF1C1C1E : 0xFFFFFFFF;
        if (key == Theme.key_dialogInputField || key == Theme.key_windowBackgroundWhiteInputField)
            return dark ? 0xFF2C2C2E : 0xFFEEEEF0;
        if (key == Theme.key_actionBarDefaultTitle || key == Theme.key_actionBarDefaultIcon
                || key == Theme.key_actionBarDefaultArchivedTitle || key == Theme.key_actionBarDefaultArchivedIcon
                || key == Theme.key_actionBarDefaultSubmenuItem || key == Theme.key_dialogTextBlack
                || key == Theme.key_chats_name || key == Theme.key_windowBackgroundWhiteBlackText)
            return dark ? 0xFFF2F2F2 : 0xFF171717;
        if (key == Theme.key_actionBarDefaultSubtitle || key == Theme.key_actionBarDefaultSubmenuItemIcon
                || key == Theme.key_actionBarDefaultSearchPlaceholder || key == Theme.key_dialogTextGray
                || key == Theme.key_dialogTextGray2 || key == Theme.key_chats_message
                || key == Theme.key_chats_actionMessage || key == Theme.key_windowBackgroundWhiteGrayText
                || key == Theme.key_windowBackgroundWhiteGrayText2 || key == Theme.key_windowBackgroundWhiteGrayIcon)
            return dark ? 0xFFA6A6AA : 0xFF737377;
        if (key == Theme.key_windowBackgroundWhiteBlueText || key == Theme.key_windowBackgroundWhiteBlueText2
                || key == Theme.key_windowBackgroundWhiteBlueText3 || key == Theme.key_windowBackgroundWhiteBlueText4
                || key == Theme.key_windowBackgroundWhiteBlueText5 || key == Theme.key_windowBackgroundWhiteBlueText6
                || key == Theme.key_windowBackgroundWhiteBlueText7 || key == Theme.key_windowBackgroundWhiteBlueIcon
                || key == Theme.key_windowBackgroundWhiteBlueHeader || key == Theme.key_windowBackgroundWhiteLinkText
                || key == Theme.key_dialogTextBlue || key == Theme.key_dialogTextBlue2
                || key == Theme.key_dialogTextLink || key == Theme.key_dialogButton
                || key == Theme.key_telegram_color || key == Theme.key_telegram_color_text
                || key == Theme.key_dialogInputFieldActivated || key == Theme.key_dialogCheckboxSquareBackground
                || key == Theme.key_dialogRadioBackgroundChecked || key == Theme.key_checkboxSquareBackground
                || key == Theme.key_radioBackgroundChecked || key == Theme.key_chats_unreadCounter
                || key == Theme.key_chats_onlineCircle)
            return accent;
        if (key == Theme.key_actionBarDefaultSelector || key == Theme.key_windowBackgroundWhiteBlueButton)
            return dark ? 0xFF24344A : 0xFFE3EFFF;
        if (key == Theme.key_chats_unreadCounterText || key == Theme.key_checkboxSquareCheck)
            return dark ? 0xFF101010 : 0xFFFFFFFF;
        if (key == Theme.key_chat_outBubble) return dark ? 0xFF234D75 : 0xFFDCEEFF;
        if (key == Theme.key_chat_outBubbleSelected) return dark ? 0xFF2E608E : 0xFFC8E2FF;
        if (key == Theme.key_chat_inBubble) return dark ? 0xFF242426 : 0xFFFFFFFF;
        if (key == Theme.key_chat_inBubbleSelected) return dark ? 0xFF363639 : 0xFFE9E9EC;
        if (key == Theme.key_chat_messageTextIn || key == Theme.key_chat_messageTextOut)
            return dark ? 0xFFF2F2F2 : 0xFF171717;
        return original;
    }

    public static int navigationBarColor(int role, int fallback) {
        final boolean dark = Theme.isCurrentThemeDark();
        switch (role) {
            case LinkiGramYouTheme.NAV_COLOR_SURFACE: return dark ? 0xFF28282A : 0xFFF0F0F0;
            case LinkiGramYouTheme.NAV_COLOR_INDICATOR: return dark ? 0xFF414144 : 0xFFFFFFFF;
            case LinkiGramYouTheme.NAV_COLOR_SELECTED: return dark ? 0xFFF2F2F2 : 0xFF171717;
            case LinkiGramYouTheme.NAV_COLOR_UNSELECTED: return dark ? 0xFFB8B8BA : 0xFF626266;
            default: return fallback;
        }
    }
}
