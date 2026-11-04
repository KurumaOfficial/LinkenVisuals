package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.util.Hand;
import ru.method.linkenvisuals.api.events.impl.EventAttackEntity;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;

public class PlayerUtils extends Module {
    private final BooleanSetting antiAfk = new BooleanSetting("modules.settings.playerutils.anti_afk", true);
    private final BooleanSetting autoRespawn = new BooleanSetting("modules.settings.playerutils.auto_respawn", true);
    private final EnumSetting<AntiAfkAction> action = new EnumSetting<>("modules.settings.playerutils.action", AntiAfkAction.Jump, antiAfk::getValue);
    private final NumberSetting delay = new NumberSetting("modules.settings.playerutils.delay", 30f, 5f, 300f, 1f, antiAfk::getValue);

    private long lastMovementMs = System.currentTimeMillis();
    private double lastX;
    private double lastY;
    private double lastZ;

    public PlayerUtils() {
        super("PlayerUtils", Category.Player);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetAfkTimer();
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) return;

        if (autoRespawn.getValue() && mc.currentScreen instanceof DeathScreen) {
            mc.player.requestRespawn();
            mc.setScreen(null);
        }

        if (!antiAfk.getValue()) return;
        if (hasMoved()) {
            resetAfkTimer();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastMovementMs < (long) (delay.getValue() * 1000L)) return;

        if (action.getValue() == AntiAfkAction.Jump) {
            mc.player.jump();
        } else {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        resetAfkTimer();
    }

    @EventHandler
    private void onAttack(EventAttackEntity event) {
        resetAfkTimer();
    }

    private boolean hasMoved() {
        return mc.player.getX() != lastX || mc.player.getY() != lastY || mc.player.getZ() != lastZ
                || mc.player.input.getMovementInput().y != 0f || mc.player.input.getMovementInput().x != 0f;
    }

    private void resetAfkTimer() {
        lastMovementMs = System.currentTimeMillis();
        if (mc.player != null) {
            lastX = mc.player.getX();
            lastY = mc.player.getY();
            lastZ = mc.player.getZ();
        }
    }

    private enum AntiAfkAction implements Nameable {
        Jump("modules.settings.playerutils.action.jump"),
        SwingHand("modules.settings.playerutils.action.swing_hand");

        private final String name;

        AntiAfkAction(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
