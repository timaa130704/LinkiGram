package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;


import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedLinearLayout;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawableRenderNode;
import org.telegram.ui.Components.glass.GlassTabView;


import me.vkryl.android.animator.ListAnimator;

@SuppressLint("ViewConstructor")
public class MainTabsLayout extends AnimatedLinearLayout {

    private final Theme.ResourcesProvider resourcesProvider;

    public MainTabsLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        updateSelectorColor();
    }

    private static final float[] PASS_TEXT_SIZES_DP = {12f, 12f, 10f};
    private static final int[] PASS_PADDINGS_DP = {16, 8, 4};

    // --- LinkiGram: режим нижней панели (порт из exteraless) ---
    public static final int BOTTOM_NAVIGATION_MODE_SHOW = app.nimarkogram.messenger.NimarkoConfig.BOTTOM_NAVIGATION_MODE_SHOW;
    public static final int BOTTOM_NAVIGATION_MODE_HIDE = app.nimarkogram.messenger.NimarkoConfig.BOTTOM_NAVIGATION_MODE_HIDE;
    public static final int BOTTOM_NAVIGATION_MODE_FLOATING = app.nimarkogram.messenger.NimarkoConfig.BOTTOM_NAVIGATION_MODE_FLOATING;

    public static int getBottomNavigationMode() {
        return app.nimarkogram.messenger.NimarkoConfig.getBottomNavigationMode();
    }

    public static void setBottomNavigationMode(int mode) {
        app.nimarkogram.messenger.NimarkoConfig.setBottomNavigationMode(mode);
    }

    public static boolean isBottomNavigationHidden() {
        return app.nimarkogram.messenger.NimarkoConfig.isBottomNavigationHidden();
    }

    public static boolean isBottomNavigationVisible() {
        return app.nimarkogram.messenger.NimarkoConfig.isBottomNavigationVisible();
    }

    public static boolean isBottomNavigationFloating() {
        return app.nimarkogram.messenger.NimarkoConfig.isBottomNavigationFloating();
    }

    private int maxWidthPx;

    public void setMaxWidth(int maxWidthPx) {
        if (this.maxWidthPx != maxWidthPx) {
            this.maxWidthPx = maxWidthPx;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int tabHeight = height - getPaddingTop() - getPaddingBottom();

        if (maxWidthPx > 0 && width > maxWidthPx) {
            width = maxWidthPx;
        }

        final int maxTotalWidthForTabs = width - getPaddingLeft() - getPaddingRight();
        final int minTotalWidthForTabs = Math.min(dp(320), maxTotalWidthForTabs);

        int chosenPass = PASS_TEXT_SIZES_DP.length - 1;
        float lastMeasuredTextSize = -1;
        for (int pass = 0; pass < PASS_TEXT_SIZES_DP.length; pass++) {
            if (PASS_TEXT_SIZES_DP[pass] != lastMeasuredTextSize) {
                measureTabTexts(PASS_TEXT_SIZES_DP[pass]);
                lastMeasuredTextSize = PASS_TEXT_SIZES_DP[pass];
            }
            final int padding = dp(PASS_PADDINGS_DP[pass]);
            float total = 0;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                if (!isViewVisible(getChildAt(a))) continue;
                final float withMargin = tabsTextWidth[a] + padding * 2;
                total += withMargin;
            }
            final boolean fits = total <= maxTotalWidthForTabs;
            if (fits || pass == PASS_TEXT_SIZES_DP.length - 1) {
                chosenPass = pass;
                break;
            }
        }

        final boolean youNavigationStyle = app.nimarkogram.messenger.appearance.AppearanceConfig.newNavigationBarStyle();
        if (youNavigationStyle) {
            for (int a = 0, N = getChildCount(); a < N; a++) {
                final View child = getChildAt(a);
                if (child instanceof Tab) {
                    ((Tab) child).setTextSizeDp(14f);
                }
            }
        } else {
            applyPassTextSize(chosenPass);
        }

        if (youNavigationStyle) {
            // Yougram-style Material navigation: compact icon slots, with only the
            // active destination expanding to make room for its label.
            final int slotWidth = dp(48);
            final int gap = dp(2);
            final int visibleCount = Math.max(1, visibleChildCount);
            final int gapsWidth = gap * (visibleCount - 1);
            final int availableExtraWidth = Math.max(0, maxTotalWidthForTabs - slotWidth * visibleCount - gapsWidth);
            float requestedExtraWidth = 0;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                final View child = getChildAt(a);
                if (!isViewVisible(child) || !(child instanceof GlassTabView)) {
                    continue;
                }
                final GlassTabView tab = (GlassTabView) child;
                requestedExtraWidth += Math.max(0, tab.measureTextWidth(14f) + dp(8)) * tab.getSelectionProgress();
            }
            final float extraScale = requestedExtraWidth > availableExtraWidth && requestedExtraWidth > 0
                    ? availableExtraWidth / requestedExtraWidth : 1f;

            int l = 0;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                final View child = getChildAt(a);
                if (!isViewVisible(child)) {
                    tabsWidth[a] = 0;
                    tabsLeftPos[a] = l;
                    child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
                    continue;
                }
                float extra = 0;
                if (child instanceof GlassTabView) {
                    final GlassTabView tab = (GlassTabView) child;
                    extra = Math.max(0, tab.measureTextWidth(14f) + dp(8)) * tab.getSelectionProgress() * extraScale;
                }
                tabsWidth[a] = Math.round(slotWidth + extra);
                tabsLeftPos[a] = l;
                l += tabsWidth[a] + gap;
                child.measure(MeasureSpec.makeMeasureSpec(tabsWidth[a], MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
            }
            if (visibleCount > 0) {
                l -= gap;
            }
            setMeasuredDimension(l + getPaddingLeft() + getPaddingRight(), height);
            calculateTotalSizesAfterMeasure();
            return;
        }

        final int tabPadding = dp(PASS_PADDINGS_DP[chosenPass]);
        final int maxTabTextWidthIfEq = (maxTotalWidthForTabs / Math.max(1, visibleChildCount)) - tabPadding * 2;

        float totalWidth = 0;
        int totalWeight = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = tabsTextWidthWithMargin[a] = 0;
                tabsWeight[a] = 0;
                continue;
            }

            tabsTextWidthWithMargin[a] = tabsTextWidth[a] + tabPadding * 2;
            tabsWeight[a] = tabsTextWidthWithMargin[a] > (maxTabTextWidthIfEq + tabPadding * 2) ? 0 : 1;

            totalWidth += tabsTextWidthWithMargin[a];
            totalWeight += tabsWeight[a];
        }

        if (totalWeight == 0) {
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsWeight[a] = isViewVisible(getChildAt(a)) ? 1 : 0;
            }
            totalWeight = visibleChildCount;
        }

        if (totalWidth > maxTotalWidthForTabs) {
            final float m = maxTotalWidthForTabs / totalWidth;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsTextWidthWithMargin[a] *= m;
            }
        } else if (totalWidth < minTotalWidthForTabs) {
            final float growW = minTotalWidthForTabs - totalWidth;
            final float growP = growW / totalWeight;

            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsTextWidthWithMargin[a] += growP * tabsWeight[a];
            }
        }

        int l = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            if (!isViewVisible(getChildAt(a))) {
                continue;
            }

            tabsWidth[a] = Math.round(tabsTextWidthWithMargin[a]);
            tabsLeftPos[a] = l;
            l += tabsWidth[a];
        }
        setMeasuredDimension(l + getPaddingLeft() + getPaddingRight(), height);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            child.measure(
                MeasureSpec.makeMeasureSpec(tabsWidth[a], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
        }

        calculateTotalSizesAfterMeasure();
    }

    public interface Tab {
        float measureTextWidth();
        default float measureTextWidth(float textSizeDp) { return measureTextWidth(); }
        default void setTextSizeDp(float textSizeDp) {}
    }

    // fills tabsTextWidth[] and return visible child count;

    private float[] tabsTextWidth;
    private float[] tabsTextWidthWithMargin;
    private int[] tabsWeight;
    private int[] tabsWidth;

    private int[] tabsLeftPos;


    private int visibleChildCount;
    private int biggestTabTextWidth;

    private void measureTabTexts(float textSizeDp) {
        final int childCount = getChildCount();
        if (tabsTextWidth == null || tabsTextWidth.length < childCount) {
            tabsTextWidth = new float[childCount];
            tabsTextWidthWithMargin = new float[childCount];
            tabsWeight = new int[childCount];
            tabsLeftPos = new int[childCount];
            tabsWidth = new int[childCount];
        }

        float maxTabWidthF = 0;
        int index = 0;

        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = -1;
                continue;
            }

            final float tabWidth;
            if (child instanceof MainTabsLayout.Tab) {
                tabWidth = ((MainTabsLayout.Tab) child).measureTextWidth(textSizeDp);
            } else {
                tabWidth = 0;
            }

            tabsTextWidth[a] = tabWidth;
            maxTabWidthF = Math.max(maxTabWidthF, tabWidth);
            index++;
        }

        biggestTabTextWidth = (int) Math.ceil(maxTabWidthF);
        visibleChildCount = index;
    }

    private void applyPassTextSize(int pass) {
        final float textSizeDp = PASS_TEXT_SIZES_DP[pass];
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof MainTabsLayout.Tab) {
                ((MainTabsLayout.Tab) child).setTextSizeDp(textSizeDp);
            }
        }
    }

    @Override
    protected void setChildVisibilityFactor(View view, float factor) {
        final float s = lerp(0.7f, 1f, factor);
        view.setAlpha(factor);
        view.setScaleX(s);
        view.setScaleY(s);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        checkVisualWidth();
        moveSelectorTo(findSelectedTab(), selectorInitialized);
    }

    @Override
    protected void onItemsChanged() {
        super.onItemsChanged();
        checkVisualWidth();
        moveSelectorTo(findSelectedTab(), selectorInitialized);
    }

    private void checkVisualWidth() {
        for (int a = 0, N = getEntriesCount(); a < N; a++) {
            final ListAnimator.Entry<Holder> entry = getEntry(a);
            final float width = entry.getRectF().width();
            ((GlassTabView) entry.item.view).setVisualWidth(width);
        }
    }

    @Override
    protected boolean shouldAnimateItemLayout() {
        // Material tab widths already follow the selection animator each frame.
        return !app.nimarkogram.messenger.appearance.AppearanceConfig.newNavigationBarStyle();
    }

    public void setTabSelected(View tab, boolean animated) {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSelected(child == tab, animated);
                ((GlassTabView) child).setSkipDrawSelector(!app.nimarkogram.messenger.appearance.AppearanceConfig.newNavigationBarStyle());
            }
        }
        moveSelectorTo(tab, animated);
        if (app.nimarkogram.messenger.appearance.AppearanceConfig.newNavigationBarStyle()) {
            requestLayout();
        }
    }

    public void setSelectorToSelectedTab(boolean animated) {
        updateSelectorColor();
        final View selected = findSelectedTab();
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSkipDrawSelector(!app.nimarkogram.messenger.appearance.AppearanceConfig.newNavigationBarStyle());
            }
        }
        moveSelectorTo(selected, animated);
    }

    private void moveSelectorTo(View tab, boolean animated) {
        if (isDraggingSelector || selectorFollowsPager || tab == null
                || tab.getVisibility() != View.VISIBLE || tab.getWidth() == 0 || getWidth() == 0) {
            return;
        }
        final float centerX = getCenterX(tab);
        if (animated && selectorInitialized && Math.abs(selectorTargetX - centerX) < 0.5f) {
            return;
        }
        selectorTargetX = centerX;
        if (animated && selectorInitialized) {
            selectedTabPositionOffsetX.animateToFinalPosition(0);
            selectedTabPositionX.animateToFinalPosition(centerX);
            // Keep the current position and velocity when a second tap retargets the spring.
            if (!selectorStretchXAnimation.isRunning()) {
                selectorStretchXAnimation.setStartVelocity(1.1f);
                selectorStretchXAnimation.animateToFinalPosition(1f);
                selectorStretchYAnimation.setStartVelocity(-0.45f);
                selectorStretchYAnimation.animateToFinalPosition(1f);
            }
        } else {
            selectedTabPositionX.cancel();
            selectedTabPositionOffsetX.cancel();
            animatedLongSelectedViewCenterX = centerX;
            animatedLongSelectedViewOffsetX = 0;
            selectorInitialized = true;
            invalidate();
        }
    }

    public void setSelectorFollowsPager(boolean followsPager) {
        selectorFollowsPager = followsPager;
    }

    public void setSelectorBetween(View from, View to, float progress) {
        if (isDraggingSelector || from == null || to == null
                || from.getWidth() == 0 || to.getWidth() == 0) {
            return;
        }
        selectedTabPositionX.cancel();
        selectedTabPositionOffsetX.cancel();
        animatedLongSelectedViewCenterX = lerp(getCenterX(from), getCenterX(to),
                Math.max(0f, Math.min(1f, progress)));
        animatedLongSelectedViewOffsetX = 0;
        selectorTargetX = animatedLongSelectedViewCenterX;
        selectorInitialized = true;
        invalidate();
    }

    private View findSelectedTab() {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() != View.VISIBLE || !isViewVisible(child)) {
                continue;
            }

            if (child instanceof GlassTabView) {
                if (((GlassTabView) child).isTabSelected()) {
                    return child;
                }
            }
        }
        return null;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)
                && Build.VERSION.SDK_INT >= 33 && selectorInitialized
                && getBackground() instanceof BlurredBackgroundDrawableRenderNode) {
            BlurredBackgroundDrawableRenderNode glass =
                    (BlurredBackgroundDrawableRenderNode) getBackground();
            Rect bounds = glass.getPaddedBounds();
            glass.setGlassLightPosition((animatedLongSelectedViewCenterX + animatedLongSelectedViewOffsetX
                    - bounds.left) / Math.max(1f, bounds.width()), selectorPress);
        }
        super.draw(canvas);
    }

    private void updateSelectorColor() {
        final int background = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        final boolean dark = androidx.core.graphics.ColorUtils.calculateLuminance(background) < 0.5;
        selectorPaint.setColor(dark ? 0x24FFFFFF : 0x14000000);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (selectorInitialized && !app.nimarkogram.messenger.appearance.AppearanceConfig.newNavigationBarStyle()) {
            final float x = animatedLongSelectedViewCenterX + animatedLongSelectedViewOffsetX;
            final float sWidth = getInterpolatedWidthByX(x, this);
            final float sHeight = getHeight() - getPaddingTop() - getPaddingBottom();
            selectorRect.set(x - sWidth / 2f, (getHeight() - sHeight) / 2f,
                    x + sWidth / 2f, (getHeight() + sHeight) / 2f);
            final float radius = sHeight / 2f;
            canvas.save();
            final float travel = Math.min(0.18f,
                    Math.abs(selectorTargetX - x) / Math.max(1f, sWidth) * 0.16f);
            final boolean liquidGlass = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);
            if (liquidGlass) {
                canvas.scale((selectorStretchX + travel) * (1f + selectorPress * 0.08f),
                        (selectorStretchY - travel * 0.3f) * (1f + selectorPress * 0.20f),
                        x, getHeight() / 2f);
            }
            canvas.drawRoundRect(selectorRect, radius, radius, selectorPaint);
            if (liquidGlass) {
                final float lightOffset = Math.max(-sWidth * 0.35f,
                        Math.min(sWidth * 0.35f, selectorTargetX - x));
                selectorLightMatrix.setTranslate(x + lightOffset, 0);
                selectorLightShader.setLocalMatrix(selectorLightMatrix);
                canvas.drawRoundRect(selectorRect, radius, radius, selectorHighlightPaint);
                canvas.drawRoundRect(selectorRect, radius, radius, selectorEdgePaint);
            }
            canvas.restore();
        }

        super.dispatchDraw(canvas);
    }


    final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectorHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectorEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF selectorRect = new RectF();
    private final Matrix selectorLightMatrix = new Matrix();
    private final LinearGradient selectorLightShader = new LinearGradient(-dp(40), 0, dp(40), 0,
            new int[]{0x00FFFFFF, 0x10FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
    private boolean selectorInitialized;
    private boolean selectorFollowsPager;
    private float selectorTargetX;
    private float selectorStretchX = 1f;
    private float selectorStretchY = 1f;
    private float selectorPress;
    private final SpringAnimation selectorPressAnimation = new SpringAnimation(this,
            new FloatPropertyCompat<MainTabsLayout>("selectorPress") {
                @Override
                public float getValue(MainTabsLayout object) {
                    return object.selectorPress;
                }

                @Override
                public void setValue(MainTabsLayout object, float value) {
                    object.selectorPress = value;
                    object.invalidate();
                }
            }).setSpring(new SpringForce(0f).setStiffness(500f).setDampingRatio(0.8f));
    private final SpringAnimation selectorStretchXAnimation = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectorStretchX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.selectorStretchX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.selectorStretchX = value;
            object.invalidate();
        }
    });
    private final SpringAnimation selectorStretchYAnimation = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectorStretchY") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.selectorStretchY;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.selectorStretchY = value;
            object.invalidate();
        }
    });

    final SpringAnimation selectedTabPositionOffsetX = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectedTabPositionOffsetX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.animatedLongSelectedViewOffsetX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.animatedLongSelectedViewOffsetX = value;
            object.invalidate();
        }
    });
    final SpringAnimation selectedTabPositionX = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectedTabPositionX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.animatedLongSelectedViewCenterX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.animatedLongSelectedViewCenterX = value;
            object.invalidate();
        }
    });

    {
        selectorHighlightPaint.setColor(Color.WHITE);
        selectorHighlightPaint.setShader(selectorLightShader);
        selectorEdgePaint.setStyle(Paint.Style.STROKE);
        selectorEdgePaint.setStrokeWidth(dp(0.75f));
        selectorEdgePaint.setColor(0x10FFFFFF);
        selectorStretchXAnimation.setSpring(new SpringForce(1f).setStiffness(420f).setDampingRatio(0.42f));
        selectorStretchYAnimation.setSpring(new SpringForce(1f).setStiffness(420f).setDampingRatio(0.42f));
        selectedTabPositionOffsetX.setSpring(new SpringForce(1)
            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
        selectedTabPositionX.setSpring(new SpringForce(0f)
            .setStiffness(320f)
            .setDampingRatio(0.78f));
    }

    private float animatedLongSelectedViewCenterX;
    private float animatedLongSelectedViewOffsetX;

    private final int touchSlop;
    private int selectorPointerId = -1;
    private float touchDownX;
    private float touchDownY;
    private boolean isDraggingSelector;
    private View dragOriginalTab;

    private void updateDragSelector(float x) {
        final float nextX = clampXToChildrenCenters(x, this);
        final float movement = Math.min(0.12f,
                Math.abs(nextX - animatedLongSelectedViewCenterX) / Math.max(1f, dp(72)));
        animatedLongSelectedViewCenterX = nextX;
        selectorTargetX = nextX;
        selectorStretchX = 1.04f + movement;
        selectorStretchY = 0.98f - movement * 0.3f;
        selectorInitialized = true;
        final View nearest = findNearestVisibleChildByX(nextX, this);
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSelected(child == nearest, true);
            }
        }
        invalidate();
    }

    private void finishSelectorDrag(boolean cancelled, float x) {
        final View target = cancelled ? dragOriginalTab : findNearestVisibleChildByX(x, this);
        isDraggingSelector = false;
        // Restore the committed tab before asking the host to switch pages. Its click
        // listener can refuse a switch while another navigation gesture is active.
        if (dragOriginalTab != null) {
            setTabSelected(dragOriginalTab, true);
        }
        if (!cancelled && target != null && target != dragOriginalTab) {
            target.performClick();
        }
        setSelectorToSelectedTab(true);
        selectorStretchXAnimation.animateToFinalPosition(1f);
        selectorStretchYAnimation.animateToFinalPosition(1f);
        dragOriginalTab = null;
        selectorPointerId = -1;
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    @Nullable
    private View findTabUnder(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            if (child instanceof GlassTabView && child.getVisibility() == View.VISIBLE
                    && isViewVisible(child) && x >= child.getX() && x <= child.getX() + child.getWidth()
                    && y >= child.getY() && y <= child.getY() + child.getHeight()) {
                return child;
            }
        }
        return null;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_POINTER_UP) {
            selectorPressAnimation.animateToFinalPosition(0f);
        }
        if (action == MotionEvent.ACTION_DOWN) {
            selectorPointerId = findTabUnder(event.getX(), event.getY()) != null
                    ? event.getPointerId(0) : -1;
            touchDownX = event.getX();
            touchDownY = event.getY();
            if (selectorPointerId != -1) {
                selectorPressAnimation.animateToFinalPosition(1f);
            }
            if (selectorPointerId != -1 && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        } else if (selectorPointerId != -1) {
            final int pointerIndex = event.findPointerIndex(selectorPointerId);
            final boolean pointerLost = pointerIndex < 0 || (action == MotionEvent.ACTION_POINTER_UP
                    && event.getPointerId(event.getActionIndex()) == selectorPointerId);
            final float x = pointerIndex >= 0 ? event.getX(pointerIndex) : touchDownX;
            final float y = pointerIndex >= 0 ? event.getY(pointerIndex) : touchDownY;
            if (pointerLost || action == MotionEvent.ACTION_CANCEL) {
                if (isDraggingSelector) {
                    finishSelectorDrag(true, x);
                    return true;
                }
                selectorPointerId = -1;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (pointerLost) {
                    final MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    return true;
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (!isDraggingSelector && Math.abs(x - touchDownX) > touchSlop
                        && Math.abs(x - touchDownX) > Math.abs(y - touchDownY)) {
                    dragOriginalTab = findSelectedTab();
                    isDraggingSelector = true;
                    selectorFollowsPager = false;
                    selectedTabPositionX.cancel();
                    selectedTabPositionOffsetX.cancel();
                    animatedLongSelectedViewOffsetX = animatedLongSelectedViewCenterX
                            - clampXToChildrenCenters(x, this);
                    selectedTabPositionOffsetX.animateToFinalPosition(0);
                    selectorStretchXAnimation.cancel();
                    selectorStretchYAnimation.cancel();
                    // Cancel the child's click/long press only once dragging is claimed.
                    final MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
                if (isDraggingSelector) {
                    updateDragSelector(x);
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP) {
                if (isDraggingSelector) {
                    finishSelectorDrag(y < -touchSlop || y > getHeight() + touchSlop, x);
                    return true;
                }
                selectorPointerId = -1;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (isDraggingSelector) {
            isDraggingSelector = false;
            if (dragOriginalTab != null) {
                setTabSelected(dragOriginalTab, false);
            }
        }
        selectorPointerId = -1;
        dragOriginalTab = null;
        selectedTabPositionX.cancel();
        selectedTabPositionOffsetX.cancel();
        selectorStretchXAnimation.cancel();
        selectorStretchYAnimation.cancel();
        selectorPressAnimation.cancel();
        selectorPress = 0f;
        selectorStretchX = selectorStretchY = 1f;
        selectorInitialized = false;
        selectorFollowsPager = false;
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        super.onDetachedFromWindow();
    }

    private static float clampXToChildrenCenters(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return x;
        }

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        boolean found = false;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE
                    || (parent instanceof MainTabsLayout && !((MainTabsLayout) parent).isViewVisible(view))) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;

            if (centerX < min) min = centerX;
            if (centerX > max) max = centerX;

            found = true;
        }

        if (!found) {
            return x;
        }

        if (x < min) return min;
        if (x > max) return max;
        return x;
    }

    @Nullable
    private static View findNearestVisibleChildByX(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return null;
        }

        View nearest = null;
        float nearestDistance = Float.MAX_VALUE;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE
                    || (parent instanceof MainTabsLayout && !((MainTabsLayout) parent).isViewVisible(view))) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;
            float distance = Math.abs(centerX - x);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = view;
            }
        }

        return nearest;
    }

    private static float getInterpolatedWidthByX(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return 0f;
        }

        View left = null;
        View right = null;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE
                    || (parent instanceof MainTabsLayout && !((MainTabsLayout) parent).isViewVisible(view))) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;

            if (centerX <= x && (left == null || centerX > getCenterX(left))) {
                left = view;
            }

            if (centerX >= x && (right == null || centerX < getCenterX(right))) {
                right = view;
            }
        }

        if (left == null && right == null) {
            return 0f;
        }

        if (left == null) {
            return right.getWidth();
        }

        if (right == null) {
            return left.getWidth();
        }

        float leftX = getCenterX(left);
        float rightX = getCenterX(right);

        if (left == right || leftX == rightX) {
            return left.getWidth();
        }

        float ratio = (x - leftX) / (rightX - leftX);
        return lerp(left.getWidth(), right.getWidth(), ratio);
    }

    private static float getCenterX(View v) {
        return v.getX() + v.getWidth() * 0.5f;
    }
}
