package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;

public class AutoSpec extends Module {
    private final BindSetting specBind = new BindSetting("modules.settings.autospec.bind", new Bind(-1, false));

    public AutoSpec() {
        super("AutoSpec", Category.Player);
    }

    @EventHandler
    private void onKey(EventKey event) {
        if (fullNullCheck() || mc.currentScreen != null || event.getAction() != 1) return;
        if (specBind.getValue().matchesKey(event.getKey())) sendSpec();
    }

    @EventHandler
    private void onMouse(EventMouse event) {
        if (fullNullCheck() || mc.currentScreen != null || event.getAction() != 1) return;
        if (specBind.getValue().matchesMouse(event.getButton())) sendSpec();
    }

    private void sendSpec() {
        if (mc.player.networkHandler == null) return;
        String target = mc.player.getGameProfile().name();
        mc.player.networkHandler.sendChatMessage("!spec " + target);
    }
}
