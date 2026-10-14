package app.exteraless.pillstack.pills;

import android.content.Context;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.Theme;

import app.nimarkogram.messenger.infocards.InfoCardType;

/**
 * Ping to the current account's data center. The number comes from the native
 * MTProto connection ({@code native_getCurrentPingTime}); no sockets of our own.
 * Adapted from exteraless (GPL)
 * {@code app.exteraless.pillstack.pills.DcPingPill}.
 */
public class DcPingCard extends TelemetryCard {

    public DcPingCard(Context context, Theme.ResourcesProvider resourcesProvider, int iconRes) {
        super(context, resourcesProvider, iconRes);
    }

    @Override
    public int getCardId() {
        return InfoCardType.DC_PING.id;
    }

    @Override
    public long getRefreshInterval() {
        return 5_000L;
    }

    @Override
    protected String measureText() {
        try {
            int ping = ConnectionsManager.native_getCurrentPingTime(UserConfig.selectedAccount);
            if (ping <= 0) {
                return null;
            }
            return LocaleController.formatString(R.string.OECardsDcPingValue, ping);
        } catch (Throwable t) {
            return null;
        }
    }
}