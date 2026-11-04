package ru.method.linkenvisuals.utils.client.discord.utils;

import com.sun.jna.Library;

public interface DiscordRPC extends Library {
    void Discord_UpdateHandlers(DiscordEventHandlers handlers);

    void Discord_UpdatePresence(DiscordRichPresence presence);

    void Discord_Respond(String userId, int reply);

    void Discord_Register(String applicationId, String command);

    void Discord_Shutdown();

    void Discord_UpdateConnection();

    void Discord_RegisterSteamGame(String applicationId, String steamId);

    void Discord_RunCallbacks();

    void Discord_Initialize(String applicationId, DiscordEventHandlers handlers, boolean autoRegister, String optionalSteamId);

    void Discord_ClearPresence();

    enum DiscordReply {
        NO(0),
        IGNORE(2),
        YES(1);

        public final int reply;

        DiscordReply(int reply) {
            this.reply = reply;
        }

        private static DiscordReply[] getReplies() {
            return new DiscordReply[]{NO, YES, IGNORE};
        }
    }
}
