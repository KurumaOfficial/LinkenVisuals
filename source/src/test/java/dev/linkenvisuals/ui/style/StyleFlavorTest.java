package dev.linkenvisuals.ui.style;

import dev.linkenvisuals.BuildFlavor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleFlavorTest {
    @Test
    void strangeExposesStrangeFamilyWithLinkenOption() {
        StyleFlavor.Rules rules = StyleFlavor.rulesFor(StyleFlavor.Family.STRANGE);

        assertEquals("Beta", rules.defaultGuiStyle());
        assertEquals(java.util.List.of("Beta", "Classic", "Linken"), rules.guiStyles());
        assertTrue(rules.allowsGuiStyle("Classic"));
        assertTrue(rules.allowsGuiStyle("Beta"));
        assertTrue(rules.allowsGuiStyle("Linken"));
        assertEquals("Linken", rules.sanitizeGuiStyle("Linken"));

        assertEquals(MenuStyle.BETA, rules.defaultMenuStyle());
        assertEquals(java.util.List.of(MenuStyle.BETA, MenuStyle.VANILLA, MenuStyle.LINKEN), rules.menuStyles());
        assertEquals(MenuStyle.LINKEN, rules.parseMenuStyle("Linken"));
        assertEquals(MenuStyle.BETA, rules.parseMenuStyle(""));
        assertEquals(MenuStyle.BETA, rules.parseMenuStyle("Beta"));
        assertEquals(MenuStyle.VANILLA, rules.parseMenuStyle("vanilla"));
        assertEquals(MenuStyle.VANILLA, rules.nextMenuStyle(MenuStyle.BETA));
        assertEquals(MenuStyle.LINKEN, rules.nextMenuStyle(MenuStyle.VANILLA));
        assertEquals(MenuStyle.BETA, rules.nextMenuStyle(MenuStyle.LINKEN));

        assertEquals(java.util.List.of("Strange", "Linken"), rules.hudStyles());
        assertEquals("Strange", rules.defaultHudStyle());
        assertTrue(rules.allowsHudStyle("Strange"));
        assertTrue(rules.allowsHudStyle("Linken"));
        assertEquals("Strange", rules.sanitizeHudStyle("Strange"));
        assertEquals("Linken", rules.sanitizeHudStyle("Linken"));
        assertEquals(java.util.List.of("Rounded"), rules.hudShapes());
        assertEquals("Rounded", rules.defaultHudShape());
        assertEquals("Rounded", rules.sanitizeHudShape("Pill"));
    }

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
    void activeFamilyMatchesBuildFlavor() {
        assertEquals(BuildFlavor.STRANGE, StyleFlavor.isStrange());
        assertEquals(BuildFlavor.STRANGE ? StyleFlavor.Family.STRANGE : StyleFlavor.Family.LINKEN,
            StyleFlavor.family());
        assertEquals(BuildFlavor.NAME, StyleFlavor.flavorName());
    }
}
