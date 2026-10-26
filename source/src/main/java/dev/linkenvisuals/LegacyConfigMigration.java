package dev.linkenvisuals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * One-shot migration of player data written by the legacy visuals modification id
 * ({@code strangevisuals}) to the current {@code linkenvisuals} locations.
 * <p>
 * Copies legacy files and directories only when the new target does not exist yet,
 * so a fresh installation or an already-migrated one is never touched and the
 * legacy originals stay in place as a fallback.
 */
public final class LegacyConfigMigration {
    /** Legacy mod id — kept as a literal: this is what the old builds wrote to disk. */
    public static final String LEGACY_ID = "strangevisuals";
    /** Current mod id. */
    public static final String CURRENT_ID = "linkenvisuals";

    /** Config files renamed by the id switch inside {@code config/}. */
    private static final List<String> LEGACY_FILES = List.of(
        "strangevisuals_config.json",
        "strangevisuals_accounts.json",
        "strangevisuals_macros.json",
        "strangevisuals_menu.json",
        "strangevisuals_hud.json",
        "strangevisuals_potion_layout.txt",
        "strangevisuals_hands.json",
        "strangevisuals_background.png"
    );

    private LegacyConfigMigration() {
    }

    /**
     * Copies every known legacy file/directory that has no current counterpart yet.
     *
     * @return number of copies performed plus human-readable failure notes.
     */
    public static Result run(Path configDir, Path gameDir) {
        int copied = 0;
        List<String> failures = new ArrayList<>();

        for (String legacyName : LEGACY_FILES) {
            String currentName = legacyName.replace(LEGACY_ID, CURRENT_ID);
            if (copyFileIfMissing(configDir.resolve(legacyName), configDir.resolve(currentName), failures)) {
                copied++;
            }
        }
        copied += copyDirIfMissing(configDir.resolve(LEGACY_ID), configDir.resolve(CURRENT_ID), failures);
        copied += copyDirIfMissing(gameDir.resolve(LEGACY_ID), gameDir.resolve(CURRENT_ID), failures);

        return new Result(copied, List.copyOf(failures));
    }

    private static boolean copyFileIfMissing(Path legacy, Path current, List<String> failures) {
        if (!Files.isRegularFile(legacy) || Files.exists(current)) {
            return false;
        }
        try {
            Files.copy(legacy, current, StandardCopyOption.COPY_ATTRIBUTES);
            return true;
        } catch (IOException e) {
            failures.add("failed to migrate " + legacy.getFileName() + ": " + e.getMessage());
            return false;
        }
    }

    private static int copyDirIfMissing(Path legacyDir, Path currentDir, List<String> failures) {
        if (!Files.isDirectory(legacyDir) || Files.exists(currentDir)) {
            return 0;
        }
        int copied = 0;
        try (Stream<Path> walk = Files.walk(legacyDir)) {
            for (Path source : walk.toList()) {
                Path target = currentDir.resolve(legacyDir.relativize(source).toString());
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                        copied++;
                    }
                } catch (IOException e) {
                    failures.add("failed to migrate " + legacyDir.relativize(source) + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            failures.add("failed to scan " + legacyDir + ": " + e.getMessage());
        }
        return copied;
    }

    /** Outcome of a migration pass. */
    public record Result(int copied, List<String> failures) {
        public boolean clean() {
            return failures.isEmpty();
        }
    }
}
