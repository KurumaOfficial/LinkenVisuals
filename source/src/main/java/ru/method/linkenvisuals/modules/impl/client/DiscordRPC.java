package ru.method.linkenvisuals.modules.impl.client;


import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.client.discord.DiscordRichPresenceUtil;


public class DiscordRPC extends Module {
    public DiscordRPC() {
        super("DiscordRPC", Category.Other);
    }


    @Override
    public void onEnable() {
        super.onEnable();
        DiscordRichPresenceUtil.discordrpc();
        if (dev.linkenvisuals.clickgui.ModuleRegistry.discordRpc() != null) {
            dev.linkenvisuals.clickgui.ModuleRegistry.discordRpc().toggleSilently(true);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        DiscordRichPresenceUtil.shutdownDiscord();
        if (dev.linkenvisuals.clickgui.ModuleRegistry.discordRpc() != null) {
            dev.linkenvisuals.clickgui.ModuleRegistry.discordRpc().toggleSilently(false);
        }
    }
}
