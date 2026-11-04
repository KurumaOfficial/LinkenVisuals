package dev.linkenvisuals.ui.linkmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkenAnimationTest {
    @Test
    void initialValueIsZero() {
        LinkenAnimation anim = LinkenAnimation.hover();
        assertEquals(0.0f, anim.get(), 0.001f);
    }

    @Test
    void forwardProgressReachesOne() {
        long[] time = {1000L};
        LinkenAnimation anim = new LinkenAnimation(130L, () -> time[0]);
        anim.update(true);
        time[0] += 130L;
        assertEquals(1.0f, anim.get(), 0.001f);
    }

    @Test
    void reverseProgressReturnsToZero() {
        long[] time = {1000L};
        LinkenAnimation anim = new LinkenAnimation(130L, () -> time[0]);
        anim.update(true);
        time[0] += 130L;
        assertEquals(1.0f, anim.get(), 0.001f);
        anim.update(false);
        time[0] += 130L;
        assertEquals(0.0f, anim.get(), 0.001f);
    }

    @Test
    void animationEasesOutCubically() {
        long[] time = {1000L};
        LinkenAnimation anim = new LinkenAnimation(100L, () -> time[0]);
        anim.update(true);
        time[0] += 20L; // t = 0.2
        float value = anim.get();
        // At t = 0.2, linear is 0.2, ease-out cubic (1 - 0.8^3) is 0.488.
        assertEquals(1.0f - 0.8f * 0.8f * 0.8f, value, 0.001f);
    }
}
