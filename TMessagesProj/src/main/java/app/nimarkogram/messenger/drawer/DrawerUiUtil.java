package app.nimarkogram.messenger.drawer;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * UI-утилиты, перенесённые из exteraGram
 * ({@code app.exteraless.utils.UIUtil}, метод {@code applyScaleStateListAnimator}).
 *
 * В LinkiGram класса {@code app.exteraless.utils.UIUtil} нет, поэтому нужный
 * метод живёт здесь — локально для пакета шторки.
 */
public final class DrawerUiUtil {

    private DrawerUiUtil() {
    }

    /**
     * «Вдавливание» вью при нажатии: 80 мс до {@code 1 - scale}, отпускание — 350 мс
     * с {@link OvershootInterpolator}.
     */
    public static void applyScaleStateListAnimator(View view, float scale, float tension) {
        if (view == null) {
            return;
        }

        final float pressedScale = 1f - scale;

        final AnimatorSet pressed = new AnimatorSet();
        pressed.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, pressedScale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, pressedScale)
        );
        pressed.setDuration(80);

        final AnimatorSet released = new AnimatorSet();
        released.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f)
        );
        released.setInterpolator(new OvershootInterpolator(tension));
        released.setDuration(350);

        final StateListAnimator animator = new StateListAnimator();
        animator.addState(new int[]{android.R.attr.state_pressed}, pressed);
        animator.addState(new int[0], released);

        view.setStateListAnimator(animator);
    }
}