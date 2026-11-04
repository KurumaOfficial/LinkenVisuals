package ru.method.linkenvisuals.utils.animations.infinity;

import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import lombok.Getter;
import lombok.Setter;

public class InfinityAnimation {
    private float output, endpoint;
    @Setter private Easing easing;

    public InfinityAnimation(Easing easing) {
        this.easing = easing;
    }

    @Getter private Animation animation = new Animation(0, 0, false, Easing.LINEAR);

    public float animate(float value, long duration) {
        duration = Math.max(1, duration);
        float previousEndpoint = endpoint;
        output = previousEndpoint - animation.getValue();
        endpoint = value;
        if (previousEndpoint != value) {
            animation = new Animation(duration, endpoint - output, false, easing);
        }

        return output;
    }

    public boolean finished() {
        return output == endpoint || animation.finished() || animation.finished(false);
    }

    public float getValue() {
        output = endpoint - animation.getValue();
        return output;
    }
}
