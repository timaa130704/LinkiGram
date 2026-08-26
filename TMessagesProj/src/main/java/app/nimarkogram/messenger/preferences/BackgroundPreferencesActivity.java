package app.nimarkogram.messenger.preferences;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.io.File;

import app.nimarkogram.messenger.NimarkoConfig;
import app.nimarkogram.messenger.NimarkoWallpaper;

public class BackgroundPreferencesActivity extends BasePreferencesActivity {
    private static final int ID_ENABLE = 1;
    private static final int ID_PICK = 2;
    private static final int ID_DIM = 3;
    private static final int ID_CARD_ALPHA = 7;
    private static final int ID_REMOVE = 4;
    private static final int ID_CHAT_GLASS = 5;
    private static final int ID_LIQUID_GLASS = 6;
    private static final int FILE_PICK_CODE = 7301;

    @Override
    public void fillItems(java.util.ArrayList<UItem> arrayList, UniversalAdapter universalAdapter) {
        arrayList.add(UItem.asHeader(LocaleController.getString(R.string.NM_BG_Header)));
        arrayList.add(UItem.asCheck(ID_ENABLE,
                        LocaleController.getString(R.string.NM_BG_Enable))
                .setChecked(NimarkoConfig.customBgEnabled));
        arrayList.add(UItem.asShadow(LocaleController.getString(R.string.NM_BG_Enable_Desc)));
        if (NimarkoConfig.customBgEnabled) {
            arrayList.add(UItem.asButton(ID_PICK, LocaleController.getString(R.string.NM_BG_Pick)));
            arrayList.add(UItem.asHeader(LocaleController.getString(R.string.NM_BG_Dim)));
            arrayList.add(UItem.asIntSlideView(1, 0, NimarkoConfig.customBgDimPercent, 80,
                    val -> val + "%",
                    val -> {
                        NimarkoConfig.setCustomBgDimPercent(val);
                        invalidateWallpaperViews();
                    }));
            arrayList.add(UItem.asHeader(LocaleController.getString(R.string.NM_BG_Cards)));
            arrayList.add(UItem.asIntSlideView(1, 30, NimarkoConfig.customBgCardAlpha, 100,
                    val -> val + "%",
                    val -> {
                        NimarkoConfig.setCustomBgCardAlpha(val);
                        invalidateWallpaperViews();
                    }));
            if (NimarkoWallpaper.hasImage()) {
                arrayList.add(UItem.asButton(ID_REMOVE, R.drawable.msg_reset, LocaleController.getString(R.string.NM_BG_Remove)));
            }
        }
        arrayList.add(UItem.asShadow(null));

        arrayList.add(UItem.asHeader(LocaleController.getString(R.string.NM_GLASS_Header)));
        arrayList.add(UItem.asCheck(ID_LIQUID_GLASS, LocaleController.getString(R.string.NM_GLASS_Liquid))
                .setChecked(NimarkoConfig.linkiAss));
        arrayList.add(UItem.asShadow(LocaleController.getString(R.string.NM_GLASS_Liquid_Desc)));
        arrayList.add(UItem.asButton(ID_CHAT_GLASS, LocaleController.getString(R.string.NM_GLASS_ChatLevel)
                + " · " + (NimarkoConfig.chatGlassLevel == 0
                ? LocaleController.getString(R.string.NM_GLASS_Default)
                : "+" + NimarkoConfig.chatGlassPercent() + "%")));
        arrayList.add(UItem.asShadow(LocaleController.getString(R.string.NM_GLASS_ChatLevel_Desc)));
    }

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.NM_BG_Title);
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == ID_ENABLE) {
            NimarkoConfig.toggleCustomBgEnabled();
            applyCheck(item, view, NimarkoConfig.customBgEnabled);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (id == ID_LIQUID_GLASS) {
            NimarkoConfig.toggleLinkiAss();
            applyCheck(item, view, NimarkoConfig.linkiAss);
            rebuildAll();
        } else if (id == ID_CHAT_GLASS) {
            NimarkoConfig.cycleChatGlassLevel();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            rebuildAll();
        } else if (id == ID_PICK) {
            openChooser();
        } else if (id == ID_REMOVE) {
            NimarkoWallpaper.removeImage();
            NimarkoConfig.setCustomBgPath("");
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            rebuildAll();
        }
    }

    @Override
    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void applyCheck(UItem item, View view, boolean value) {
        item.checked = value;
        updateCheckState(view, value);
    }

    private void openChooser() {
        try {
            Activity act = getParentActivity();
            if (act == null) return;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
            act.startActivityForResult(intent, FILE_PICK_CODE);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILE_PICK_CODE) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                NimarkoWallpaper.saveFromUri(uri);
                NimarkoConfig.setCustomBgPath(NimarkoWallpaper.getFile().getAbsolutePath());
                AndroidUtilities.runOnUIThread(this::rebuildAll);
            } catch (Throwable t) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (getParentActivity() != null) {
                        org.telegram.ui.Components.BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.NM_BG_Error)).show();
                    }
                });
            }
        });
    }

    private void invalidateWallpaperViews() {
        if (fragmentView instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) fragmentView;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View c = vg.getChildAt(i);
                if (c instanceof NimarkoWallpaper.WallpaperView) {
                    c.invalidate();
                }
            }
        }
    }

    private void rebuildAll() {
        if (getParentLayout() != null) {
            getParentLayout().rebuildAllFragmentViews(false, false);
        }
    }
}
