package ru.method.linkenvisuals.utils.client.discord.callbacks;


import com.sun.jna.Callback;


public interface SpectateGameCallback extends Callback {
    void apply(String spectateSecret);
}
