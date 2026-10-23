package app.nimarkogram.messenger.preferences;

import android.view.View;
import java.util.ArrayList;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import app.nimarkogram.messenger.features.ChatExtras;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

public class ChatExtrasPreferencesActivity extends BasePreferencesActivity {
    private final String[] keys = {"cleanOpen", "cleanPaste", "inlineMath", "confirmCall", "confirmRecording", "hdPhotos", "deletedQuote"};
    private final int[] titles = {R.string.LX_CleanOpen, R.string.LX_CleanPaste, R.string.LX_Math, R.string.LX_ConfirmCall, R.string.LX_ConfirmRecording, R.string.LX_HD, R.string.LX_DeletedQuote};
    @Override public String getTitle() { return LocaleController.getString(R.string.LX_Title); }
    @Override public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        for (int i = 0; i < keys.length; i++) items.add(SettingsHelper.asSwitchCG(i + 1, LocaleController.getString(titles[i])).setChecked(ChatExtras.enabled(keys[i])));
        items.add(UItem.asButton(100, R.drawable.msg_info_solar, LocaleController.getString(R.string.LX_ReadDates)));
        items.add(UItem.asButton(102, R.drawable.msg_settings_solar, LocaleController.getString(R.string.LX_Liner)));
        items.add(UItem.asButton(101, R.drawable.msg_settings_solar, LocaleController.getString(R.string.LX_Offline)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.LX_Info)));
    }
    @Override public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= 1 && item.id <= keys.length) {
            ChatExtras.toggle(keys[item.id - 1]); listView.adapter.update(false);
        } else if (item.id == 100) presentFragment(new AyuMomentsPreferencesActivity());
        else if (item.id == 102) presentFragment(new LinerLoginActivity());
        else if (item.id == 101) presentFragment(new app.exteraless.speech.VoskSettingsActivity());
    }
}
