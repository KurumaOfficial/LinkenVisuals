package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;

public class TapeMouse extends Module {
    private final EnumSetting<Button> button = new EnumSetting<>("modules.settings.tape_mouse.button", Button.Left);
    private final NumberSetting delay = new NumberSetting("modules.settings.tape_mouse.delay", 50f, 30f, 500f, 1f);
    private long lastActionMs;

    public TapeMouse() {
        super("TapeMouse", Category.Player);
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck() || mc.currentScreen != null || mc.interactionManager == null) return;
        long now = System.currentTimeMillis();
        if (now - lastActionMs < delay.getValue()) return;
        lastActionMs = now;

        if (button.getValue() == Button.Left) leftClick();
        else rightClick();
    }

    private void leftClick() {
        HitResult target = mc.crosshairTarget;
        if (target instanceof EntityHitResult entityHit && entityHit.getEntity() != mc.player) {
            mc.interactionManager.attackEntity(mc.player, entityHit.getEntity());
            mc.player.swingHand(Hand.MAIN_HAND);
            return;
        }
        if (target instanceof BlockHitResult blockHit) {
            mc.interactionManager.attackBlock(blockHit.getBlockPos(), blockHit.getSide());
            mc.player.swingHand(Hand.MAIN_HAND);
            return;
        }
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void rightClick() {
        HitResult target = mc.crosshairTarget;
        if (target instanceof EntityHitResult entityHit) {
            mc.interactionManager.interactEntity(mc.player, entityHit.getEntity(), Hand.MAIN_HAND);
        } else if (target instanceof BlockHitResult blockHit) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
        } else {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        }
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private enum Button implements Nameable {
        Left("modules.settings.tape_mouse.button.left"),
        Right("modules.settings.tape_mouse.button.right");

        private final String name;

        Button(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
