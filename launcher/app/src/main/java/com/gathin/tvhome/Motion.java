package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * One motion language for the launcher, in the spirit of tvOS: critically damped springs (no bounce on
 * D-pad moves, which happen dozens of times a day), interruptible because a spring always continues from
 * the view's current value, and only scale / rotation / translationZ / alpha, which the GPU composites.
 *
 * Stiffness from Apple's "response" (seconds to settle): stiffness = (2π / response)².
 */
final class Motion {
    private Motion() {}

    /** ~0.22 s: focus lift of tiles, pills and settings rows. */
    static final float FOCUS = 800f;
    /** ~0.36 s: the direction tilt settling flat, slow enough to read as weight. */
    static final float TILT_SETTLE = 300f;
    /** ~0.16 s: press-in on OK. */
    static final float PRESS = 1500f;
    /** ~0.4 s: the dock rising and peeking. */
    static final float DOCK = 250f;

    /** Strong ease-out for things that appear (overlays): fast start, gentle landing. */
    static final Interpolator EASE_OUT = new PathInterpolator(0.23f, 1f, 0.32f, 1f);

    /** Degrees a focused tile leans toward the D-pad direction before settling. */
    private static final float TILT_DEG = 4f;

    /** The last D-pad key pressed and when (MainActivity sets them), so a new focus can lean the way you moved. */
    private static int lastDirection;
    private static long lastDirectionAt;
    /** A focus change later than this after the key (Back, a programmatic focus) does not lean. */
    private static final long DIRECTION_FRESH_MS = 400;

    static void onDirectionKey(int keyCode) {
        lastDirection = keyCode;
        lastDirectionAt = SystemClock.uptimeMillis();
    }

    /** The pending D-pad direction, used once; 0 when there is none or it is stale. */
    private static int takeDirection() {
        int d = SystemClock.uptimeMillis() - lastDirectionAt <= DIRECTION_FRESH_MS ? lastDirection : 0;
        lastDirection = 0;
        return d;
    }

    /** False when the TV's animation scale is off (accessibility): then values are set directly. */
    static boolean enabled() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    /** Springs {@code property} of {@code v} to {@code target}, re-using its spring when one is running. */
    static void springTo(View v, DynamicAnimation.ViewProperty property, int tagId, float target, float stiffness) {
        SpringAnimation s = (SpringAnimation) v.getTag(tagId);
        if (!enabled()) {
            if (s != null) s.cancel();
            property.setValue(v, target);
            return;
        }
        if (s == null) {
            s = new SpringAnimation(v, property);
            s.setSpring(new SpringForce().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
            v.setTag(tagId, s);
        }
        s.getSpring().setStiffness(stiffness);
        s.animateToFinalPosition(target);
    }

    static void scaleTo(View v, float scale, float stiffness) {
        springTo(v, DynamicAnimation.SCALE_X, R.id.motion_scale_x, scale, stiffness);
        springTo(v, DynamicAnimation.SCALE_Y, R.id.motion_scale_y, scale, stiffness);
    }

    /**
     * Focus gained or lost: scale and shadow depth spring to their state. On gain the view first leans
     * a few degrees toward the direction of the D-pad move, then springs flat (the tvOS parallax hint).
     */
    static void focus(View v, boolean focused, float scale, float liftPx, boolean tilt) {
        scaleTo(v, focused ? scale : 1f, FOCUS);
        springTo(v, DynamicAnimation.TRANSLATION_Z, R.id.motion_translation_z, focused ? liftPx : 0f, FOCUS);
        if (!focused || !tilt || !enabled()) return;
        float rx = 0f, ry = 0f;
        switch (takeDirection()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: ry = TILT_DEG; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: ry = -TILT_DEG; break;
            case KeyEvent.KEYCODE_DPAD_UP: rx = -TILT_DEG; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: rx = TILT_DEG; break;
            default: return;
        }
        v.setCameraDistance(8000f * v.getResources().getDisplayMetrics().density);
        v.setRotationX(rx);
        v.setRotationY(ry);
        springTo(v, DynamicAnimation.ROTATION_X, R.id.motion_rotation_x, 0f, TILT_SETTLE);
        springTo(v, DynamicAnimation.ROTATION_Y, R.id.motion_rotation_y, 0f, TILT_SETTLE);
    }

    /** Stops every running spring on {@code v} (a recycled tile) and puts it flat at {@code scale}. */
    static void reset(View v, float scale) {
        int[] ids = {R.id.motion_scale_x, R.id.motion_scale_y, R.id.motion_rotation_x, R.id.motion_rotation_y,
                R.id.motion_translation_z};
        for (int id : ids) {
            Object s = v.getTag(id);
            if (s instanceof SpringAnimation) ((SpringAnimation) s).cancel();
        }
        v.setScaleX(scale);
        v.setScaleY(scale);
        v.setRotationX(0f);
        v.setRotationY(0f);
        v.setTranslationZ(0f);
    }

    /** OK pressed / released on a focused view: sink a little, then come back to its focus scale. */
    static void press(View v, boolean down, float focusScale) {
        scaleTo(v, down ? focusScale * 0.945f : focusScale, PRESS);
    }

    /**
     * Press-in for any focusable: on OK key-down (first, not repeats) it sinks, on key-up it comes back.
     * Always returns false so the click still fires. Use as the body of an OnKeyListener.
     */
    static boolean handlePress(View v, int keyCode, KeyEvent event, float focusScale) {
        if (!isSelect(keyCode)) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) press(v, true, focusScale);
        else if (event.getAction() == KeyEvent.ACTION_UP) press(v, false, focusScale);
        return false;
    }

    /** True for the OK / Enter keys. */
    static boolean isSelect(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }
}
