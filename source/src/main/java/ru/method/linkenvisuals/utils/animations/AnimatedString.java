package ru.method.linkenvisuals.utils.animations;

import lombok.Getter;

import java.util.Objects;

public class AnimatedString {

    private final long duration;
    private final Easing easing;

    @Getter
    private String current;
    @Getter
    private String previous;

    private long startTime;

    public AnimatedString(long duration, Easing easing, String initial) {
        this.duration = Math.max(1, duration);
        this.easing = easing;
        this.current = initial != null ? initial : "";
        this.previous = this.current;
        this.startTime = 0;
    }

    public void update(String target) {
        String normalized = target != null ? target : "";
        if (Objects.equals(normalized, current)) {
            if (!isAnimating()) {
                previous = current;
            }
            return;
        }
        previous = current;
        current = normalized;
        startTime = System.currentTimeMillis();
    }

    public float progress() {
        if (duration <= 0 || !isAnimating()) return 1f;
        float raw = (System.currentTimeMillis() - startTime) / (float) duration;
        if (raw >= 1f) {
            startTime = 0;
            previous = current;
            return 1f;
        }
        return (float) easing.apply(Math.max(0f, Math.min(1f, raw)));
    }

    public boolean isAnimating() {
        return startTime > 0;
    }
}
