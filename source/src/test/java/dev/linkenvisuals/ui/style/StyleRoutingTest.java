package dev.linkenvisuals.ui.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleRoutingTest {
    @Test
    void videoSettingsCyclesThroughStyles() {
        if (dev.linkenvisuals.BuildFlavor.STRANGE) {
            assertEquals(MenuStyle.VANILLA, StyleRouting.nextStyle(MenuStyle.BETA));
            assertEquals(MenuStyle.LINKEN, StyleRouting.nextStyle(MenuStyle.VANILLA));
            assertEquals(MenuStyle.BETA, StyleRouting.nextStyle(MenuStyle.LINKEN));

            assertEquals(MenuStyle.VANILLA, StyleRouting.nextMenuStyle(MenuStyle.BETA));
            assertEquals(MenuStyle.LINKEN, StyleRouting.nextMenuStyle(MenuStyle.VANILLA));
            assertEquals(MenuStyle.BETA, StyleRouting.nextMenuStyle(MenuStyle.LINKEN));

            MenuStyle style = MenuStyle.BETA;
            for (int i = 0; i < 3; i++) {
                style = StyleRouting.nextStyle(style);
            }
            assertEquals(MenuStyle.BETA, style, "three steps must return to the starting style");
        } else {
            assertEquals(MenuStyle.LINKEN, StyleRouting.nextStyle(MenuStyle.LINKEN));
            assertEquals(MenuStyle.LINKEN, StyleRouting.nextMenuStyle(MenuStyle.LINKEN));
        }
    }

    @Test
    void resolveGuiScreenSupportsAllStyles() {
        assertEquals(dev.linkenvisuals.clickgui.ClickGuiScreen.class, StyleRouting.resolveGuiScreenClass("Beta"));
        assertEquals(dev.linkenvisuals.clickgui.classic.ClassicGuiScreen.class, StyleRouting.resolveGuiScreenClass("Classic"));
        assertEquals(dev.linkenvisuals.clickgui.link.LinkGuiScreen.class, StyleRouting.resolveGuiScreenClass("Linken"));
        assertEquals(dev.linkenvisuals.clickgui.link.LinkGuiScreen.class, StyleRouting.resolveGuiScreenClass("linken"));
        assertEquals(dev.linkenvisuals.clickgui.ClickGuiScreen.class, StyleRouting.resolveGuiScreenClass("unknown"));
    }

    @Test
    void linkenNeverAppliesTheEditablePreset() {
        assertTrue(StyleRouting.shouldApplyPreset(MenuStyle.BETA));
        assertTrue(StyleRouting.shouldApplyPreset(MenuStyle.VANILLA));
        assertFalse(StyleRouting.shouldApplyPreset(MenuStyle.LINKEN));
    }

    @Test
    void unportedLinkenScreensUseVanillaChromeInsteadOfBeta() {
        assertFalse(StyleRouting.usesVanillaChrome(MenuStyle.BETA));
        assertTrue(StyleRouting.usesVanillaChrome(MenuStyle.VANILLA));
        assertTrue(StyleRouting.usesVanillaChrome(MenuStyle.LINKEN),
            "LINKEN fallback screens must not inherit Beta styling");
    }
}
