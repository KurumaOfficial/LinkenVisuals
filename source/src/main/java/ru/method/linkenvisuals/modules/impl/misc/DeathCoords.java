package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.chat.ChatUtils;

public class DeathCoords extends Module {
    private boolean sentForCurrentDeath;

    public DeathCoords() {
        super("DeathCoords", Category.Player);
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) return;

        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0.0f;
        if (!dead) {
            sentForCurrentDeath = false;
            return;
        }

        if (sentForCurrentDeath) return;
        BlockPos pos = mc.player.getBlockPos();
        ChatUtils.sendMessage("Координаты смерти: " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        sentForCurrentDeath = true;
    }
}
