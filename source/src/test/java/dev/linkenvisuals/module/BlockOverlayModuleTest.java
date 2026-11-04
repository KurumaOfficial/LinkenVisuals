package dev.linkenvisuals.module;

import dev.linkenvisuals.module.impl.BlockOverlayModule;
import dev.linkenvisuals.module.impl.BlockOverlayModule.FillMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** BlockOverlay module wiring: modes, shader names, settings, registration. */
class BlockOverlayModuleTest {
    @Test
    void nineModesWithMatchingShaderNames() {
        assertEquals(9, FillMode.values().length);
        List<String> names = List.of("aurora", "plasma", "cosmos", "cobweb", "fireworks",
            "galaxy", "nebula", "starfield", "stars");
        for (FillMode mode : FillMode.values()) {
            assertTrue(names.contains(mode.shaderName()),
                "unexpected shader name: " + mode.shaderName());
        }
    }

    @Test
    void defaultsMirrorDonor() {
        BlockOverlayModule module = new BlockOverlayModule();
        assertEquals("blockoverlay", module.id());
        assertEquals("Block Overlay", module.info().name());
        // TEMPORARILY DISABLED: the module is hidden from the GUI until the
        // block-overlay rendering bugs are fixed.
        assertFalse(module.enabled(), "overlay must stay off while hidden from the GUI");
        assertTrue(module.fill.value());
        assertTrue(module.useShader.value());
        assertEquals(FillMode.AURORA, module.mode());
        assertEquals(70.0f, module.fillAlpha.value());
        assertEquals(255.0f, module.outlineAlpha.value());
        assertEquals(1.0f, module.shaderSpeed.value());
    }

    @Test
    void modeFollowsSettingIndex() {
        BlockOverlayModule module = new BlockOverlayModule();
        module.fillMode.set("Stars");
        assertEquals(FillMode.STARS, module.mode());
    }

    @Test
    void hiddenFromGuiWhileDisabled() {
        BlockOverlayModule module = new BlockOverlayModule();
        assertFalse(module.enabled());
        // TEMPORARILY DISABLED: not registered, so neither GUI nor config can
        // turn it on. Re-enable the registration together with the fix.
        assertNull(Modules.get("blockoverlay"));
    }
}
