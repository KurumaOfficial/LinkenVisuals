package dev.linkenvisuals.media;

import dev.redstones.mediaplayerinfo.IMediaSession;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.media.SystemMediaController.MediaSnapshot;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class DynamicIslandMedia {

    public static final Identifier ARTWORK_ID = Identifier.of(LinkenVisuals.MOD_ID, "media_artwork_strip");

    private static final boolean MEDIA_POLLING_ENABLED = !Boolean.getBoolean("linkenvisuals.mediaPollingDisabled");
    private static final boolean MEDIA_DEBUG = Boolean.getBoolean("linkenvisuals.mediaDebug");
    private static final long MEDIA_POLL_INTERVAL_MS = Long.getLong("linkenvisuals.mediaPollIntervalMs", 450L);
    private static final long FAST_POLL_INTERVAL_MS = Long.getLong("linkenvisuals.mediaFastPollIntervalMs", 120L);
    private static final long FAST_POLL_WINDOW_MS = Long.getLong("linkenvisuals.mediaFastPollWindowMs", 4000L);
    private static final long OPTIMISTIC_PLAYING_GUARD_MS = Long.getLong("linkenvisuals.mediaOptimisticGuardMs", 900L);
    private static final long MEDIA_WATCHDOG_TIMEOUT_MS = Long.getLong("linkenvisuals.mediaWatchdogTimeoutMs", 5000L);
    private static final int MAX_STUCK_RECOVERIES = Integer.getInteger("linkenvisuals.mediaMaxStuckRecoveries", 3);
    private static final MediaSnapshot NO_MEDIA = new MediaSnapshot("", "", "", false, 0L, 0L, null, null);

    private final Map<Integer, NativeImageBackedTexture> artworkCache = new ConcurrentHashMap<>();
    private final List<Integer> artworkCacheOrder = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean pollInFlight = new AtomicBoolean(false);
    private final AtomicReference<MediaSnapshot> pendingSnapshot = new AtomicReference<>();
    private volatile long pollStartedAtMs;
    private volatile int stuckRecoveries;

    private ExecutorService mediaExecutor;
    private volatile boolean mediaNativeDisabled;
    private volatile MediaSnapshot currentMediaInfo;
    private volatile IMediaSession activeSession;
    private volatile String displayTitle = "";
    private volatile String displayArtist = "";
    private NativeImageBackedTexture artworkTexture;
    private int artworkHash;
    private long lastMediaPoll;
    private volatile long fastPollUntilMs;
    private volatile long lastOptimisticToggleMs;
    private volatile long lastMediaSeenAtMs;
    private long lastKnownDurationMs;
    private long lastKnownPositionMs;
    private long lastPositionSyncMs;

    public void ensureExecutor() {
        if (!MEDIA_POLLING_ENABLED) {
            mediaNativeDisabled = true;
            return;
        }
        if (mediaExecutor == null || mediaExecutor.isShutdown()) {
            mediaExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(() -> {
                    boolean winRtInitialized = false;
                    try {
                        winRtInitialized = WinRtInterop.ensureInitialized();
                        r.run();
                    } finally {
                        if (winRtInitialized) {
                            WinRtInterop.uninitializeCurrentThread();
                        }
                    }
                }, "DynamicIsland-Media");
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    private void recoverStuckMediaThread() {
        ExecutorService stuck = mediaExecutor;
        mediaExecutor = null;
        pollInFlight.set(false);
        pendingSnapshot.set(null);
        pollStartedAtMs = 0L;
        if (stuck != null) {
            stuck.shutdownNow();
        }
        stuckRecoveries++;
        LinkenVisuals.LOGGER.warn("[MediaPlayerInfo] Poll stuck > {} ms — restarted media thread (recovery #{})",
                MEDIA_WATCHDOG_TIMEOUT_MS, stuckRecoveries);
        if (stuckRecoveries >= MAX_STUCK_RECOVERIES) {
            mediaNativeDisabled = true;
            LinkenVisuals.LOGGER.warn("[MediaPlayerInfo] Disabling media polling after {} stuck recoveries", stuckRecoveries);
        }
    }

    public void shutdown() {
        pendingSnapshot.set(null);
        pollInFlight.set(false);
        pollStartedAtMs = 0L;
        stuckRecoveries = 0;

        if (mediaExecutor != null) {
            mediaExecutor.shutdownNow();
            mediaExecutor = null;
        }

        destroyArtworkTexture();
        clearArtworkCache();
        currentMediaInfo = null;
        activeSession = null;
        lastMediaSeenAtMs = 0L;
        lastKnownDurationMs = 0L;
        lastKnownPositionMs = 0L;
        lastPositionSyncMs = 0L;
    }

    public MediaUpdate update() {
        if (MEDIA_POLLING_ENABLED && (mediaExecutor == null || mediaExecutor.isShutdown())) {
            ensureExecutor();
        }
        if (mediaNativeDisabled) {
            return MediaUpdate.NONE;
        }

        if (pollInFlight.get() && pollStartedAtMs > 0L
                && System.currentTimeMillis() - pollStartedAtMs > MEDIA_WATCHDOG_TIMEOUT_MS) {
            recoverStuckMediaThread();
            return MediaUpdate.NONE;
        }

        ExecutorService executor = mediaExecutor;
        if (executor == null || executor.isShutdown()) {
            return MediaUpdate.NONE;
        }

        boolean mediaChanged = false;
        boolean cleared = false;
        long now = System.currentTimeMillis();
        MediaSnapshot snapshot = pendingSnapshot.getAndSet(null);
        if (snapshot != null) {
            if (snapshot == NO_MEDIA) {
                if (currentMediaInfo != null && now - lastMediaSeenAtMs >= 6000L) {
                    debugMedia("clearing media state after stale NO_MEDIA");
                    clearMediaState();
                    cleared = true;
                }
            } else {
                lastMediaSeenAtMs = now;
                debugMedia("applying media snapshot owner='" + snapshot.owner() + "' title='" + snapshot.title() + "'");
                mediaChanged = applySnapshot(snapshot);
            }
        }

        long pollInterval = now < fastPollUntilMs ? FAST_POLL_INTERVAL_MS : MEDIA_POLL_INTERVAL_MS;
        if (now - lastMediaPoll < pollInterval) {
            return new MediaUpdate(mediaChanged, cleared);
        }
        if (pollInFlight.get()) {
            return new MediaUpdate(mediaChanged, cleared);
        }
        lastMediaPoll = now;

        if (!pollInFlight.compareAndSet(false, true)) {
            return new MediaUpdate(mediaChanged, cleared);
        }
        pollStartedAtMs = now;

        try {
            executor.submit(() -> {
                try {
                    MediaSnapshot result = SystemMediaController.pollSnapshot(
                            activeSession,
                            currentMediaInfo == null ? null : currentMediaInfo.owner(),
                            currentMediaInfo,
                            lastMediaSeenAtMs
                    );
                    pendingSnapshot.set(result == null ? NO_MEDIA : result);
                } catch (Throwable throwable) {
                    LinkenVisuals.LOGGER.debug("[MediaPlayerInfo] Poll failed", throwable);
                    pendingSnapshot.set(NO_MEDIA);
                } finally {
                    pollInFlight.set(false);
                    pollStartedAtMs = 0L;
                    stuckRecoveries = 0;
                }
            });
        } catch (RejectedExecutionException ignored) {
            pollInFlight.set(false);
            pollStartedAtMs = 0L;
        }

        return new MediaUpdate(mediaChanged, cleared);
    }

    public MediaUpdate applyPending() {
        if (mediaNativeDisabled) {
            return MediaUpdate.NONE;
        }

        boolean mediaChanged = false;
        boolean cleared = false;
        long now = System.currentTimeMillis();
        MediaSnapshot snapshot = pendingSnapshot.getAndSet(null);
        if (snapshot == null) {
            return MediaUpdate.NONE;
        }
        if (snapshot == NO_MEDIA) {
            if (currentMediaInfo != null && now - lastMediaSeenAtMs >= 6000L) {
                debugMedia("clearing media state after stale NO_MEDIA");
                clearMediaState();
                cleared = true;
            }
        } else {
            lastMediaSeenAtMs = now;
            debugMedia("applying media snapshot owner='" + snapshot.owner() + "' title='" + snapshot.title() + "'");
            mediaChanged = applySnapshot(snapshot);
        }
        return new MediaUpdate(mediaChanged, cleared);
    }

    public boolean hasMedia() {
        if (currentMediaInfo == null) {
            return false;
        }
        return currentMediaInfo.playing()
                || !safeText(currentMediaInfo.title()).isEmpty()
                || !safeText(currentMediaInfo.artist()).isEmpty();
    }

    public boolean isPlaying() {
        return currentMediaInfo != null && currentMediaInfo.playing();
    }

    public String getTitle() {
        return displayTitle;
    }

    public String getArtist() {
        return displayArtist;
    }

    public String getRawTitle() {
        return currentMediaInfo == null ? "" : safeText(currentMediaInfo.title());
    }

    public String getRawArtist() {
        return currentMediaInfo == null ? "" : safeText(currentMediaInfo.artist());
    }

    public long getRawDurationMs() {
        return currentMediaInfo == null ? 0L : currentMediaInfo.duration();
    }

    public long getDurationMs() {
        if (currentMediaInfo != null && currentMediaInfo.duration() > 0) {
            lastKnownDurationMs = currentMediaInfo.duration();
        }
        return lastKnownDurationMs;
    }

    public long getPositionMs() {
        if (currentMediaInfo != null && currentMediaInfo.playing() && lastPositionSyncMs > 0L) {
            long livePosition = lastKnownPositionMs + Math.max(0L, System.currentTimeMillis() - lastPositionSyncMs);
            return lastKnownDurationMs > 0L ? Math.min(livePosition, lastKnownDurationMs) : livePosition;
        }
        return lastKnownPositionMs;
    }

    public IMediaSession getActiveSession() {
        return activeSession;
    }

    public boolean previous() {
        return submitMediaAction(WindowsMediaKeys.MediaAction.PREVIOUS, isPlaying(), getRawTitle());
    }

    public boolean playPause() {
        boolean wasPlaying = isPlaying();
        if (wasPlaying) {
            lastKnownPositionMs = getPositionMs();
            lastPositionSyncMs = 0L;
        } else {
            lastPositionSyncMs = System.currentTimeMillis();
        }
        applyOptimisticPlayPauseToggle();
        return submitMediaAction(WindowsMediaKeys.MediaAction.PLAY_PAUSE, wasPlaying, null);
    }

    public boolean next() {
        return submitMediaAction(WindowsMediaKeys.MediaAction.NEXT, isPlaying(), getRawTitle());
    }

    public NativeImageBackedTexture getArtworkTexture() {
        return artworkTexture;
    }

    private boolean submitMediaAction(WindowsMediaKeys.MediaAction action, boolean playingBefore, String titleBefore) {
        if (!MEDIA_POLLING_ENABLED) {
            return false;
        }
        ensureExecutor();
        ExecutorService executor = mediaExecutor;
        if (executor == null || executor.isShutdown()) {
            return false;
        }

        fastPollUntilMs = System.currentTimeMillis() + FAST_POLL_WINDOW_MS;
        lastMediaPoll = 0L;

        try {
            executor.submit(() -> pollAfterMediaAction(action, playingBefore, titleBefore));
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }

    private void pollAfterMediaAction(WindowsMediaKeys.MediaAction action, boolean playingBefore, String titleBefore) {
        try {
            IMediaSession session = SystemMediaController.resolveActionSession(activeSession);
            SystemMediaController.performAction(session, action);
        } catch (Throwable throwable) {
            LinkenVisuals.LOGGER.debug("[MediaPlayerInfo] Media action {} failed", action, throwable);
        }

        long[] delays = action == WindowsMediaKeys.MediaAction.PLAY_PAUSE
                ? new long[] {0L, 80L, 180L, 350L, 650L, 1100L}
                : new long[] {0L, 120L, 280L, 500L, 900L, 1400L, 2200L};
        long previousDelay = 0L;
        for (long delay : delays) {
            long sleepMs = Math.max(0L, delay - previousDelay);
            previousDelay = delay;
            if (sleepMs > 0L) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            try {
                MediaSnapshot result = SystemMediaController.pollSnapshot(
                        activeSession,
                        currentMediaInfo == null ? null : currentMediaInfo.owner(),
                        currentMediaInfo,
                        lastMediaSeenAtMs
                );
                if (result == null) {
                    continue;
                }
                pendingSnapshot.set(result);
                if (action == WindowsMediaKeys.MediaAction.PLAY_PAUSE) {
                    if (result.playing() != playingBefore) {
                        return;
                    }
                } else if (titleBefore != null && !titleBefore.isBlank()
                        && !Objects.equals(safeText(result.title()), titleBefore)) {
                    return;
                }
            } catch (Throwable throwable) {
                LinkenVisuals.LOGGER.debug("[MediaPlayerInfo] Post-action poll failed", throwable);
            }
        }
    }

    private void applyOptimisticPlayPauseToggle() {
        MediaSnapshot current = currentMediaInfo;
        if (current == null) {
            return;
        }
        byte[] artwork = current.artwork();
        currentMediaInfo = new MediaSnapshot(
                current.owner(),
                current.title(),
                current.artist(),
                !current.playing(),
                current.duration(),
                lastKnownPositionMs,
                artwork == null ? null : Arrays.copyOf(artwork, artwork.length),
                current.session()
        );
        lastOptimisticToggleMs = System.currentTimeMillis();
    }

    private boolean applySnapshot(MediaSnapshot snapshot) {
        if (snapshot == null) {
            clearMediaState();
            return false;
        }

        snapshot = reconcileOptimisticPlaying(snapshot);
        boolean mediaChanged = hasCurrentMediaChanged(snapshot);
        if (mediaChanged) {
            lastKnownDurationMs = 0L;
            lastKnownPositionMs = 0L;
        }

        currentMediaInfo = snapshot;
        activeSession = snapshot.session();
        displayTitle = resolveDisplayTitle(snapshot.title());
        displayArtist = resolveDisplayArtist(snapshot.artist());

        long duration = Math.max(0L, snapshot.duration());
        long position = Math.max(0L, snapshot.position());
        if (mediaChanged) {
            lastKnownDurationMs = duration;
            if (duration > 0 && position > duration) {
                position = duration;
            }
            lastKnownPositionMs = position;
            lastPositionSyncMs = System.currentTimeMillis();
        } else {
            if (duration > 0) {
                lastKnownDurationMs = duration;
            } else if (snapshot.duration() == 0L) {
                lastKnownDurationMs = 0L;
            }
            if (lastKnownDurationMs > 0 && position > lastKnownDurationMs) {
                position = lastKnownDurationMs;
            }
            lastKnownPositionMs = position;
            lastPositionSyncMs = System.currentTimeMillis();
        }

        updateArtwork(snapshot.artwork());
        return mediaChanged;
    }

    private void clearMediaState() {
        currentMediaInfo = null;
        activeSession = null;
        displayTitle = "";
        displayArtist = "";
        destroyArtworkTexture();
        lastMediaSeenAtMs = 0L;
        lastKnownDurationMs = 0L;
        lastKnownPositionMs = 0L;
        lastPositionSyncMs = 0L;
    }

    private void updateArtwork(byte[] artworkPng) {
        int hash = artworkPng == null ? 0 : Arrays.hashCode(artworkPng);
        if (hash != 0 && hash == artworkHash && artworkTexture != null) {
            touchArtwork(hash);
            return;
        }

        if (artworkPng == null || artworkPng.length == 0) {
            destroyArtworkTexture();
            return;
        }

        NativeImageBackedTexture cachedTexture = artworkCache.get(hash);
        if (cachedTexture != null) {
            setArtworkTexture(hash, cachedTexture);
            touchArtwork(hash);
            return;
        }

        NativeImageBackedTexture decodedTexture = decodeArtwork(artworkPng);
        if (decodedTexture == null) {
            destroyArtworkTexture();
            return;
        }

        addArtworkToCache(hash, decodedTexture);
        setArtworkTexture(hash, decodedTexture);
    }

    private void destroyArtworkTexture() {
        if (artworkTexture != null) {
            boolean cachedTexture = artworkCache.getOrDefault(artworkHash, null) == artworkTexture;
            if (!cachedTexture) {
                artworkTexture.close();
            }
        }
        artworkTexture = null;
        artworkHash = 0;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getTextureManager() != null) {
                mc.getTextureManager().destroyTexture(ARTWORK_ID);
            }
        } catch (Throwable ignored) {
        }
    }

    private NativeImageBackedTexture decodeArtwork(byte[] artworkPng) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(artworkPng)) {
            NativeImage image = NativeImage.read(stream);
            return new NativeImageBackedTexture(() -> "media_art", image);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void setArtworkTexture(int hash, NativeImageBackedTexture texture) {
        if (artworkTexture != null && artworkTexture != texture) {
            boolean cachedTexture = artworkCache.getOrDefault(artworkHash, null) == artworkTexture;
            if (!cachedTexture) {
                artworkTexture.close();
            }
        }
        artworkTexture = texture;
        artworkHash = hash;
        if (texture != null) {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.getTextureManager() != null) {
                    mc.getTextureManager().registerTexture(ARTWORK_ID, texture);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void addArtworkToCache(int hash, NativeImageBackedTexture texture) {
        artworkCache.put(hash, texture);
        synchronized (artworkCacheOrder) {
            artworkCacheOrder.remove(Integer.valueOf(hash));
            artworkCacheOrder.add(hash);
            while (artworkCacheOrder.size() > 6) {
                Integer evict = artworkCacheOrder.remove(0);
                NativeImageBackedTexture removed = artworkCache.remove(evict);
                if (removed != null && removed != artworkTexture) {
                    removed.close();
                }
            }
        }
    }

    private void touchArtwork(int hash) {
        synchronized (artworkCacheOrder) {
            artworkCacheOrder.remove(Integer.valueOf(hash));
            artworkCacheOrder.add(hash);
        }
    }

    private void clearArtworkCache() {
        for (Map.Entry<Integer, NativeImageBackedTexture> entry : artworkCache.entrySet()) {
            NativeImageBackedTexture texture = entry.getValue();
            if (texture != null && texture != artworkTexture) {
                texture.close();
            }
        }
        artworkCache.clear();
        synchronized (artworkCacheOrder) {
            artworkCacheOrder.clear();
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static String resolveDisplayTitle(String title) {
        String safe = safeText(title);
        return safe.isEmpty() ? "Unknown track" : safe;
    }

    private static String resolveDisplayArtist(String artist) {
        return safeText(artist);
    }

    private static void debugMedia(String message) {
        if (MEDIA_DEBUG) {
            LinkenVisuals.LOGGER.info("[MediaPlayerInfo] {}", message);
        }
    }

    private MediaSnapshot reconcileOptimisticPlaying(MediaSnapshot incoming) {
        if (incoming == null || currentMediaInfo == null || lastOptimisticToggleMs <= 0L) {
            return incoming;
        }
        long elapsed = System.currentTimeMillis() - lastOptimisticToggleMs;
        if (elapsed > OPTIMISTIC_PLAYING_GUARD_MS) {
            lastOptimisticToggleMs = 0L;
            return incoming;
        }
        if (!isSameTrack(incoming, currentMediaInfo)) {
            lastOptimisticToggleMs = 0L;
            return incoming;
        }
        if (incoming.playing() == currentMediaInfo.playing()) {
            lastOptimisticToggleMs = 0L;
            return incoming;
        }
        byte[] artwork = incoming.artwork();
        return new MediaSnapshot(
                incoming.owner(),
                incoming.title(),
                incoming.artist(),
                currentMediaInfo.playing(),
                incoming.duration(),
                incoming.position(),
                artwork == null ? null : Arrays.copyOf(artwork, artwork.length),
                incoming.session()
        );
    }

    private static boolean isSameTrack(MediaSnapshot left, MediaSnapshot right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.owner(), right.owner())
                && Objects.equals(left.title(), right.title())
                && Objects.equals(left.artist(), right.artist());
    }

    private boolean hasCurrentMediaChanged(MediaSnapshot incoming) {
        if (incoming == null || currentMediaInfo == null) return true;
        if (!Objects.equals(incoming.owner(), currentMediaInfo.owner())) return true;
        if (!Objects.equals(incoming.title(), currentMediaInfo.title())) return true;
        if (!Objects.equals(incoming.artist(), currentMediaInfo.artist())) return true;
        return incoming.playing() != currentMediaInfo.playing();
    }

    public record MediaUpdate(boolean mediaChanged, boolean cleared) {
        public static final MediaUpdate NONE = new MediaUpdate(false, false);
    }
}
