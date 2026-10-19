package app.nimarkogram.messenger.preferences;

import android.view.View;
import java.util.ArrayList;
import org.telegram.messenger.*;
import org.telegram.ui.Cells.AppIconsSelectorCell;
import org.telegram.ui.Components.*;

public class LauncherIconsActivity extends NimarkoUniversalPreferencesActivity {
    private AppIconsSelectorCell picker;
    @Override protected CharSequence getTitle() { return LocaleController.getString(R.string.LF_Icons); }
    @Override public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (picker == null) picker = new AppIconsSelectorCell(getContext(), this, currentAccount);
        items.add(UItem.asCustom(picker, 115));
        items.add(UItem.asShadow(LocaleController.getString(R.string.LF_IconHelp)));
    }
    @Override public void onClick(UItem item, View view, int position, float x, float y) {}
    @Override public boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
}
