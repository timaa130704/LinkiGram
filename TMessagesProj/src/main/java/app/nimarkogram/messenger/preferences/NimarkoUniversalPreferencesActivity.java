package app.nimarkogram.messenger.preferences;

import android.content.Context;
import android.view.View;

import androidx.core.view.ViewCompat;

import org.telegram.ui.Components.UniversalFragment;

public abstract class NimarkoUniversalPreferencesActivity extends UniversalFragment {

    protected void showRestartBulletin() {
        org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(
                org.telegram.messenger.R.raw.info,
                org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.NM_RestartRequired),
                org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.NM_Restart),
                () -> {
                    android.content.Context ctx = getParentActivity() != null ? getParentActivity() : getContext();
                    app.nimarkogram.messenger.utils.AppRestartHelper.triggerRebirth(ctx);
                }
        ).show();
    }

    protected void setMD3(boolean enabled) {
        // Compatibility hook retained for the LinkiGram settings activities.
    }

    protected static void updateCheckState(android.view.View view, boolean checked) {
        if (view instanceof org.telegram.ui.Cells.TextCheckCell) {
            ((org.telegram.ui.Cells.TextCheckCell) view).setChecked(checked);
        } else if (view instanceof org.telegram.ui.Cells.NotificationsCheckCell) {
            ((org.telegram.ui.Cells.NotificationsCheckCell) view).setChecked(checked);
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        
        ViewCompat.setOnApplyWindowInsetsListener(view, this::onInsetsInternal);
        ViewCompat.requestApplyInsets(view);
        return view;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (listView != null) {
            listView.setPadding(0, 0, 0, bottom);
            listView.setClipToPadding(false);
        }
    }
}
