package app.exteraless.pillstack.pills;

import android.app.ActivityManager;
import android.content.Context;

import org.telegram.ui.ActionBar.Theme;

import app.nimarkogram.messenger.infocards.InfoCardType;

/**
 * RAM usage in percent. Adapted from exteraless (GPL)
 * {@code app.exteraless.pillstack.pills.RamPill}.
 */
public class RamCard extends TelemetryCard {

    public RamCard(Context context, Theme.ResourcesProvider resourcesProvider, int iconRes) {
        super(context, resourcesProvider, iconRes);
    }

    @Override
    public int getCardId() {
        return InfoCardType.RAM.id;
    }

    @Override
    public long getRefreshInterval() {
        return 3_000L;
    }

    @Override
    protected String measureText() {
        try {
            ActivityManager manager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) {
                return null;
            }
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            if (info.totalMem <= 0) {
                return null;
            }
            long used = info.totalMem - info.availMem;
            return (used * 100 / info.totalMem) + "%";
        } catch (Throwable t) {
            return null;
        }
    }
}