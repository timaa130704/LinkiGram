package app.exteraless.pillstack.pills;

import android.content.Context;
import android.view.Gravity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.LaunchActivity;

import app.nimarkogram.messenger.infocards.BaseInfoCard;
import app.nimarkogram.messenger.infocards.preferences.InfoCardsPreferencesActivity;

/**
 * Base for local-telemetry info cards (RAM, CPU, network speed, DC ping).
 * Adapted from exteraless (GPL) {@code app.exteraless.pillstack.pills.TelemetryPill}
 * to LinkiGram's {@link BaseInfoCard} info-card system.
 *
 * <p>The measurement runs off the UI thread; only the finished text is applied on
 * the UI thread, so a 2-3 second refresh never blocks drawing.
 */
public abstract class TelemetryCard extends BaseInfoCard {

    private final int iconRes;
    private volatile int measureGeneration;

    public TelemetryCard(Context context, Theme.ResourcesProvider resourcesProvider, int iconRes) {
        super(context, resourcesProvider);
        this.iconRes = iconRes;
        setIcon(iconRes);
    }

    /** Measure the value; {@code null} means "unknown" and renders a dash. */
    protected abstract String measureText();

    @Override
    public void onUpdateData(boolean force) {
        final int generation = ++measureGeneration;
        final int icon = iconRes;
        Utilities.globalQueue.postRunnable(() -> {
            String measured;
            try {
                measured = measureText();
            } catch (Throwable t) {
                measured = null;
            }
            final String text = measured != null ? measured : "\u2014";
            AndroidUtilities.runOnUIThread(() -> {
                if (generation != measureGeneration || !isAttachedToWindow()) {
                    return;
                }
                setIcon(icon);
                setText(text, true);
                markDataUpdated();
            });
        });
    }

    @Override
    public void onCardClicked() {
        onUpdateData(true);
    }

    @Override
    public boolean onCardLongClicked() {
        final BaseFragment fragment = getCurrentFragment();
        if (fragment == null) {
            return false;
        }
        final ItemOptions options = ItemOptions.makeOptions(fragment, this);
        options.add(R.drawable.msg_retry, LocaleController.getString(R.string.Refresh),
                () -> onUpdateData(true));
        options.add(R.drawable.msg_settings, LocaleController.getString(R.string.Settings),
                () -> fragment.presentFragment(new InfoCardsPreferencesActivity()));
        options.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)
                .show();
        return true;
    }

    @Override
    public void updateColors() {
        applyColorMode();
    }

    static BaseFragment getCurrentFragment() {
        try {
            LaunchActivity la = LaunchActivity.instance;
            if (la != null) {
                INavigationLayout layout = la.getActionBarLayout();
                if (layout != null) {
                    return layout.getLastFragment();
                }
            }
        } catch (Throwable ignore) {
        }
        return null;
    }
}