package dev.linkenvisuals;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Guards the flavor branding assets used by the menu, HUD and ClickGUI. */
class BrandingAssetsTest {
    @Test
    void linkenLogosArePackaged() {
        assertNotNull(resource("textures/gui/lv_logo.png"), "Linken LV logo is missing");
        assertNotNull(resource("textures/gui/logo.png"), "Linken HUD logo is missing");
    }

    private static InputStream resource(String path) {
        return BrandingAssetsTest.class.getResourceAsStream("/assets/linkenvisuals/" + path);
    }
}
