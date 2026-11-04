package ru.method.linkenvisuals.utils.mediaplayer;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import net.fabricmc.loader.api.FabricLoader;
import ru.method.linkenvisuals.LinkenVisuals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SystemMediaController {

    private static final String LOG_PREFIX = "[MediaPlayerInfo]";

    /** Пока после последних живых SMTC-данных не прошло столько мс, скан окон не включаем. */
    private static final long WINDOW_SCAN_GRACE_MS = 5_000L;

    /**
     * Бонус за играющую сессию. Должен ПЕРЕБИВАТЬ максимальный owner-скор (Яндекс=3000): играющий трек
     * обязан побеждать поставленный на паузу «приоритетный» источник. Раньше было +100 — его перебивал
     * owner-бонус, и HUD залипал на паузной Яндекс/Spotify-сессии вместо играющего браузерного SoundCloud
     * (у браузера owner-скор = 0). Owner-приоритет теперь работает лишь как тай-брейк МЕЖДУ играющими.
     */
    private static final int PLAYING_BONUS = 100_000;

    // Crash-cookie: MediaPlayerInfo.dll (WinRT SMTC) на части машин бросает НЕОБРАБОТАННОЕ C++ исключение
    // (0xe06d7363) прямо в getMediaSessions() — оно рушит JVM внутри нативного вызова, до возврата, и
    // Java try/catch его не ловит (это не Throwable). Поэтому пишем маркер ПЕРЕД первым нативным вызовом
    // и снимаем ПОСЛЕ него; если на старте маркер найден — прошлый запуск умер в этом нативе, интеграцию
    // системного плеера отключаем навсегда (удалить linkenvisuals-media.lock вручную = снова включить).
    private static final Path PROBE_LOCK =
            FabricLoader.getInstance().getConfigDir().resolve("linkenvisuals-media.lock");
    private static volatile boolean mediaDisabled;
    private static volatile boolean probeDone;

    static {
        try {
            if (Files.exists(PROBE_LOCK)) {
                mediaDisabled = true;
                LinkenVisuals.LOGGER.warn("{} прошлый запуск упал в нативе MediaPlayerInfo.dll — "
                        + "интеграция системного плеера отключена (удали {} чтобы включить снова)",
                        LOG_PREFIX, PROBE_LOCK);
            }
        } catch (Throwable ignored) {
        }
    }

    private SystemMediaController() {
    }

    public static boolean isDisabled() {
        return mediaDisabled;
    }

    public static List<IMediaSession> fetchSessions() {
        if (mediaDisabled) {
            return List.of();
        }
        if (!WinRtInterop.ensureInitialized()) {
            LinkenVisuals.LOGGER.debug("{} WinRT/COM initialization unavailable", LOG_PREFIX);
            return List.of();
        }
        boolean firstProbe = !probeDone;
        if (firstProbe) {
            try {
                Files.writeString(PROBE_LOCK, "probing");   // маркер: вошли в нативный вызов
            } catch (Throwable ignored) {
            }
        }
        try {
            List<IMediaSession> sessions = MediaPlayerInfo.Instance.getMediaSessions();
            clearProbe(firstProbe);   // натив ВЕРНУЛСЯ — снимаем маркер
            return sessions == null ? List.of() : sessions;
        } catch (Throwable throwable) {
            // Java-исключение (не нативный краш): натив всё же вернул управление — маркер тоже снимаем.
            clearProbe(firstProbe);
            LinkenVisuals.LOGGER.debug("{} Failed to fetch media sessions", LOG_PREFIX, throwable);
            return List.of();
        }
    }

    private static void clearProbe(boolean firstProbe) {
        if (!firstProbe) {
            return;
        }
        probeDone = true;
        try {
            Files.deleteIfExists(PROBE_LOCK);
        } catch (Throwable ignored) {
        }
    }

    public static IMediaSession selectBestSession(
            List<IMediaSession> sessions,
            IMediaSession preferredSession,
            String preferredOwner,
            MediaSnapshot currentMedia,
            long lastMediaSeenAtMs
    ) {
        IMediaSession bestSession = null;
        MediaSnapshot bestSnapshot = null;
        int bestScore = Integer.MIN_VALUE;
        long now = System.currentTimeMillis();

        for (IMediaSession session : sessions) {
            if (session == null) {
                continue;
            }
            MediaSnapshot snapshot = captureSnapshot(session);
            if (snapshot == null) {
                continue;
            }
            int score = scoreSnapshot(snapshot, preferredSession, preferredOwner, session, currentMedia, lastMediaSeenAtMs, now);
            if (bestSnapshot == null || score > bestScore) {
                bestSnapshot = snapshot;
                bestSession = session;
                bestScore = score;
            }
        }

        if (bestSession != null) {
            return bestSession;
        }
        return preferredSession;
    }

    public static MediaSnapshot captureSnapshot(IMediaSession session) {
        try {
            MediaInfo media = session.getMedia();
            if (media == null) {
                return null;
            }

            String title = safeText(media.getTitle());
            String artist = safeText(media.getArtist());
            boolean playing = media.getPlaying();
            long duration = normalizeMediaTime(media.getDuration());
            long position = normalizeMediaTime(media.getPosition());
            if (!playing && title.isEmpty() && artist.isEmpty() && duration <= 0L) {
                return null;
            }

            byte[] artwork = media.getArtworkPng();
            if (artwork != null && artwork.length > 0) {
                artwork = Arrays.copyOf(artwork, artwork.length);
            } else {
                artwork = null;
            }

            return new MediaSnapshot(
                    safeText(session.getOwner()),
                    title,
                    artist,
                    playing,
                    duration,
                    position,
                    artwork,
                    session
            );
        } catch (Throwable throwable) {
            LinkenVisuals.LOGGER.debug("{} Session '{}' getMedia() failed", LOG_PREFIX, session.getOwner(), throwable);
            return null;
        }
    }

    public static MediaSnapshot pollSnapshot(
            IMediaSession preferredSession,
            String preferredOwner,
            MediaSnapshot currentMedia,
            long lastMediaSeenAtMs
    ) {
        List<IMediaSession> sessions = fetchSessions();
        if (!sessions.isEmpty()) {
            IMediaSession session = selectBestSession(
                    sessions,
                    preferredSession,
                    preferredOwner,
                    currentMedia,
                    lastMediaSeenAtMs
            );
            if (session != null) {
                MediaSnapshot snapshot = captureSnapshot(session);
                if (snapshot != null) {
                    return snapshot;
                }
            }
        }

        // При смене трека SMTC на миг «пустеет» (плеер чистит метаданные и ставит паузу) — мгновенный
        // фолбэк на заголовки окон подхватывал случайную вкладку браузера («Today …») на один-два
        // опроса. Если живые SMTC-данные были недавно, дырку пережидаем: null = NO_MEDIA, а HUD и так
        // держит последний трек до 6 с (stale-логика в DynamicIslandMedia).
        if (lastMediaSeenAtMs > 0L && System.currentTimeMillis() - lastMediaSeenAtMs < WINDOW_SCAN_GRACE_MS) {
            return null;
        }

        WindowsWindowTitleScanner.WindowMediaCandidate fallback = WindowsWindowTitleScanner.scanBestCandidate();
        if (fallback == null) {
            return null;
        }

        return new MediaSnapshot(
                fallback.owner(),
                fallback.title(),
                fallback.artist(),
                true,
                0L,
                0L,
                null,
                null
        );
    }

    public static boolean performAction(IMediaSession session, WindowsMediaKeys.MediaAction action) {
        if (session != null) {
            try {
                switch (action) {
                    case PREVIOUS -> session.previous();
                    case PLAY_PAUSE -> session.playPause();
                    case NEXT -> session.next();
                }
                return true;
            } catch (Throwable throwable) {
                LinkenVisuals.LOGGER.debug("{} Session action {} failed, trying media keys", LOG_PREFIX, action, throwable);
            }
        }
        return WindowsMediaKeys.send(action);
    }

    public static IMediaSession resolveActionSession(IMediaSession activeSession) {
        if (activeSession != null) {
            return activeSession;
        }
        List<IMediaSession> sessions = fetchSessions();
        return selectBestSession(sessions, null, null, null, 0L);
    }

    private static int scoreSnapshot(
            MediaSnapshot snap,
            IMediaSession preferredSession,
            String preferredOwner,
            IMediaSession session,
            MediaSnapshot currentMedia,
            long lastMediaSeenAtMs,
            long now
    ) {
        int score = scoreOwner(snap.owner());
        if (snap.playing()) score += PLAYING_BONUS;
        if (preferredSession != null && session == preferredSession) score += 40;
        if (preferredOwner != null && Objects.equals(preferredOwner, snap.owner())) score += 30;

        boolean hasAnyText = !snap.title().isEmpty() || !snap.artist().isEmpty();
        if (!hasAnyText) score -= 1000;
        if (!snap.title().isEmpty()) score += 20;
        if (!snap.artist().isEmpty()) score += 10;
        if (snap.duration() > 0) score += 5;
        if (snap.position() > 0) score += 2;

        if (!snap.playing() && snap.duration() > 0 && snap.position() >= snap.duration()) {
            score -= 30;
        }
        if (snap.playing() && snap.duration() > 0 && snap.position() >= snap.duration()) {
            score -= 10;
        }

        if (currentMedia != null
                && Objects.equals(currentMedia.owner(), snap.owner())
                && Objects.equals(currentMedia.title(), snap.title())
                && Objects.equals(currentMedia.artist(), snap.artist())
                && snap.duration() > 0
                && snap.position() >= snap.duration()
                && now - lastMediaSeenAtMs > 1200L) {
            score -= 80;
        }
        return score;
    }

    private static int scoreOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            return 0;
        }
        String lower = owner.toLowerCase(Locale.ROOT);
        if (lower.contains("yandex")) return 3000;
        if (lower.contains("spotify") || lower.contains("music")) return 1200;
        if (lower.contains("youtube")) return 120;
        return 0;
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static long normalizeMediaTime(long raw) {
        if (raw <= 0L) return 0L;
        if (raw < 100_000L) return raw * 1000L;
        return raw;
    }

    public record MediaSnapshot(
            String owner,
            String title,
            String artist,
            boolean playing,
            long duration,
            long position,
            byte[] artwork,
            IMediaSession session
    ) {
    }
}
