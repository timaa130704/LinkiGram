package app.nimarkogram.messenger.appearance;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.util.SparseIntArray;
import app.nimarkogram.messenger.NimarkoConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** ColorOS-inspired palette for LinkiGram's native Android views. */
public final class ColorOs17Theme {
    private static final String PREF_ENABLED = "colorOs17ThemeEnabled";
    private static final String PREF_PREVIOUS_YOU = "colorOs17PreviousYouTheme";
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
        boolean previous = isEnabled();
        if (previous == value) return;
        boolean restoreYou = !value && NimarkoConfig.getPreferences().getBoolean(PREF_PREVIOUS_YOU, false);
        if (value) {
            NimarkoConfig.getPreferences().edit().putBoolean(PREF_PREVIOUS_YOU,
                    LinkiGramYouTheme.isEnabled()).apply();
        }
        enabled = value;
        loaded = true;
        NimarkoConfig.getPreferences().edit().putBoolean(PREF_ENABLED, value).apply();
        if (value) LinkiGramYouTheme.setEnabled(false);
        else if (restoreYou) LinkiGramYouTheme.setEnabled(true);
    }

    public static int resolveColor(int key, int original) {
        if (!isEnabled()) return original;
        final boolean dark = Theme.isCurrentThemeDark();
        final int accent = dark ? 0xFF65AEFF : 0xFF0080FF;
        int role = ColorRoles.KEYS.get(key, 0);
        if (role != 0) {
            switch (role) {
                case 1: return dark ? 0xFF1C1C1E : 0xFFFFFFFF;
                case 2: return dark ? 0xFFF2F2F2 : 0xFF171717;
                case 3: return dark ? 0xFFA6A6AA : 0xFF737377;
                case 4: return accent;
                case 5: return dark ? 0xFF363639 : 0xFFE6E6E9;
                case 6: return 0xFFFFFFFF;
                case 7: return dark ? 0xFF101010 : 0xFFF5F5F5;
                case 8: return dark ? 0x3365AEFF : 0x220080FF;
                default: break;
            }
        }
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

    public static int accentColor() {
        return Theme.isCurrentThemeDark() ? 0xFF65AEFF : 0xFF0080FF;
    }

    public static int controlTrackColor() {
        return Theme.isCurrentThemeDark() ? 0xFF424246 : 0xFFE1E1E5;
    }

    /** Keep nine-patch content insets while replacing only the visible surface. */
    public static Drawable surfaceDrawable(Drawable original, int color, boolean topOnly) {
        if (!isEnabled() || original == null) return original;
        Rect padding = new Rect();
        original.getPadding(padding);
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(color);
        float radius = AndroidUtilities.dp(topOnly ? 28 : 24);
        if (topOnly) panel.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        else panel.setCornerRadius(radius);
        panel.setStroke(AndroidUtilities.dp(1), Theme.isCurrentThemeDark() ? 0x14FFFFFF : 0x08000000);
        return new InsetDrawable(panel, padding.left, padding.top, padding.right, padding.bottom);
    }

    private static final class ColorRoles {
        static final SparseIntArray KEYS = new SparseIntArray();
        static {
            add(1, Theme.key_chat_messagePanelBackground, Theme.key_chat_emojiPanelBackground,
                    Theme.key_player_background, Theme.key_chats_menuBackground,
                    Theme.key_chats_menuTopBackground, Theme.key_chats_menuTopBackgroundCats,
                    Theme.key_avatar_backgroundActionBarBlue);
            add(2, Theme.key_chat_messagePanelText, Theme.key_player_actionBarTitle,
                    Theme.key_player_actionBarItems, Theme.key_player_button,
                    Theme.key_profile_title, Theme.key_chat_inAudioTitleText,
                    Theme.key_chat_outAudioTitleText, Theme.key_chat_inReplyMessageText,
                    Theme.key_chat_outReplyMessageText, Theme.key_chat_inContactNameText,
                    Theme.key_chat_outContactNameText, Theme.key_dialogSearchText,
                    Theme.key_chats_menuItemText, Theme.key_chats_menuName,
                    Theme.key_avatar_actionBarIconBlue, Theme.key_chat_emojiPanelTrendingTitle,
                    Theme.key_chat_emojiPanelStickerSetName, Theme.key_chat_attachPermissionText);
            add(3, Theme.key_chat_messagePanelHint, Theme.key_chat_messagePanelIcons,
                    Theme.key_player_actionBarSubtitle, Theme.key_player_time,
                    Theme.key_profile_status, Theme.key_profile_tabText,
                    Theme.key_chat_inTimeText, Theme.key_chat_outTimeText,
                    Theme.key_chat_inTimeSelectedText, Theme.key_chat_outTimeSelectedText,
                    Theme.key_chat_inAudioDurationText, Theme.key_chat_outAudioDurationText,
                    Theme.key_chat_inAudioPerformerText, Theme.key_chat_outAudioPerformerText,
                    Theme.key_chats_date, Theme.key_chats_pinnedIcon,
                    Theme.key_chats_menuItemIcon, Theme.key_chats_menuPhone,
                    Theme.key_chats_menuPhoneCats, Theme.key_avatar_subtitleInProfileBlue,
                    Theme.key_chat_emojiPanelIcon, Theme.key_chat_emojiPanelEmptyText,
                    Theme.key_chat_emojiPanelBackspace, Theme.key_chat_emojiPanelTrendingDescription,
                    Theme.key_dialogSearchHint, Theme.key_dialogSearchIcon,
                    Theme.key_actionBarTabUnactiveText);
            add(4, Theme.key_chat_messagePanelSend, Theme.key_chat_messagePanelCursor,
                    Theme.key_chat_messagePanelVoiceBackground, Theme.key_player_progress,
                    Theme.key_player_buttonActive, Theme.key_profile_tabSelectedText,
                    Theme.key_profile_tabSelectedLine, Theme.key_profile_actionBackground,
                    Theme.key_chats_actionBackground, Theme.key_switchTrackChecked,
                    Theme.key_switchTrackBlueChecked, Theme.key_switch2TrackChecked,
                    Theme.key_chat_inReplyLine, Theme.key_chat_outReplyLine,
                    Theme.key_chat_inReplyNameText, Theme.key_chat_outReplyNameText,
                    Theme.key_chat_inViaBotNameText, Theme.key_chat_outViaBotNameText,
                    Theme.key_chat_inForwardedNameText, Theme.key_chat_outForwardedNameText,
                    Theme.key_chat_inAudioProgress, Theme.key_chat_outAudioProgress,
                    Theme.key_chat_inAudioSeekbarFill, Theme.key_chat_outAudioSeekbarFill,
                    Theme.key_chat_inVoiceSeekbarFill, Theme.key_chat_outVoiceSeekbarFill,
                    Theme.key_chat_inFileProgress, Theme.key_chat_outFileProgress,
                    Theme.key_chat_outSentCheckRead, Theme.key_chat_outSentCheckReadSelected,
                    Theme.key_chat_outSentCheck, Theme.key_chat_outSentCheckSelected,
                    Theme.key_dialogTextBlue4, Theme.key_actionBarDefaultSearch,
                    Theme.key_featuredStickers_addButton, Theme.key_dialogFloatingButton,
                    Theme.key_chats_menuItemCheck, Theme.key_chat_emojiPanelIconSelected,
                    Theme.key_chat_emojiPanelStickerPackSelectorLine,
                    Theme.key_actionBarTabActiveText, Theme.key_actionBarTabLine);
            add(5, Theme.key_player_progressBackground, Theme.key_player_progressCachedBackground,
                    Theme.key_switchTrack, Theme.key_switchTrackBlue, Theme.key_switch2Track,
                    Theme.key_chats_unreadCounterMuted, Theme.key_chat_inAudioSeekbar,
                    Theme.key_chat_outAudioSeekbar, Theme.key_chat_inVoiceSeekbar,
                    Theme.key_chat_outVoiceSeekbar, Theme.key_dialogSearchBackground);
            add(6, Theme.key_chats_actionIcon, Theme.key_profile_actionIcon,
                    Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked,
                    Theme.key_featuredStickers_buttonText, Theme.key_dialogFloatingIcon);
            add(7, Theme.key_chat_wallpaper);
            add(8, Theme.key_player_actionBarSelector, Theme.key_profile_tabSelector,
                    Theme.key_chat_selectedBackground, Theme.key_dialogLinkSelection,
                    Theme.key_listSelector, Theme.key_switchTrackBlueSelector,
                    Theme.key_switchTrackBlueSelectorChecked, Theme.key_actionBarTabSelector,
                    Theme.key_chat_emojiPanelStickerPackSelector);
        }
        private static void add(int role, int... keys) {
            for (int key : keys) KEYS.put(key, role);
        }
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
