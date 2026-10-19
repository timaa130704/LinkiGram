package app.nimarkogram.messenger.preferences;

import android.view.View;
import java.util.ArrayList;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import app.nimarkogram.messenger.feed.FeedPreferences;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

public class ScreenAppearanceActivity extends NimarkoUniversalPreferencesActivity {
    private final String screen;
    public ScreenAppearanceActivity(String screen) { this.screen = screen; }
    @Override protected CharSequence getTitle() { return LocaleController.getString("drawer".equals(screen) ? R.string.LF_DrawerStyle : R.string.LF_FeedStyle); }
    @Override public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(SettingsHelper.asSwitchCG(1, LocaleController.getString(R.string.LF_Compact)).setChecked(FeedPreferences.compact(screen)));
        items.add(SettingsHelper.asSwitchCG(2, LocaleController.getString(R.string.LF_Labels)).setChecked(FeedPreferences.labels(screen)));
        items.add(UItem.asButton(3, LocaleController.getString(R.string.LF_Accent)));
        items.add(UItem.asButton(4, LocaleController.getString(R.string.Reset)));
    }
    @Override public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1 || item.id == 2) {
            String suffix = item.id == 1 ? "compact" : "labels";
            String key = "linki_" + screen + "_" + suffix;
            FeedPreferences.prefs().edit().putBoolean(key, !FeedPreferences.prefs().getBoolean(key, item.id == 2)).apply();
        } else if (item.id == 3) {
            int[] colors = {0, 0xff1e88e5, 0xff8e24aa, 0xff00897b, 0xffe53935, 0xfffb8c00};
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(LocaleController.getString(R.string.LF_Accent))
                    .setItems(new CharSequence[]{LocaleController.getString(R.string.Default), "● Blue", "● Purple", "● Teal", "● Red", "● Orange"}, (d, which) -> {
                        android.content.SharedPreferences.Editor edit = FeedPreferences.prefs().edit();
                        String key = "linki_" + screen + "_accent";
                        if (which == 0) edit.remove(key); else edit.putInt(key, colors[which]);
                        edit.apply(); refresh();
                    }).create());
            return;
        } else if (item.id == 4) {
            FeedPreferences.prefs().edit().remove("linki_" + screen + "_compact").remove("linki_" + screen + "_labels").remove("linki_" + screen + "_accent").apply();
        }
        refresh();
    }
    private void refresh() { listView.adapter.update(true); }
    @Override public boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
}
