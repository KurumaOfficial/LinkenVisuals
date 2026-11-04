package ru.method.linkenvisuals.utils.animations;

import lombok.Setter;
import ru.method.linkenvisuals.utils.math.TimerUtils;

public class Animation {
    private static final long MAX_FRAME_STEP_MS = 120L;

    private final TimerUtils timer = new TimerUtils();
    private long duration;
    private final Easing easing;
    private boolean forward;
    @Setter private double value;
    private long lastElapsed;

    public Animation(long duration, double value, boolean forward, Easing easing) {
        this.duration = Math.max(0L, duration);
        this.value = value;
        this.forward = forward;
        this.easing = easing;
        this.lastElapsed = Math.min(timer.getElapsed(), this.duration);
    }

    public void update(boolean forward) {
        if (this.forward == forward) return;

        long elapsed = Math.min(timer.getElapsed(), duration);
        long mirroredElapsed = duration - elapsed;
        timer.setStartTime(System.currentTimeMillis() - mirroredElapsed);
        this.forward = forward;
        this.lastElapsed = Math.min(timer.getElapsed(), duration);
    }

    public boolean finished(boolean forward) {
        return timer.passed(duration) && (forward == this.forward);
    }

    public boolean finished() {
        return timer.passed(duration) && this.forward;
    }

    public float getValue() {
        if (duration <= 0L) return forward ? (float) value : 0.0f;

        long elapsed = resolveElapsed();

        if (forward) {
            if (elapsed >= duration) return (float) value;
            return (float) (easing.apply(elapsed / (double) duration) * value);
        } else {
            if (elapsed >= duration) return 0.0f;
            return (float) ((1 - easing.apply(elapsed / (double) duration)) * value);
        }
    }

    public float getLinear() {
        if (duration <= 0L) return forward ? (float) value : 0.0f;

        long elapsed = resolveElapsed();

        if (forward) {
            if (elapsed >= duration) return (float) value;
            return (float) (elapsed / (double) duration * value);
        } else {
            if (elapsed >= duration) return 0.0f;
            return (float) ((1 - elapsed / (double) duration) * value);
        }
    }

    public float getReversedValue() {
        return 1 - getValue();
    }

    public void reset() {
        timer.reset();
        lastElapsed = 0L;
    }

    public void update() {
        if (finished()) update(false);
        else if (finished(false)) update(true);
    }

    public boolean isForward() {
        return this.forward;
    }

    public void snapTo(boolean forward) {
        this.forward = forward;
        if (forward) {
            timer.setStartTime(System.currentTimeMillis());
            lastElapsed = 0L;
        } else {
            timer.setStartTime(System.currentTimeMillis() - duration);
            lastElapsed = duration;
        }
    }

    /** Instantly finish an open animation (value = 1). */
    public void snapOpen() {
        this.forward = true;
        if (duration <= 0L) {
            lastElapsed = 0L;
            return;
        }
        lastElapsed = duration;
        timer.setStartTime(System.currentTimeMillis() - duration);
    }

    public void setDuration(long duration) {
        long newDuration = Math.max(0L, duration);
        double progress = this.duration > 0L ? Math.min(1d, lastElapsed / (double) this.duration) : (forward ? 1d : 0d);
        this.duration = newDuration;
        lastElapsed = (long) Math.round(progress * this.duration);
        if (!forward && this.duration == 0L) {
            lastElapsed = 0L;
        }
        timer.setStartTime(System.currentTimeMillis() - lastElapsed);
    }

    private long resolveElapsed() {
        if (duration <= 0L) return 0L;

        long elapsed = Math.min(timer.getElapsed(), duration);
        if (elapsed < 0L) {
            timer.reset();
            lastElapsed = 0L;
            return 0L;
        }

        if (elapsed < lastElapsed) {
            lastElapsed = elapsed;
            return elapsed;
        }

        long delta = elapsed - lastElapsed;
        if (delta > MAX_FRAME_STEP_MS) {
            elapsed = lastElapsed + MAX_FRAME_STEP_MS;
            timer.setStartTime(System.currentTimeMillis() - elapsed);
        }

        lastElapsed = elapsed;
        return elapsed;
    }
}
