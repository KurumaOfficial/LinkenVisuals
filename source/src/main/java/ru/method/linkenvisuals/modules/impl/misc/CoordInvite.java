package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.modules.settings.impl.StringSetting;

public class CoordInvite extends Module {
    private final BindSetting sendBind = new BindSetting("modules.settings.coord_invite.bind", new Bind(-1, false));
    private final StringSetting playerName = new StringSetting("modules.settings.coord_invite.player", "", "nickname");

    public CoordInvite() {
        super("CoordInvite", Category.Player);
    }

    @EventHandler
    private void onKey(EventKey event) {
        if (fullNullCheck() || mc.currentScreen != null || event.getAction() != 1) return;
        if (sendBind.getValue().matchesKey(event.getKey())) sendCoords();
    }

    @EventHandler
    private void onMouse(EventMouse event) {
        if (fullNullCheck() || mc.currentScreen != null || event.getAction() != 1) return;
        if (sendBind.getValue().matchesMouse(event.getButton())) sendCoords();
    }

    private void sendCoords() {
        if (mc.player.networkHandler == null) return;
        BlockPos pos = mc.player.getBlockPos();
        String coords = "Координаты: " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        String target = playerName.getValue().trim();
        if (target.isEmpty()) {
            mc.player.networkHandler.sendChatMessage(coords);
        } else {
            mc.player.networkHandler.sendChatCommand("msg " + target + " " + coords);
        }
    }
}
