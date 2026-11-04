package ru.method.linkenvisuals.modules.impl.misc;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.managers.misc.UserSyncManager;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;


public class AutoAccept extends Module {

    public AutoAccept() {
        super("AutoAccept", Category.Player);
    }

    @EventHandler
    public void onPacketReceive(EventPacket.Receive e) {
        if (fullNullCheck()) return;

        if (e.getPacket() instanceof GameMessageS2CPacket packet
                && (packet.content().getString().toLowerCase().contains("хочет")
                || packet.content().getString().toLowerCase().contains("телепортироваться")
                || packet.content().getString().toLowerCase().contains("к вам")
                || packet.content().getString().toLowerCase().contains("wants")
                || packet.content().getString().toLowerCase().contains("teleport")
                || packet.content().getString().toLowerCase().contains("to you"))) {
            String content = packet.content().getString();
            if (shouldAcceptFrom(content)) {
                mc.getNetworkHandler().sendChatCommand("tpaccept");
            }
        }
    }

    private boolean shouldAcceptFrom(String content) {
        if (content == null || content.isBlank()) return false;

        for (String name : LinkenVisuals.getInstance().getFriendManager().getFriends()) {
            if (name == null || name.isBlank()) continue;
            if (content.contains(name)) return true;
        }

        UserSyncManager syncManager = LinkenVisuals.getInstance().getUserSyncManager();
        return syncManager != null && syncManager.containsPartyMemberName(content);
    }
}
