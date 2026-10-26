/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.StateSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Keep;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;

import me.vkryl.android.animator.BoolAnimator;

public class Switch extends View {
    private final BoolAnimator animatorIconVisibility = new BoolAnimator(this, CubicBezierInterpolator.EASE_OUT_QUINT, 380L, true);

    private RectF rectF;

    private float progress;
    private ObjectAnimator checkAnimator;
    private ObjectAnimator iconAnimator;

    private boolean attachedToWindow;
    private boolean isChecked;
    private Paint paint;
    private Paint paint2;

    private int drawIconType;
    private float iconProgress = 1.0f;

    private OnCheckedChangeListener onCheckedChangeListener;

    private int trackColorKey = Theme.key_fill_RedNormal;
    private int trackCheckedColorKey = Theme.key_switch2TrackChecked;
    private int thumbColorKey = Theme.key_windowBackgroundWhite;
    private int thumbCheckedColorKey = Theme.key_windowBackgroundWhite;

    private Drawable iconDrawable;
    private int lastIconColor;

    private boolean drawRipple;
    private RippleDrawable rippleDrawable;
    private Paint ripplePaint;
    private int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};
    private int colorSet;

    private boolean bitmapsCreated;
    private Bitmap[] overlayBitmap;
    private Canvas[] overlayCanvas;
    private Bitmap overlayMaskBitmap;
    private Canvas overlayMaskCanvas;
    private float overlayCx;
    private float overlayCy;
    private float overlayRad;
    private Paint overlayEraserPaint;
    private Paint overlayMaskPaint;

    private Theme.ResourcesProvider resourcesProvider;

    private int overrideColorProgress;

    public interface OnCheckedChangeListener {
        void onCheckedChanged(Switch view, boolean isChecked);
    }

    public Switch(Context context) {
        this(context, null);
    }

    public Switch(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        rectF = new RectF();

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2.setStyle(Paint.Style.STROKE);
        paint2.setStrokeCap(Paint.Cap.ROUND);
        paint2.setStrokeWidth(AndroidUtilities.dp(2));

        setHapticFeedbackEnabled(true);
    }

    @Keep
    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        invalidate();
    }

    @Keep
    public float getProgress() {
        return progress;
    }

    @Keep
    public void setIconProgress(float value) {
        if (iconProgress == value) {
            return;
        }
        iconProgress = value;
        invalidate();
    }

    @Keep
    public float getIconProgress() {
        return iconProgress;
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
            checkAnimator = null;
        }
    }

    private void cancelIconAnimator() {
        if (iconAnimator != null) {
            iconAnimator.cancel();
            iconAnimator = null;
        }
    }

    public void setDrawIconType(int type) {
        drawIconType = type;
    }

    public void setDrawRipple(boolean value) {
        if (Build.VERSION.SDK_INT < 21 || value == drawRipple) {
            return;
        }
        drawRipple = value;

        if (rippleDrawable == null) {
            ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ripplePaint.setColor(0xffffffff);
            Drawable maskDrawable;
            if (Build.VERSION.SDK_INT >= 23) {
                maskDrawable = null;
            } else {
                maskDrawable = new Drawable() {
                    @Override
                    public void draw(Canvas canvas) {
                        android.graphics.Rect bounds = getBounds();
                        canvas.drawCircle(bounds.centerX(), bounds.centerY(), AndroidUtilities.dp(18), ripplePaint);
                    }

                    @Override
                    public void setAlpha(int alpha) {

                    }

                    @Override
                    public void setColorFilter(ColorFilter colorFilter) {

                    }

                    @Override
                    public int getOpacity() {
                        return PixelFormat.UNKNOWN;
                    }
                };
            }
            ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{0}
            );
            rippleDrawable = new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
            if (Build.VERSION.SDK_INT >= 23) {
                rippleDrawable.setRadius(AndroidUtilities.dp(18));
            }
            rippleDrawable.setCallback(this);
        }
        if (isChecked && colorSet != 2 || !isChecked && colorSet != 1) {
            int color = Theme.getColor(isChecked ? Theme.key_switchTrackBlueSelectorChecked : Theme.key_switchTrackBlueSelector, resourcesProvider);
            color = processColor(color);
            ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{color}
            );
            rippleDrawable.setColor(colorStateList);
            colorSet = isChecked ? 2 : 1;
        }
        if (Build.VERSION.SDK_INT >= 28 && value) {
            rippleDrawable.setHotspot(isChecked ? 0 : AndroidUtilities.dp(100), AndroidUtilities.dp(18));
        }
        rippleDrawable.setState(value ? pressedState : StateSet.NOTHING);
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || rippleDrawable != null && who == rippleDrawable;
    }

    protected int processColor(int color) {
        return color;
    }

    /** LinkiGram: MD3-переключатель (порт настроек exteraless, NM_Style_Switches). */
    private static boolean isMd3Style() {
        return app.nimarkogram.messenger.appearance.LinkiGramYouTheme.isEnabled()
                || app.nimarkogram.messenger.NimarkoConfig.switchStyle == app.nimarkogram.messenger.NimarkoConfig.SWITCH_STYLE_MD3;
    }

    public void setColors(int track, int trackChecked, int thumb, int thumbChecked) {
        trackColorKey = track;
        trackCheckedColorKey = trackChecked;
        thumbColorKey = thumb;
        thumbCheckedColorKey = thumbChecked;
    }

    private void animateToCheckedState(boolean newCheckedState) {
        checkAnimator = ObjectAnimator.ofFloat(this, "progress", newCheckedState ? 1 : 0);
        checkAnimator.setDuration(app.nimarkogram.messenger.appearance.ColorOs17Theme.isEnabled() ? 380 : 200);
        if (app.nimarkogram.messenger.appearance.ColorOs17Theme.isEnabled()) {
            checkAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        }
        checkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                checkAnimator = null;
            }
        });
        checkAnimator.start();
    }

    private void animateIcon(boolean newCheckedState) {
        iconAnimator = ObjectAnimator.ofFloat(this, "iconProgress", newCheckedState ? 1 : 0);
        iconAnimator.setDuration(200);
        iconAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                iconAnimator = null;
            }
        });
        iconAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        onCheckedChangeListener = listener;
    }

    public void setChecked(boolean checked, boolean animated) {
        setChecked(checked, drawIconType, animated);
    }

    public void setChecked(boolean checked, int iconType, boolean animated) {
        if (checked != isChecked) {
            isChecked = checked;
            if (attachedToWindow && animated) {
                animateToCheckedState(checked);
            } else {
                cancelCheckAnimator();
                setProgress(checked ? 1.0f : 0.0f);
            }
            if (onCheckedChangeListener != null) {
                onCheckedChangeListener.onCheckedChanged(this, checked);
            }
        }
        setDrawIconType(iconType, animated);
    }

    public void setIcon(int icon) {
        if (icon != 0) {
            iconDrawable = getResources().getDrawable(icon).mutate();
            if (iconDrawable != null) {
                iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = Theme.getColor(isChecked ? trackCheckedColorKey : trackColorKey, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            }
        } else {
            iconDrawable = null;
        }
        invalidate();
    }

    public void setIconVisible(boolean visible, boolean animated) {
        animatorIconVisibility.setValue(visible, animated);
    }

    public void setDrawIconType(int iconType, boolean animated) {
        if (drawIconType != iconType) {
            drawIconType = iconType;
            if (attachedToWindow && animated) {
                animateIcon(iconType == 0);
            } else {
                cancelIconAnimator();
                setIconProgress(iconType == 0 ? 1.0f : 0.0f);
            }
        }
    }

    public boolean hasIcon() {
        return iconDrawable != null;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setOverrideColor(int override) {
        if (overrideColorProgress == override) {
            return;
        }
        if (override == 0) {
            overrideColorProgress = 0;
            invalidate();
            return;
        }
        if (overlayBitmap == null) {
            try {
                overlayBitmap = new Bitmap[2];
                overlayCanvas = new Canvas[2];
                for (int a = 0; a < 2; a++) {
                    overlayBitmap[a] = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                    overlayCanvas[a] = new Canvas(overlayBitmap[a]);
                }
                overlayMaskBitmap = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                overlayMaskCanvas = new Canvas(overlayMaskBitmap);

                overlayEraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                overlayEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

                overlayMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                overlayMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                bitmapsCreated = true;
            } catch (Throwable e) {
                return;
            }
        }
        if (!bitmapsCreated) {
            return;
        }
        overrideColorProgress = override;
        overlayCx = 0;
        overlayCy = 0;
        overlayRad = 0;
        invalidate();
    }

    public void setOverrideColorProgress(float cx, float cy, float rad) {
        overlayCx = cx;
        overlayCy = cy;
        overlayRad = rad;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }
        if (app.nimarkogram.messenger.appearance.ColorOs17Theme.isEnabled()) {
            drawColorOsSwitch(canvas);
            return;
        }

        if (app.nimarkogram.messenger.appearance.LinkiGramYouTheme.isEnabled()) {
            drawMaterialYouSwitch(canvas);
            return;
        }

        int width = AndroidUtilities.dp(31);
        int thumb = AndroidUtilities.dp(20);
        int x = (getMeasuredWidth() - width) / 2;
        float y = (getMeasuredHeight() - AndroidUtilities.dpf2(14)) / 2;
        int tx = x + AndroidUtilities.dp(7) + (int) (AndroidUtilities.dp(17) * progress);
        int ty = getMeasuredHeight() / 2;
        if (isMd3Style()) tx = x + AndroidUtilities.dp(8) + (int) (AndroidUtilities.dp(16) * Math.max(0f, Math.min(1f, progress)));


        int color1;
        int color2;
        float colorProgress;
        int r1;
        int r2;
        int g1;
        int g2;
        int b1;
        int b2;
        int a1;
        int a2;
        int red;
        int green;
        int blue;
        int alpha;
        int color;

        for (int a = 0; a < 2; a++) {
            if (a == 1 && overrideColorProgress == 0) {
                continue;
            }
            Canvas canvasToDraw = a == 0 ? canvas : overlayCanvas[0];

            if (a == 1) {
                overlayBitmap[0].eraseColor(0);
                paint.setColor(0xff000000);
                overlayMaskCanvas.drawRect(0, 0, overlayMaskBitmap.getWidth(), overlayMaskBitmap.getHeight(), paint);
                overlayMaskCanvas.drawCircle(overlayCx - getX(), overlayCy - getY(), overlayRad, overlayEraserPaint);
            }
            if (overrideColorProgress == 1) {
                colorProgress = a == 0 ? 0 : 1;
            } else if (overrideColorProgress == 2) {
                colorProgress = a == 0 ? 1 : 0;
            } else {
                colorProgress = progress;
            }

            color1 = processColor(Theme.getColor(trackColorKey, resourcesProvider));
            color2 = processColor(Theme.getColor(trackCheckedColorKey, resourcesProvider));
            if (a == 0 && iconDrawable != null && lastIconColor != (isChecked ? color2 : color1)) {
                iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = (isChecked ? color2 : color1), PorterDuff.Mode.MULTIPLY));
            }

            r1 = Color.red(color1);
            r2 = Color.red(color2);
            g1 = Color.green(color1);
            g2 = Color.green(color2);
            b1 = Color.blue(color1);
            b2 = Color.blue(color2);
            a1 = Color.alpha(color1);
            a2 = Color.alpha(color2);

            red = (int) (r1 + (r2 - r1) * colorProgress);
            green = (int) (g1 + (g2 - g1) * colorProgress);
            blue = (int) (b1 + (b2 - b1) * colorProgress);
            alpha = (int) (a1 + (a2 - a1) * colorProgress);
            color = ((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff);
            paint.setColor(color);
            paint2.setColor(color);

            if (isMd3Style()) {
                final float trackH = AndroidUtilities.dp(26);
                rectF.set(-AndroidUtilities.dp(2), ty - trackH / 2f,
                        getMeasuredWidth() + AndroidUtilities.dp(3), ty + trackH / 2f);
                canvasToDraw.drawRoundRect(rectF, AndroidUtilities.dpf2(14), AndroidUtilities.dpf2(14), paint);
            } else {
                rectF.set(x, y, x + width, y + AndroidUtilities.dpf2(14));
                canvasToDraw.drawRoundRect(rectF, AndroidUtilities.dpf2(7), AndroidUtilities.dpf2(7), paint);
                canvasToDraw.drawCircle(tx, ty, AndroidUtilities.dpf2(10), paint);
            }

            if (a == 0 && rippleDrawable != null) {
                rippleDrawable.setBounds(tx - AndroidUtilities.dp(18), ty - AndroidUtilities.dp(18), tx + AndroidUtilities.dp(18), ty + AndroidUtilities.dp(18));
                rippleDrawable.draw(canvasToDraw);
            } else if (a == 1) {
                canvasToDraw.drawBitmap(overlayMaskBitmap, 0, 0, overlayMaskPaint);
            }
        }
        if (overrideColorProgress != 0) {
            canvas.drawBitmap(overlayBitmap[0], 0, 0, null);
        }

        for (int a = 0; a < 2; a++) {
            if (a == 1 && overrideColorProgress == 0) {
                continue;
            }
            Canvas canvasToDraw = a == 0 ? canvas : overlayCanvas[1];

            if (a == 1) {
                overlayBitmap[1].eraseColor(0);
            }
            if (overrideColorProgress == 1) {
                colorProgress = a == 0 ? 0 : 1;
            } else if (overrideColorProgress == 2) {
                colorProgress = a == 0 ? 1 : 0;
            } else {
                colorProgress = progress;
            }

            color1 = Theme.getColor(thumbColorKey, resourcesProvider);
            color2 = processColor(Theme.getColor(thumbCheckedColorKey, resourcesProvider));
            r1 = Color.red(color1);
            r2 = Color.red(color2);
            g1 = Color.green(color1);
            g2 = Color.green(color2);
            b1 = Color.blue(color1);
            b2 = Color.blue(color2);
            a1 = Color.alpha(color1);
            a2 = Color.alpha(color2);

            red = (int) (r1 + (r2 - r1) * colorProgress);
            green = (int) (g1 + (g2 - g1) * colorProgress);
            blue = (int) (b1 + (b2 - b1) * colorProgress);
            alpha = (int) (a1 + (a2 - a1) * colorProgress);
            paint.setColor(((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff));

            if (isMd3Style()) {
                final float p = Math.max(0f, Math.min(1f, progress));
                final float thumbR = AndroidUtilities.dp(9) - AndroidUtilities.dp(2) * (1f - p) * (1f - p);
                final float stretch = AndroidUtilities.dpf2(3) * (1f - Math.abs(2f * p - 1f));
                rectF.set(tx - thumbR - stretch, ty - thumbR, tx + thumbR + stretch, ty + thumbR);
                canvasToDraw.drawRoundRect(rectF, thumbR, thumbR, paint);
                continue;
            }
            canvasToDraw.drawCircle(tx, ty, AndroidUtilities.dp(8), paint);

            if (a == 0) {
                if (iconDrawable != null) {
                    final float factor = animatorIconVisibility.getFloatValue();
                    if (factor > 0) {
                        final boolean needScale = factor < 1;
                        if (needScale) {
                            canvas.save();
                            canvas.scale(factor, factor, tx, ty);
                        }
                        iconDrawable.setBounds(tx - iconDrawable.getIntrinsicWidth() / 2, ty - iconDrawable.getIntrinsicHeight() / 2, tx + iconDrawable.getIntrinsicWidth() / 2, ty + iconDrawable.getIntrinsicHeight() / 2);
                        iconDrawable.draw(canvasToDraw);
                        if (needScale) {
                            canvas.restore();
                        }
                    }
                } else if (drawIconType == 1) {
                    tx -= AndroidUtilities.dp(10.8f) - AndroidUtilities.dp(1.3f) * progress;
                    ty -= AndroidUtilities.dp(8.5f) - AndroidUtilities.dp(0.5f) * progress;
                    int startX2 = (int) AndroidUtilities.dpf2(4.6f) + tx;
                    int startY2 = (int) (AndroidUtilities.dpf2(9.5f) + ty);
                    int endX2 = startX2 + AndroidUtilities.dp(2);
                    int endY2 = startY2 + AndroidUtilities.dp(2);

                    int startX = (int) AndroidUtilities.dpf2(7.5f) + tx;
                    int startY = (int) AndroidUtilities.dpf2(5.4f) + ty;
                    int endX = startX + AndroidUtilities.dp(7);
                    int endY = startY + AndroidUtilities.dp(7);

                    startX = (int) (startX + (startX2 - startX) * progress);
                    startY = (int) (startY + (startY2 - startY) * progress);
                    endX = (int) (endX + (endX2 - endX) * progress);
                    endY = (int) (endY + (endY2 - endY) * progress);
                    canvasToDraw.drawLine(startX, startY, endX, endY, paint2);

                    startX = (int) AndroidUtilities.dpf2(7.5f) + tx;
                    startY = (int) AndroidUtilities.dpf2(12.5f) + ty;
                    endX = startX + AndroidUtilities.dp(7);
                    endY = startY - AndroidUtilities.dp(7);
                    canvasToDraw.drawLine(startX, startY, endX, endY, paint2);
                } else if (drawIconType == 2 || iconAnimator != null) {
                    paint2.setAlpha((int) (255 * (1.0f - iconProgress)));
                    canvasToDraw.drawLine(tx, ty, tx, ty - AndroidUtilities.dp(5), paint2);
                    canvasToDraw.save();
                    canvasToDraw.rotate(-90 * iconProgress, tx, ty);
                    canvasToDraw.drawLine(tx, ty, tx + AndroidUtilities.dp(4), ty, paint2);
                    canvasToDraw.restore();
                }
            }
            if (a == 1) {
                canvasToDraw.drawBitmap(overlayMaskBitmap, 0, 0, overlayMaskPaint);
            }
        }
        if (overrideColorProgress != 0) {
            canvas.drawBitmap(overlayBitmap[1], 0, 0, null);
        }
    }

    private void drawMaterialYouSwitch(Canvas canvas) {
        float p = Math.max(0f, Math.min(1f, progress));
        float scale = Math.min(getMeasuredWidth() / AndroidUtilities.dpf2(52), getMeasuredHeight() / AndroidUtilities.dpf2(32));
        if (scale <= 0) return;
        int saved = canvas.save();
        canvas.translate(getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
        canvas.scale(scale, scale);
        final float halfWidth = AndroidUtilities.dpf2(26), halfHeight = AndroidUtilities.dpf2(16);
        int primary = app.nimarkogram.messenger.appearance.ClientTheme.color(app.nimarkogram.messenger.appearance.ClientTheme.PRIMARY);
        int outline = app.nimarkogram.messenger.appearance.ClientTheme.color(app.nimarkogram.messenger.appearance.ClientTheme.OUTLINE);
        paint.setColor(processColor(androidx.core.graphics.ColorUtils.blendARGB(
                app.nimarkogram.messenger.appearance.ClientTheme.color(app.nimarkogram.messenger.appearance.ClientTheme.TRACK), primary, p)));
        rectF.set(-halfWidth, -halfHeight, halfWidth, halfHeight);
        canvas.drawRoundRect(rectF, halfHeight, halfHeight, paint);
        if (p < 1f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dpf2(2));
            paint.setColor(processColor(Theme.multAlpha(outline, 1 - p)));
            rectF.inset(AndroidUtilities.dpf2(1), AndroidUtilities.dpf2(1));
            canvas.drawRoundRect(rectF, halfHeight, halfHeight, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        float direction = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 1 - p : p;
        float cx = AndroidUtilities.dpf2(-10 + 20 * direction);
        if (rippleDrawable != null) {
            int r = AndroidUtilities.dp(20);
            rippleDrawable.setBounds((int) cx - r, -r, (int) cx + r, r);
            rippleDrawable.draw(canvas);
        }
        float radius = AndroidUtilities.dpf2(8 + 4 * p);
        paint.setColor(processColor(androidx.core.graphics.ColorUtils.blendARGB(outline,
                app.nimarkogram.messenger.appearance.ClientTheme.color(app.nimarkogram.messenger.appearance.ClientTheme.ON_PRIMARY), p)));
        canvas.drawCircle(cx, 0, radius, paint);
        if (iconDrawable != null) {
            int size = AndroidUtilities.dp(16);
            iconDrawable.setColorFilter(new PorterDuffColorFilter(primary, PorterDuff.Mode.SRC_IN));
            iconDrawable.setBounds((int) cx - size / 2, -size / 2, (int) cx + size / 2, size / 2);
            iconDrawable.draw(canvas);
        } else if (drawIconType != 0 && p > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dpf2(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(processColor(Theme.multAlpha(primary, p)));
            canvas.drawLine(cx - AndroidUtilities.dp(4), 0, cx - AndroidUtilities.dp(1), AndroidUtilities.dp(3), paint);
            canvas.drawLine(cx - AndroidUtilities.dp(1), AndroidUtilities.dp(3), cx + AndroidUtilities.dp(5), -AndroidUtilities.dp(3), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }
        canvas.restoreToCount(saved);
    }

    private void drawColorOsSwitch(Canvas canvas) {
        float p = Math.max(0f, Math.min(1f, progress));
        float w = Math.min(AndroidUtilities.dp(44), getMeasuredWidth() - AndroidUtilities.dp(2));
        float h = Math.min(AndroidUtilities.dp(26), getMeasuredHeight() - AndroidUtilities.dp(2));
        if (w <= 0 || h <= 0) return;
        float left = (getMeasuredWidth() - w) / 2f;
        float cy = getMeasuredHeight() / 2f;
        paint.setColor(processColor(androidx.core.graphics.ColorUtils.blendARGB(
                app.nimarkogram.messenger.appearance.ColorOs17Theme.controlTrackColor(),
                app.nimarkogram.messenger.appearance.ColorOs17Theme.accentColor(), p)));
        rectF.set(left, cy - h / 2, left + w, cy + h / 2);
        canvas.drawRoundRect(rectF, h / 2, h / 2, paint);
        float r = Math.max(0, h / 2 - AndroidUtilities.dp(3));
        float direction = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 1 - p : p;
        float cx = left + h / 2 + (w - h) * direction;
        float stretch = AndroidUtilities.dp(2) * (1 - Math.abs(2 * p - 1));
        paint.setColor(0xFFFFFFFF);
        rectF.set(cx - r - stretch, cy - r, cx + r + stretch, cy + r);
        canvas.drawRoundRect(rectF, r, r, paint);
        if (iconDrawable != null) {
            int size = (int) Math.min(AndroidUtilities.dp(12), r * 1.4f);
            iconDrawable.setColorFilter(new PorterDuffColorFilter(
                    app.nimarkogram.messenger.appearance.ColorOs17Theme.accentColor(), PorterDuff.Mode.SRC_IN));
            iconDrawable.setBounds((int) cx - size / 2, (int) cy - size / 2,
                    (int) cx + size / 2, (int) cy + size / 2);
            iconDrawable.draw(canvas);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(isChecked);
        //info.setContentDescription(isChecked ? LocaleController.getString(R.string.NotificationsOn) : LocaleController.getString(R.string.NotificationsOff));
    }
}
