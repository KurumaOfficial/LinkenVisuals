package dev.linkenvisuals.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.linkenvisuals.LinkenVisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * High-performance, zero-allocation translation engine for the visuals modification.
 * Supports auto-detection of Minecraft language with native Russian, Ukrainian, and English translations.
 */
public final class I18n {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static final Map<String, Map<String, String>> TRANSLATIONS = new HashMap<>();
    private static String languageOverride = "auto";

    static {
        loadLanguage("en_us");
        loadLanguage("ru_ru");
        loadLanguage("uk_ua");
    }

    private I18n() {
    }

    private static void loadLanguage(String langCode) {
        String path = "/assets/linkenvisuals/lang/" + langCode + ".json";
        try (java.io.InputStream stream = I18n.class.getResourceAsStream(path)) {
            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    Map<String, String> map = GSON.fromJson(reader, MAP_TYPE);
                    if (map != null) {
                        TRANSLATIONS.put(langCode, map);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LinkenVisuals.LOGGER.warn("Failed to load embedded translation bundle for '{}': {}", langCode, e.getMessage());
        }

        Identifier id = LinkenVisuals.id("lang/" + langCode + ".json");
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getResourceManager() == null) {
            return;
        }
        Optional<Resource> resource = client.getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {
            Map<String, String> map = GSON.fromJson(reader, MAP_TYPE);
            if (map != null) {
                TRANSLATIONS.put(langCode, map);
            }
        } catch (Exception e) {
            LinkenVisuals.LOGGER.warn("Failed to load resource translation bundle for '{}': {}", langCode, e.getMessage());
        }
    }

    private static volatile String cachedEffectiveLanguage = null;
    private static String lastGameLang = null;

    public static void setLanguageOverride(String lang) {
        languageOverride = lang != null ? lang.toLowerCase() : "auto";
        cachedEffectiveLanguage = null;
    }

    public static String getLanguageOverride() {
        return languageOverride;
    }

    public static String getEffectiveLanguage() {
        MinecraftClient client = MinecraftClient.getInstance();
        String gameLang = client != null && client.getLanguageManager() != null
            ? client.getLanguageManager().getLanguage() : null;
        if (gameLang != null && gameLang.equals(lastGameLang) && cachedEffectiveLanguage != null) {
            return cachedEffectiveLanguage;
        }
        lastGameLang = gameLang;

        // The Minecraft language is authoritative for Russian: a stale
        // language=en_us in the mod config must never force English over a
        // Russian client. Every non-Russian client falls back to English.
        if (gameLang != null && gameLang.toLowerCase(Locale.ROOT).startsWith("ru")) {
            return cachedEffectiveLanguage = "ru_ru";
        }
        // Explicit RU/UK choices remain available for non-Russian clients
        // (Classic GUI language switch); en_us is just the fallback and must
        // not override the game language above.
        if (("ru_ru".equals(languageOverride) || "uk_ua".equals(languageOverride))
            && TRANSLATIONS.containsKey(languageOverride)) {
            return cachedEffectiveLanguage = languageOverride;
        }
        return cachedEffectiveLanguage = "en_us";
    }

    public static boolean has(String key) {
        if (key == null) return false;
        if (TRANSLATIONS.isEmpty()) {
            loadLanguage("en_us");
            loadLanguage("ru_ru");
            loadLanguage("uk_ua");
        }
        String lang = getEffectiveLanguage();
        Map<String, String> bundle = TRANSLATIONS.get(lang);
        if (bundle != null && bundle.containsKey(key)) return true;
        Map<String, String> en = TRANSLATIONS.get("en_us");
        if (en != null && en.containsKey(key)) return true;
        Language language = Language.getInstance();
        return language != null && language.hasTranslation(key);
    }

    public static String get(String key) {
        if (key == null) return "";
        if (TRANSLATIONS.isEmpty()) {
            loadLanguage("en_us");
            loadLanguage("ru_ru");
            loadLanguage("uk_ua");
        }
        String lang = getEffectiveLanguage();
        Map<String, String> bundle = TRANSLATIONS.get(lang);
        if (bundle != null) {
            String val = bundle.get(key);
            if (val != null) return val;
        }
        // Fallback to English
        Map<String, String> en = TRANSLATIONS.get("en_us");
        if (en != null) {
            String val = en.get(key);
            if (val != null) return val;
        }
        // Fallback to the raw Minecraft translation. Calling vanilla
        // I18n.translate(key) here would pre-format parameterised strings with
        // zero arguments and return "Format error: ..."; get(key, args) below
        // owns the formatting.
        Language language = Language.getInstance();
        if (language != null && language.hasTranslation(key)) {
            return language.get(key);
        }
        return key;
    }

    public static String get(String key, Object... args) {
        String base = get(key);
        if (args != null && args.length > 0) {
            try {
                return String.format(base, args);
            } catch (Exception e) {
                LinkenVisuals.LOGGER.warn("Failed to format translation string for key '{}': {}", key, e.getMessage());
            }
        }
        return base;
    }
}
