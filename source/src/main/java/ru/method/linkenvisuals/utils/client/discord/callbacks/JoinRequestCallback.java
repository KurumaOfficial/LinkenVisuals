package ru.method.linkenvisuals.utils.client.discord.callbacks;


import com.sun.jna.Callback;
import ru.method.linkenvisuals.utils.client.discord.utils.DiscordUser;


public interface JoinRequestCallback extends Callback {
    void apply(DiscordUser user);
}
