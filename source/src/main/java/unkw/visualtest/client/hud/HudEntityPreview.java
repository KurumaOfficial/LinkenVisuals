package unkw.visualtest.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.entity.LivingEntity;

public final class HudEntityPreview {
    private static boolean rendering;

    private HudEntityPreview() {
    }

    public static Framebuffer getFramebufferOverride() {
        return null;
    }

    public static void render(DrawContext context, MinecraftClient client, LivingEntity entity, int x, int y, int width, int height) {
        if (rendering || entity == null || entity.isRemoved() || width < 4 || height < 4) {
            return;
        }
        rendering = true;
        try {
            int size = (int) (Math.min(width, height) * 0.42f);
            InventoryScreen.drawEntity(context, x, y, x + width, y + height, size, 0.0625f, 0, 0, entity);
        } catch (Throwable ignored) {
        } finally {
            rendering = false;
        }
    }
}
