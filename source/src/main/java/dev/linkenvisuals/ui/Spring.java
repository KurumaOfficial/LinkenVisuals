package dev.linkenvisuals.ui;

public final class Spring {
    private final double stiffness;
    private final double damping;
    private double value;
    private double velocity;

    public Spring(double initial, double stiffness, double damping) {
        this.value = initial;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public void step(double target, double deltaSeconds) {
        if (deltaSeconds <= 0.0) {
            return;
        }
        double remaining = Math.min(deltaSeconds, 0.1);
        double dt = 1.0 / 120.0; // 120Hz sub-stepping for rock-solid stability and zero jitter
        while (remaining > 0.0) {
            double stepDt = Math.min(remaining, dt);
            double force = (target - value) * stiffness - velocity * damping;
            velocity += force * stepDt;
            value += velocity * stepDt;
            remaining -= stepDt;
        }
        if (Math.abs(target - value) < 0.0005 && Math.abs(velocity) < 0.0005) {
            value = target;
            velocity = 0.0;
        }
    }

    public void reset(double target) {
        value = target;
        velocity = 0.0;
    }

    public double get() {
        return value;
    }
}
