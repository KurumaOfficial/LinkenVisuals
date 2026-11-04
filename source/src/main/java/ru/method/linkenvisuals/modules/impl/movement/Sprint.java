package ru.method.linkenvisuals.modules.impl.movement;


import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import lombok.AllArgsConstructor;
import meteordevelopment.orbit.EventHandler;
import ru.method.linkenvisuals.api.events.impl.EventAttackEntity;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.utils.network.NetworkUtils;


public class Sprint extends Module {

    // Always — жать спринт всегда (прежнее поведение). OnForward — перенос AutoSprint из Kimiko:
    // держим спринт только пока зажата клавиша «вперёд».
    private final EnumSetting<Mode> mode = new EnumSetting<>("settings.sprint.mode", Mode.Always);
    private final BooleanSetting sprintReset = new BooleanSetting("settings.sprint.reset", true);

    public Sprint() {
        super("Sprint", Category.Movement);
    }

    @Override
    public boolean isUnsafe() {
        return true;
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (fullNullCheck()) return;

        if (mc.player.isRiding()) {
            return;
        }
        if (mode.getValue() == Mode.OnForward) {
            mc.options.sprintKey.setPressed(mc.options.forwardKey.isPressed());
        } else {
            mc.options.sprintKey.setPressed(true);
        }
    }

    @EventHandler
    public void onAttack(EventAttackEntity e) {
        if (fullNullCheck() || !sprintReset.getValue()) return;
        if (e.getPlayer() != mc.player || !(e.getTarget() instanceof LivingEntity)) return;
        if (mc.getNetworkHandler() == null || mc.player.isRiding() || !mc.player.isSprinting()) return;

        NetworkUtils.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        NetworkUtils.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
    }

    @Override
    public void onDisable() {
        if (fullNullCheck()) return;
        mc.options.sprintKey.setPressed(false);
        super.onDisable();
    }

    @AllArgsConstructor
    public enum Mode implements Nameable {
        Always("settings.sprint.mode.always"),
        OnForward("settings.sprint.mode.onforward");

        private final String name;

        @Override
        public String getName() {
            return name;
        }
    }
}
