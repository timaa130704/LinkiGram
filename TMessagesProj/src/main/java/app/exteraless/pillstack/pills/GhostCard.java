package app.exteraless.pillstack.pills;

import android.content.Context;
import android.view.Gravity;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;

import app.nimarkogram.messenger.NimarkoConfig;
import app.nimarkogram.messenger.infocards.BaseInfoCard;
import app.nimarkogram.messenger.infocards.InfoCardType;
import app.nimarkogram.messenger.infocards.preferences.InfoCardsPreferencesActivity;
import app.nimarkogram.messenger.preferences.GeneralPreferencesActivity;

/**
 * Ghost mode: tap toggles it, long press opens its settings. Adapted from
 * exteraless (GPL) {@code app.exteraless.pillstack.pills.GhostPill} onto
 * LinkiGram's {@link NimarkoConfig} ghost toggles.
 */
public class GhostCard extends BaseInfoCard implements NotificationCenter.NotificationCenterDelegate {

    private int observedAccount = -1;

    public GhostCard(Context context, Theme.ResourcesProvider resourcesProvider, int iconRes) {
        super(context, resourcesProvider);
        setIcon(iconRes);
    }

    @Override
    public int getCardId() {
        return InfoCardType.GHOST.id;
    }

    @Override
    public long getRefreshInterval() {
        return 0;
    }

    @Override
    protected void onAttachedToWindow() {
        bindObserver();
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (observedAccount >= 0) {
            NotificationCenter.getInstance(observedAccount)
                    .removeObserver(this, NotificationCenter.mainUserInfoChanged);
            observedAccount = -1;
        }
        super.onDetachedFromWindow();
    }

    private void bindObserver() {
        final int selected = UserConfig.selectedAccount;
        if (observedAccount == selected) {
            return;
        }
        if (observedAccount >= 0) {
            NotificationCenter.getInstance(observedAccount)
                    .removeObserver(this, NotificationCenter.mainUserInfoChanged);
        }
        observedAccount = selected;
        NotificationCenter.getInstance(observedAccount)
                .addObserver(this, NotificationCenter.mainUserInfoChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mainUserInfoChanged) {
            onUpdateData(true);
        }
    }

    @Override
    public void onUpdateData(boolean force) {
        bindObserver();
        setText(LocaleController.getString(R.string.NM_GhostMode), true);
        applyColorMode();
        markDataUpdated();
    }

    @Override
    public void onCardClicked() {
        final boolean wasActive = NimarkoConfig.isGhostModeActive();
        NimarkoConfig.setGhostMode(!wasActive);
        onUpdateData(true);
        NotificationCenter.getInstance(UserConfig.selectedAccount)
                .postNotificationName(NotificationCenter.mainUserInfoChanged);

        final BaseFragment fragment = TelemetryCard.getCurrentFragment();
        if (fragment != null) {
            BulletinFactory.of(fragment)
                    .createSuccessBulletin(LocaleController.getString(
                            wasActive ? R.string.OECardsGhostOff : R.string.OECardsGhostOn))
                    .show();
        }
    }

    @Override
    public boolean onCardLongClicked() {
        final BaseFragment fragment = TelemetryCard.getCurrentFragment();
        if (fragment == null) {
            return false;
        }
        final ItemOptions options = ItemOptions.makeOptions(fragment, this);
        options.add(R.drawable.ghost_solar, LocaleController.getString(R.string.NM_GhostMode),
                () -> fragment.presentFragment(new GeneralPreferencesActivity()));
        options.add(R.drawable.msg_settings, LocaleController.getString(R.string.Settings),
                () -> fragment.presentFragment(new InfoCardsPreferencesActivity()));
        options.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)
                .show();
        return true;
    }

    @Override
    protected int contentColorOverride() {
        return NimarkoConfig.isGhostModeActive()
                ? Theme.getColor(Theme.key_windowBackgroundWhiteGreenText, resourcesProvider)
                : 0;
    }

    @Override
    public void updateColors() {
        applyColorMode();
    }
}