package ru.method.linkenvisuals.utils.server;


import ru.method.linkenvisuals.api.events.impl.EventTick;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;


public class ServerHandler {

    private boolean initialized = false;
    private ServerList serverList;

    @EventHandler
    public void onTick(EventTick event) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (serverList == null) {
            serverList = new ServerList(mc);
            serverList.loadFile();
        }

        if (mc.currentScreen instanceof MultiplayerScreen && !initialized) {
            addProtectedServersSimple();
            initialized = true;
        }
    }

    private void addProtectedServersSimple() {
        for (ServerInfo prot : ServerManager.getProtectedServers()) {
            boolean found = false;
            for (int i = 0; i < serverList.size(); i++) {
                if (serverList.get(i).address.equals(prot.address)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                serverList.add(new ServerInfo(prot.name, prot.address, ServerInfo.ServerType.OTHER), false);
            }
        }
        serverList.saveFile();
    }
}
