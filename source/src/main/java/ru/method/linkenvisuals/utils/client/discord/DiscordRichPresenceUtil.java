package ru.method.linkenvisuals.utils.client.discord;


import ru.method.linkenvisuals.LinkenVisuals;


public final class DiscordRichPresenceUtil {
    private DiscordRichPresenceUtil() {
    }

    public static void discordrpc() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) return;
        DiscordManager manager = instance.getDiscordManager();
        if (manager == null) return;
        manager.init();
        manager.forcePresenceReplace();
    }

    public static void shutdownDiscord() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) return;
        DiscordManager manager = instance.getDiscordManager();
        if (manager == null) return;
        manager.stopRPC();
    }
}
