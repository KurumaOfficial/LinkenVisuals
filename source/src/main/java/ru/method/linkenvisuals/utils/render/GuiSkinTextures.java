package ru.method.linkenvisuals.utils.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.screen.clickgui.ClickGui;

import java.util.UUID;

/**
 * Resolves player skin textures for GUI rendering without triggering session-service profile fetches.
 * Uses a mod-side cache populated while the player is in-world (before ClickGUI opens).
 */
public final class GuiSkinTextures {
    private static SkinTextures cachedSkinTextures;
    private static UUID cachedSkinTexturesUuid;

    private GuiSkinTextures() {
    }

    public static boolean isClickGuiOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.currentScreen instanceof ClickGui;
    }

    public static void cacheLocalSkinTextures(UUID uuid, SkinTextures textures) {
        if (uuid == null || textures == null) {
            return;
        }

        cachedSkinTexturesUuid = uuid;
        cachedSkinTextures = textures;
    }

    public static Identifier resolveTexture(PlayerEntity player) {
        if (player == null) {
            return DefaultSkinHelper.getTexture();
        }

        SkinTextures cached = getCachedSkinTextures(player.getUuid());
        if (cached != null) {
            return cached.body().texturePath();
        }

        return DefaultSkinHelper.getSkinTextures(player.getUuid()).body().texturePath();
    }

    public static SkinTextures resolveLocalSkinTextures(AbstractClientPlayerEntity player) {
        if (player == null) {
            return DefaultSkinHelper.getSkinTextures(UUID.randomUUID());
        }

        SkinTextures cached = getCachedSkinTextures(player.getUuid());
        if (cached != null) {
            return cached;
        }

        return DefaultSkinHelper.getSkinTextures(player.getUuid());
    }

    private static SkinTextures getCachedSkinTextures(UUID uuid) {
        if (uuid == null || cachedSkinTextures == null || !uuid.equals(cachedSkinTexturesUuid)) {
            return null;
        }

        return cachedSkinTextures;
    }
}
