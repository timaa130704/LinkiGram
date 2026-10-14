package app.exteraless.pillstack.pills;

import android.content.Context;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedReader;
import java.io.FileReader;

import app.nimarkogram.messenger.infocards.InfoCardType;

/**
 * CPU load in percent from {@code /proc/stat}: the busy share of ticks between
 * two samples. The first sample only records the baseline, so no number appears
 * until the second. Adapted from exteraless (GPL)
 * {@code app.exteraless.pillstack.pills.CpuPill}.
 */
public class CpuCard extends TelemetryCard {

    private static long previousTotal = -1;
    private static long previousIdle;

    public CpuCard(Context context, Theme.ResourcesProvider resourcesProvider, int iconRes) {
        super(context, resourcesProvider, iconRes);
    }

    @Override
    public int getCardId() {
        return InfoCardType.CPU.id;
    }

    @Override
    public long getRefreshInterval() {
        return 3_000L;
    }

    @Override
    protected String measureText() {
        try {
            long[] stat = readCpuStat();
            if (stat == null) {
                return null;
            }
            long idle = stat[0];
            long total = stat[1];
            if (previousTotal < 0) {
                previousIdle = idle;
                previousTotal = total;
                return null;
            }
            long deltaTotal = total - previousTotal;
            long deltaIdle = idle - previousIdle;
            previousIdle = idle;
            previousTotal = total;
            if (deltaTotal <= 0) {
                return null;
            }
            long percent = (deltaTotal - deltaIdle) * 100 / deltaTotal;
            return Math.max(0, Math.min(percent, 100)) + "%";
        } catch (Throwable t) {
            return null;
        }
    }

    /** [0] - idle+iowait, [1] - the sum of all ticks. */
    private static long[] readCpuStat() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) {
                return null;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 8) {
                return null;
            }
            long total = 0;
            for (int i = 1; i <= 8 && i < parts.length; i++) {
                total += Long.parseLong(parts[i]);
            }
            long idle = Long.parseLong(parts[4]) + Long.parseLong(parts[5]);
            return new long[]{idle, total};
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }
}