package app.nimarkogram.messenger.preferences;

import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import app.nimarkogram.messenger.NimarkoConfig;

/** Shortcuts to the privacy presets already supported by LinkiGram. */
public final class QuickPrivacyModesActivity extends BasePreferencesActivity {
    private static final int NORMAL = 1;
    private static final int GHOST = 2;
    private static final int PARANOIA = 3;

    @Override public String getTitle() { return LocaleController.getString(R.string.NM_QuickPrivacyModes); }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_QuickPrivacyModesInfo)));
        items.add(UItem.asButton(NORMAL, R.drawable.msg_settings_solar, LocaleController.getString(R.string.NM_PresetNormal)));
        items.add(UItem.asButton(GHOST, R.drawable.ghost_solar, LocaleController.getString(R.string.NM_PresetGhost)));
        items.add(UItem.asButton(PARANOIA, R.drawable.msg_secret_solar, LocaleController.getString(R.string.NM_PresetParanoia)));
        items.add(UItem.asShadow(null));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        int preset = item.id == NORMAL ? NimarkoConfig.PRESET_NORMAL
                : item.id == GHOST ? NimarkoConfig.PRESET_GHOST
                : item.id == PARANOIA ? NimarkoConfig.PRESET_PARANOIA : -1;
        if (preset < 0) return;
        NimarkoConfig.applyPrivacyPreset(preset);
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
        BulletinFactory.of(this).createSimpleBulletin(R.raw.done,
                LocaleController.getString(R.string.NM_QuickPrivacyModesApplied)).show();
    }
}
