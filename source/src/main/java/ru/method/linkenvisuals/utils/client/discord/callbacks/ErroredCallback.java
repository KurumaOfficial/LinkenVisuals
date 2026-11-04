package ru.method.linkenvisuals.utils.client.discord.callbacks;


import com.sun.jna.Callback;


public interface ErroredCallback extends Callback {
    void apply(int code, String message);
}
