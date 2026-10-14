package app.exteraless.pillstack.pills;

import android.content.Context;
import android.net.TrafficStats;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import app.nimarkogram.messenger.infocards.InfoCardType;

/**
 * Current network speed: the TrafficStats delta between samples. The dominant
 * direction is shown, e.g. "\u21931.2 MB/s" or "\u2191340 KB/s". Adapted from
 * exteraless (GPL) {@code app.exteraless.pillstack.pills.NetSpeedPill}.
 */
public class NetSpeedCard extends TelemetryCard {

    private static long previousRx = -1;
    private static long previousTx;
    private static long previousTime;

    public NetSpeedCard(Context context, Theme.ResourcesProvider resourcesProvider, int iconRes) {
        super(context, resourcesProvider, iconRes);
    }

    @Override
    public int getCardId() {
        return InfoCardType.NET_SPEED.id;
    }

    @Override
    public long getRefreshInterval() {
        return 2_000L;
    }

    @Override
    protected String measureText() {
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {
            return null;
        }
        long now = SystemClock.elapsedRealtime();
        if (previousRx < 0) {
            previousRx = rx;
            previousTx = tx;
            previousTime = now;
            return null;
        }
        long elapsed = now - previousTime;
        if (elapsed <= 0) {
            return null;
        }
        long rxSpeed = Math.max(0, rx - previousRx) * 1000 / elapsed;
        long txSpeed = Math.max(0, tx - previousTx) * 1000 / elapsed;
        previousRx = rx;
        previousTx = tx;
        previousTime = now;
        if (rxSpeed >= txSpeed) {
            return "\u2193" + AndroidUtilities.formatFileSize(rxSpeed) + "/s";
        }
        return "\u2191" + AndroidUtilities.formatFileSize(txSpeed) + "/s";
    }
}