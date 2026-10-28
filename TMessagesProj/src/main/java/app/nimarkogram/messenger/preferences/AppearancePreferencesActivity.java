/**
 * This file is part of LinkiGram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * LinkiGram modifications:
 * Copyright Ettacent, 2026.
 *
 * Portions derived from Cherrygram:
 * Copyright github.com/arsLan4k1390, 2022-2026.
 */

package app.nimarkogram.messenger.preferences;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

import app.nimarkogram.messenger.NimarkoConfig;
import app.nimarkogram.messenger.appearance.AppearanceConfig;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

public class AppearancePreferencesActivity extends NimarkoUniversalPreferencesActivity {

    private final int centerTitleRow = 1;
    private final int hideSearchBar = 2;
    private final int snowflakesRow = 3;

    private static final int SETTINGS_ICON_COLORS = 21001;
    private static final int YOU_CUSTOMIZE = 21002;
    private final int iconPackRow = 4;
    private final int oneUISwitchesRow = 5;
    private final int disableDividersRow = 6;
    private final int materialCardsRow = 24;
    private final int sectionRadiusRow = 25;
    private final int glareOnElementsRow = 10;
    private final int forumAvatarsRow = 12;
    private final int forceBlurRow = 13;
    private final int hideStatusRow = 14;
    private final int customTitleRow = 15;
    private final int mediaGlowRow = 16;
    private final int disableSendHintsRow = 17;
    private final int hideBubbleTailRow = 18;
    private final int onlineIndicatorRow = 19;
    private final int hideStickerTimeRow = 20;
    private final int iosStyleComposerRow = 21;

    private final int foldersRow = 7;
    private final int bottomTabsRow = 8;
    private final int messagesAndProfilesRow = 9;
    private final int appBackgroundRow = 22;
    private final int classicUiRow = 23;
    private final int navigationDrawerRow = 26;
    private final int immersiveDrawerRow = 27;
    private final int liquidGlassRow = 28;
    private final int linkiYouRow = 29;
    private final int colorOs17Row = 30;
    @Override protected void prepareSettingsSearch() {
        materialExpanded = true;
        iosExpanded = true;
    }

    private boolean materialExpanded;
    private boolean iosExpanded;
    private static final int MATERIAL_GROUP = 100;
    private static final int IOS_GROUP = 101;
    private static final int MATERIAL_ALL = 102;
    private static final int MATERIAL_HEADERS = 103;
    private static final int STYLE_BASE = 110;
    private static final int[] MATERIAL_LABELS = {R.string.NM_Style_Loaders, R.string.NM_Style_Sliders,
            R.string.NM_Style_Switches, R.string.NM_Style_ChatHeader, R.string.NM_Style_Navigation,
            R.string.NM_Style_ListItems, R.string.OEAppearanceMd3Player, R.string.OEAppearanceMd3MiniPlayer};
    private static final int[] IOS_LABELS = {R.string.NM_Style_Navigation, R.string.NM_Style_ChatHeader};

    private static String styleCount(String[] keys) {
        int n = 0;
        for (String key : keys) {
            if (AppearanceConfig.enabled(key)) n++;
        }
        return String.valueOf(n);
    }

    private app.nimarkogram.messenger.preferences.components.AvatarCornersPreviewCell avatarCornersCell;
    private app.nimarkogram.messenger.preferences.components.StickerSizeCell stickerSizeCell;

    private String stickerTimeLabel() {
        return getString(NimarkoConfig.stickerTimeMode == 2 ? R.string.Hide
                : NimarkoConfig.stickerTimeMode == 1 ? R.string.LinkiStickerTimeSide : R.string.LinkiStickerTimeDefault);
    }

    @Override
    protected CharSequence getTitle() {
         
        return getString(R.string.AP_Header_Appearance);
    }

    @Override
    public View createView(Context context) {
        setMD3(true);
        return super.createView(context);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean linkiYou = app.nimarkogram.messenger.appearance.LinkiGramYouTheme.isEnabled()
                || app.nimarkogram.messenger.appearance.ColorOs17Theme.isEnabled();
        items.add(UItem.asHeader(getString(R.string.AP_Header_Appearance)));
        items.add(SettingsHelper.asSwitchCG(linkiYouRow, getString(R.string.NM_LinkiGramYou),
                        getString(R.string.LinkiClientMaterialDescription))
                .setChecked(app.nimarkogram.messenger.appearance.LinkiGramYouTheme.isEnabled()));
        items.add(SettingsHelper.asSwitchCG(colorOs17Row, getString(R.string.NM_ColorOS17),
                        getString(R.string.NM_ColorOS17_Desc))
                .setChecked(app.nimarkogram.messenger.appearance.ColorOs17Theme.isEnabled()));
        if (app.nimarkogram.messenger.appearance.LinkiGramYouTheme.isEnabled())
            items.add(UItem.asButton(YOU_CUSTOMIZE, getString(R.string.LinkiYouCustomize)));
        items.add(UItem.asButton(2002, getString(R.string.LF_Menu)));
        items.add(UItem.asButton(iconPackRow, getString(R.string.AP_IconReplacements), getIconPackValueText()));
        items.add(UItem.asButton(SETTINGS_ICON_COLORS, getString(R.string.LinkiSettingsIconColors), getSettingsIconColorValue()));
        items.add(UItem.asButton(oneUISwitchesRow, getString(R.string.NM_SwitchStyle), getSwitchStyleValueText()).setEnabled(!linkiYou));
        items.add(UItem.asButton(MATERIAL_GROUP, getString(R.string.NM_Style_Material),
                styleCount(AppearanceConfig.MATERIAL_KEYS) + "/" + AppearanceConfig.MATERIAL_KEYS.length));
        if (materialExpanded) {
            items.add(SettingsHelper.asSwitchCG(MATERIAL_ALL, getString(R.string.NM_Material_All))
                    .setChecked(Integer.parseInt(styleCount(AppearanceConfig.MATERIAL_KEYS)) == AppearanceConfig.MATERIAL_KEYS.length)
                    .setEnabled(!linkiYou));
            for (int i = 0; i < AppearanceConfig.MATERIAL_KEYS.length; i++) {
                items.add(SettingsHelper.asSwitchCG(STYLE_BASE + i, getString(MATERIAL_LABELS[i]))
                        .setChecked(AppearanceConfig.enabled(AppearanceConfig.MATERIAL_KEYS[i]))
                        .setEnabled(!linkiYou));
            }
        }
        items.add(UItem.asButton(IOS_GROUP, getString(R.string.NM_Style_IOS),
                styleCount(AppearanceConfig.IOS_KEYS) + "/2"));
        if (iosExpanded) {
            for (int i = 0; i < AppearanceConfig.IOS_KEYS.length; i++) {
                items.add(SettingsHelper.asSwitchCG(STYLE_BASE + 8 + i, getString(IOS_LABELS[i]))
                        .setChecked(AppearanceConfig.enabled(AppearanceConfig.IOS_KEYS[i]))
                        .setEnabled(!app.nimarkogram.messenger.appearance.ColorOs17Theme.isEnabled()));
            }
        }
        items.add(UItem.asButton(disableDividersRow, getString(R.string.NM_Material_Dividers),
                getString(new int[]{R.string.NM_Material_DividerHidden, R.string.NM_Material_DividerLine,
                        R.string.NM_Material_DividerSegments}[app.nimarkogram.messenger.appearance.MaterialStyle.dividerStyle()]))
                .setEnabled(!linkiYou));
        items.add(SettingsHelper.asSwitchCG(MATERIAL_HEADERS, getString(R.string.NM_Material_Headers))
                .setChecked(app.nimarkogram.messenger.appearance.MaterialStyle.separatedHeaders())
                .setEnabled(!linkiYou && !app.nimarkogram.messenger.appearance.MaterialStyle.segments()));
        items.add(SettingsHelper.asSwitchCG(materialCardsRow,
                        getString(R.string.AP_MaterialCards),
                        getString(R.string.AP_MaterialCards_Desc))
                .setChecked(NimarkoConfig.materialCards).setEnabled(!linkiYou));
        items.add(UItem.asButton(sectionRadiusRow, getString(R.string.AP_SectionRadius), getSectionRadiusValueText()));
        items.add(SettingsHelper.asSwitchCG(forceBlurRow, getString(R.string.NM_ForceBlur))
                .setChecked(NimarkoConfig.forceBlur));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            items.add(SettingsHelper.asSwitchCG(liquidGlassRow,
                            getString(R.string.NM_LiquidGlass),
                            getString(R.string.NM_LiquidGlass_Desc))
                    .setChecked(LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS)));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.AP_Header)));
        items.add(SettingsHelper.asSwitchCG(centerTitleRow, getString(R.string.AP_CenterTitle))
                .setChecked(app.nimarkogram.messenger.NimarkoConfig.centerTitle)
        );
        items.add(SettingsHelper.asSwitchCG(hideSearchBar, getString(R.string.AP_HideSearchBar))
                .setChecked(app.nimarkogram.messenger.NimarkoConfig.hideSearchBar)
        );
        items.add(SettingsHelper.asSwitchCG(hideStatusRow, getString(R.string.NM_HideActionBarStatus))
                .setChecked(NimarkoConfig.hideActionBarStatus));
        items.add(SettingsHelper.asSwitchCG(snowflakesRow, getString(R.string.CP_Snowflakes_Header))
                .setChecked(app.nimarkogram.messenger.NimarkoConfig.drawSnowInActionBar)
        );
        items.add(UItem.asButton(customTitleRow, getString(R.string.NM_CustomTitle), getCustomTitleValueText()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.NM_ClassicUi_Header)));
        items.add(SettingsHelper.asSwitchCG(classicUiRow,
                        getString(R.string.NM_ClassicUi),
                        getString(R.string.NM_ClassicUi_Desc))
                .setChecked(NimarkoConfig.classicUi));
        items.add(SettingsHelper.asSwitchCG(navigationDrawerRow,
                        getString(R.string.NM_Drawer_NavigationDrawer),
                        getString(R.string.NM_Drawer_NavigationDrawer_Desc))
                .setChecked(NimarkoConfig.navigationDrawer));
        if (NimarkoConfig.navigationDrawer) {
            items.add(SettingsHelper.asSwitchCG(immersiveDrawerRow,
                            getString(R.string.NM_Drawer_Immersive),
                            getString(R.string.NM_Drawer_Immersive_Desc))
                    .setChecked(NimarkoConfig.immersiveDrawerAnimation));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.NM_Chat_Section)));
        items.add(SettingsHelper.asSwitchCG(iosStyleComposerRow,
                        getString(R.string.NM_IOSStyleComposer),
                        getString(R.string.NM_IOSStyleComposer_Desc))
                .setChecked(NimarkoConfig.iosStyleComposer));
        items.add(SettingsHelper.asSwitchCG(hideBubbleTailRow,
                        getString(R.string.NM_HideBubbleTail),
                        getString(R.string.NM_HideBubbleTail_Desc))
                .setChecked(NimarkoConfig.hideBubbleTail));
        items.add(SettingsHelper.asSwitchCG(onlineIndicatorRow,
                        getString(R.string.NM_OnlineIndicatorInGroups),
                        getString(R.string.NM_OnlineIndicatorInGroups_Desc))
                .setChecked(NimarkoConfig.onlineIndicatorInGroups));
        items.add(SettingsHelper.asSwitchCG(disableSendHintsRow,
                        getString(R.string.NM_DisableSendHints),
                        getString(R.string.NM_DisableSendHints_Desc))
                .setChecked(NimarkoConfig.disableSendHints));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.NM_Effects_Header)));
        items.add(SettingsHelper.asSwitchCG(glareOnElementsRow,
                        getString(R.string.AP_GlareOnElements),
                        getString(R.string.AP_GlareOnElementsInfo))
                .setChecked(app.nimarkogram.messenger.NimarkoConfig.glareOnElements)
        );
        items.add(SettingsHelper.asSwitchCG(mediaGlowRow,
                        getString(R.string.NM_MediaGlow),
                        getString(R.string.NM_MediaGlow_Desc))
                .setChecked(NimarkoConfig.mediaGlow)
        );
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.NM_AvatarCorners_Header)));
        if (avatarCornersCell == null) {
            avatarCornersCell = new app.nimarkogram.messenger.preferences.components.AvatarCornersPreviewCell(getContext(), this);
        }
        items.add(UItem.asCustom(avatarCornersCell));
        items.add(SettingsHelper.asSwitchCG(forumAvatarsRow,
                        getString(R.string.NM_ForumAvatarsLikeChats),
                        getString(R.string.NM_ForumAvatarsLikeChats_Desc))
                .setChecked(NimarkoConfig.forumAvatarsLikeChats)
        );
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.NM_Stickers_Header)));
        if (stickerSizeCell == null) {
            stickerSizeCell = new app.nimarkogram.messenger.preferences.components.StickerSizeCell(getContext(), this);
        }
        items.add(UItem.asCustom(stickerSizeCell));
        items.add(UItem.asButton(hideStickerTimeRow, getString(R.string.LinkiStickerTime), stickerTimeLabel()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.LocalMiscellaneousCache)));
        items.add(UItem.asButton(foldersRow, R.drawable.msg_folders, getString(R.string.CP_Filters_Header)));
        items.add(UItem.asButton(bottomTabsRow, R.drawable.tabs_reorder, getString(R.string.CP_MainTabs_Header)));
        items.add(UItem.asButton(appBackgroundRow, R.drawable.msg_media, getString(R.string.NM_BG_Title)));
        items.add(UItem.asButton(messagesAndProfilesRow, R.drawable.msg_customize, getString(R.string.CP_ProfileReplyBackground)));
        items.add(UItem.asShadow(null));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == colorOs17Row) {
            boolean enabled = !app.nimarkogram.messenger.appearance.ColorOs17Theme.isEnabled();
            app.nimarkogram.messenger.appearance.ColorOs17Theme.setEnabled(enabled);
            updateCheckState(view, enabled);
            refreshMaterialViews();
            return;
        }
        if (item.id == linkiYouRow) {
            boolean enabled = !app.nimarkogram.messenger.appearance.LinkiGramYouTheme.isEnabled();
            app.nimarkogram.messenger.appearance.LinkiGramYouTheme.setEnabled(enabled);
            updateCheckState(view, enabled);
            refreshMaterialViews();
            return;
        }
        if ((app.nimarkogram.messenger.appearance.LinkiGramYouTheme.isEnabled()
                || app.nimarkogram.messenger.appearance.ColorOs17Theme.isEnabled())
                && (item.id == MATERIAL_ALL || item.id == MATERIAL_HEADERS || item.id == disableDividersRow
                || item.id == materialCardsRow || item.id == oneUISwitchesRow
                || (item.id >= STYLE_BASE && item.id < STYLE_BASE + 8))) return;
        if (app.nimarkogram.messenger.appearance.ColorOs17Theme.isEnabled()
                && item.id >= STYLE_BASE + 8 && item.id < STYLE_BASE + 10) return;
        if (item.id == YOU_CUSTOMIZE) { presentFragment(new YouCustomizationActivity()); return; }
        if (item.id == SETTINGS_ICON_COLORS) { showSettingsIconColors(); return; }
        if (item.id == 2002) { presentFragment(new DrawerPreferencesActivity()); return; }
        if (item.id == MATERIAL_ALL) {
            boolean enable = Integer.parseInt(styleCount(AppearanceConfig.MATERIAL_KEYS)) != AppearanceConfig.MATERIAL_KEYS.length;
            for (String key : AppearanceConfig.MATERIAL_KEYS) AppearanceConfig.setEnabled(key, enable);
            refreshMaterialViews();
            return;
        } else if (item.id == MATERIAL_HEADERS) {
            if (!app.nimarkogram.messenger.appearance.MaterialStyle.segments()) {
                NimarkoConfig.getPreferences().edit().putBoolean("materialSeparateHeaders",
                        !app.nimarkogram.messenger.appearance.MaterialStyle.separatedHeaders()).apply();
                refreshMaterialViews();
            }
            return;
        }
        if (item.id == MATERIAL_GROUP || item.id == IOS_GROUP) {
            if (item.id == MATERIAL_GROUP) {
                materialExpanded = !materialExpanded;
            } else {
                iosExpanded = !iosExpanded;
            }
            listView.adapter.update(true);
            return;
        } else if (item.id >= STYLE_BASE && item.id < STYLE_BASE + 10) {
            int index = item.id - STYLE_BASE;
            String key = index < 8 ? AppearanceConfig.MATERIAL_KEYS[index] : AppearanceConfig.IOS_KEYS[index - 8];
            AppearanceConfig.setEnabled(key, !AppearanceConfig.enabled(key));
            Theme.applyCommonTheme();
            updateCheckState(view, AppearanceConfig.enabled(key));
            listView.adapter.update(true);
            if (getParentLayout() != null) {
                getParentLayout().rebuildAllFragmentViews(false, false);
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.cgTabsUpdated);
            return;
        }
        if (item.id == centerTitleRow) {
            
            if (getActionBar() != null) {
                getActionBar().prepareCenterTitleAnimation();
            }
            NimarkoConfig.toggleCenterTitle();
            updateCheckState(view, app.nimarkogram.messenger.NimarkoConfig.centerTitle);
            if (getActionBar() != null) {
                getActionBar().requestLayout();
            }

            if (getParentLayout() != null) {
                getParentLayout().rebuildAllFragmentViews(false, false);
            }
            return;
        } else  if (item.id == hideSearchBar) {
            NimarkoConfig.toggleHideSearchBar();
            updateCheckState(view, app.nimarkogram.messenger.NimarkoConfig.hideSearchBar);

            getNotificationCenter().postNotificationName(NotificationCenter.cgUpdateSearchFiledVisibility);
        } else if (item.id == snowflakesRow) {
            NimarkoConfig.toggleDrawSnowInActionBar();
            updateCheckState(view, app.nimarkogram.messenger.NimarkoConfig.drawSnowInActionBar);

            showRestartBulletin();
        } else if (item.id == iconPackRow) {
            presentFragment(new IconPackSelectorActivity());   
        } else if (item.id == oneUISwitchesRow) {
            java.util.ArrayList<CharSequence> opts = new java.util.ArrayList<>();
            opts.add(getString(R.string.Default));
            opts.add("One UI");
            opts.add("MD3");
            app.nimarkogram.messenger.preferences.helpers.PopupHelper.show(opts, getString(R.string.NM_SwitchStyle),
                    NimarkoConfig.switchStyle, getContext(), i -> {
                        NimarkoConfig.setSwitchStyle(i);
                        SettingsHelper.updateButtonValue(view, getSwitchStyleValueText());
                        
                        if (listView != null) {
                            for (int k = 0; k < listView.getChildCount(); k++) {
                                View c = listView.getChildAt(k);
                                if (c != null) c.invalidate();
                            }
                        }
                        if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
                        
                        showRestartBulletin();
                    });
        } else if (item.id == disableDividersRow) {
            java.util.ArrayList<CharSequence> styles = new java.util.ArrayList<>();
            styles.add(getString(R.string.NM_Material_DividerHidden));
            styles.add(getString(R.string.NM_Material_DividerLine));
            styles.add(getString(R.string.NM_Material_DividerSegments));
            app.nimarkogram.messenger.preferences.helpers.PopupHelper.show(styles, getString(R.string.NM_Material_Dividers),
                    app.nimarkogram.messenger.appearance.MaterialStyle.dividerStyle(), getContext(), value -> {
                        NimarkoConfig.getPreferences().edit().putInt("materialDividerStyle", value).apply();
                        refreshMaterialViews();
                    });
        } else if (item.id == materialCardsRow) {
            NimarkoConfig.toggleMaterialCards();
            updateCheckState(view, NimarkoConfig.materialCards);

            SettingsHelper.updateButtonValue(view, getSectionRadiusValueText());
            refreshSectionCards();
        } else if (item.id == sectionRadiusRow) {
            final int[] values = {8, 12, 16, 20, 24, 28};
            final java.util.ArrayList<CharSequence> opts = new java.util.ArrayList<>();
            for (int v : values) {
                opts.add(v + " dp");
            }
            int current = -1;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == NimarkoConfig.sectionRadius) {
                    current = i;
                }
            }
            app.nimarkogram.messenger.preferences.helpers.PopupHelper.show(opts, getString(R.string.AP_SectionRadius),
                    current, getContext(), i -> {
                        NimarkoConfig.setSectionRadius(values[i]);
                        SettingsHelper.updateButtonValue(view, getSectionRadiusValueText());
                        refreshSectionCards();
                    });
        } else if (item.id == glareOnElementsRow) {
            NimarkoConfig.toggleGlareOnElements();
            updateCheckState(view, NimarkoConfig.glareOnElements);
            
            if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
        } else if (item.id == mediaGlowRow) {
            NimarkoConfig.toggleMediaGlow();
            updateCheckState(view, NimarkoConfig.mediaGlow);
            listView.adapter.update(true);   
        } else if (item.id == forumAvatarsRow) {
            NimarkoConfig.toggleForumAvatarsLikeChats();
            updateCheckState(view, NimarkoConfig.forumAvatarsLikeChats);
            
            if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
        } else if (item.id == liquidGlassRow) {
            LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS);
            updateCheckState(view, LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS));
            if (getParentLayout() != null) {
                getParentLayout().rebuildAllFragmentViews(false, false);
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.cgTabsUpdated);
        } else if (item.id == forceBlurRow) {
            NimarkoConfig.toggleForceBlur();
            updateCheckState(view, NimarkoConfig.forceBlur);
            Theme.applyCommonTheme();
            if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
        } else if (item.id == hideStatusRow) {
            NimarkoConfig.toggleHideActionBarStatus();
            updateCheckState(view, NimarkoConfig.hideActionBarStatus);
            if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
        } else if (item.id == disableSendHintsRow) {
            NimarkoConfig.toggleDisableSendHints();
            updateCheckState(view, NimarkoConfig.disableSendHints);
        } else if (item.id == classicUiRow) {
            NimarkoConfig.setClassicUi(!NimarkoConfig.classicUi);
            updateCheckState(view, NimarkoConfig.classicUi);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            if (getParentLayout() != null) {
                getParentLayout().rebuildAllFragmentViews(false, false);
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.cgTabsUpdated);
            showRestartBulletin();
        } else if (item.id == navigationDrawerRow) {
            NimarkoConfig.toggleNavigationDrawer();
            updateCheckState(view, NimarkoConfig.navigationDrawer);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
            if (getParentLayout() != null) {
                getParentLayout().rebuildAllFragmentViews(false, false);
            }
            showRestartBulletin();
        } else if (item.id == immersiveDrawerRow) {
            NimarkoConfig.toggleImmersiveDrawerAnimation();
            updateCheckState(view, NimarkoConfig.immersiveDrawerAnimation);
        } else if (item.id == iosStyleComposerRow) {
            NimarkoConfig.toggleIosStyleComposer();
            updateCheckState(view, NimarkoConfig.iosStyleComposer);
            
            if (getParentLayout() != null) {
                getParentLayout().rebuildAllFragmentViews(false, false);
            }
        } else if (item.id == hideBubbleTailRow) {
            NimarkoConfig.toggleHideBubbleTail();
            updateCheckState(view, NimarkoConfig.hideBubbleTail);
            
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.nmUpdateBubbleShape);
        } else if (item.id == onlineIndicatorRow) {
            NimarkoConfig.toggleOnlineIndicatorInGroups();
            updateCheckState(view, NimarkoConfig.onlineIndicatorInGroups);
            
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.nmUpdateOnlineIndicator);
        } else if (item.id == hideStickerTimeRow) {
            ArrayList<String> options = new ArrayList<>();
            options.add(getString(R.string.LinkiStickerTimeDefault));
            options.add(getString(R.string.LinkiStickerTimeSide));
            options.add(getString(R.string.Hide));
            app.nimarkogram.messenger.preferences.helpers.PopupHelper.show(options,
                    getString(R.string.LinkiStickerTime), NimarkoConfig.stickerTimeMode, getContext(), i -> {
                        NimarkoConfig.setStickerTimeMode(i);
                        SettingsHelper.updateButtonValue(view, stickerTimeLabel());
                        if (stickerSizeCell != null) stickerSizeCell.refreshPreview();
                    });
            
            if (stickerSizeCell != null) stickerSizeCell.refreshPreview();
        } else if (item.id == customTitleRow) {
            showCustomTitleDialog(view);
        } else if (item.id == foldersRow) {
            presentFragment(new FoldersPreferencesActivity());
        } else if (item.id == bottomTabsRow) {
            presentFragment(new BottomTabsPreferencesActivity());
        } else if (item.id == appBackgroundRow) {
            presentFragment(new BackgroundPreferencesActivity());
        } else if (item.id == messagesAndProfilesRow) {
            presentFragment(new MessagesAndProfilesPreferencesActivity());
        }
    }

    @Override
    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        
        return false;
    }

    private String getIconPackValueText()  {
        return switch (app.nimarkogram.messenger.NimarkoConfig.iconReplacement) {
            case NimarkoConfig.ICON_REPLACE_SOLAR -> getString(R.string.AP_IconReplacement_Solar);
            case NimarkoConfig.ICON_REPLACE_LIQUID_GLASS -> getString(R.string.NM_IconPack_LiquidTitle);
            case NimarkoConfig.ICON_REPLACE_PLUMPY -> getString(R.string.NM_IconPack_PlumpyTitle);
            default -> getString(R.string.Default);
        };
    }

    private String getSwitchStyleValueText() {
        if (app.nimarkogram.messenger.appearance.ColorOs17Theme.isEnabled()) return "ColorOS 17";
        if (app.nimarkogram.messenger.appearance.LinkiGramYouTheme.isEnabled()) return "Material You";
        return switch (NimarkoConfig.switchStyle) {
            case NimarkoConfig.SWITCH_STYLE_ONEUI -> "One UI";
            case NimarkoConfig.SWITCH_STYLE_MD3 -> "MD3";
            default -> getString(R.string.Default);
        };
    }

    private String getSectionRadiusValueText() {
        return (app.nimarkogram.messenger.appearance.ClientTheme.isEnabled()
                ? app.nimarkogram.messenger.appearance.ClientTheme.sectionRadius() : NimarkoConfig.sectionRadius) + " dp";
    }

    /**
     * Радиус карточек хранится в RecyclerListView на момент вызова setSections,
     * поэтому после его смены секции надо пересобрать, а не просто перерисовать.
     */
    private void refreshMaterialViews() {
        Theme.refreshThemeColors(false, true);
        if (listView != null) listView.adapter.update(true);
        if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(true, true);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.cgTabsUpdated);
    }

    private void refreshSectionCards() {
        if (listView == null) {
            return;
        }
        if (NimarkoConfig.materialCards || app.nimarkogram.messenger.appearance.MaterialStyle.segments()) {
            listView.setSections(true);
        } else {
            listView.disableSections();
        }
        for (int k = 0; k < listView.getChildCount(); k++) {
            View child = listView.getChildAt(k);
            if (child != null) {
                child.invalidate();
            }
        }
        listView.invalidateItemDecorations();
        listView.invalidate();
    }

    private String getCustomTitleValueText() {
        return NimarkoConfig.customTitleEnabled && !NimarkoConfig.customTitleText.trim().isEmpty()
                ? NimarkoConfig.customTitleText : getString(R.string.Default);
    }

    private String[] settingsIconColorLabels() {
        return new String[] {getString(R.string.LinkiIconsNeutral), getString(R.string.LinkiIconsAccent),
                getString(R.string.LinkiIconsOriginal), getString(R.string.LinkiIconsCustom)};
    }

    private String getSettingsIconColorValue() {
        int mode = app.nimarkogram.messenger.appearance.SettingsIconColors.mode();
        return mode == app.nimarkogram.messenger.appearance.SettingsIconColors.CUSTOM
                ? String.format(java.util.Locale.ROOT, "#%06X",
                    app.nimarkogram.messenger.appearance.SettingsIconColors.customColor() & 0xFFFFFF)
                : settingsIconColorLabels()[mode];
    }

    private void refreshSettingsIconColors() {
        listView.adapter.update(true);
        if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
    }

    private void showSettingsIconColors() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.LinkiSettingsIconColors));
        builder.setItems(settingsIconColorLabels(), (dialog, which) -> {
            if (which == app.nimarkogram.messenger.appearance.SettingsIconColors.CUSTOM) {
                showSettingsIconCustomColor();
            } else {
                app.nimarkogram.messenger.appearance.SettingsIconColors.setMode(which);
                refreshSettingsIconColors();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showSettingsIconCustomColor() {
        if (getParentActivity() == null) return;
        EditText input = new EditText(getParentActivity());
        input.setSingleLine(true);
        input.setText(String.format(java.util.Locale.ROOT, "#%06X",
                app.nimarkogram.messenger.appearance.SettingsIconColors.customColor() & 0xFFFFFF));
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHint("#RRGGBB");
        FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(4), AndroidUtilities.dp(22), AndroidUtilities.dp(4));
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.LinkiIconsCustom));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.OK), null);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (!value.startsWith("#")) value = "#" + value;
            if (!value.matches("#[0-9a-fA-F]{6}")) {
                input.setError(getString(R.string.LinkiIconsInvalidColor));
                return;
            }
            app.nimarkogram.messenger.appearance.SettingsIconColors.setCustomColor(android.graphics.Color.parseColor(value));
            dialog.dismiss();
            refreshSettingsIconColors();
        });
    }

    private void showCustomTitleDialog(View view) {
        Context ctx = getContext();
        if (ctx == null) return;
        EditText input = new EditText(ctx);
        input.setText(NimarkoConfig.customTitleText);
        input.setHint(getString(R.string.AppName));
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setSingleLine(true);
        FrameLayout container = new FrameLayout(ctx);
        container.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(4), AndroidUtilities.dp(22), AndroidUtilities.dp(4));
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(getString(R.string.NM_CustomTitle));
        b.setView(container);
        b.setPositiveButton(getString(R.string.OK), (d, w) -> {
            String t = input.getText().toString().trim();
            NimarkoConfig.setCustomTitle(!t.isEmpty(), t);
            SettingsHelper.updateButtonValue(view, getCustomTitleValueText());
            if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        b.show();
    }

}
