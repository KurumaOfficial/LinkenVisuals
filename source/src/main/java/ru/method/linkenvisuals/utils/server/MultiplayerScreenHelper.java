package ru.method.linkenvisuals.utils.server;

import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import ru.method.linkenvisuals.api.mixins.accessors.IServerEntry;

public final class MultiplayerScreenHelper {

    private MultiplayerScreenHelper() {
    }

    public static ServerInfo getServer(MultiplayerServerListWidget.Entry entry) {
        if (!(entry instanceof MultiplayerServerListWidget.ServerEntry serverEntry)) {
            return null;
        }
        return ((IServerEntry) serverEntry).linkenvisuals$getServer();
    }
}
