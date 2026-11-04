package ru.method.linkenvisuals.utils.client.discord;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.utils.CommunityLinks;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.client.discord.utils.DiscordEventHandlers;
import ru.method.linkenvisuals.utils.client.discord.utils.DiscordRPC;
import ru.method.linkenvisuals.utils.client.discord.utils.DiscordRichPresence;
import ru.method.linkenvisuals.utils.client.discord.utils.DiscordRpcProvider;
import ru.method.linkenvisuals.utils.client.discord.utils.RPCButton;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;

@Getter
@Setter
public class DiscordManager implements Wrapper {
    private static final String APPLICATION_ID = "1500886498462011443";
    // Ссылки из централизованного CommunityLinks (устаревшие slug'и приведены к эталонным).
    private static final String DISCORD_LINK = CommunityLinks.DISCORD_URL;
    private static final String TELEGRAM_LINK = CommunityLinks.TELEGRAM_URL;
    private static final String LARGE_IMAGE_KEY = "photo_2026-05-03_12-21-54";
    private static final String SMALL_IMAGE_KEY = "photo_2026-05-03_12-21-54";
    private static final String CLIENT_NAME = "Linken Visuals";
    private static final String CLIENT_VERSION = "2.0";
    private static final String MAIN_MENU_DETAILS = "\u0412 \u0433\u043B\u0430\u0432\u043D\u043E\u043C \u043C\u0435\u043D\u044E";
    private static final String SINGLEPLAYER_DETAILS = "\u041E\u0434\u0438\u043D\u043E\u0447\u043D\u0430\u044F \u0438\u0433\u0440\u0430";
    private static final String PLAYING_ON_PREFIX = "\u0418\u0433\u0440\u0430\u0435\u0442 \u043D\u0430 ";
    private static final String PLAYER_PREFIX = "\u0418\u0433\u0440\u043E\u043A: ";
    private static final String NICK_PREFIX = "\u041D\u0438\u043A: ";
    private static final String MULTIPLAYER_FALLBACK = "multiplayer";
    private static final long PRESENCE_UPDATE_INTERVAL_MS = 3000L;

    private volatile boolean running;
    private volatile boolean ready;
    private volatile String lastStatus = "idle";
    private volatile String lastError = "";
    private volatile String lastDetails = "";
    private volatile String lastState = "";
    private volatile long lastInitializeAt;
    private volatile long lastReadyAt;
    private volatile long lastPresenceUpdateAt;
    private volatile long presenceUpdateCount;
    private long rpcStartTimestamp;
    private DiscordInfo info = new DiscordInfo("Unknown", "", "");
    private Identifier avatarId;
    private Thread daemonThread;
    private DiscordEventHandlers handlers;
    private DiscordRPC rpc;

    public synchronized void init() {
        if (running) {
            forcePresenceReplace();
            return;
        }

        try {
            lastInitializeAt = System.currentTimeMillis();
            lastStatus = "loading native library";
            lastError = "";
            DiscordRPC loaded = DiscordRpcProvider.get();
            if (loaded == null) {
                Throwable err = DiscordRpcProvider.getLoadError();
                if (err != null) {
                    LinkenVisuals.LOGGER.warn("Discord RPC native library unavailable", err);
                    lastError = err.getClass().getSimpleName() + ": " + err.getMessage();
                } else {
                    LinkenVisuals.LOGGER.warn("Discord RPC native library unavailable");
                    lastError = "native library unavailable";
                }
                lastStatus = "native library unavailable";
                running = false;
                return;
            }
            rpc = loaded;
            lastStatus = "initializing discord rpc";
            LinkenVisuals.LOGGER.info("[DiscordRPC] Native library loaded from {}", debugLibraryPath());

            DiscordEventHandlers.Builder handlerBuilder = new DiscordEventHandlers.Builder()
                    .ready(user -> {
                        String username = user.username != null ? user.username : "Unknown";
                        String userId = user.userId != null ? user.userId : "N/A";
                        setInfo(new DiscordInfo(username, buildAvatarUrl(user.userId, user.avatar), userId));
                        ready = true;
                        lastReadyAt = System.currentTimeMillis();
                        lastStatus = "ready: " + username;
                        lastError = "";
                        LinkenVisuals.LOGGER.info("[DiscordRPC] Ready as {} ({})", username, userId);
                        updatePresence();
                    })
                    .errored((code, message) -> {
                        lastStatus = "error " + code;
                        lastError = message;
                        LinkenVisuals.LOGGER.error("Discord RPC error {}: {}", code, message);
                    })
                    .disconnected((code, message) -> {
                        ready = false;
                        lastStatus = "disconnected " + code;
                        lastError = message;
                        LinkenVisuals.LOGGER.warn("Discord RPC disconnected {}: {}", code, message);
                    });
            handlers = handlerBuilder.build();

            rpc.Discord_Initialize(APPLICATION_ID, handlers, true, "");
            running = true;
            ready = false;
            lastStatus = "initialized, waiting for Discord ready";
            rpcStartTimestamp = System.currentTimeMillis() / 1000L;
            forcePresenceReplace();
            daemonThread = new DiscordDaemonThread();
            daemonThread.start();
            updatePresence();
        } catch (Throwable throwable) {
            LinkenVisuals.LOGGER.error("Failed to initialize Discord RPC", throwable);
            lastStatus = "initialize failed";
            lastError = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            running = false;
            ready = false;
            rpc = null;
            DiscordRpcProvider.resetLoadError();
        }
    }

    public synchronized void stopRPC() {
        if (!running) {
            return;
        }

        running = false;
        ready = false;
        lastStatus = "stopped";
        DiscordRPC active = rpc;
        rpc = null;
        if (active != null) {
            try {
                active.Discord_ClearPresence();
            } catch (Throwable ignored) {
            }
            try {
                active.Discord_Shutdown();
            } catch (Throwable ignored) {
            }
        }

        if (daemonThread != null) {
            daemonThread.interrupt();
            daemonThread = null;
        }

        rpcStartTimestamp = 0L;
        if (avatarId != null && mc != null) {
            mc.getTextureManager().destroyTexture(avatarId);
            avatarId = null;
        }
        info = new DiscordInfo("Unknown", "", "");
    }

    public void load() {
        if (avatarId == null && info.avatarUrl() != null && !info.avatarUrl().isEmpty()) {
            try {
                NativeImage image = downloadImage(info.avatarUrl());
                if (image != null) {
                    registerDynamicTexture("discord-avatar-" + info.userId(), image);
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void updatePresence() {
        try {
            DiscordRPC active = rpc;
            if (!running || active == null) {
                return;
            }

            DiscordRichPresence.Builder builder = new DiscordRichPresence.Builder()
                    .setStartTimestamp(rpcStartTimestamp > 0L ? rpcStartTimestamp : System.currentTimeMillis() / 1000L)
                    .setDetails(resolveDetails())
                    .setState(resolveState())
                    .setLargeImage(LARGE_IMAGE_KEY, CLIENT_NAME + " " + CLIENT_VERSION)
                    .setSmallImage(SMALL_IMAGE_KEY, resolveSmallImageText());

            builder.setButtons(
                    RPCButton.create("\u0422\u0435\u043B\u0435\u0433\u0440\u0430\u043C \u043A\u0430\u043D\u0430\u043B", TELEGRAM_LINK),
                    RPCButton.create("\u0414\u0438\u0441\u043A\u043E\u0440\u0434 \u0441\u0435\u0440\u0432\u0435\u0440", DISCORD_LINK)
            );
            DiscordRichPresence presence = builder.build();
            active.Discord_UpdatePresence(presence);
            lastDetails = presence.details;
            lastState = presence.state;
            lastPresenceUpdateAt = System.currentTimeMillis();
            presenceUpdateCount++;
            if (!ready) {
                lastStatus = "presence sent, waiting for Discord ready";
            }
        } catch (Throwable throwable) {
            LinkenVisuals.LOGGER.error("Failed to update Discord presence", throwable);
            lastStatus = "presence update failed";
            lastError = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        }
    }

    public synchronized void forcePresenceReplace() {
        DiscordRPC active = rpc;
        if (!running || active == null) {
            return;
        }

        try {
            active.Discord_ClearPresence();
            active.Discord_RunCallbacks();
            updatePresence();
        } catch (Throwable throwable) {
            LinkenVisuals.LOGGER.error("Failed to replace Discord presence", throwable);
        }
    }

    public void ensurePresence() {
        if (!running || rpc == null) {
            init();
            return;
        }
        forcePresenceReplace();
    }

    public String debugStatus() {
        return "DiscordRPC"
                + "\nrunning=" + running
                + "\nready=" + ready
                + "\nstatus=" + lastStatus
                + "\nerror=" + (lastError == null || lastError.isBlank() ? "-" : lastError)
                + "\nupdates=" + presenceUpdateCount
                + "\nlastUpdateMsAgo=" + ageMs(lastPresenceUpdateAt)
                + "\nlastReadyMsAgo=" + ageMs(lastReadyAt)
                + "\ndetails=" + (lastDetails == null || lastDetails.isBlank() ? "-" : lastDetails)
                + "\nstate=" + (lastState == null || lastState.isBlank() ? "-" : lastState)
                + "\ndll=" + debugLibraryPath();
    }

    private String debugLibraryPath() {
        Path path = DiscordRpcProvider.getExtractedLibraryPath();
        return path == null ? "-" : path.toAbsolutePath().toString();
    }

    private long ageMs(long timestamp) {
        return timestamp <= 0L ? -1L : Math.max(0L, System.currentTimeMillis() - timestamp);
    }

    private String resolveDetails() {
        if (mc == null || mc.player == null || mc.world == null) {
            return MAIN_MENU_DETAILS;
        }

        if (mc.isInSingleplayer()) {
            return SINGLEPLAYER_DETAILS;
        }

        return PLAYING_ON_PREFIX + resolveServerDomain();
    }

    private String resolveState() {
        String playerName = resolvePlayerName();
        if (mc == null || mc.player == null || mc.world == null) {
            return CLIENT_NAME + " " + CLIENT_VERSION;
        }

        if (mc.isInSingleplayer()) {
            return PLAYER_PREFIX + playerName;
        }

        return NICK_PREFIX + playerName;
    }

    private String resolveSmallImageText() {
        if (mc == null || mc.player == null || mc.world == null) {
            return "Minecraft 1.21.4";
        }

        if (mc.isInSingleplayer()) {
            return "Singleplayer";
        }

        return resolveServerDomain();
    }

    private String resolvePlayerName() {
        if (mc != null && mc.player != null && mc.player.getGameProfile() != null) {
            return mc.player.getGameProfile().name();
        }
        if (mc != null && mc.getSession() != null && mc.getSession().getUsername() != null) {
            return mc.getSession().getUsername();
        }
        return "Unknown";
    }

    private String resolveServerAddress() {
        ServerInfo serverInfo = mc == null ? null : mc.getCurrentServerEntry();
        if (serverInfo == null || serverInfo.address == null || serverInfo.address.isBlank()) {
            return MULTIPLAYER_FALLBACK;
        }
        return serverInfo.address.trim();
    }

    private String resolveServerDomain() {
        String address = resolveServerAddress();
        if (MULTIPLAYER_FALLBACK.equals(address)) {
            return address;
        }

        if (address.startsWith("[")) {
            int closingBracket = address.indexOf(']');
            return closingBracket > 0 ? address.substring(0, closingBracket + 1) : address;
        }

        int separator = address.indexOf(':');
        return separator > 0 ? address.substring(0, separator) : address;
    }

    private String buildAvatarUrl(String userId, String avatarHash) {
        if (userId == null || avatarHash == null || userId.isEmpty() || avatarHash.isEmpty()) {
            return "";
        }
        return "https://cdn.discordapp.com/avatars/" + userId + "/" + avatarHash + ".png";
    }

    private NativeImage downloadImage(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "linkenvisuals-DiscordRPC");
        try (InputStream stream = connection.getInputStream()) {
            return NativeImage.read(stream);
        } finally {
            connection.disconnect();
        }
    }

    private void registerDynamicTexture(String prefix, NativeImage image) {
        if (mc == null) {
            image.close();
            return;
        }

        mc.execute(() -> {
            try {
                TextureManager manager = mc.getTextureManager();
                if (manager == null) {
                    image.close();
                    return;
                }
                Identifier id = LinkenVisuals.id(prefix);
                if (this.avatarId != null) {
                    manager.destroyTexture(this.avatarId);
                }
                manager.registerTexture(id, new NativeImageBackedTexture(() -> "discord_avatar", image));
                this.avatarId = id;
            } catch (Throwable t) {
                image.close();
            }
        });
    }

    private class DiscordDaemonThread extends Thread {
        private DiscordDaemonThread() {
            setName("Discord-RPC");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (running) {
                try {
                    DiscordRPC active = rpc;
                    if (active == null) {
                        break;
                    }

                    active.Discord_RunCallbacks();
                    load();
                    updatePresence();
                } catch (Throwable t) {
                    LinkenVisuals.LOGGER.warn("[DiscordRPC] Presence update tick failed: {}", t.getMessage());
                }
                try {
                    Thread.sleep(PRESENCE_UPDATE_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
    }

    public record DiscordInfo(String userName, String avatarUrl, String userId) {
    }
}
