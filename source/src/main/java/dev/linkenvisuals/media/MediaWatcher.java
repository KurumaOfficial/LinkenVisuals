package dev.linkenvisuals.media;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.linkenvisuals.LinkenVisuals;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Media transport integration service (playback track, artist, timeline).
 *
 * <p>On Windows the built-in provider spawns an isolated PowerShell sub-process querying
 * {@code Windows.Media.Control} via Windows Runtime (WinRT) APIs and streams one
 * tab-separated line per poll. The script is piped through stdin ({@code -Command -}) and
 * every script line is a complete statement on purpose: such input may be executed
 * line-by-line depending on the host, where multi-line constructs are never executed.
 * No {@code -ExecutionPolicy} flag is passed: the policy gates script files, not stdin
 * commands, so dropping it keeps behaviour identical while avoiding an antivirus
 * heuristic trigger.
 *
 * <p>An external script may replace the built-in provider: create
 * {@code config/linkenvisuals_media.cmd} (Windows) or {@code config/linkenvisuals_media.sh}
 * (other platforms) and it is executed instead. It must print the same line format:
 * {@code title TAB artist TAB app TAB playing(0|1) TAB position_ms TAB length_ms},
 * empty line when nothing is playing. A platform with neither the built-in provider nor
 * an external script reports "unsupported platform" once and stays idle.
 */
public final class MediaWatcher {
    private static final String SCRIPT = """
        [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
        $OutputEncoding = [System.Text.Encoding]::UTF8
        Add-Type -AssemblyName System.Runtime.WindowsRuntime
        $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
        function Await($op, $type) { $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op)); $task.Wait(2500) | Out-Null; $task.Result }
        $mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
        $mgr = Await ($mgrType::RequestAsync()) ($mgrType)
        $propType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]
        while ($true) { $best = ''; $bestScore = -1000; try { if ($null -ne $mgr) { $sessions = $mgr.GetSessions(); foreach ($s in $sessions) { $props = Await ($s.TryGetMediaPropertiesAsync()) ($propType); if ($null -eq $props) { continue }; $title = ("" + $props.Title).Trim(); $artist = ("" + $props.Artist).Trim(); if ([string]::IsNullOrWhiteSpace($artist)) { $artist = ("" + $props.AlbumArtist).Trim() }; if ([string]::IsNullOrWhiteSpace($title) -and [string]::IsNullOrWhiteSpace($artist)) { continue }; $info = $s.GetPlaybackInfo(); $playing = '0'; if ("$($info.PlaybackStatus)" -eq 'Playing') { $playing = '1' }; $pos = 0; $len = 0; try { $tl = $s.GetTimelineProperties(); $pos = [math]::Round($tl.Position.TotalMilliseconds); $len = [math]::Round($tl.EndTime.TotalMilliseconds) } catch { }; $app = "" + $s.SourceAppUserModelId; $line = "$title`t$artist`t$app`t$playing`t$pos`t$len"; $score = 0; if ($playing -eq '1') { $score += 50000 }; if ("$($props.PlaybackType)" -eq 'Music') { $score += 20 }; $id = $app.ToLower(); if ($id -match 'spotify|soundcloud|yandex|music|deezer|tidal|aimp|foobar|winamp|apple') { $score += 100 } elseif ($id -match 'chrome|edge|firefox|opera|brave') { $score += 10 }; if ($score -gt $bestScore) { $bestScore = $score; $best = $line } } } } catch { }; Write-Output $best; [Console]::Out.Flush(); Start-Sleep -Milliseconds 1000 }
        """;

    private static final boolean WINDOWS =
        System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final String SCRIPT_FILE =
        WINDOWS ? "linkenvisuals_media.cmd" : "linkenvisuals_media.sh";

    private static volatile Track track = Track.NONE;
    private static Process process;
    private static boolean started;
    private static Provider provider;

    private MediaWatcher() {
    }

    public record Track(String title, String artist, String app, boolean playing,
                        long position, long length) {
        public static final Track NONE = new Track("", "", "", false, 0L, 0L);

        public boolean present() {
            return !title.isBlank();
        }
    }

    /** Resolved media source: the command to run and whether the built-in script must be piped to stdin. */
    private record Provider(List<String> command, boolean feedScript) {
    }

    public static Track current() {
        DynamicIslandMedia m = DynamicIslandMediaHolder.shared();
        if (m.hasMedia()) {
            return new Track(m.getTitle(), m.getArtist(), "SMTC", m.isPlaying(), m.getPositionMs(), m.getDurationMs());
        }
        return track;
    }

    public static synchronized void ensureRunning() {
        if (started) {
            return;
        }
        started = true;
        provider = resolveProvider();
        if (provider == null) {
            LinkenVisuals.LOGGER.info("MediaWatcher: unsupported platform "
                + "(no Windows SMTC and no external script at config/{})", SCRIPT_FILE);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(MediaWatcher::stop, "visuals-modification-media-cleanup"));
        Thread thread = new Thread(MediaWatcher::pump, "visuals-modification-media");
        thread.setDaemon(true);
        thread.start();
    }

    private static Provider resolveProvider() {
        Path script = FabricLoader.getInstance().getConfigDir().resolve(SCRIPT_FILE);
        if (Files.isRegularFile(script)) {
            LinkenVisuals.LOGGER.info("MediaWatcher: using external script {}", script);
            return WINDOWS
                ? new Provider(List.of("cmd.exe", "/c", script.toString()), false)
                : new Provider(List.of("sh", script.toString()), false);
        }
        if (WINDOWS) {
            byte[] utf16 = SCRIPT.getBytes(StandardCharsets.UTF_16LE);
            String b64 = java.util.Base64.getEncoder().encodeToString(utf16);
            return new Provider(List.of("powershell.exe", "-NoProfile", "-NonInteractive",
                "-EncodedCommand", b64), false);
        }
        return null;
    }

    public static synchronized void stop() {
        started = false;
        provider = null;
        track = Track.NONE;
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
    }

    private static void pump() {
        while (started) {
            Provider active = provider;
            if (active == null) {
                return;
            }
            try {
                ProcessBuilder builder = new ProcessBuilder(active.command());
                builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                process = builder.start();

                try (OutputStream out = process.getOutputStream()) {
                    if (active.feedScript()) {
                        out.write(SCRIPT.getBytes(StandardCharsets.UTF_8));
                        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    }
                }

                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        track = parse(line);
                    }
                }
            } catch (IOException e) {
                LinkenVisuals.LOGGER.debug("MediaWatcher script read failed: {}", e.getMessage());
                track = Track.NONE;
            } finally {
                if (process != null) {
                    process.destroyForcibly();
                    process = null;
                }
            }

            if (started) {
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // Package-private for tests.
    static Track parse(String line) {
        if (line == null || line.isBlank()) {
            return Track.NONE;
        }
        String[] parts = line.split("\t", -1);
        if (parts.length < 6) {
            return Track.NONE;
        }
        String title = parts[0].trim();
        String artist = parts[1].trim();

        if (title.isBlank()) {
            title = artist;
            artist = "";
        }
        return new Track(title, artist, parts[2].trim(), "1".equals(parts[3].trim()),
            parseLong(parts[4]), parseLong(parts[5]));
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LinkenVisuals.LOGGER.debug("MediaWatcher: non-numeric timestamp '{}'", value);
            return 0L;
        }
    }
}
