package ru.method.linkenvisuals.utils.client.discord.callbacks;


import com.sun.jna.Callback;


public interface JoinGameCallback extends Callback {
    void apply(String joinSecret);
}
