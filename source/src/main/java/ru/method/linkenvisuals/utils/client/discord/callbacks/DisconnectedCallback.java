package ru.method.linkenvisuals.utils.client.discord.callbacks;


import com.sun.jna.Callback;


public interface DisconnectedCallback extends Callback {
    void apply(int code, String message);
}
