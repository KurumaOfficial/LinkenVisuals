package dev.linkenvisuals.ui.linkmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkenMenuLayoutTest {
    private static final int[][] RESOLUTIONS = {
        {1280, 720},
        {1920, 1080},
        {2560, 1440},
        {3840, 2160}
    };

    @Test
    void scaleTracksScreenHeight() {
        for (int[] resolution : RESOLUTIONS) {
            int width = resolution[0];
            int height = resolution[1];
            float scale = LinkenMenuLayout.scaleFor(height);
            assertTrue(scale > 0.0f, "scale must stay positive at " + width + "x" + height);
            assertEquals(height / LinkenMenuLayout.DESIGN_HEIGHT, scale, 1.0e-6f);
            assertEquals(width / scale, LinkenMenuLayout.virtualWidth(width, height), 1.0e-4f);
        }
    }

    @Test
    void everyElementStaysInsideTheCanvas() {
        for (int[] resolution : RESOLUTIONS) {
            int width = resolution[0];
            int height = resolution[1];
            LinkenMenuLayout.Metrics m = LinkenMenuLayout.compute(width, height);
            String label = width + "x" + height;

            assertInside(m.eyebrow(), m, label);
            assertInside(m.headline(), m, label);
            assertInside(m.subtitle(), m, label);
            assertInside(m.account(), m, label);
            assertInside(m.multiplayer(), m, label);
            assertInside(m.singleplayer(), m, label);
            assertInside(m.settings(), m, label);
            assertInside(m.quit(), m, label);
            assertInside(m.socials(), m, label);
            assertInside(m.music(), m, label);
            assertInside(m.footer(), m, label);
            assertInside(m.clock(), m, label);
            assertInside(m.wordmark(), m, label);
        }
    }

    @Test
    void columnContainsEveryMenuRowAndNeverOverlapsTheMediaBlock() {
        for (int[] resolution : RESOLUTIONS) {
            LinkenMenuLayout.Metrics m = LinkenMenuLayout.compute(resolution[0], resolution[1]);
            String label = resolution[0] + "x" + resolution[1];

            assertContained(m.column(), m.eyebrow(), label);
            assertContained(m.column(), m.headline(), label);
            assertContained(m.column(), m.subtitle(), label);
            assertContained(m.column(), m.account(), label);
            assertContained(m.column(), m.multiplayer(), label);
            assertContained(m.column(), m.singleplayer(), label);
            assertContained(m.column(), m.settings(), label);
            assertContained(m.column(), m.quit(), label);

            assertFalse(m.column().intersects(m.music()), "column and media block overlap at " + label);
            assertFalse(m.column().intersects(m.footer()), "column and footer overlap at " + label);
            assertFalse(m.music().intersects(m.footer()), "media block and footer overlap at " + label);
            assertFalse(m.socials().intersects(m.footer()), "socials and footer overlap at " + label);
            assertFalse(m.music().intersects(m.clock()), "media block and clock overlap at " + label);
        }
    }

    @Test
    void rowsFollowTheDonorOrderAndMeasurements() {
        for (int[] resolution : RESOLUTIONS) {
            LinkenMenuLayout.Metrics m = LinkenMenuLayout.compute(resolution[0], resolution[1]);
            String label = resolution[0] + "x" + resolution[1];

            assertEquals(LinkenMenuLayout.COLUMN_X, m.column().x(), 1.0e-6f);
            assertEquals(LinkenMenuLayout.COLUMN_TOP, m.column().y(), 1.0e-6f);
            assertEquals(LinkenMenuLayout.COLUMN_WIDTH, m.column().width(), 1.0e-6f);
            assertEquals(LinkenMenuLayout.BUTTON_HEIGHT, m.multiplayer().height(), 1.0e-6f);
            assertEquals(LinkenMenuLayout.QUIT_WIDTH, m.quit().width(), 1.0e-6f);
            assertEquals(m.multiplayer().width(), m.singleplayer().width(), 1.0e-6f);

            assertTrue(m.multiplayer().bottom() < m.singleplayer().y(), "row order broken at " + label);
            assertTrue(m.singleplayer().bottom() < m.settings().y(), "row order broken at " + label);
            assertFalse(m.settings().intersects(m.quit()), "settings and quit overlap at " + label);
            assertEquals(m.settings().y(), m.quit().y(), 1.0e-6f);

            // Donor CustomMainMenuScreen: 300 + 13.02 + 14 + 2*43.7 + 8 +
            // 17.64 + 28 + 38 + 18 = 524.06. Using the nominal 10/14 font
            // sizes lifted the stack 7 px above the donor.
            if (resolution[0] == 1920 && resolution[1] == 1080) {
                assertEquals(524.06f, m.multiplayer().y(), 0.01f);
            }
        }
    }

    @Test
    void mediaBlockAndWordmarkKeepTheDonorAnchors() {
        LinkenMenuLayout.Metrics m = LinkenMenuLayout.compute(1920, 1080);
        assertEquals(320.0f, m.music().width(), 1.0e-6f);
        assertEquals((1920.0f - 320.0f) * 0.5f, m.music().x(), 1.0e-6f);
        assertEquals(1080.0f - 44.0f, m.music().bottom(), 1.0e-6f);
        assertEquals(1920.0f * 0.5f, m.wordmark().x() + m.wordmark().width() * 0.5f, 1.0e-6f);
        assertEquals(4, LinkenMenuLayout.SOCIAL_COUNT);
        assertEquals(38.0f * 4.0f + 9.0f * 3.0f, m.socials().width(), 1.0e-6f);
    }

    @Test
    void layoutKeepsDesignProportionsOnEveryAspectRatio() {
        LinkenMenuLayout.Metrics hd = LinkenMenuLayout.compute(1920, 1080);
        LinkenMenuLayout.Metrics uhd = LinkenMenuLayout.compute(3840, 2160);

        assertEquals(hd.scale() * 2.0f, uhd.scale(), 1.0e-6f);
        assertEquals(hd.width(), uhd.width(), 1.0e-4f);
        assertEquals(hd.column(), uhd.column());
        assertEquals(hd.music(), uhd.music());
        assertEquals(hd.footer(), uhd.footer());
    }

    @Test
    void rectExpandAndMarginHitTesting() {
        LinkenMenuLayout.Rect rect = new LinkenMenuLayout.Rect(100.0f, 100.0f, 50.0f, 50.0f);
        assertTrue(rect.contains(100.0f, 100.0f));
        assertTrue(rect.contains(150.0f, 150.0f));
        assertFalse(rect.contains(95.0f, 100.0f));
        assertFalse(rect.contains(100.0f, 155.0f));

        assertTrue(rect.contains(95.0f, 100.0f, 6.0f));
        assertTrue(rect.contains(100.0f, 155.0f, 6.0f));
        assertFalse(rect.contains(90.0f, 100.0f, 6.0f));

        LinkenMenuLayout.Rect expanded = rect.expand(8.0f);
        assertEquals(92.0f, expanded.x(), 1.0e-6f);
        assertEquals(92.0f, expanded.y(), 1.0e-6f);
        assertEquals(66.0f, expanded.width(), 1.0e-6f);
        assertEquals(66.0f, expanded.height(), 1.0e-6f);
        assertTrue(expanded.contains(95.0f, 100.0f));
    }

    private static void assertInside(LinkenMenuLayout.Rect rect, LinkenMenuLayout.Metrics metrics, String label) {
        assertTrue(rect.inside(metrics.width(), metrics.height()),
            "element " + rect + " is clipped at " + label);
    }

    private static void assertContained(LinkenMenuLayout.Rect outer, LinkenMenuLayout.Rect inner, String label) {
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom(),
            "element " + inner + " escapes its container at " + label);
    }
}
