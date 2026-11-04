package dev.linkenvisuals;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangParityTest {
    private static final Set<String> LANGUAGES = new LinkedHashSet<>(Set.of("en_us", "ru_ru", "uk_ua"));

    @Test
    void allLanguagesShareTheSameKeySet() throws IOException {
        Set<String> reference = keys("en_us");
        assertTrue(reference.contains("linkenvisuals.menu.singleplayer"), "sanity: expected key missing");
        for (String language : LANGUAGES) {
            Set<String> current = keys(language);
            Set<String> missing = new TreeSet<>(reference);
            missing.removeAll(current);
            Set<String> extra = new TreeSet<>(current);
            extra.removeAll(reference);
            assertEquals(Set.of(), missing, language + " is missing keys: " + missing);
            assertEquals(Set.of(), extra, language + " has extra keys: " + extra);
        }
    }

    private static Set<String> keys(String language) throws IOException {
        return new TreeSet<>(JsonParser.parseString(raw(language)).getAsJsonObject().keySet());
    }

    private static String raw(String language) throws IOException {
        try (InputStream in = LangParityTest.class.getResourceAsStream(
            "/assets/linkenvisuals/lang/" + language + ".json")) {
            assertNotNull(in, "missing language file: " + language);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
