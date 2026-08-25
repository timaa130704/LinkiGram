package app.nimarkogram.messenger.preferences;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.ArrayList;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import app.nimarkogram.messenger.NimarkoConfig;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;
import app.nimarkogram.messenger.preferences.tabs.MainTabsPreviewCell;
import app.nimarkogram.messenger.utils.ui.MainTabsManager;

public class BottomTabsPreferencesActivity extends BasePreferencesActivity {
    private static final int ID_SHOW_TABS = 1;
    private static final int ID_SHOW_TITLE = 2;
    private static final int ID_FORCE_OPEN_CHATS = 4;
    private static final int ID_SHOW_SEARCH_IN_TABS = 5;
    private static final int ID_SEMI_TRANSPARENT = 7;
    private static final int ID_TINT_COLOR = 8;
    private static final int ID_TINT_RESET = 9;
    private static final int ID_LINKI_ASS = 10;
    private static final int ID_RESET_ORDER = 6;

    private MainTabsPreviewCell editorCell;

    private ArrayList<MainTabsManager.Tab> tabs;
    private ArrayList<MainTabsManager.Tab> initialTabs;
    private boolean resetToDefaults;
    private boolean fragmentAlive;
    private int uiGeneration;
    private int pendingStructureRefreshGeneration;

    private final Runnable delayedStructureRefresh = () -> {
        if (!fragmentAlive || pendingStructureRefreshGeneration != uiGeneration) {
            return;
        }
        if (editorCell != null) {
            editorCell.setOnReorderCommitted(null);
        }
        editorCell = null;
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
            listView.adapter.notifyDataSetChanged();
        }
    };

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.NM_AP_BottomTabs);
    }

    @Override
    public boolean onFragmentCreate() {
        fragmentAlive = true;
        uiGeneration++;
        initialTabs = new ArrayList<>();
        for (MainTabsManager.Tab t : MainTabsManager.INSTANCE.getAllTabs()) {
            initialTabs.add(new MainTabsManager.Tab(t.getType(), t.enabled));
        }
        tabs = new ArrayList<>();
        for (MainTabsManager.Tab t : MainTabsManager.INSTANCE.getAllTabs()) {
            tabs.add(new MainTabsManager.Tab(t.getType(), t.enabled));
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        fragmentAlive = false;
        uiGeneration++;
        AndroidUtilities.cancelRunOnUIThread(delayedStructureRefresh);
        if (editorCell != null) {
            editorCell.setOnReorderCommitted(null);
        }
        super.onFragmentDestroy();
        commitTabs(true);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (NimarkoConfig.showMainTabs) {
            if (editorCell == null && getContext() != null) {
                editorCell = new MainTabsPreviewCell(getContext());
                editorCell.setEditMode(true);
                editorCell.setTabs(getContext(), getResourceProvider(), tabs);
                
                editorCell.setOnReorderCommitted(() -> {
                    resetToDefaults = false;
                    postCgTabsUpdated();
                });
            } else if (editorCell != null) {
                editorCell.refresh();
            }
            if (editorCell != null) {
                items.add(UItem.asCustom(editorCell,
                        org.telegram.ui.DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS));
                items.add(UItem.asShadow(LocaleController.getString(R.string.NM_BT_EditorFooter)));
            }
        }

        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_BT_LayoutHeader)));

        UItem enableTabs = SettingsHelper.asSwitchCG(ID_SHOW_TABS,
                        LocaleController.getString(R.string.NM_BT_ShowTabs))
                .setChecked(NimarkoConfig.showMainTabs);
        enableTabs.hideDivider = true;
        items.add(enableTabs);

        if (NimarkoConfig.showMainTabs) {

            items.add(UItem.asShadow(null));

            items.add(UItem.asHeader(LocaleController.getString(R.string.NM_BT_AppearanceHeader)));
            items.add(UItem.asCheck(ID_SHOW_TITLE,
                            LocaleController.getString(R.string.NM_BT_ShowTabsTitle))
                    .setChecked(NimarkoConfig.showMainTabsTitle));
            items.add(UItem.asCheck(ID_SHOW_SEARCH_IN_TABS,
                            LocaleController.getString(R.string.NM_BT_ShowSearchInTabs))
                    .setChecked(NimarkoConfig.showSearchInTabs));
            items.add(UItem.asShadow(LocaleController.getString(R.string.NM_BT_ShowSearchInTabs_Desc)));
            items.add(UItem.asCheck(ID_SEMI_TRANSPARENT,
                            LocaleController.getString(R.string.NM_BT_SemiTransparentTabs))
                    .setChecked(NimarkoConfig.mainTabsSemiTransparent));
            items.add(UItem.asShadow(LocaleController.getString(R.string.NM_BT_SemiTransparentTabs_Desc)));
            if (NimarkoConfig.mainTabsSemiTransparent) {
                items.add(UItem.asButton(ID_TINT_COLOR, getTintColorTitle()));
                if (NimarkoConfig.mainTabsTintColor != 0) {
                    items.add(UItem.asButton(ID_TINT_RESET, R.drawable.msg_reset,
                            LocaleController.getString(R.string.NM_BT_TintReset)));
                }
                items.add(UItem.asShadow(LocaleController.getString(R.string.NM_BT_TintColor_Desc)));
            }
            items.add(UItem.asCheck(ID_LINKI_ASS,
                            LocaleController.getString(R.string.NM_GLASS_Liquid))
                    .setChecked(NimarkoConfig.linkiAss));
            items.add(UItem.asShadow(LocaleController.getString(R.string.NM_GLASS_Liquid_Desc)));

            items.add(UItem.asHeader(LocaleController.getString(R.string.NM_BT_ActionsHeader)));
            items.add(SettingsHelper.asSwitchCG(ID_FORCE_OPEN_CHATS,
                            LocaleController.getString(R.string.NM_BT_ForceOpenChats),
                            LocaleController.getString(R.string.NM_BT_ForceOpenChats_Desc))
                    .setChecked(NimarkoConfig.mainTabsForceOpenChats));
            items.add(UItem.asButton(ID_RESET_ORDER, R.drawable.msg_reset,
                    LocaleController.getString(R.string.Reset)));
            items.add(UItem.asShadow(null));
        } else {
            items.add(UItem.asShadow(null));
        }
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == ID_SHOW_TABS) {
            NimarkoConfig.toggleShowMainTabs();
            applyCheck(item, view, NimarkoConfig.showMainTabs);
            
            AndroidUtilities.cancelRunOnUIThread(delayedStructureRefresh);
            pendingStructureRefreshGeneration = uiGeneration;
            AndroidUtilities.runOnUIThread(delayedStructureRefresh, 220);
            
            postCgTabsUpdated();
            showRestartBulletin();
            rebuildMainTabsFragments();
        } else if (id == ID_SHOW_TITLE) {
            NimarkoConfig.toggleShowMainTabsTitle();
            applyCheck(item, view, NimarkoConfig.showMainTabsTitle);
            if (editorCell != null) {
                editorCell.refresh();
            }
            postCgTabsUpdated();
            rebuildMainTabsFragments();
        } else if (id == ID_SHOW_SEARCH_IN_TABS) {
            NimarkoConfig.toggleShowSearchInTabs();
            applyCheck(item, view, NimarkoConfig.showSearchInTabs);
            if (editorCell != null) {
                editorCell.refresh();
            }
            postCgTabsUpdated();
            rebuildMainTabsFragments();
        } else if (id == ID_SEMI_TRANSPARENT) {
            NimarkoConfig.toggleMainTabsSemiTransparent();
            applyCheck(item, view, NimarkoConfig.mainTabsSemiTransparent);
            postCgTabsUpdated();
            rebuildMainTabsFragments();
        } else if (id == ID_LINKI_ASS) {
            NimarkoConfig.toggleLinkiAss();
            applyCheck(item, view, NimarkoConfig.linkiAss);
            postCgTabsUpdated();
            rebuildMainTabsFragments();
        } else if (id == ID_TINT_COLOR) {
            showTintColorPicker();
        } else if (id == ID_TINT_RESET) {
            NimarkoConfig.setMainTabsTintColor(0);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            postCgTabsUpdated();
            rebuildMainTabsFragments();
        } else if (id == ID_FORCE_OPEN_CHATS) {
            NimarkoConfig.toggleMainTabsForceOpenChats();
            applyCheck(item, view, NimarkoConfig.mainTabsForceOpenChats);
        } else if (id == ID_RESET_ORDER) {
            
            NimarkoConfig.setMainTabsOrder(null);
            resetToDefaults = true;
            tabs.clear();
            for (MainTabsManager.Tab t : MainTabsManager.INSTANCE.getAllTabs()) {
                tabs.add(new MainTabsManager.Tab(t.getType(), t.enabled));
            }
            
            initialTabs = new ArrayList<>();
            for (MainTabsManager.Tab t : tabs) {
                initialTabs.add(new MainTabsManager.Tab(t.getType(), t.enabled));
            }
            if (editorCell != null) {
                editorCell.setTabs(getContext(), getResourceProvider(), tabs);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            postCgTabsUpdated();
            rebuildMainTabsFragments();
        }
    }

    private void commitTabs(boolean notify) {
        if (tabs == null || resetToDefaults) return;
        MainTabsManager.INSTANCE.saveTabs(tabs);
        if (notify && !tabs.equals(initialTabs)) {
            postCgTabsUpdated();
        }
    }

    private void postCgTabsUpdated() {
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> NotificationCenter.getGlobalInstance()
                        .postNotificationName(NotificationCenter.cgTabsUpdated),
                80);
    }

    private String getTintColorTitle() {
        int c = NimarkoConfig.mainTabsTintColor;
        if (c == 0) {
            return LocaleController.getString(R.string.NM_BT_TintColor) + " · " + LocaleController.getString(R.string.NM_BT_TintOff);
        }
        return LocaleController.getString(R.string.NM_BT_TintColor) + " · " + String.format("#%06X", 0xFFFFFF & c);
    }

    private void showTintColorPicker() {
        final android.content.Context context = getParentActivity() != null ? getParentActivity() : getContext();
        org.telegram.ui.Components.ColorPicker colorPicker = new org.telegram.ui.Components.ColorPicker(context, false, (color, n, applyNow) -> {
            NimarkoConfig.setMainTabsTintColor(color | 0xFF000000);
            if (applyNow) {
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                postCgTabsUpdated();
                rebuildMainTabsFragments();
            }
        }) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, android.view.View.MeasureSpec.makeMeasureSpec(org.telegram.messenger.AndroidUtilities.dp(320), android.view.View.MeasureSpec.EXACTLY));
            }
        };
        colorPicker.setColor(NimarkoConfig.mainTabsTintColor != 0 ? NimarkoConfig.mainTabsTintColor : 0xFFFFFFFF, 0);
        colorPicker.setType(-1, true, 1, 1, false, 0, false);
        org.telegram.ui.ActionBar.BottomSheet bottomSheet = new org.telegram.ui.ActionBar.BottomSheet(context, false);
        bottomSheet.setCustomView(colorPicker);
        bottomSheet.setDimBehind(false);
        bottomSheet.show();
    }

    private void rebuildMainTabsFragments() {
        if (getParentLayout() != null) {
            getParentLayout().rebuildAllFragmentViews(false, false);
        }
    }

    private void applyCheck(UItem item, View view, boolean value) {
        item.checked = value;
        updateCheckState(view, value);
    }
}
