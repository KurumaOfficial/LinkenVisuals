package dev.linkenvisuals.ui.style;

import dev.linkenvisuals.BuildFlavor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleFlavorTest {
    @Test
    void linkenExposesOnlyLinkenFamily() {
        StyleFlavor.Rules rules = StyleFlavor.rulesFor(StyleFlavor.Family.LINKEN);

        assertEquals("Linken", rules.defaultGuiStyle());
        assertEquals(java.util.List.of("Linken"), rules.guiStyles());
        assertTrue(rules.allowsGuiStyle("Linken"));
        assertEquals("Linken", rules.sanitizeGuiStyle("Linken"));

        assertEquals(MenuStyle.LINKEN, rules.defaultMenuStyle());
        assertEquals(java.util.List.of(MenuStyle.LINKEN), rules.menuStyles());
        assertTrue(rules.allowsMenuStyle(MenuStyle.LINKEN));
        assertEquals(MenuStyle.LINKEN, rules.parseMenuStyle("Linken"));
        assertEquals(MenuStyle.LINKEN, rules.nextMenuStyle(MenuStyle.LINKEN));

        assertEquals(java.util.List.of("Card", "Minimal", "Neon", "Aurora", "Glass", "Solid", "Gradient", "Outline"), rules.hudStyles());
        assertEquals("Card", rules.defaultHudStyle());
        assertTrue(rules.allowsHudStyle("Neon"));
        assertTrue(rules.allowsHudStyle("Card"));
        assertEquals("Card", rules.sanitizeHudStyle("Card"));
        assertEquals(java.util.List.of("Rounded", "Pill", "Sharp"), rules.hudShapes());
        assertEquals("Rounded", rules.defaultHudShape());
        assertEquals("Sharp", rules.sanitizeHudShape("Sharp"));
    }

    @Test
    void activeFamilyIsLinken() {
        assertEquals(StyleFlavor.Family.LINKEN, StyleFlavor.family());
        assertEquals(BuildFlavor.NAME, StyleFlavor.flavorName());
    }
}
