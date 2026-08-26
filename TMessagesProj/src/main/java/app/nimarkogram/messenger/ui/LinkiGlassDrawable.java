package app.nimarkogram.messenger.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

/**
 * Linki Ass: стеклянная подложка для тостов, шитов и диалогов.
 *
 * Рисует полупрозрачную основу заданного цвета, световой блик по верхней кромке
 * и тонкую обводку — тот же язык, что у стеклянных ползунков и панелей.
 */
public class LinkiGlassDrawable extends Drawable {

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final float radius;
    private final boolean dark;
    private int alpha = 255;

    public LinkiGlassDrawable(float radius, int color) {
        this(radius, color, 0.82f);
    }

    /**
     * @param radius     радиус скругления в пикселях
     * @param color      базовый цвет подложки
     * @param opacity    доля непрозрачности базы (0..1), остальное отдаётся стеклу
     */
    public LinkiGlassDrawable(float radius, int color, float opacity) {
        this.radius = radius;
        this.dark = AndroidUtilities.computePerceivedBrightness(color) < 0.721f;

        final int baseAlpha = (int) (Math.max(0f, Math.min(1f, opacity)) * 255);
        basePaint.setColor(Color.argb(baseAlpha, Color.red(color), Color.green(color), Color.blue(color)));

        glarePaint.setColor(dark ? 0x1FFFFFFF : 0x33FFFFFF);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dpf2(0.66f));
        strokePaint.setColor(dark ? 0x24FFFFFF : 0x1A000000);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }
        rect.set(b);
        canvas.drawRoundRect(rect, radius, radius, basePaint);

        // блик по верхней кромке
        final float glareHeight = Math.min(rect.height() * 0.42f, AndroidUtilities.dpf2(18));
        rect.set(b.left, b.top, b.right, b.top + glareHeight);
        canvas.drawRoundRect(rect, radius, radius, glarePaint);

        rect.set(b);
        rect.inset(strokePaint.getStrokeWidth() / 2f, strokePaint.getStrokeWidth() / 2f);
        canvas.drawRoundRect(rect, radius, radius, strokePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) {
            return;
        }
        this.alpha = alpha;
        basePaint.setAlpha(basePaint.getAlpha() * alpha / 255);
        glarePaint.setAlpha(glarePaint.getAlpha() * alpha / 255);
        strokePaint.setAlpha(strokePaint.getAlpha() * alpha / 255);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        basePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
