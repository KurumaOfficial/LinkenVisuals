package dev.linkenvisuals.cosmetic.character;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Procedural generator for custom 3D character models (Roblox Bacon and Jason GTA 6).
 * Outputs rigid triangle-soup .mesh assets (compatible with {@link RudyMesh}) and
 * high-resolution 512x512 texture maps matching the reference images.
 */
public final class CharacterAssetGenerator {
    private static final int TEX_SIZE = 512;

    private CharacterAssetGenerator() {
    }

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : "source/src/main/resources/assets/linkenvisuals/characters");
        dir.mkdirs();
        generateAll(dir);
    }

    public static void generateAll(File outputDir) throws IOException {
        outputDir.mkdirs();
        generateRobloxBacon(new File(outputDir, "roblox_bacon.mesh"), new File(outputDir, "roblox_bacon.png"));
        generateJasonGta6(new File(outputDir, "jason_gta6.mesh"), new File(outputDir, "jason_gta6.png"));
    }

    // =========================================================================
    // 1. ROBLOX NOOB / BACON HAIR (R6 Blocky / Beveled Cylinder Head / Pal Hair)
    // =========================================================================

    public static void generateRobloxBacon(File meshFile, File textureFile) throws IOException {
        List<MeshPart> parts = new ArrayList<>();

        // Part 0: Head (Smoothly beveled 16-segment cylindrical head + 3D Pal Bacon hair)
        MeshBuilder head = new MeshBuilder(0, 0);
        // Base head: beveled cylinder from y = -0.44 to 0.0 with radius 0.22.
        // Front (-Z) receives face texture (eyes + mouth); sides, back, top, bottom get solid yellow.
        head.addRobloxHead(-0.44f, 0.0f, 0.22f,
            0.0f, 0.0f, 0.25f, 0.25f,
            0.20f, 0.02f, 0.24f, 0.06f);

        // Pal Hair - Top mass
        head.addBox(-0.24f, -0.52f, -0.24f, 0.24f, -0.40f, 0.24f,
            0.25f, 0.0f, 0.50f, 0.25f);
        // Pal Hair - Distinctive center front bacon hair strand draping over forehead
        head.addBox(-0.06f, -0.48f, -0.26f, 0.06f, -0.22f, -0.21f,
            0.25f, 0.25f, 0.35f, 0.40f);
        // Pal Hair - Left front curved bacon strand
        head.addBox(-0.18f, -0.46f, -0.25f, -0.07f, -0.26f, -0.21f,
            0.35f, 0.25f, 0.45f, 0.40f);
        // Pal Hair - Right front curved bacon strand
        head.addBox(0.07f, -0.46f, -0.25f, 0.18f, -0.26f, -0.21f,
            0.35f, 0.25f, 0.45f, 0.40f);
        // Pal Hair - Left and Right side hair volumes
        head.addBox(-0.26f, -0.44f, -0.20f, -0.21f, -0.18f, 0.20f,
            0.25f, 0.0f, 0.50f, 0.25f);
        head.addBox(0.21f, -0.44f, -0.20f, 0.26f, -0.18f, 0.20f,
            0.25f, 0.0f, 0.50f, 0.25f);
        // Pal Hair - Back hair layer
        head.addBox(-0.24f, -0.44f, 0.21f, 0.24f, -0.16f, 0.26f,
            0.25f, 0.0f, 0.50f, 0.25f);
        parts.add(head.build());

        // Part 1: Torso (Classic R6 Roblox Blue Torso #0D69AC)
        MeshBuilder body = new MeshBuilder(1, 0);
        // Main torso: x: -0.25..0.25, y: 0.0..0.72, z: -0.13..0.13
        body.addBox(-0.25f, 0.0f, -0.13f, 0.25f, 0.72f, 0.13f,
            0.0f, 0.25f, 0.25f, 0.50f);
        parts.add(body.build());

        // Part 2: Right Arm (Classic R6 Yellow Blocky Arm #F5CD2F + C-clamp hand)
        MeshBuilder rightArm = new MeshBuilder(2, 0);
        // Arm main block: x: -0.12..0.12, y: -0.06..0.48, z: -0.12..0.12
        rightArm.addBox(-0.12f, -0.06f, -0.12f, 0.12f, 0.48f, 0.12f,
            0.0f, 0.50f, 0.25f, 0.70f);
        // 3D Roblox C-clamp hand (wrist base + 2 curved prongs with empty center)
        rightArm.addBox(-0.09f, 0.48f, -0.09f, 0.09f, 0.54f, 0.09f,
            0.0f, 0.50f, 0.25f, 0.70f);
        rightArm.addBox(-0.09f, 0.54f, -0.09f, -0.02f, 0.65f, 0.09f,
            0.0f, 0.50f, 0.25f, 0.70f);
        rightArm.addBox(0.02f, 0.54f, -0.09f, 0.09f, 0.65f, 0.09f,
            0.0f, 0.50f, 0.25f, 0.70f);
        parts.add(rightArm.build());

        // Part 3: Left Arm (Symmetrical Yellow Blocky Arm + C-clamp hand)
        MeshBuilder leftArm = new MeshBuilder(3, 0);
        leftArm.addBox(-0.12f, -0.06f, -0.12f, 0.12f, 0.48f, 0.12f,
            0.0f, 0.50f, 0.25f, 0.70f);
        leftArm.addBox(-0.09f, 0.48f, -0.09f, 0.09f, 0.54f, 0.09f,
            0.0f, 0.50f, 0.25f, 0.70f);
        leftArm.addBox(-0.09f, 0.54f, -0.09f, -0.02f, 0.65f, 0.09f,
            0.0f, 0.50f, 0.25f, 0.70f);
        leftArm.addBox(0.02f, 0.54f, -0.09f, 0.09f, 0.65f, 0.09f,
            0.0f, 0.50f, 0.25f, 0.70f);
        parts.add(leftArm.build());

        // Part 4: Right Leg (Classic R6 Olive/Lime Green Leg #A3B243)
        MeshBuilder rightLeg = new MeshBuilder(4, 0);
        // Leg block: x: -0.12..0.12, y: 0.0..0.72, z: -0.12..0.12
        rightLeg.addBox(-0.12f, 0.0f, -0.12f, 0.12f, 0.72f, 0.12f,
            0.0f, 0.70f, 0.25f, 0.95f);
        parts.add(rightLeg.build());

        // Part 5: Left Leg (Symmetrical Olive/Lime Green Leg #A3B243)
        MeshBuilder leftLeg = new MeshBuilder(5, 0);
        leftLeg.addBox(-0.12f, 0.0f, -0.12f, 0.12f, 0.72f, 0.12f,
            0.0f, 0.70f, 0.25f, 0.95f);
        parts.add(leftLeg.build());

        writeMeshFile(meshFile, parts);
        writeRobloxBaconTexture(textureFile);
    }

    private static void writeRobloxBaconTexture(File file) throws IOException {
        BufferedImage img = new BufferedImage(TEX_SIZE, TEX_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background: transparent
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, TEX_SIZE, TEX_SIZE);

        // --- Region 1: Head Face & Skin (0..128, 0..128) ---
        // Bright Yellow #F5CD2F matching user reference screenshot
        Color robloxYellow = new Color(0xF5, 0xCD, 0x2F);
        g.setColor(robloxYellow);
        g.fillRect(0, 0, 128, 128);
        // Subtle edge shading
        g.setColor(new Color(0xDE, 0xB6, 0x24));
        g.drawRect(1, 1, 126, 126);

        // Solid yellow patch reserved at (102..122, 10..30) for sides/back/top/bottom mapping
        g.setColor(robloxYellow);
        g.fillRect(100, 8, 25, 25);

        // Classic Roblox Smiley Face (strictly in the front center):
        // Vertical oval eyes at x=40 and x=77, y=48
        g.setColor(new Color(0x11, 0x12, 0x14));
        g.fillOval(40, 48, 11, 20);
        g.fillOval(77, 48, 11, 20);
        // Iconic curved smile
        g.setStroke(new BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(36, 62, 56, 32, 200, 140);

        // --- Region 2: Pal Bacon Hair (128..256, 0..128) & Bangs (128..256, 128..205) ---
        Color hairDark = new Color(0x4A, 0x25, 0x11);
        Color hairMid = new Color(0x78, 0x3E, 0x1D);
        Color hairLight = new Color(0xA9, 0x63, 0x32);
        Color hairBaconStreak = new Color(0xC2, 0x7A, 0x45);

        g.setPaint(new GradientPaint(128, 0, hairMid, 256, 128, hairDark));
        g.fillRect(128, 0, 128, 205);
        // Bacon hair wavy streaks
        g.setStroke(new BasicStroke(4.0f));
        for (int y = 8; y < 200; y += 12) {
            g.setColor(y % 24 == 0 ? hairLight : hairBaconStreak);
            g.drawLine(132, y, 252, y + 6);
        }

        // --- Region 3: Torso (0..128, 128..256) - Bright Blue #0D69AC ---
        Color torsoBlue = new Color(0x0D, 0x69, 0xAC);
        Color torsoBorder = new Color(0x09, 0x52, 0x86);
        g.setColor(torsoBlue);
        g.fillRect(0, 128, 128, 128);
        g.setColor(torsoBorder);
        g.drawRect(1, 129, 126, 126);

        // --- Region 4: Arms (0..128, 256..358) - Bright Yellow #F5CD2F ---
        g.setColor(robloxYellow);
        g.fillRect(0, 256, 128, 102);
        g.setColor(new Color(0xDE, 0xB6, 0x24));
        g.drawRect(1, 257, 126, 100);

        // --- Region 5: Legs (0..128, 358..486) - Olive / Lime Green #A3B243 ---
        Color legGreen = new Color(0xA3, 0xB2, 0x43);
        Color legBorder = new Color(0x7F, 0x8E, 0x2E);
        g.setColor(legGreen);
        g.fillRect(0, 358, 128, 128);
        g.setColor(legBorder);
        g.drawRect(1, 359, 126, 126);

        g.dispose();
        ImageIO.write(img, "png", file);
    }

    // =========================================================================
    // 2. JASON FROM GTA 6 (Muscular Athletic / Silver Chain / Camo Cargo)
    // =========================================================================

    public static void generateJasonGta6(File meshFile, File textureFile) throws IOException {
        List<MeshPart> parts = new ArrayList<>();

        // Part 0: Head (Handsome rugged face, backward camo cap, tactical sunglasses)
        MeshBuilder head = new MeshBuilder(0, 0);
        // Base head: Front (-Z) gets face with eyes/beard, all other 5 faces get clean skin without eyes/mouth
        head.addBoxFrontSide(-0.22f, -0.44f, -0.22f, 0.22f, 0.0f, 0.22f,
            0.0f, 0.0f, 0.25f, 0.25f,
            0.20f, 0.02f, 0.24f, 0.06f);
        // Backward Baseball Cap Crown
        head.addBox(-0.23f, -0.50f, -0.23f, 0.23f, -0.40f, 0.23f,
            0.25f, 0.0f, 0.50f, 0.25f);
        // 3D Backward Brim / Visor projecting back over neck
        head.addBox(-0.16f, -0.44f, 0.22f, 0.16f, -0.40f, 0.35f,
            0.25f, 0.0f, 0.50f, 0.25f);
        // 3D Tactical Sunglasses on face
        head.addBox(-0.20f, -0.32f, -0.245f, 0.20f, -0.23f, -0.215f,
            0.25f, 0.25f, 0.45f, 0.35f);
        parts.add(head.build());

        // Part 1: Torso (Muscular bare upper body, 3D silver chain, tactical belt)
        MeshBuilder body = new MeshBuilder(1, 0);
        // Defined torso: Front (-Z) gets muscular chest/abs, other faces get muscular skin/back
        body.addBoxFrontSide(-0.25f, 0.0f, -0.13f, 0.25f, 0.72f, 0.13f,
            0.0f, 0.25f, 0.25f, 0.50f,
            0.20f, 0.02f, 0.24f, 0.06f);
        // 3D Silver chain necklace hanging down from neck to mid-chest
        body.addBox(-0.12f, 0.04f, -0.142f, -0.08f, 0.22f, -0.125f,
            0.25f, 0.35f, 0.35f, 0.50f);
        body.addBox(0.08f, 0.04f, -0.142f, 0.12f, 0.22f, -0.125f,
            0.25f, 0.35f, 0.35f, 0.50f);
        body.addBox(-0.08f, 0.20f, -0.144f, 0.08f, 0.235f, -0.125f,
            0.25f, 0.35f, 0.35f, 0.50f);
        // Tactical belt volume at waist
        body.addBox(-0.255f, 0.65f, -0.135f, 0.255f, 0.72f, 0.135f,
            0.35f, 0.35f, 0.50f, 0.50f);
        parts.add(body.build());

        // Part 2: Right Arm (Muscular arm + wrist strap)
        MeshBuilder rightArm = new MeshBuilder(2, 0);
        rightArm.addBox(-0.12f, -0.06f, -0.12f, 0.12f, 0.66f, 0.12f,
            0.0f, 0.50f, 0.15f, 0.75f);
        rightArm.addBox(-0.125f, 0.48f, -0.125f, 0.125f, 0.53f, 0.125f,
            0.15f, 0.50f, 0.25f, 0.60f);
        parts.add(rightArm.build());

        // Part 3: Left Arm (Muscular arm + 3D sports watch)
        MeshBuilder leftArm = new MeshBuilder(3, 0);
        leftArm.addBox(-0.12f, -0.06f, -0.12f, 0.12f, 0.66f, 0.12f,
            0.0f, 0.50f, 0.15f, 0.75f);
        leftArm.addBox(-0.13f, 0.46f, -0.13f, 0.13f, 0.52f, 0.13f,
            0.15f, 0.60f, 0.25f, 0.75f);
        leftArm.addBox(-0.145f, 0.47f, -0.04f, -0.125f, 0.51f, 0.04f,
            0.15f, 0.60f, 0.25f, 0.75f);
        parts.add(leftArm.build());

        // Part 4: Right Leg (Camouflage cargo shorts, 3D side pocket, athletic sock & sneaker)
        MeshBuilder rightLeg = new MeshBuilder(4, 0);
        rightLeg.addBox(-0.125f, 0.0f, -0.125f, 0.125f, 0.54f, 0.125f,
            0.0f, 0.75f, 0.15f, 1.0f);
        rightLeg.addBox(-0.16f, 0.18f, -0.06f, -0.12f, 0.36f, 0.06f,
            0.0f, 0.75f, 0.15f, 1.0f);
        rightLeg.addBox(-0.115f, 0.54f, -0.115f, 0.115f, 0.63f, 0.115f,
            0.15f, 0.75f, 0.25f, 0.88f);
        rightLeg.addBox(-0.125f, 0.63f, -0.15f, 0.125f, 0.75f, 0.13f,
            0.15f, 0.88f, 0.30f, 1.0f);
        parts.add(rightLeg.build());

        // Part 5: Left Leg (Symmetrical with outer side pocket at +X)
        MeshBuilder leftLeg = new MeshBuilder(5, 0);
        leftLeg.addBox(-0.125f, 0.0f, -0.125f, 0.125f, 0.54f, 0.125f,
            0.0f, 0.75f, 0.15f, 1.0f);
        leftLeg.addBox(0.12f, 0.18f, -0.06f, 0.16f, 0.36f, 0.06f,
            0.0f, 0.75f, 0.15f, 1.0f);
        leftLeg.addBox(-0.115f, 0.54f, -0.115f, 0.115f, 0.63f, 0.115f,
            0.15f, 0.75f, 0.25f, 0.88f);
        leftLeg.addBox(-0.125f, 0.63f, -0.15f, 0.125f, 0.75f, 0.13f,
            0.15f, 0.88f, 0.30f, 1.0f);
        parts.add(leftLeg.build());

        writeMeshFile(meshFile, parts);
        writeJasonGta6Texture(textureFile);
    }

    private static void writeJasonGta6Texture(File file) throws IOException {
        BufferedImage img = new BufferedImage(TEX_SIZE, TEX_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, TEX_SIZE, TEX_SIZE);

        // --- Region 1: Handsome Tanned Face & Skin (0..128, 0..128) ---
        Color skinBase = new Color(0xC6, 0x8C, 0x65);
        Color skinShadow = new Color(0xAD, 0x75, 0x50);
        Color eyeBrown = new Color(0x3B, 0x24, 0x14);
        Color browDark = new Color(0x2C, 0x20, 0x18);
        Color stubbleColor = new Color(0x3A, 0x2C, 0x23, 175);

        g.setColor(skinBase);
        g.fillRect(0, 0, 128, 128);

        // Solid skin margin reserved at (102..122, 10..30) for sides/back/top/bottom mapping
        g.setColor(skinBase);
        g.fillRect(100, 8, 25, 25);

        // Shading edges
        g.setColor(skinShadow);
        g.drawRect(1, 1, 126, 126);

        // Eyebrows & Eyes
        g.setColor(browDark);
        g.fillRect(36, 42, 18, 5); // left brow
        g.fillRect(74, 42, 18, 5); // right brow
        g.setColor(Color.WHITE);
        g.fillRect(38, 48, 14, 8);
        g.fillRect(76, 48, 14, 8);
        g.setColor(eyeBrown);
        g.fillOval(43, 49, 7, 7);
        g.fillOval(81, 49, 7, 7);
        // Beard & 5 o'clock stubble around jaw and upper lip
        g.setColor(stubbleColor);
        for (int y = 72; y < 122; y += 3) {
            for (int x = 24; x < 104; x += 3) {
                if ((x * 17 + y * 31) % 5 < 3) {
                    g.fillRect(x, y, 2, 2);
                }
            }
        }

        // --- Region 2: Camo Cap & Tactical Sunglasses (128..256, 0..128 & 128..230, 128..179) ---
        Color camoBase = new Color(0x6A, 0x6D, 0x52);
        Color camoTan = new Color(0x9C, 0x91, 0x74);
        Color camoBrown = new Color(0x4E, 0x3C, 0x2C);
        Color camoDarkGreen = new Color(0x3B, 0x44, 0x31);

        g.setColor(camoBase);
        g.fillRect(128, 0, 128, 128);
        for (int i = 0; i < 24; i++) {
            g.setColor(i % 3 == 0 ? camoTan : (i % 3 == 1 ? camoBrown : camoDarkGreen));
            int cx = 130 + (i * 29) % 110;
            int cy = 6 + (i * 37) % 110;
            g.fillOval(cx, cy, 28, 18);
        }

        // Tactical Sunglasses (128..230, 128..179)
        Color frameColor = new Color(0x1B, 0x1E, 0x1C);
        Color lensEmerald = new Color(0x12, 0x47, 0x38);
        Color lensHighlight = new Color(0x35, 0xA7, 0x88);
        g.setColor(frameColor);
        g.fillRoundRect(128, 128, 102, 51, 8, 8);
        // Dual polarized emerald lenses
        g.setPaint(new GradientPaint(134, 134, lensHighlight, 172, 172, lensEmerald));
        g.fillRoundRect(134, 134, 40, 38, 6, 6);
        g.fillRoundRect(182, 134, 40, 38, 6, 6);

        // --- Region 3: Muscular Bare Torso (0..128, 128..256) ---
        g.setColor(skinBase);
        g.fillRect(0, 128, 128, 128);
        // Pectoral muscles definition
        g.setColor(skinShadow);
        g.setStroke(new BasicStroke(3.0f));
        g.drawRoundRect(14, 140, 46, 30, 12, 12);
        g.drawRoundRect(68, 140, 46, 30, 12, 12);
        // Six-pack abdominal muscles
        g.drawRoundRect(28, 180, 32, 20, 8, 8);
        g.drawRoundRect(68, 180, 32, 20, 8, 8);
        g.drawRoundRect(28, 204, 32, 20, 8, 8);
        g.drawRoundRect(68, 204, 32, 20, 8, 8);
        g.drawRoundRect(30, 228, 30, 18, 6, 6);
        g.drawRoundRect(68, 228, 30, 18, 6, 6);
        // Chest and abdominal hair texture
        g.setColor(new Color(0x56, 0x38, 0x26));
        for (int y = 146; y < 190; y += 4) {
            for (int x = 40; x < 88; x += 4) {
                if ((x * 13 + y * 7) % 4 == 0) {
                    g.fillRect(x, y, 1, 2);
                }
            }
        }

        // --- Region 4: Silver Curb Chain Necklace (128..180, 180..256) ---
        Color silverHigh = new Color(0xF2, 0xF5, 0xF8);
        Color silverDark = new Color(0x76, 0x7F, 0x88);
        g.setColor(new Color(0x30, 0x35, 0x3A));
        g.fillRect(128, 180, 52, 76);
        for (int y = 182; y < 254; y += 8) {
            g.setColor(silverHigh);
            g.fillOval(136, y, 36, 6);
            g.setColor(silverDark);
            g.fillOval(144, y + 1, 20, 4);
        }

        // --- Region 5: Tactical Belt (180..256, 180..256) ---
        Color beltOlive = new Color(0x3A, 0x40, 0x32);
        Color buckleMatte = new Color(0x1B, 0x1C, 0x1D);
        g.setColor(beltOlive);
        g.fillRect(180, 180, 76, 76);
        g.setColor(buckleMatte);
        g.fillRoundRect(198, 198, 40, 38, 6, 6);
        g.setColor(new Color(0x5A, 0x5C, 0x5E));
        g.drawRect(205, 205, 26, 24);

        // --- Region 6: Muscular Arm Skin & Watch (0..128, 256..384) ---
        g.setColor(skinBase);
        g.fillRect(0, 256, 77, 128);
        g.setColor(skinShadow);
        g.setStroke(new BasicStroke(2.0f));
        g.drawArc(10, 270, 32, 60, 290, 140);
        g.drawArc(36, 310, 28, 50, 100, 140);
        // Sports watch (77..128, 256..384)
        Color watchCase = new Color(0x1F, 0x22, 0x24);
        Color watchDial = new Color(0x0C, 0x12, 0x15);
        Color watchAccent = new Color(0x4B, 0xB5, 0x80);
        g.setColor(watchCase);
        g.fillRect(77, 256, 51, 128);
        g.setColor(watchDial);
        g.fillOval(84, 290, 36, 36);
        g.setColor(watchAccent);
        g.fillOval(100, 306, 4, 4);

        // --- Region 7: Camo Cargo Shorts/Pants (0..77, 384..512) ---
        g.setColor(camoBase);
        g.fillRect(0, 384, 77, 128);
        for (int i = 0; i < 28; i++) {
            g.setColor(i % 3 == 0 ? camoTan : (i % 3 == 1 ? camoBrown : camoDarkGreen));
            int cx = (i * 23) % 65;
            int cy = 388 + (i * 31) % 115;
            g.fillOval(cx, cy, 22, 16);
        }
        g.setColor(new Color(0x28, 0x23, 0x1C));
        g.setStroke(new BasicStroke(2.0f));
        g.drawRect(6, 412, 64, 48);

        // --- Region 8: Athletic Striped Socks & Sneakers (77..154, 384..512) ---
        Color sockWhite = new Color(0xED, 0xEF, 0xEF);
        Color stripeRed = new Color(0xB5, 0x2B, 0x27);
        Color stripeBlue = new Color(0x23, 0x4E, 0x96);
        Color shoeNavy = new Color(0x1D, 0x2D, 0x44);
        Color shoeMidsole = new Color(0xF2, 0xF4, 0xF5);

        // Socks: y=384..448
        g.setColor(sockWhite);
        g.fillRect(77, 384, 77, 64);
        g.setColor(stripeRed);
        g.fillRect(77, 396, 77, 6);
        g.setColor(stripeBlue);
        g.fillRect(77, 406, 77, 6);

        // Sneakers: y=448..512
        g.setColor(shoeNavy);
        g.fillRect(77, 448, 77, 64);
        g.setColor(shoeMidsole);
        g.fillRect(77, 496, 77, 12);
        g.setColor(new Color(0x28, 0x2C, 0x30));
        g.fillRect(77, 508, 77, 4);

        g.dispose();
        ImageIO.write(img, "png", file);
    }

    // =========================================================================
    // MESH DATA STRUCTURES & UTILITIES
    // =========================================================================

    public record MeshPart(int part, int material, float[] vertices) {
    }

    public static final class MeshBuilder {
        private final int part;
        private final int material;
        private final List<Float> vertices = new ArrayList<>();

        public MeshBuilder(int part, int material) {
            this.part = part;
            this.material = material;
        }

        /**
         * Adds an axis-aligned box with normals and uniform UV coordinates across all faces.
         */
        public void addBox(float x0, float y0, float z0, float x1, float y1, float z1,
                           float u0, float v0, float u1, float v1) {
            addBoxFaces(x0, y0, z0, x1, y1, z1,
                u0, v0, u1, v1,
                u0, v0, u1, v1,
                u0, v0, u1, v1,
                u0, v0, u1, v1,
                u0, v0, u1, v1,
                u0, v0, u1, v1);
        }

        /**
         * Adds an axis-aligned box with individual per-face UV coordinates.
         * Front face is -Z (entity forward in Minecraft model coordinates).
         */
        public void addBoxFaces(float x0, float y0, float z0, float x1, float y1, float z1,
                                float fu0, float fv0, float fu1, float fv1,
                                float bu0, float bv0, float bu1, float bv1,
                                float tu0, float tv0, float tu1, float tv1,
                                float du0, float dv0, float du1, float dv1,
                                float ru0, float rv0, float ru1, float rv1,
                                float lu0, float lv0, float lu1, float lv1) {
            // Front (-Z in entity space: x0 is viewer left, x1 is viewer right)
            quad(x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0, 0, -1, fu0, fv1, fu1, fv0);
            // Back (+Z in entity space: from behind, +X is viewer left, -X is viewer right)
            quad(x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0, 0, 1, bu0, bv1, bu1, bv0);
            // Top (-Y in entity space is UP)
            quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, tu0, tv0, tu1, tv1);
            // Bottom (+Y is DOWN)
            quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, du0, dv0, du1, dv1);
            // Left face (+X in entity space)
            quad(x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1, 0, 0, lu0, lv1, lu1, lv0);
            // Right face (-X in entity space)
            quad(x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, -1, 0, 0, ru1, rv1, ru0, rv0);
        }

        /**
         * Adds an axis-aligned box where front face (-Z) receives front UVs (e.g. face/eyes),
         * and all other 5 faces (back, top, bottom, right, left) receive side UVs (e.g. solid skin).
         */
        public void addBoxFrontSide(float x0, float y0, float z0, float x1, float y1, float z1,
                                    float fu0, float fv0, float fu1, float fv1,
                                    float su0, float sv0, float su1, float sv1) {
            addBoxFaces(x0, y0, z0, x1, y1, z1,
                fu0, fv0, fu1, fv1,
                su0, sv0, su1, sv1,
                su0, sv0, su1, sv1,
                su0, sv0, su1, sv1,
                su0, sv0, su1, sv1,
                su0, sv0, su1, sv1);
        }

        /**
         * Builds an authentic Roblox smoothly beveled cylindrical head (16 segments with beveled rims).
         * Front face (-Z) receives face texture; sides, back, and top/bottom caps receive solid yellow.
         */
        public void addRobloxHead(float yTop, float yBottom, float radius,
                                  float fu0, float fv0, float fu1, float fv1,
                                  float su0, float sv0, float su1, float sv1) {
            int segments = 16;
            float bevelH = 0.04f;
            float bevelR = 0.04f;
            float innerR = radius - bevelR;
            float yRimTop = yTop + bevelH;
            float yRimBottom = yBottom - bevelH;

            float[][] innerTop = new float[segments][3];
            float[][] outerTop = new float[segments][3];
            float[][] outerBottom = new float[segments][3];
            float[][] innerBottom = new float[segments][3];
            float[] cosArr = new float[segments];
            float[] sinArr = new float[segments];

            for (int i = 0; i < segments; i++) {
                double angle = i * 2.0 * Math.PI / segments;
                float sin = (float) Math.sin(angle);
                float cos = (float) Math.cos(angle);
                sinArr[i] = sin;
                cosArr[i] = cos;

                float xOut = radius * sin;
                float zOut = -radius * cos; // Front is -Z
                float xIn = innerR * sin;
                float zIn = -innerR * cos;

                innerTop[i] = new float[]{xIn, yTop, zIn};
                outerTop[i] = new float[]{xOut, yRimTop, zOut};
                outerBottom[i] = new float[]{xOut, yRimBottom, zOut};
                innerBottom[i] = new float[]{xIn, yBottom, zIn};
            }

            // 1. Top flat cap (fan at yTop, normal 0, -1, 0, solid yellow)
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                triangle(0f, yTop, 0f,
                         innerTop[next][0], yTop, innerTop[next][2],
                         innerTop[i][0], yTop, innerTop[i][2],
                         0f, -1f, 0f, su0, sv0, su1, sv1);
            }

            // 2. Top bevel rim (quad from innerTop to outerTop)
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                float nx = (sinArr[i] + sinArr[next]) * 0.5f;
                float nz = -(cosArr[i] + cosArr[next]) * 0.5f;
                quadCustom(innerTop[i][0], innerTop[i][1], innerTop[i][2],
                           innerTop[next][0], innerTop[next][1], innerTop[next][2],
                           outerTop[next][0], outerTop[next][1], outerTop[next][2],
                           outerTop[i][0], outerTop[i][1], outerTop[i][2],
                           nx, -0.7f, nz,
                           su0, sv0, su1, sv1);
            }

            // 3. Main cylinder wall (from outerTop to outerBottom)
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                float midCos = (cosArr[i] + cosArr[next]) * 0.5f;
                float nx = (sinArr[i] + sinArr[next]) * 0.5f;
                float nz = -(cosArr[i] + cosArr[next]) * 0.5f;

                if (midCos > 0.35f) {
                    float u_i = fu0 + (0.5f + outerTop[i][0] / (2.0f * radius)) * (fu1 - fu0);
                    float u_next = fu0 + (0.5f + outerTop[next][0] / (2.0f * radius)) * (fu1 - fu0);
                    float v_top = fv0;
                    float v_bottom = fv1;

                    v(outerTop[i][0], outerTop[i][1], outerTop[i][2], nx, 0f, nz, u_i, v_top);
                    v(outerTop[next][0], outerTop[next][1], outerTop[next][2], nx, 0f, nz, u_next, v_top);
                    v(outerBottom[next][0], outerBottom[next][1], outerBottom[next][2], nx, 0f, nz, u_next, v_bottom);

                    v(outerTop[i][0], outerTop[i][1], outerTop[i][2], nx, 0f, nz, u_i, v_top);
                    v(outerBottom[next][0], outerBottom[next][1], outerBottom[next][2], nx, 0f, nz, u_next, v_bottom);
                    v(outerBottom[i][0], outerBottom[i][1], outerBottom[i][2], nx, 0f, nz, u_i, v_bottom);
                } else {
                    quadCustom(outerTop[i][0], outerTop[i][1], outerTop[i][2],
                               outerTop[next][0], outerTop[next][1], outerTop[next][2],
                               outerBottom[next][0], outerBottom[next][1], outerBottom[next][2],
                               outerBottom[i][0], outerBottom[i][1], outerBottom[i][2],
                               nx, 0f, nz,
                               su0, sv0, su1, sv1);
                }
            }

            // 4. Bottom bevel rim (from outerBottom to innerBottom)
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                float nx = (sinArr[i] + sinArr[next]) * 0.5f;
                float nz = -(cosArr[i] + cosArr[next]) * 0.5f;
                quadCustom(outerBottom[i][0], outerBottom[i][1], outerBottom[i][2],
                           outerBottom[next][0], outerBottom[next][1], outerBottom[next][2],
                           innerBottom[next][0], innerBottom[next][1], innerBottom[next][2],
                           innerBottom[i][0], innerBottom[i][1], innerBottom[i][2],
                           nx, 0.7f, nz,
                           su0, sv0, su1, sv1);
            }

            // 5. Bottom flat cap (fan at yBottom, normal 0, 1, 0, solid yellow)
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                triangle(0f, yBottom, 0f,
                         innerBottom[i][0], yBottom, innerBottom[i][2],
                         innerBottom[next][0], yBottom, innerBottom[next][2],
                         0f, 1f, 0f, su0, sv0, su1, sv1);
            }
        }

        private void triangle(float x0, float y0, float z0,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float nx, float ny, float nz,
                              float u0, float v0, float u1, float v1) {
            v(x0, y0, z0, nx, ny, nz, u0, v0);
            v(x1, y1, z1, nx, ny, nz, u1, v0);
            v(x2, y2, z2, nx, ny, nz, u1, v1);
        }

        private void quadCustom(float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float nx, float ny, float nz,
                                float u0, float v0, float u1, float v1) {
            v(x0, y0, z0, nx, ny, nz, u0, v0);
            v(x1, y1, z1, nx, ny, nz, u1, v0);
            v(x2, y2, z2, nx, ny, nz, u1, v1);

            v(x0, y0, z0, nx, ny, nz, u0, v0);
            v(x2, y2, z2, nx, ny, nz, u1, v1);
            v(x3, y3, z3, nx, ny, nz, u0, v1);
        }

        private void quad(float x0, float y0, float z0,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float x3, float y3, float z3,
                          float nx, float ny, float nz,
                          float u0, float v0, float u1, float v1) {
            // Triangle 1: 0, 1, 2
            v(x0, y0, z0, nx, ny, nz, u0, v0);
            v(x1, y1, z1, nx, ny, nz, u1, v0);
            v(x2, y2, z2, nx, ny, nz, u1, v1);
            // Triangle 2: 0, 2, 3
            v(x0, y0, z0, nx, ny, nz, u0, v0);
            v(x2, y2, z2, nx, ny, nz, u1, v1);
            v(x3, y3, z3, nx, ny, nz, u0, v1);
        }

        private void v(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
            vertices.add(x);
            vertices.add(y);
            vertices.add(z);
            vertices.add(nx);
            vertices.add(ny);
            vertices.add(nz);
            vertices.add(u);
            vertices.add(v);
        }

        public MeshPart build() {
            float[] data = new float[vertices.size()];
            for (int i = 0; i < data.length; i++) {
                data[i] = vertices.get(i);
            }
            return new MeshPart(part, material, data);
        }
    }

    private static void writeMeshFile(File file, List<MeshPart> parts) throws IOException {
        file.getParentFile().mkdirs();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(RudyMesh.MAGIC); // 0x52554459
            out.writeInt(parts.size());
            for (MeshPart part : parts) {
                out.writeInt(part.part());
                out.writeInt(part.material());
                out.writeInt(part.vertices().length / RudyMesh.VERTEX_FLOATS);
                for (float f : part.vertices()) {
                    out.writeFloat(f);
                }
            }
        }
    }
}
