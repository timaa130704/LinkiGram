package app.nimarkogram.messenger.appearance;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.util.SparseIntArray;

import com.google.android.material.color.utilities.DynamicScheme;
import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.SchemeTonalSpot;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

/** Shared semantic roles and geometry for both complete client appearances. */
public final class ClientTheme {
    public static final int BACKGROUND = 1, SURFACE = 2, SURFACE_HIGH = 3,
            ON_SURFACE = 4, SECONDARY = 5, PRIMARY = 6, ON_PRIMARY = 7,
            CONTAINER = 8, ON_CONTAINER = 9, OUTLINE = 10, TRACK = 11,
            SELECTOR = 12, ERROR = 13, INVERSE = 14, ON_INVERSE = 15,
            SURFACE_SELECTED = 16, CONTAINER_SELECTED = 17, DIVIDER = 18;
    private static final SparseIntArray ROLES = new SparseIntArray();
    private static volatile Palette cachedPalette;

    private ClientTheme() { }

    public static boolean isEnabled() {
        return ColorOs17Theme.isEnabled() || LinkiGramYouTheme.isEnabled();
    }

    public static int resolveColor(int key, int original) {
        if (!isEnabled()) return original;
        if (key == Theme.key_chat_outBubbleGradient1 || key == Theme.key_chat_outBubbleGradient2
                || key == Theme.key_chat_outBubbleGradient3 || key == Theme.key_chat_outBubbleGradientAnimated)
            return 0;
        if (LinkiGramYouTheme.isEnabled()) {
            if (key == Theme.key_actionBarTabActiveText) return color(ON_CONTAINER);
            if (key == Theme.key_actionBarTabLine) return color(CONTAINER);
        }
        int role = ROLES.get(key, 0);
        return role == 0 ? original : color(role);
    }

    public static int color(int role) {
        final boolean dark = Theme.isCurrentThemeDark();
        if (ColorOs17Theme.isEnabled()) {
            switch (role) {
                case BACKGROUND: return dark ? 0xFF101010 : 0xFFF5F5F5;
                case SURFACE: return dark ? 0xFF1C1C1E : 0xFFFFFFFF;
                case SURFACE_HIGH: return dark ? 0xFF28282A : 0xFFF0F0F0;
                case ON_SURFACE: return dark ? 0xFFF2F2F2 : 0xFF171717;
                case SECONDARY: return dark ? 0xFFA6A6AA : 0xFF737377;
                case PRIMARY: return dark ? 0xFF65AEFF : 0xFF0066D6;
                case ON_PRIMARY: return dark ? 0xFF101010 : 0xFFFFFFFF;
                case CONTAINER: return dark ? 0xFF203E60 : 0xFFDCEEFF;
                case ON_CONTAINER: return dark ? 0xFFDCEEFF : 0xFF003F80;
                case OUTLINE: return dark ? 0xFF68686C : 0xFF77777C;
                case TRACK: return dark ? 0xFF424246 : 0xFFE1E1E5;
                case SELECTOR: return dark ? 0x2465AEFF : 0x180066D6;
                case ERROR: return dark ? 0xFFFFB4AB : 0xFFBA1A1A;
                case INVERSE: return dark ? 0xFFE5E5E7 : 0xFF303033;
                case ON_INVERSE: return dark ? 0xFF222224 : 0xFFF5F5F5;
                case SURFACE_SELECTED: return dark ? 0xFF363639 : 0xFFE9E9EC;
                case CONTAINER_SELECTED: return dark ? 0xFF2B4C70 : 0xFFC8E2FF;
                case DIVIDER: return dark ? 0xFF363639 : 0xFFE6E6E9;
                default: return 0;
            }
        }
        int[] colors = palette(dark).colors;
        return colors[role > 0 && role < colors.length ? role : ON_SURFACE];
    }

    private static Palette palette(boolean dark) {
        int seed = 0xFF6750A4;
        Context context = ApplicationLoader.applicationContext;
        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { seed = context.getColor(android.R.color.system_accent1_500); }
            catch (RuntimeException ignored) { }
        }
        Palette result = cachedPalette;
        if (result == null || result.dark != dark || result.seed != seed) {
            synchronized (ClientTheme.class) {
                result = cachedPalette;
                if (result == null || result.dark != dark || result.seed != seed) {
                    cachedPalette = result = new Palette(seed, dark);
                }
            }
        }
        return result;
    }

    private static final class Palette {
        final int seed;
        final boolean dark;
        final int[] colors;
        Palette(int seed, boolean dark) {
            this.seed = seed;
            this.dark = dark;
            DynamicScheme s = new SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0);
            int primary = s.getPrimary();
            colors = new int[]{0, s.getSurface(), s.getSurfaceContainerLow(), s.getSurfaceContainerHigh(),
                    s.getOnSurface(), s.getOnSurfaceVariant(), primary, s.getOnPrimary(),
                    s.getPrimaryContainer(), s.getOnPrimaryContainer(), s.getOutline(),
                    s.getSurfaceContainerHighest(), Theme.multAlpha(primary, 0.12f), s.getError(),
                    s.getInverseSurface(), s.getInverseOnSurface(), s.getSurfaceContainerHighest(),
                    s.getSecondaryContainer(), s.getOutlineVariant()};
        }
    }

    public static int popupRadius() { return ColorOs17Theme.isEnabled() ? 24 : 16; }
    public static int dialogRadius() { return ColorOs17Theme.isEnabled() ? 24 : 28; }
    public static int sectionRadius() { return ColorOs17Theme.isEnabled() ? 20 : 24; }
    public static int bubbleRadius() { return ColorOs17Theme.isEnabled() ? 20 : 22; }
    public static int menuHeight() { return ColorOs17Theme.isEnabled() ? 52 : 48; }

    /** Retain nine-patch padding; a replacement color filter replaces the fill tint. */
    public static Drawable surfaceDrawable(Drawable original, int color, boolean topOnly) {
        return roundedSurface(original, color, topOnly, dialogRadius());
    }

    public static Drawable popupDrawable(Drawable original, int color) {
        return roundedSurface(original, color, false, popupRadius());
    }

    private static Drawable roundedSurface(Drawable original, int color, boolean topOnly, int radius) {
        if (!isEnabled() || original == null) return original;
        Rect padding = new Rect();
        original.getPadding(padding);
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0xFFFFFFFF);
        float r = AndroidUtilities.dp(topOnly ? 28 : radius);
        if (topOnly) panel.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        else panel.setCornerRadius(r);
        Drawable result = new InsetDrawable(panel, padding.left, padding.top, padding.right, padding.bottom);
        result.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        return result;
    }

    static {
        add(BACKGROUND, Theme.key_windowBackgroundGray, Theme.key_dialogBackgroundGray,
                Theme.key_actionBarDefault, Theme.key_actionBarDefaultArchived, Theme.key_chat_wallpaper);
        add(SURFACE, Theme.key_windowBackgroundWhite, Theme.key_dialogBackground,
                Theme.key_chat_messagePanelBackground, Theme.key_chat_emojiPanelBackground,
                Theme.key_player_background, Theme.key_chats_menuBackground, Theme.key_chats_menuTopBackground,
                Theme.key_chats_menuTopBackgroundCats, Theme.key_chat_inBubble, Theme.key_chat_inBubbleSelected);
        add(SURFACE_HIGH, Theme.key_actionBarDefaultSubmenuBackground, Theme.key_dialogSearchBackground,
                Theme.key_dialogInputField, Theme.key_windowBackgroundWhiteInputField,
                Theme.key_chat_emojiPanelStickerPackSelector, Theme.key_glass_targetMainTabs,
                Theme.key_glass_targetMainTopPanel);
        add(ON_SURFACE, Theme.key_actionBarDefaultIcon, Theme.key_actionBarDefaultTitle,
                Theme.key_actionBarDefaultArchivedIcon, Theme.key_actionBarDefaultArchivedTitle,
                Theme.key_actionBarDefaultSubmenuItem, Theme.key_dialogTextBlack, Theme.key_dialogSearchText,
                Theme.key_windowBackgroundWhiteBlackText, Theme.key_chats_name, Theme.key_chat_messageTextIn,
                Theme.key_chat_messagePanelText, Theme.key_player_actionBarTitle, Theme.key_player_actionBarItems,
                Theme.key_player_button, Theme.key_profile_title, Theme.key_chats_menuItemText,
                Theme.key_chats_menuName, Theme.key_chat_inAudioTitleText, Theme.key_chat_inFileNameText,
                Theme.key_chat_inContactNameText, Theme.key_chat_inReplyMessageText,
                Theme.key_chat_emojiPanelTrendingTitle, Theme.key_chat_emojiPanelStickerSetName,
                Theme.key_chat_attachPermissionText, Theme.key_glass_defaultIcon,
                Theme.key_glass_defaultText, Theme.key_glass_tabUnselected);
        add(ON_PRIMARY, Theme.key_glass_tabSelectedText);
        add(SECONDARY, Theme.key_actionBarDefaultSubtitle, Theme.key_actionBarDefaultSearchPlaceholder,
                Theme.key_actionBarDefaultArchivedSearchPlaceholder, Theme.key_actionBarDefaultSubmenuItemIcon,
                Theme.key_windowBackgroundWhiteGrayText, Theme.key_windowBackgroundWhiteGrayText2,
                Theme.key_windowBackgroundWhiteGrayText3, Theme.key_windowBackgroundWhiteGrayText4,
                Theme.key_windowBackgroundWhiteGrayText5, Theme.key_windowBackgroundWhiteGrayIcon,
                Theme.key_dialogTextGray, Theme.key_dialogTextGray2, Theme.key_dialogTextGray3,
                Theme.key_dialogTextGray4, Theme.key_dialogTextHint, Theme.key_dialogIcon,
                Theme.key_dialogSearchHint, Theme.key_dialogSearchIcon, Theme.key_dialogEmptyText,
                Theme.key_chats_message, Theme.key_chats_actionMessage, Theme.key_chats_date,
                Theme.key_chats_pinnedIcon, Theme.key_chats_menuItemIcon, Theme.key_chats_menuPhone,
                Theme.key_chats_menuPhoneCats, Theme.key_player_actionBarSubtitle, Theme.key_player_time,
                Theme.key_profile_status, Theme.key_profile_tabText, Theme.key_chat_messagePanelHint,
                Theme.key_chat_messagePanelIcons, Theme.key_chat_emojiPanelIcon,
                Theme.key_chat_emojiPanelEmptyText, Theme.key_chat_emojiPanelBackspace,
                Theme.key_chat_emojiPanelTrendingDescription, Theme.key_chat_inTimeText,
                Theme.key_chat_inTimeSelectedText, Theme.key_chat_inAudioDurationText,
                Theme.key_chat_inAudioPerformerText, Theme.key_chat_inFileInfoText,
                Theme.key_chat_inContactPhoneText, Theme.key_actionBarTabUnactiveText,
                Theme.key_avatar_subtitleInProfileBlue);
        add(PRIMARY, Theme.key_windowBackgroundWhiteBlueText, Theme.key_windowBackgroundWhiteBlueText2,
                Theme.key_windowBackgroundWhiteBlueText3, Theme.key_windowBackgroundWhiteBlueText4,
                Theme.key_windowBackgroundWhiteBlueText5, Theme.key_windowBackgroundWhiteBlueText6,
                Theme.key_windowBackgroundWhiteBlueText7, Theme.key_windowBackgroundWhiteBlueIcon,
                Theme.key_windowBackgroundWhiteBlueHeader, Theme.key_windowBackgroundWhiteLinkText,
                Theme.key_dialogTextBlue, Theme.key_dialogTextBlue2, Theme.key_dialogTextBlue4,
                Theme.key_dialogTextLink, Theme.key_dialogButton, Theme.key_dialogInputFieldActivated,
                Theme.key_dialogCheckboxSquareBackground, Theme.key_checkboxSquareBackground,
                Theme.key_dialogRadioBackgroundChecked, Theme.key_radioBackgroundChecked,
                Theme.key_dialogRoundCheckBox, Theme.key_dialogLineProgress, Theme.key_dialog_inlineProgress,
                Theme.key_telegram_color, Theme.key_telegram_color_text, Theme.key_chats_unreadCounter,
                Theme.key_chats_onlineCircle, Theme.key_chats_actionBackground, Theme.key_profile_actionBackground,
                Theme.key_dialogFloatingButton, Theme.key_dialogFloatingButtonPressed,
                Theme.key_switchTrackChecked, Theme.key_switchTrackBlueChecked, Theme.key_switch2TrackChecked,
                Theme.key_chat_messagePanelSend, Theme.key_chat_messagePanelCursor,
                Theme.key_chat_messagePanelVoiceBackground, Theme.key_player_progress, Theme.key_player_buttonActive,
                Theme.key_profile_tabSelectedText, Theme.key_profile_tabSelectedLine,
                Theme.key_actionBarTabActiveText, Theme.key_actionBarTabLine, Theme.key_actionBarDefaultSearch,
                Theme.key_actionBarDefaultArchivedSearch, Theme.key_chat_emojiPanelIconSelected,
                Theme.key_chat_emojiPanelStickerPackSelectorLine, Theme.key_featuredStickers_addButton,
                Theme.key_chat_inReplyLine, Theme.key_chat_inReplyNameText, Theme.key_chat_inForwardedNameText,
                Theme.key_chat_inViaBotNameText, Theme.key_chat_inSiteNameText, Theme.key_chat_inAudioProgress,
                Theme.key_chat_inAudioSeekbarFill, Theme.key_chat_inVoiceSeekbarFill, Theme.key_chat_inFileProgress,
                Theme.key_chat_TextSelectionCursor, Theme.key_chat_inAudioSelectedProgress,
                Theme.key_groupcreate_cursor, Theme.key_groupcreate_sectionText,
                Theme.key_picker_enabledButton, Theme.key_picker_badge, Theme.key_location_sendLocationText,
                Theme.key_location_actionActiveIcon, Theme.key_dialog_liveLocationProgress);
        add(ON_PRIMARY, Theme.key_chats_unreadCounterText, Theme.key_chats_actionIcon,
                Theme.key_profile_actionIcon, Theme.key_dialogFloatingIcon, Theme.key_checkboxSquareCheck,
                Theme.key_dialogCheckboxSquareCheck, Theme.key_dialogRoundCheckBoxCheck, Theme.key_checkboxCheck,
                Theme.key_switchTrackBlueThumbChecked, Theme.key_featuredStickers_buttonText,
                Theme.key_picker_badgeText);
        add(CONTAINER, Theme.key_chat_outBubble, Theme.key_chat_outBubbleSelected,
                Theme.key_windowBackgroundWhiteBlueButton, Theme.key_chat_inReactionButtonBackground,
                Theme.key_groupcreate_spanBackground);
        add(ON_CONTAINER, Theme.key_chat_messageTextOut, Theme.key_chat_outTimeText,
                Theme.key_chat_outTimeSelectedText, Theme.key_chat_outAudioTitleText,
                Theme.key_chat_outAudioPerformerText, Theme.key_chat_outAudioDurationText,
                Theme.key_chat_outFileNameText, Theme.key_chat_outFileInfoText,
                Theme.key_chat_outContactNameText, Theme.key_chat_outContactPhoneText,
                Theme.key_chat_outReplyMessageText, Theme.key_chat_outReplyNameText,
                Theme.key_chat_outForwardedNameText, Theme.key_chat_outViaBotNameText,
                Theme.key_chat_outReplyLine, Theme.key_chat_outSiteNameText,
                Theme.key_chat_outAudioProgress, Theme.key_chat_outAudioSeekbarFill,
                Theme.key_chat_outVoiceSeekbarFill, Theme.key_chat_outFileProgress,
                Theme.key_chat_outSentCheckRead, Theme.key_chat_outSentCheckReadSelected,
                Theme.key_chat_outSentCheck, Theme.key_chat_outSentCheckSelected,
                Theme.key_chat_outTextSelectionCursor, Theme.key_chat_outReactionButtonText,
                Theme.key_chat_inReactionButtonText, Theme.key_groupcreate_spanText);
        add(TRACK, Theme.key_switchTrack, Theme.key_switchTrackBlue, Theme.key_switch2Track,
                Theme.key_player_progressBackground, Theme.key_player_progressCachedBackground,
                Theme.key_chats_unreadCounterMuted, Theme.key_dialogLineProgressBackground,
                Theme.key_dialog_inlineProgressBackground, Theme.key_dialogCheckboxSquareDisabled,
                Theme.key_chat_inAudioSeekbar, Theme.key_chat_outAudioSeekbar,
                Theme.key_chat_inVoiceSeekbar, Theme.key_chat_outVoiceSeekbar);
        add(OUTLINE, Theme.key_divider, Theme.key_dialogGrayLine, Theme.key_actionBarDefaultSubmenuSeparator,
                Theme.key_radioBackground, Theme.key_dialogRadioBackground,
                Theme.key_dialogCheckboxSquareUnchecked, Theme.key_checkboxSquareUnchecked);
        add(SELECTOR, Theme.key_listSelector, Theme.key_actionBarDefaultSelector,
                Theme.key_dialogButtonSelector, Theme.key_dialogLinkSelection, Theme.key_profile_tabSelector,
                Theme.key_player_actionBarSelector, Theme.key_actionBarTabSelector,
                Theme.key_chat_selectedBackground, Theme.key_chat_linkSelectBackground,
                Theme.key_chat_outLinkSelectBackground, Theme.key_chat_inTextSelectionHighlight,
                Theme.key_chat_outTextSelectionHighlight, Theme.key_switchTrackBlueSelector,
                Theme.key_switchTrackBlueSelectorChecked);
        add(ERROR, Theme.key_text_RedRegular, Theme.key_text_RedBold, Theme.key_fill_RedNormal);
        add(INVERSE, Theme.key_undo_background);
        add(ON_INVERSE, Theme.key_undo_infoColor, Theme.key_undo_cancelColor);
        add(SURFACE, Theme.key_avatar_backgroundActionBarBlue, Theme.key_chat_recordedVoiceBackground);
        add(SURFACE_HIGH, Theme.key_chat_emojiSearchBackground, Theme.key_chat_botKeyboardButtonBackground,
                Theme.key_sharedMedia_photoPlaceholder, Theme.key_chat_recordedVoiceDarkerBackground);
        add(ON_SURFACE, Theme.key_avatar_actionBarIconBlue, Theme.key_chat_searchPanelText,
                Theme.key_chat_recordTime, Theme.key_chat_botKeyboardButtonText);
        add(SECONDARY, Theme.key_chat_replyPanelIcons, Theme.key_chat_replyPanelClose,
                Theme.key_chat_emojiSearchIcon, Theme.key_chat_emojiBottomPanelIcon,
                Theme.key_chat_emojiPanelStickerSetNameIcon, Theme.key_groupcreate_hintText,
                Theme.key_chats_sentClock);
        add(PRIMARY, Theme.key_chat_replyPanelName, Theme.key_chat_replyPanelLine,
                Theme.key_chat_searchPanelIcons, Theme.key_chat_botButtonText,
                Theme.key_chat_botSwitchToInlineText, Theme.key_chat_emojiPanelStickerSetNameHighlight,
                Theme.key_chat_recordedVoicePlayPause, Theme.key_chat_recordedVoiceProgress,
                Theme.key_glass_tabSelected,
                Theme.key_chat_recordVoiceCancel, Theme.key_profile_creatorIcon,
                Theme.key_profile_verifiedBackground, Theme.key_chats_verifiedBackground,
                Theme.key_chats_sentCheck, Theme.key_chats_sentReadCheck,
                Theme.key_sharedMedia_startStopLoadIcon, Theme.key_featuredStickers_addButton2,
                Theme.key_featuredStickers_addButtonPressed, Theme.key_featuredStickers_unread,
                Theme.key_chats_tabUnreadActiveBackground);
        add(ON_PRIMARY, Theme.key_profile_verifiedCheck, Theme.key_chats_verifiedCheck,
                Theme.key_chat_inReactionButtonTextSelected, Theme.key_chat_outReactionButtonTextSelected);
        add(CONTAINER, Theme.key_chat_outReactionButtonBackground, Theme.key_sharedMedia_linkPlaceholder);
        add(ON_CONTAINER, Theme.key_sharedMedia_linkPlaceholderText);
        add(TRACK, Theme.key_chat_recordedVoiceProgressInner, Theme.key_chats_tabUnreadUnactiveBackground);
        add(SELECTOR, Theme.key_chat_botKeyboardButtonBackgroundPressed,
                Theme.key_chats_actionPressedBackground, Theme.key_profile_actionPressedBackground,
                Theme.key_chats_tabletSelectedOverlay);
        add(SURFACE_SELECTED, Theme.key_chat_inBubbleSelected);
        add(CONTAINER_SELECTED, Theme.key_chat_outBubbleSelected);
        add(DIVIDER, Theme.key_divider, Theme.key_dialogGrayLine, Theme.key_actionBarDefaultSubmenuSeparator);
        add(SURFACE_HIGH, Theme.key_chats_archivePinBackground, Theme.key_chats_archivePullDownBackground, Theme.key_chat_goDownButton);
        add(SURFACE, Theme.key_chat_topPanelBackground);
        add(BACKGROUND, Theme.key_actionBarBrowser);
        add(CONTAINER, Theme.key_dialogTopBackground, Theme.key_dialogReactionMentionBackground, Theme.key_chats_archiveBackground,
                Theme.key_chats_archivePullDownBackgroundActive, Theme.key_actionBarActionModeDefault, Theme.key_actionBarActionModeDefaultTop,
                Theme.key_actionBarActionModeReaction);
        add(ON_SURFACE, Theme.key_chats_nameArchived, Theme.key_chat_inReplyMediaMessageText, Theme.key_chat_inReplyMediaMessageSelectedText);
        add(SECONDARY, Theme.key_dialogEmptyImage, Theme.key_dialogGiftsTabText, Theme.key_windowBackgroundWhiteGrayText6,
                Theme.key_windowBackgroundWhiteGrayText7, Theme.key_windowBackgroundWhiteGrayText8, Theme.key_windowBackgroundWhiteHintText,
                Theme.key_chats_messageArchived, Theme.key_chats_message_threeLines, Theme.key_chats_date_bold,
                Theme.key_chats_muteIcon, Theme.key_chat_inContactPhoneSelectedText, Theme.key_chat_inAudioPerformerSelectedText,
                Theme.key_chat_inAudioDurationSelectedText, Theme.key_chat_inFileInfoSelectedText, Theme.key_chat_inVenueInfoText,
                Theme.key_chat_inVenueInfoSelectedText, Theme.key_chat_topPanelClose, Theme.key_chat_topPanelMessage);
        add(PRIMARY, Theme.key_dialogScrollGlow, Theme.key_windowBackgroundChecked, Theme.key_windowBackgroundWhiteInputFieldActivated,
                Theme.key_windowBackgroundWhiteValueText, Theme.key_chats_nameMessage, Theme.key_chats_nameMessageArchived,
                Theme.key_chats_nameMessage_threeLines, Theme.key_chats_nameMessageArchived_threeLines, Theme.key_chats_attachMessage,
                Theme.key_chats_menuItemCheck, Theme.key_actionBarActionModeReactionDot, Theme.key_chat_inPreviewInstantText,
                Theme.key_chat_inPsaNameText, Theme.key_chat_inAdminText, Theme.key_chat_inAdminSelectedText,
                Theme.key_chat_inFileProgressSelected, Theme.key_chat_messageLinkIn, Theme.key_chat_messageLinkOut,
                Theme.key_chat_topPanelLine, Theme.key_chat_topPanelTitle, Theme.key_chat_goDownButtonCounterBackground);
        add(ON_PRIMARY, Theme.key_windowBackgroundCheckText, Theme.key_chats_mentionIcon, Theme.key_chat_goDownButtonCounter);
        add(ON_CONTAINER, Theme.key_chats_archiveIcon, Theme.key_chats_archiveText, Theme.key_actionBarActionModeDefaultIcon,
                Theme.key_actionBarActionModeReactionText, Theme.key_chat_outPreviewInstantText, Theme.key_chat_outReplyLine2,
                Theme.key_chat_outReplyMediaMessageText, Theme.key_chat_outReplyMediaMessageSelectedText, Theme.key_chat_outContactPhoneSelectedText,
                Theme.key_chat_outAudioPerformerSelectedText, Theme.key_chat_outAdminText, Theme.key_chat_outAdminSelectedText,
                Theme.key_chat_outAudioSelectedProgress, Theme.key_chat_outAudioDurationSelectedText, Theme.key_chat_outFileProgressSelected,
                Theme.key_chat_outFileInfoSelectedText, Theme.key_chat_outVenueInfoText, Theme.key_chat_outVenueInfoSelectedText,
                Theme.key_chat_outPsaNameText);
        add(TRACK, Theme.key_windowBackgroundUnchecked, Theme.key_chat_outAudioCacheSeekbar, Theme.key_chat_outAudioSeekbarSelected,
                Theme.key_chat_outVoiceSeekbarSelected, Theme.key_chat_inAudioCacheSeekbar, Theme.key_chat_inAudioSeekbarSelected,
                Theme.key_chat_inVoiceSeekbarSelected);
        add(SELECTOR, Theme.key_windowBackgroundWhiteLinkSelection, Theme.key_chats_pinnedOverlay, Theme.key_actionBarWhiteSelector,
                Theme.key_actionBarActionModeDefaultSelector, Theme.key_actionBarDefaultArchivedSelector);
        add(ERROR, Theme.key_dialogSwipeRemove, Theme.key_chats_sentError);
    }

    private static void add(int role, int... keys) {
        for (int key : keys) ROLES.put(key, role);
    }
}
