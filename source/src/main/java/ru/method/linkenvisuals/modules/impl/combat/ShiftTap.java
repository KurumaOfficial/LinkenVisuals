package ru.method.linkenvisuals.modules.impl.combat;


import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import ru.method.linkenvisuals.api.events.impl.EventAttackEntity;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;


public class ShiftTap extends Module {

    private final NumberSetting holdTicks = new NumberSetting("settings.shifttap.hold_ticks", 3.0f, 1.0f, 8.0f, 1.0f);

    private int sneakTicksLeft;
    private boolean forcedSneak;
    private long lastActivationTime; 
    private static final long COOLDOWN_MS = 100; 

    public ShiftTap() {
        super("ShiftTap", Category.Combat);
        getSettings().add(holdTicks);
    }

    @EventHandler
    private void onAttack(EventAttackEntity event) {
        if (fullNullCheck()) return;

        PlayerEntity player = event.getPlayer();
        Entity target = event.getTarget();
        if (player == null || player != mc.player) return;
        if (!(target instanceof LivingEntity)) return;

        if (forcedSneak || sneakTicksLeft > 0) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActivationTime < COOLDOWN_MS) return;

        if (canPerformCrit(player)) {
            sneakTicksLeft = Math.max(1, Math.round(holdTicks.getValue()));
            lastActivationTime = currentTime;
            mc.options.sneakKey.setPressed(true);
            forcedSneak = true;
        }
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) {
            resetSneakState();
            return;
        }

        if (sneakTicksLeft > 0) {
            mc.options.sneakKey.setPressed(true);
            forcedSneak = true;
            sneakTicksLeft--;
        } else if (forcedSneak) {
            boolean physicallyPressed = mc.getWindow() != null
                    && org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            mc.options.sneakKey.setPressed(physicallyPressed);
            forcedSneak = false;
        }
    }

    @Override
    public void onDisable() {
        resetSneakState();
        super.onDisable();
    }

    private boolean canPerformCrit(PlayerEntity player) {
        if (player.isSubmergedInWater() || player.isTouchingWater()) return false;
        if (player.isClimbing()) return false;
        if (player.hasVehicle()) return false;
        return true;
    }

    private void resetSneakState() {
        sneakTicksLeft = 0;
        if (forcedSneak && mc != null && mc.options != null) {
            mc.options.sneakKey.setPressed(false);
        }
        forcedSneak = false;
    }
}

