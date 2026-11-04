package ru.method.linkenvisuals.modules.impl.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Arm;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandNotificationManager;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;

/**
 * Смена ведущей руки (правая/левая) одним нажатием клавиши, без захода в меню настроек
 * (перенос HandSwap из Kimiko).
 */
public class HandSwap extends Module {

    private final BindSetting swapBind = new BindSetting("settings.handswap.key", new Bind(-1, false));
    private final BooleanSetting notify = new BooleanSetting("settings.handswap.notify", true);

    public HandSwap() {
        super("HandSwap", Category.Player);
    }

    @EventHandler
    public void onKey(EventKey e) {
        if (mc.currentScreen != null) return;
        if (e.getAction() == 1 && swapBind.getValue().matchesKey(e.getKey())) {
            swap();
        }
    }

    @EventHandler
    public void onMouse(EventMouse e) {
        if (mc.currentScreen != null) return;
        if (e.getAction() == 1 && swapBind.getValue().matchesMouse(e.getButton())) {
            e.consume();
            swap();
        }
    }

    private void swap() {
        if (mc.options == null) return;
        Arm arm = mc.options.getMainArm().getValue().getOpposite();
        mc.options.getMainArm().setValue(arm);
        mc.options.sendClientSettings();
        mc.options.write();
        if (notify.getValue()) {
            DynamicIslandNotificationManager.getInstance()
                    .addNotification("HandSwap → " + (arm == Arm.LEFT ? "L" : "R"), true);
        }
    }
}
