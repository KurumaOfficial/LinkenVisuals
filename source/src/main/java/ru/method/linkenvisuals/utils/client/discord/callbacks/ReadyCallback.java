package ru.method.linkenvisuals.utils.client.discord.callbacks;


import com.sun.jna.Callback;
import ru.method.linkenvisuals.utils.client.discord.utils.DiscordUser;


public interface ReadyCallback extends Callback {
    void apply(DiscordUser user);
}
