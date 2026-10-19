package app.nimarkogram.messenger.preferences;

import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.*;
import app.nimarkogram.messenger.drawer.*;
import app.nimarkogram.messenger.feed.FeedPreferences;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

public class DrawerPreferencesActivity extends NimarkoUniversalPreferencesActivity {
    private final ArrayList<Integer> ids = new ArrayList<>();
    @Override protected CharSequence getTitle() { return LocaleController.getString(R.string.LF_Menu); }
    @Override public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(LocaleController.getString(R.string.LF_MenuHelp)));
        items.add(SettingsHelper.asSwitchCG(-12, LocaleController.getString(R.string.NM_Drawer_NavigationDrawer)).setChecked(app.nimarkogram.messenger.NimarkoConfig.navigationDrawer));
        items.add(UItem.asButton(-10, LocaleController.getString(R.string.LF_DrawerStyle)));
        items.add(UItem.asButton(-11, LocaleController.getString(R.string.Reset)));
        ids.clear(); ids.addAll(MainMenuLayout.getLayout());
        for (int id : MainMenuLayout.getAllItemIds()) if (!ids.contains(id)) ids.add(id);
        for (int i = 0; i < ids.size(); i++) {
            MainMenuHelper.MenuItemInfo info = MainMenuHelper.describeItem(ids.get(i));
            if (info != null) items.add(SettingsHelper.asSwitchCG(i + 1, info.text())
                    .setChecked(MainMenuLayout.getLayout().contains(ids.get(i))));
        }
        items.add(UItem.asHeader(LocaleController.getString(R.string.CP_Filters_Header)));
        for (MessagesController.DialogFilter filter : getMessagesController().dialogFilters) {
            if (filter.id == 0) continue;
            items.add(SettingsHelper.asSwitchCG(10000 + filter.id, filter.name)
                    .setChecked(FeedPreferences.prefs().getBoolean("linki_folder_" + currentAccount + "_" + filter.id, false)));
        }
    }
    @Override public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == -12) {
            app.nimarkogram.messenger.NimarkoConfig.toggleNavigationDrawer();
            listView.adapter.update(true);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
            if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
            showRestartBulletin();
            return;
        }
        if (item.id == -10) { presentFragment(new ScreenAppearanceActivity("drawer")); return; }
        if (item.id == -11) {
            MainMenuLayout.reset();
            android.content.SharedPreferences.Editor edit = FeedPreferences.prefs().edit();
            for (MessagesController.DialogFilter filter : getMessagesController().dialogFilters) edit.remove("linki_folder_" + currentAccount + "_" + filter.id);
            edit.apply();
        } else if (item.id >= 10000) {
            String key = "linki_folder_" + currentAccount + "_" + (item.id - 10000);
            FeedPreferences.prefs().edit().putBoolean(key, !FeedPreferences.prefs().getBoolean(key, false)).apply();
        } else if (item.id > 0 && item.id <= ids.size()) {
            int id = ids.get(item.id - 1);
            ArrayList<Integer> visible = MainMenuLayout.getLayoutMutable(), hidden = MainMenuLayout.getHiddenItemsMutable();
            if (visible.remove(Integer.valueOf(id))) hidden.add(id); else { hidden.remove(Integer.valueOf(id)); visible.add(id); }
            MainMenuLayout.save(visible, hidden);
        }
        listView.adapter.update(true);
    }
    @Override public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id <= 0 || item.id > ids.size()) return false;
        int id = ids.get(item.id - 1);
        if (!MainMenuLayout.getLayout().contains(id)) return false;
        showDialog(new AlertDialog.Builder(getParentActivity()).setItems(new CharSequence[]{LocaleController.getString(R.string.LF_MoveUp), LocaleController.getString(R.string.LF_MoveDown)}, (d, which) -> {
            ArrayList<Integer> visible = MainMenuLayout.getLayoutMutable();
            int from = visible.indexOf(id), to = from + (which == 0 ? -1 : 1);
            if (from >= 0 && to >= 0 && to < visible.size()) {
                Collections.swap(visible, from, to);
                MainMenuLayout.save(visible, MainMenuLayout.getHiddenItems());
                listView.adapter.update(true);
            }
        }).create());
        return true;
    }
}
