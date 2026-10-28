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
    }
    @Override public boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
    private void refresh() {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
        if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
    }
}
