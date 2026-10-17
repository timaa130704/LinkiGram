package app.nimarkogram.messenger.appearance;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public final class MaterialProgress {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();

    public void draw(Canvas canvas, RectF bounds, float start, float sweep, Paint indicator, boolean wavy) {
        float radius = Math.min(bounds.width(), bounds.height()) / 2f;
        if (radius <= 0) return;
        float angle = Math.max(-360, Math.min(360, sweep));
        float gap = (float) Math.toDegrees(AndroidUtilities.dp(2) / radius);
        float remainder = 360 - Math.abs(angle) - 2 * gap;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setStrokeWidth(indicator.getStrokeWidth());
        track.setColor(Theme.multAlpha(indicator.getColor(), 0.2f));
        if (remainder > 0) canvas.drawArc(bounds, start + (angle >= 0 ? angle : 0) + gap, remainder, false, track);
        if (!wavy || Math.abs(angle) >= 359) {
            canvas.drawArc(bounds, start, angle, false, indicator);
            return;
        }
        wave.rewind();
        int points = Math.max(8, (int) (Math.abs(angle) / 2));
        double phase = SystemClock.elapsedRealtime() / 1000.0 * AndroidUtilities.dp(5);
        for (int i = 0; i <= points; i++) {
            float fraction = i / (float) points;
            double radians = Math.toRadians(start + angle * fraction);
            double along = radius * Math.abs(Math.toRadians(angle)) * fraction + phase;
            double r = radius + AndroidUtilities.dpf2(1.6f) * Math.sin(along * 2 * Math.PI / AndroidUtilities.dp(15));
            float x = bounds.centerX() + (float) (r * Math.cos(radians));
            float y = bounds.centerY() + (float) (r * Math.sin(radians));
            if (i == 0) wave.moveTo(x, y); else wave.lineTo(x, y);
        }
        canvas.drawPath(wave, indicator);
    }
}
