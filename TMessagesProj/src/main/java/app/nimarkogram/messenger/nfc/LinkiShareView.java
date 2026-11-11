package app.nimarkogram.messenger.nfc;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * The Linki-Share visual: two avatars that connect while the phones are held
 * together, with expanding rings and a burst once the profile is received.
 */
public class LinkiShareView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint letterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final ValueAnimator animator;
    private float phase;

    private int accentColor;
    private int surfaceColor;
    private int textColor;
    private int mutedColor;

    private String selfLabel = "";
    private String peerLabel = null;
    private boolean success;
    private long successAt;

    public LinkiShareView(Context context) {
        super(context);
        ringPaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStyle(Paint.Style.STROKE);
        letterPaint.setTextAlign(Paint.Align.CENTER);
        letterPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    public void setColors(int accent, int surface, int text, int muted) {
        accentColor = accent;
        surfaceColor = surface;
        textColor = text;
        mutedColor = muted;
        invalidate();
    }

    public void setSelfLabel(String label) {
        selfLabel = label == null ? "" : label;
        invalidate();
    }

    public void setPeerLabel(String label) {
        peerLabel = label;
        invalidate();
    }

    public void setSuccess(boolean value) {
        success = value;
        successAt = android.os.SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!animator.isStarted()) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        final float cx = w / 2f;
        final float cy = h / 2f;
        final float radius = Math.min(w, h) * 0.115f;
        final float gap = radius * 2.15f;

        final float selfCx = cx - (success ? gap / 2f : 0f);
        final float peerCx = cx + gap / 2f;

        if (!success) {
            // Concentric rings pulsing outwards from the own avatar.
            for (int i = 0; i < 3; i++) {
                final float p = (phase + i / 3f) % 1f;
                ringPaint.setColor(accentColor);
                ringPaint.setAlpha((int) (140 * (1f - p)));
                ringPaint.setStrokeWidth(Math.max(1f, radius * 0.05f));
                canvas.drawCircle(selfCx, cy, radius * (1f + p * 1.6f), ringPaint);
            }
        } else {
            final long since = android.os.SystemClock.uptimeMillis() - successAt;
            if (since < 900) {
                final float p = since / 900f;
                ringPaint.setColor(accentColor);
                ringPaint.setAlpha((int) (190 * (1f - p)));
                ringPaint.setStrokeWidth(Math.max(1f, radius * 0.09f * (1f - p * 0.5f)));
                canvas.drawCircle(cx, cy, radius * (1.1f + p * 2.6f), ringPaint);
                postInvalidateOnAnimation();
            }
            // A gradient beam between the two avatars.
            beamPaint.setShader(new LinearGradient(
                    selfCx + radius, cy, peerCx - radius, cy,
                    new int[]{accentColor, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            beamPaint.setStrokeWidth(radius * 0.22f);
            beamPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(selfCx + radius, cy, peerCx - radius, cy, beamPaint);
            beamPaint.setShader(null);
        }

        drawAvatar(canvas, selfCx, cy, radius, selfLabel, true);
        if (success || peerLabel != null) {
            drawAvatar(canvas, peerCx, cy, radius, peerLabel, success);
        }
    }

    private void drawAvatar(Canvas canvas, float x, float y, float radius, String label, boolean filled) {
        final RectF oval = new RectF(x - radius, y - radius, x + radius, y + radius);
        if (filled) {
            circlePaint.setColor(surfaceColor);
            canvas.drawOval(oval, circlePaint);
            outlinePaint.setColor(accentColor);
            outlinePaint.setStrokeWidth(Math.max(2f, radius * 0.07f));
            canvas.drawOval(oval, outlinePaint);
            letterPaint.setColor(textColor);
            letterPaint.setTextSize(radius * 0.86f);
            canvas.drawText(initials(label), x, y + radius * 0.30f, letterPaint);
        } else {
            outlinePaint.setColor(mutedColor);
            outlinePaint.setStrokeWidth(Math.max(2f, radius * 0.06f));
            outlinePaint.setAlpha(200);
            canvas.drawOval(oval, outlinePaint);
            outlinePaint.setAlpha(255);
            letterPaint.setColor(mutedColor);
            letterPaint.setTextSize(radius * 0.86f);
            canvas.drawText("?", x, y + radius * 0.30f, letterPaint);
        }
    }

    private static String initials(String label) {
        if (label == null || label.trim().isEmpty()) {
            return "";
        }
        final String[] parts = label.trim().split("\\s+");
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length && i < 2; i++) {
            if (!parts[i].isEmpty()) {
                out.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return out.toString();
    }
}