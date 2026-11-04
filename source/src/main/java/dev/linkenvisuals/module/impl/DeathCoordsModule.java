package dev.linkenvisuals.module.impl;

import dev.linkenvisuals.clickgui.Category;
import dev.linkenvisuals.clickgui.setting.Setting;
import dev.linkenvisuals.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * DeathCoords (Linken donor port): Prints player coordinates upon death to chat.
 */
public final class DeathCoordsModule extends Module {
    private boolean sentForCurrentDeath;

    public DeathCoordsModule() {
        super("deathcoords", "Death Coords", "prints coordinates in chat on death",
            Category.PLAYER,
            List.of(),
            List.<Setting>of());
    }

    public void tick(MinecraftClient client) {
        if (!enabled() || client == null || client.player == null) {
            return;
        }

        boolean dead = client.player.isDead() || client.player.getHealth() <= 0.0f;
        if (!dead) {
            sentForCurrentDeath = false;
            return;
        }

        if (sentForCurrentDeath) {
            return;
        }

        BlockPos pos = client.player.getBlockPos();
        String coordsText = String.format("Координаты смерти: %d %d %d", pos.getX(), pos.getY(), pos.getZ());
        if (client.inGameHud != null && client.inGameHud.getChatHud() != null) {
            client.inGameHud.getChatHud().addMessage(
                Text.literal("[Visuals] ").formatted(Formatting.DARK_PURPLE)
                    .append(Text.literal(coordsText).formatted(Formatting.GRAY))
            );
        }
        sentForCurrentDeath = true;
    }
}
