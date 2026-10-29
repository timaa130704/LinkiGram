package app.nimarkogram.messenger.preferences;

import android.view.View;
import java.util.ArrayList;
import java.util.Locale;
import org.telegram.messenger.R;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import app.nimarkogram.messenger.appearance.LinkiGramYouTheme;

public class YouCustomizationActivity extends NimarkoUniversalPreferencesActivity {
    @Override public String getTitle() { return LocaleController.getString(R.string.LinkiYouCustomize); }
    @Override public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int color = LinkiGramYouTheme.getSeedColor();
        items.add(UItem.asHeader(LocaleController.getString(R.string.LinkiYouColors)));
        items.add(UItem.asButton(1, LocaleController.getString(R.string.LinkiYouAccent),
                color == 0 ? LocaleController.getString(R.string.LinkiYouSystemColor) : String.format(Locale.ROOT, "#%06X", color & 0xffffff)));
        items.add(UItem.asButton(2, LocaleController.getString(R.string.LinkiYouSystemColor)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.LinkiYouColorsInfo)));
        items.add(UItem.asHeader(LocaleController.getString(R.string.LinkiYouLayout)));
        items.add(UItem.asButton(3, LocaleController.getString(R.string.NM_AP_BottomTabs)));
        items.add(UItem.asButton(4, LocaleController.getString(R.string.LF_Menu)));
        items.add(UItem.asButton(5, "Поделиться палитрой You"));
        items.add(UItem.asButton(6, "Импортировать палитру You"));
    }
    @Override public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            ColorPicker picker = new ColorPicker(getParentActivity(), false, (color, n, applyNow) -> {
                if (applyNow) { LinkiGramYouTheme.setSeedColor(color); refresh(); }
            });
            int current = LinkiGramYouTheme.getSeedColor();
            picker.setColor(current == 0 ? 0xff6750a4 : current, 0);
            picker.setType(-1, true, 1, 1, false, 0, false);
            BottomSheet sheet = new BottomSheet(getParentActivity(), false);
            sheet.setCustomView(picker);
            sheet.show();
        } else if (item.id == 2) { LinkiGramYouTheme.setSeedColor(0); refresh(); }
        else if (item.id == 3) presentFragment(new BottomTabsPreferencesActivity());
        else if (item.id == 4) presentFragment(new DrawerPreferencesActivity());
        else if (item.id == 5) {
            try {
                String preset = new org.json.JSONObject().put("format", "linkigram-you")
                        .put("version", 1).put("seed", LinkiGramYouTheme.getSeedColor()).toString();
                android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(android.content.Intent.EXTRA_TEXT, preset);
                getParentActivity().startActivity(android.content.Intent.createChooser(send, "Палитра LinkiGram You"));
            } catch (Exception error) { showPresetError(); }
        } else if (item.id == 6) {
            android.widget.EditText input = new android.widget.EditText(getParentActivity());
            input.setHint("Вставьте код палитры");
            input.setMaxLines(5);
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4096)});
            input.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack, getResourceProvider()));
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider())
                    .setTitle("Импорт палитры You").setView(input)
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
                        try {
                            org.json.JSONObject preset = new org.json.JSONObject(input.getText().toString());
                            if (!"linkigram-you".equals(preset.getString("format")) || preset.getInt("version") != 1)
                                throw new IllegalArgumentException();
                            Object value = preset.get("seed");
                            if (!(value instanceof Number)) throw new IllegalArgumentException();
                            long seed = ((Number) value).longValue();
                            if (seed < Integer.MIN_VALUE || seed > Integer.MAX_VALUE) throw new IllegalArgumentException();
                            LinkiGramYouTheme.setSeedColor((int) seed);
                            refresh();
                        } catch (Exception error) { showPresetError(); }
                    }).create());
        }
    }
    @Override public boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
    private void refresh() {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
        if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
    }
    private void showPresetError() {
        showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle("Палитра LinkiGram You").setMessage("Не удалось прочитать палитру. Проверьте код и версию формата.")
                .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
    }
}
