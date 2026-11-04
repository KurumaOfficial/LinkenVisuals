package ru.method.linkenvisuals.utils.server;


import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import java.util.ArrayList;
import java.util.List;


public class ServerOrderUtil {

    public static void normalizeProtectedOnTop(ServerList list) {
        if (list == null) return;
        int protCount = ServerManager.protectedCount();
        if (protCount == 0) return;

        boolean needsReorder = false;
        for (int i = 0; i < Math.min(protCount, list.size()); i++) {
            ServerInfo server = list.get(i);
            ServerInfo expected = ServerManager.getProtectedServers().get(i);
            if (!server.address.equals(expected.address)) {
                needsReorder = true;
                break;
            }
        }

        if (!needsReorder) return;

        List<ServerInfo> current = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            current.add(list.get(i));
        }

        List<ServerInfo> result = new ArrayList<>();

        for (int i = 0; i < protCount; i++) {
            ServerInfo prot = ServerManager.getProtectedServers().get(i);
            result.add(new ServerInfo(prot.name, prot.address, ServerInfo.ServerType.OTHER));
        }

        for (ServerInfo server : current) {
            boolean isProtected = false;
            for (ServerInfo prot : ServerManager.getProtectedServers()) {
                if (server.address.equals(prot.address)) {
                    isProtected = true;
                    break;
                }
            }
            if (!isProtected) {
                result.add(server);
            }
        }

        while (list.size() > 0) {
            list.remove(list.get(0));
        }
        for (ServerInfo server : result) {
            list.add(server, false);
        }
    }

    public static boolean isProtectedIndex(ServerList list, int index) {
        return index >= 0 && index < Math.min(ServerManager.protectedCount(), list.size());
    }
}
