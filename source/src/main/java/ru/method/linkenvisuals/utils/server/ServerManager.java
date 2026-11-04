package ru.method.linkenvisuals.utils.server;


import net.minecraft.client.network.ServerInfo;
import java.util.ArrayList;
import java.util.List;


public class ServerManager {
    private static final List<ServerInfo> PROTECTED_SERVERS = new ArrayList<>();

    public static List<ServerInfo> getProtectedServers() {
        return new ArrayList<>(PROTECTED_SERVERS);
    }

    public static boolean isProtectedServer(ServerInfo server) {
        return server != null && PROTECTED_SERVERS.stream().anyMatch(s ->
                s.address.equals(server.address) && s.name.equals(server.name));
    }

    public static boolean isProtectedAddress(String address) {
        if (address == null) return false;
        for (ServerInfo s : PROTECTED_SERVERS) {
            if (address.equals(s.address)) return true;
        }
        return false;
    }

    public static ServerInfo getProtectedByAddr(String address) {
        if (address == null) return null;
        for (ServerInfo s : PROTECTED_SERVERS) {
            if (address.equals(s.address)) return s;
        }
        return null;
    }

    public static boolean containsProtected(ServerInfo server) {
        return isProtectedServer(server);
    }

    public static int protectedCount() {
        return PROTECTED_SERVERS.size();
    }
}
