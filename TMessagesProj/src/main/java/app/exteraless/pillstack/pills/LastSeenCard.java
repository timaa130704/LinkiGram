package app.exteraless.pillstack.pills;

import android.content.Context;
import android.view.Gravity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;

import app.nimarkogram.messenger.infocards.BaseInfoCard;
import app.nimarkogram.messenger.infocards.InfoCardType;
import app.nimarkogram.messenger.infocards.preferences.InfoCardsPreferencesActivity;

/**
 * The account's own last-seen status. Adapted from exteraless (GPL)
 * {@code app.exteraless.pillstack.pills.LastSeenPill}; the exteraless
 * {@code OwnLastSeen} helper is not present here, so the fallback goes through
 * {@link LocaleController#formatUserStatus(int, TLRPC.User)}.
 */
public class LastSeenCard extends BaseInfoCard {

    private boolean lifecycleAttached;
    private int requestGeneration;
    private boolean online;

    public LastSeenCard(Context context, Theme.ResourcesProvider resourcesProvider, int iconRes) {
        super(context, resourcesProvider);
        setIcon(iconRes);
    }

    @Override
    public int getCardId() {
        return InfoCardType.LAST_SEEN.id;
    }

    @Override
    public long getRefreshInterval() {
        return 60_000L;
    }

    @Override
    protected void onAttachedToWindow() {
        lifecycleAttached = true;
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        lifecycleAttached = false;
        requestGeneration++;
        super.onDetachedFromWindow();
    }

    @Override
    public void onUpdateData(boolean force) {
        if (!lifecycleAttached || !isAttachedToWindow()) {
            return;
        }
        final int account = UserConfig.selectedAccount;
        final TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        if (self == null) {
            setStatus("\u2014", false);
            return;
        }
        final TLRPC.InputUser inputUser = MessagesController.getInstance(account).getInputUser(self);
        if (inputUser == null) {
            setStatus("\u2014", false);
            return;
        }
        final int generation = ++requestGeneration;
        if (force) {
            startLoading();
        }
        final TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
        req.id.add(inputUser);
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (generation != requestGeneration || !lifecycleAttached || !isAttachedToWindow()) {
                return;
            }
            stopLoading();
            TLRPC.User user = null;
            if (res instanceof Vector) {
                for (Object object : ((Vector) res).objects) {
                    if (object instanceof TLRPC.User && ((TLRPC.User) object).id == self.id) {
                        user = (TLRPC.User) object;
                    }
                }
            }
            if (user == null || user.status == null) {
                setStatus("\u2014", false);
                return;
            }
            final boolean isOnline = user.status instanceof TLRPC.TL_userStatusOnline
                    && user.status.expires > ConnectionsManager.getInstance(account).getCurrentTime();
            final CharSequence status = isOnline
                    ? LocaleController.getString(R.string.Online)
                    : LocaleController.formatUserStatus(account, user);
            setStatus(status, isOnline);
            markDataUpdated();
        }));
    }

    private void setStatus(CharSequence text, boolean isOnline) {
        setText(text, true);
        if (online != isOnline) {
            online = isOnline;
            applyColorMode();
        }
    }

    @Override
    public void onCardClicked() {
        onUpdateData(true);
    }

    @Override
    public boolean onCardLongClicked() {
        final BaseFragment fragment = TelemetryCard.getCurrentFragment();
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
    protected int contentColorOverride() {
        return online
                ? Theme.getColor(Theme.key_windowBackgroundWhiteGreenText, resourcesProvider)
                : 0;
    }

    @Override
    public void updateColors() {
        applyColorMode();
    }
}