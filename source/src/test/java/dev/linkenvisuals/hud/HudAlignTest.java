package dev.linkenvisuals.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudAlignTest {
    @Test
    void verticalAndHorizontalCentering() {
        assertEquals(20.0f, HudAlign.centerY(10.0f, 40.0f, 20.0f), 0.001f);
        assertEquals(35.0f, HudAlign.centerX(20.0f, 50.0f, 20.0f), 0.001f);
        assertEquals(60.0f, HudAlign.rightX(10.0f, 80.0f, 20.0f, 10.0f), 0.001f);
        assertEquals(18.0f, HudAlign.leftX(10.0f, 8.0f), 0.001f);
    }

    @Test
    void opticalTextCentering() {
        float y = 10.0f;
        float h = 20.0f;
        float size = 8.0f;
        float baseline = HudAlign.baselineY(y, h, size);
        float top = HudAlign.centerTextY(y, h, size);

        // Baseline must be below top by ascender * size
        assertEquals(0.952f * size, baseline - top, 0.001f);
        // Midpoint of font cap-height should align with container center (20.0f)
        float capMid = baseline - (0.70f * 0.5f * size);
        assertEquals(20.0f, capMid, 0.001f);
    }

    @Test
    void fluentRowLayout() {
        HudAlign.Row row = HudAlign.row(100.0f, 50.0f, 20.0f, 7.0f, 6.0f);

        assertEquals(107.0f, row.current(), 0.001f);
        float item1X = row.next(10.0f);
        assertEquals(107.0f, item1X, 0.001f);
        assertEquals(123.0f, row.current(), 0.001f); // 107 + 10 + 6

        float item2X = row.next(20.0f);
        assertEquals(123.0f, item2X, 0.001f);
        assertEquals(149.0f, row.current(), 0.001f); // 123 + 20 + 6

        assertEquals(55.0f, row.centerY(10.0f), 0.001f);
        assertEquals(60.0f, row.midY(), 0.001f);
    }
}
