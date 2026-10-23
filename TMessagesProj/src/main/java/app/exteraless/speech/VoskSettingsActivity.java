package app.exteraless.speech;

import android.view.View;
import java.util.ArrayList;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import app.nimarkogram.messenger.preferences.BasePreferencesActivity;

public class VoskSettingsActivity extends BasePreferencesActivity implements VoskManager.DownloadListener {
    @Override public String getTitle() { return LocaleController.getString(R.string.LX_Offline); }
    @Override public boolean onFragmentCreate() { VoskManager.addListener(this); return super.onFragmentCreate(); }
    @Override public void onFragmentDestroy() { VoskManager.removeListener(this); super.onFragmentDestroy(); }
    @Override public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int i = 0;
        for (VoskModel model : VoskModels.all()) {
            String value = VoskManager.isInstalled(model.code)
                    ? (model.code.equals(VoskManager.getLanguage()) ? "✓ " : "") + LocaleController.getString(R.string.LX_ModelReady)
                    : LocaleController.getString(R.string.LX_ModelDownload) + " · " + (model.size / 1048576) + " MB";
            if (model.code.equals(VoskManager.getDownloadingCode())) value = (VoskManager.getDownloadedBytes() / 1048576) + " / " + (VoskManager.getTotalBytes() / 1048576) + " MB";
            items.add(UItem.asButton(++i, model.language, value));
        }
        if (VoskManager.getDownloadingCode() != null) items.add(UItem.asButton(900, LocaleController.getString(R.string.Cancel)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.LX_ModelInfo)));
    }
    @Override public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 900) { VoskManager.cancelDownload(); return; }
        if (item.id < 1 || item.id > VoskModels.all().size()) return;
        VoskModel model = VoskModels.all().get(item.id - 1);
        if (VoskManager.isInstalled(model.code)) VoskManager.setLanguage(model.code);
        else VoskManager.download(model);
        listView.adapter.update(false);
    }
    @Override public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < 1 || item.id > VoskModels.all().size()) return false;
        VoskModel model = VoskModels.all().get(item.id - 1);
        if (!VoskManager.isInstalled(model.code)) return false;
        new AlertDialog.Builder(getContext()).setTitle(LocaleController.getString(R.string.Delete) + " · " + model.language)
                .setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> { VoskManager.delete(model.code); listView.adapter.update(false); })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).show();
        return true;
    }
    @Override public void onProgress(long done, long total) { if (listView != null) listView.adapter.update(false); }
    @Override public void onFinished(VoskModel model, Throwable error) {
        if (listView != null) listView.adapter.update(false);
        if (error != null && getContext() != null) new AlertDialog.Builder(getContext()).setMessage(LocaleController.getString(R.string.LX_RecognitionError))
                .setPositiveButton(LocaleController.getString(R.string.OK), null).show();
    }
}
