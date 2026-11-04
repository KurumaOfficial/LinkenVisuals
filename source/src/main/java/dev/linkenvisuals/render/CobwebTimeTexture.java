package dev.linkenvisuals.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;

/**
 * 1x1 texture carrying the cobweb animation phase. Vertex attributes are not reliable for
 * per-frame GUI animation in the deferred 1.21.11 renderer (the batched element keeps the
 * first vertex buffer), so the phase is uploaded as a texture every frame instead.
 */
public final class CobwebTimeTexture {
    private static final Identifier ID = LinkenVisuals.id("dynamic/cobweb_time");
    private static NativeImage image;
    private static NativeImageBackedTexture texture;

    private CobwebTimeTexture() {
    }

    public static Identifier id() {
        return ID;
    }

    public static void update() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        if (image == null) {
            image = new NativeImage(1, 1, true);
            texture = new NativeImageBackedTexture(() -> "cobweb_time", image);
            mc.getTextureManager().registerTexture(ID, texture);
        }
        int phase = (int) ((System.currentTimeMillis() % 12_000L) / 12_000.0 * 255.0);
        image.setColorArgb(0, 0, 0xFF000000 | (phase << 16) | (phase << 8) | phase);
        texture.upload();
    }
}
