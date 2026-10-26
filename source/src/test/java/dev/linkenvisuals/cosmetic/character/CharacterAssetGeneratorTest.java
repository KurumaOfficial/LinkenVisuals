package dev.linkenvisuals.cosmetic.character;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterAssetGeneratorTest {
    @Test
    void generatesAndValidatesCharacterAssets() throws IOException {
        File dir = new File("src/main/resources/assets/linkenvisuals/characters");
        CharacterAssetGenerator.generateAll(dir);

        File baconMesh = new File(dir, "roblox_bacon.mesh");
        File baconPng = new File(dir, "roblox_bacon.png");
        File jasonMesh = new File(dir, "jason_gta6.mesh");
        File jasonPng = new File(dir, "jason_gta6.png");

        assertTrue(baconMesh.exists(), "roblox_bacon.mesh should exist");
        assertTrue(baconPng.exists(), "roblox_bacon.png should exist");
        assertTrue(jasonMesh.exists(), "jason_gta6.mesh should exist");
        assertTrue(jasonPng.exists(), "jason_gta6.png should exist");

        // Validate Roblox Bacon Mesh
        try (FileInputStream in = new FileInputStream(baconMesh)) {
            RudyMesh.Part[] parts = RudyMesh.parse(in, 1);
            assertEquals(6, parts.length);
            for (int i = 0; i < parts.length; i++) {
                assertEquals(i, parts[i].part());
                assertTrue(parts[i].vertices().length >= 24);
                for (float v : parts[i].vertices()) {
                    assertTrue(Float.isFinite(v));
                }
            }
        }

        // Validate Jason GTA 6 Mesh
        try (FileInputStream in = new FileInputStream(jasonMesh)) {
            RudyMesh.Part[] parts = RudyMesh.parse(in, 1);
            assertEquals(6, parts.length);
            for (int i = 0; i < parts.length; i++) {
                assertEquals(i, parts[i].part());
                assertTrue(parts[i].vertices().length >= 24);
                for (float v : parts[i].vertices()) {
                    assertTrue(Float.isFinite(v));
                }
            }
        }

        // Validate Textures
        BufferedImage baconImg = ImageIO.read(baconPng);
        assertNotNull(baconImg);
        assertEquals(512, baconImg.getWidth());
        assertEquals(512, baconImg.getHeight());

        BufferedImage jasonImg = ImageIO.read(jasonPng);
        assertNotNull(jasonImg);
        assertEquals(512, jasonImg.getWidth());
        assertEquals(512, jasonImg.getHeight());

        // Verify Roblox Bacon head UV unwrapping: face is strictly on front (-Z) and not mirrored
        try (FileInputStream in = new FileInputStream(baconMesh)) {
            RudyMesh.Part[] parts = RudyMesh.parse(in, 1);
            RudyMesh.Part head = parts[0];
            float[] data = head.vertices();
            int frontFaceCount = 0;
            for (int i = 0; i < data.length; i += RudyMesh.VERTEX_FLOATS) {
                float x = data[i];
                float nz = data[i + 5];
                float u = data[i + 6];
                float v = data[i + 7];

                // Front face cylinder wall has v == 0.0f (top) or v == 0.25f (bottom), within head radius 0.22
                boolean isFaceWallVertex = (Math.abs(v - 0.0f) < 1e-4f || Math.abs(v - 0.25f) < 1e-4f)
                        && u >= 0.0f && u < 0.249f && x >= -0.23f && x <= 0.23f && data[i + 1] >= -0.45f;

                if (isFaceWallVertex) {
                    // Face texture MUST only appear on front-facing geometry (nz < -0.3f)
                    assertTrue(nz < -0.3f, "Face texture mapped to non-front normal: nz=" + nz);
                    frontFaceCount++;
                    // Face must not be horizontally mirrored: left of face (x < -0.05) has u < 0.125, right (x > 0.05) has u > 0.125
                    if (x < -0.05f) {
                        assertTrue(u < 0.125f, "Left face vertex has right-hand UV: x=" + x + ", u=" + u);
                    } else if (x > 0.05f) {
                        assertTrue(u > 0.125f, "Right face vertex has left-hand UV: x=" + x + ", u=" + u);
                    }
                }
            }
            assertTrue(frontFaceCount > 0, "Roblox Bacon head must have front face vertices");
        }
    }
}
