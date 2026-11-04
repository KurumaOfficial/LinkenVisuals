package dev.linkenvisuals.cosmetic.character;

import dev.linkenvisuals.render.state.RudyRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class CustomCharacterTest {

    private static final class DummyState implements RudyRenderState {
        private boolean rudy;
        private String custom = "Default";
        private boolean airborne;

        @Override
        public boolean linkenvisuals$isRudy() {
            return rudy;
        }

        @Override
        public void linkenvisuals$setRudy(boolean rudy) {
            this.rudy = rudy;
        }

        @Override
        public String linkenvisuals$getCustomCharacter() {
            return custom;
        }

        @Override
        public void linkenvisuals$setCustomCharacter(String character) {
            this.custom = character;
        }

        @Override
        public boolean linkenvisuals$isAirborne() {
            return airborne;
        }

        @Override
        public void linkenvisuals$setAirborne(boolean airborne) {
            this.airborne = airborne;
        }
    }

    @Test
    void activeDetection() {
        DummyState state = new DummyState();
        assertFalse(RobloxBaconCharacter.active(state));
        assertFalse(JasonCharacter.active(state));

        state.linkenvisuals$setCustomCharacter("Roblox Bacon");
        assertTrue(RobloxBaconCharacter.active(state));
        assertFalse(JasonCharacter.active(state));

        state.linkenvisuals$setCustomCharacter("Jason GTA 6");
        assertFalse(RobloxBaconCharacter.active(state));
        assertTrue(JasonCharacter.active(state));
        assertFalse(AstolfoMesh.active(state));

        state.linkenvisuals$setCustomCharacter("Astolfo");
        assertFalse(RobloxBaconCharacter.active(state));
        assertFalse(JasonCharacter.active(state));
        assertTrue(AstolfoMesh.active(state));
    }

    @Test
    void parsesAstolfoMeshAsset() {
        assertTrue(AstolfoMesh.available(), "Astolfo assets should be present and valid");
    }

    @Test
    void parsesRobloxBaconMeshAsset() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/assets/linkenvisuals/characters/roblox_bacon.mesh")) {
            assertNotNull(stream, "roblox_bacon.mesh must exist on classpath");
            try (DataInputStream in = new DataInputStream(stream)) {
                RudyMesh.Part[] parts = RudyMesh.parse(in, 1);
                assertEquals(6, parts.length);
                for (int i = 0; i < 6; i++) {
                    assertEquals(i, parts[i].part());
                    assertTrue(parts[i].vertices().length > 0);
                }
            }
        }
    }

    @Test
    void parsesJasonGta6MeshAsset() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/assets/linkenvisuals/characters/jason_gta6.mesh")) {
            assertNotNull(stream, "jason_gta6.mesh must exist on classpath");
            try (DataInputStream in = new DataInputStream(stream)) {
                RudyMesh.Part[] parts = RudyMesh.parse(in, 1);
                assertEquals(6, parts.length);
                for (int i = 0; i < 6; i++) {
                    assertEquals(i, parts[i].part());
                    assertTrue(parts[i].vertices().length > 0);
                }
            }
        }
    }

    @Test
    void armGripTransforms() {
        MatrixStack stack = new MatrixStack();
        RobloxBaconCharacter.armGrip(Arm.RIGHT, stack);
        Matrix4f right = stack.peek().getPositionMatrix();
        assertTrue(right.m30() > 0, "Right arm grip should translate right");

        MatrixStack stackLeft = new MatrixStack();
        RobloxBaconCharacter.armGrip(Arm.LEFT, stackLeft);
        Matrix4f left = stackLeft.peek().getPositionMatrix();
        assertTrue(left.m30() < 0, "Left arm grip should translate left");

        MatrixStack jasonStack = new MatrixStack();
        JasonCharacter.armGrip(Arm.RIGHT, jasonStack);
        Matrix4f jasonRight = jasonStack.peek().getPositionMatrix();
        assertTrue(jasonRight.m30() > 0, "Jason right arm grip should translate right");
    }
}
