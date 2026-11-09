package app.nimarkogram.messenger.preferences;

import android.view.View;

import java.util.ArrayList;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import app.nimarkogram.messenger.NimarkoConfig;

public class DialogsMenuItemsPreferencesActivity extends BasePreferencesActivity {

    private static final int ID_SMART_CENTER = 1;
    private static final int ID_THEME = 2;
    private static final int ID_READ_ALL = 3;
    private static final int ID_NEW_GROUP = 4;
    private static final int ID_SAVED = 5;
    private static final int ID_SETTINGS = 6;
    private static final int ID_REORDER = 7;

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.NM_DM_Title);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_SMART_CENTER, LocaleController.getString(R.string.LinkiSmartCenter))
                .setChecked(NimarkoConfig.dialogsMenuShowSmartCenter));
        items.add(UItem.asCheck(ID_THEME, LocaleController.getString(R.string.NM_DM_Theme))
                .setChecked(NimarkoConfig.dialogsMenuShowTheme));
        items.add(UItem.asCheck(ID_READ_ALL, LocaleController.getString(R.string.MarkAllAsRead))
                .setChecked(NimarkoConfig.dialogsMenuShowReadAll));
        items.add(UItem.asCheck(ID_NEW_GROUP, LocaleController.getString(R.string.NewGroup))
                .setChecked(NimarkoConfig.dialogsMenuShowNewGroup));
        items.add(UItem.asCheck(ID_SAVED, LocaleController.getString(R.string.SavedMessages))
                .setChecked(NimarkoConfig.dialogsMenuShowSaved));
        items.add(UItem.asCheck(ID_SETTINGS, LocaleController.getString(R.string.Settings))
                .setChecked(NimarkoConfig.dialogsMenuShowSettings));
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_REORDER, R.drawable.msg_list, LocaleController.getString(R.string.NM_DM_Reorder)));
        items.add(UItem.asShadow(null));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == ID_SMART_CENTER) {
            NimarkoConfig.toggleDialogsMenuShowSmartCenter();
            updateCheckState(view, NimarkoConfig.dialogsMenuShowSmartCenter);
        } else if (id == ID_THEME) {
            NimarkoConfig.toggleDialogsMenuShowTheme();
            updateCheckState(view, NimarkoConfig.dialogsMenuShowTheme);
        } else if (id == ID_READ_ALL) {
            NimarkoConfig.toggleDialogsMenuShowReadAll();
            updateCheckState(view, NimarkoConfig.dialogsMenuShowReadAll);
        } else if (id == ID_NEW_GROUP) {
            NimarkoConfig.toggleDialogsMenuShowNewGroup();
            updateCheckState(view, NimarkoConfig.dialogsMenuShowNewGroup);
        } else if (id == ID_SAVED) {
            NimarkoConfig.toggleDialogsMenuShowSaved();
            updateCheckState(view, NimarkoConfig.dialogsMenuShowSaved);
        } else if (id == ID_SETTINGS) {
            NimarkoConfig.toggleDialogsMenuShowSettings();
            updateCheckState(view, NimarkoConfig.dialogsMenuShowSettings);
        } else if (id == ID_REORDER) {
            presentFragment(new DialogsMenuOrderPreferencesActivity());
        }
    }
}
