package ru.method.linkenvisuals.utils.client.discord.utils;


import com.sun.jna.Structure;
import ru.method.linkenvisuals.utils.client.discord.callbacks.DisconnectedCallback;
import ru.method.linkenvisuals.utils.client.discord.callbacks.ErroredCallback;
import ru.method.linkenvisuals.utils.client.discord.callbacks.JoinGameCallback;
import ru.method.linkenvisuals.utils.client.discord.callbacks.JoinRequestCallback;
import ru.method.linkenvisuals.utils.client.discord.callbacks.ReadyCallback;
import ru.method.linkenvisuals.utils.client.discord.callbacks.SpectateGameCallback;

import java.util.Arrays;
import java.util.List;


public class DiscordEventHandlers extends Structure {
    public DisconnectedCallback disconnected;
    public JoinRequestCallback joinRequest;
    public SpectateGameCallback spectateGame;
    public ReadyCallback ready;
    public ErroredCallback errored;
    public JoinGameCallback joinGame;

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("ready", "disconnected", "errored", "joinGame", "spectateGame", "joinRequest");
    }

    public static class Builder {
        private final DiscordEventHandlers handlers = new DiscordEventHandlers();

        public DiscordEventHandlers build() {
            return this.handlers;
        }

        public Builder disconnected(DisconnectedCallback value) {
            this.handlers.disconnected = value;
            return this;
        }

        public Builder errored(ErroredCallback value) {
            this.handlers.errored = value;
            return this;
        }

        public Builder ready(ReadyCallback value) {
            this.handlers.ready = value;
            return this;
        }

        public Builder joinRequest(JoinRequestCallback value) {
            this.handlers.joinRequest = value;
            return this;
        }

        public Builder joinGame(JoinGameCallback value) {
            this.handlers.joinGame = value;
            return this;
        }

        public Builder spectateGame(SpectateGameCallback value) {
            this.handlers.spectateGame = value;
            return this;
        }
    }
}
