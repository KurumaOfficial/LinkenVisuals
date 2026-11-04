package ru.method.linkenvisuals.managers.misc;

import com.google.gson.*;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.cosmetics.service.CosmeticsService;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandNotificationManager;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.modules.impl.render.DamageParticles;
import ru.method.linkenvisuals.modules.impl.render.Particles;
import ru.method.linkenvisuals.modules.impl.render.Zoom;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeManager;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.impl.*;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.api.Position;
import ru.method.linkenvisuals.utils.macro.Macro;
import ru.method.linkenvisuals.utils.other.FileUtils;

import lombok.Cleanup;

import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;


public class ConfigManager {

    private static final int CONFIG_FORMAT_VERSION = 2;
    private static final long AUTO_SAVE_COOLDOWN_MS = 400L;

    private static final String CONFIG_EXTENSION = ".linken";
    private static final String LEGACY_CONFIG_EXTENSION = ".prime";
    private static final String THEME_EXTENSION = ".theme";

    private String currentConfig = "default";
    private final String extension = CONFIG_EXTENSION;
    private boolean initialized = false;
    private int loadedConfigFormatVersion = 1;
    private final Object autoSaveLock = new Object();
    // Общий монитор для сохранения: не даёт shutdown-hook'у и потоку автосейва
    // писать одни и те же файлы конфига одновременно (иначе возможна порча файла).
    private final Object saveLock = new Object();
    private volatile long lastAutoSaveAt = 0L;
    private volatile boolean autoSavePending = false;
    private volatile boolean autoSaveWorkerScheduled = false;
    // Ник автора каждого конфига (имя → ник). Кэшируется, чтобы не читать файл каждый кадр в панели.
    private final java.util.Map<String, String> authorCache = new java.util.concurrent.ConcurrentHashMap<>();

    public ConfigManager() {
        try {
            createDirectories();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LinkenVisuals.LOGGER.info("[ConfigManager] Saving configs on shutdown...");
                saveAll();
            }));
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[ConfigManager] Error during initialization:", e);
        }
    }

    public void initialize() {
        if (!initialized) {
            LinkenVisuals.LOGGER.info("[ConfigManager] Loading configs...");

            for (HudElement element : LinkenVisuals.getInstance().getHudManager().getHudElements()) {
                if (element.isToggled()) element.setToggled(false);
                for (Setting<?> setting : element.getSettings()) {
                    setting.reset();
                }
            }

            try {
                loadGlobals();
                loadMacros();

                // Живой снимок последней сессии (именованный конфиг + рантайм-тумблеры) храним в
                // ЛОКАЛЬНОМ default.linken и грузим ЕГО — так изменения переживают перезаход, а сам
                // именованный .cfg остаётся нетронутым. Имя текущего конфига для отображения/сохранения
                // берём из globals (desiredName), т.к. loadConfig("default") выставит currentConfig="default".
                String desiredName = currentConfig;
                File configsDir = LinkenVisuals.getInstance().getConfigsDir();
                boolean liveExists = resolveExistingConfigPath(configsDir, "default") != null;
                boolean namedExists = resolveExistingConfigPath(configsDir, desiredName) != null;

                if (liveExists) {
                    loadConfig("default");
                    currentConfig = desiredName;
                } else if (namedExists) {
                    loadConfig(desiredName);
                } else {
                    initializeDefaults();
                    persistDefaultConfig();
                }

                LinkenVisuals.LOGGER.info("[ConfigManager] Configs loaded successfully");
            } catch (Exception e) {
                LinkenVisuals.LOGGER.error("[ConfigManager] Error loading configs:", e);
            }

            initialized = true;
        }
    }

    public void initializeDefaults() {
        LinkenVisuals.LOGGER.info("[ConfigManager] Initializing default HUD elements as disabled...");

        for (HudElement element : LinkenVisuals.getInstance().getHudManager().getHudElements()) {
            BooleanSetting setting = LinkenVisuals.getInstance().getHudManager().getElementSetting(element);
            element.setToggled(setting == null || setting.getValue());
        }

        ru.method.linkenvisuals.modules.impl.render.CustomVitals vitals =
                LinkenVisuals.getInstance().getModuleManager().getModule(
                        ru.method.linkenvisuals.modules.impl.render.CustomVitals.class);
        if (vitals != null) {
            vitals.setToggled(true);
        }

        LinkenVisuals.LOGGER.info("[ConfigManager] Default HUD elements initialized (disabled)");
    }

    public String getCurrentConfigName() {
        return currentConfig;
    }

    private void createDirectories() {
        try {
            File globalsDir = LinkenVisuals.getInstance().getGlobalsDir();
            File configsDir = LinkenVisuals.getInstance().getConfigsDir();
            File themesDir = LinkenVisuals.getInstance().getThemesDir();

            if (!globalsDir.exists()) {
                globalsDir.mkdirs();
                LinkenVisuals.LOGGER.info("[ConfigManager] Created globals directory");
            }
            if (!configsDir.exists()) {
                configsDir.mkdirs();
                LinkenVisuals.LOGGER.info("[ConfigManager] Created configs directory");
            }
            if (!themesDir.exists()) {
                themesDir.mkdirs();
                LinkenVisuals.LOGGER.info("[ConfigManager] Created themes directory");
            }
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[ConfigManager] Error creating directories:", e);
        }
    }

    private void loadAll() {
        try {
            loadGlobals();
            loadMacros();
            loadConfig(currentConfig);
            LinkenVisuals.LOGGER.info("[ConfigManager] Configs loaded successfully");
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[ConfigManager] Error loading configs:", e);
        }
    }

    private void saveAll() {
        saveAll(true);
    }

    private void saveAll(boolean verbose) {
        // Сериализуем все сохранения под общим монитором: shutdown-hook и поток
        // автосейва не смогут одновременно писать одни и те же файлы.
        synchronized (saveLock) {
            try {
                // Автосейв/шатдаун пишут сессионное состояние (globals/macros) И ЖИВОЙ СНИМОК настроек
                // в ЛОКАЛЬНЫЙ default.linken — чтобы рантайм-переключения модулей ПЕРЕЖИЛИ перезаход игры.
                // ИМЕНОВАННЫЙ конфиг (myconfig.linken) при этом НЕ трогаем: он остаётся явным снимком и
                // переписывается только по ЯВНОМУ сохранению (saveConfig — команда «.config save» ИЛИ
                // кнопка GUI-менеджера). Так тумблеры сохраняются локально, но не мутируют сам .cfg-файл.
                saveGlobals(verbose);
                saveMacros(verbose);
                persistDefaultConfig();
                if (verbose) {
                    LinkenVisuals.LOGGER.info("[ConfigManager] Session state saved (named config untouched — save explicitly)");
                }
            } catch (Exception e) {
                LinkenVisuals.LOGGER.error("[ConfigManager] Error saving configs:", e);
            }
        }
    }

    public void forceSave() {
        scheduleAutoSave();
    }

    public void saveNow() {
        synchronized (autoSaveLock) {
            autoSavePending = false;
            lastAutoSaveAt = System.currentTimeMillis();
        }
        saveAll(true);
    }

    public void saveConfig(String config) throws Exception {
        saveConfig(config, true, true); // явное сохранение → штампуем автора текущим ником
    }

    private void saveConfig(String config, boolean verbose, boolean stampAuthor) throws Exception {
        String normalizedConfig = normalizeConfigName(config);
        try {
            writeConfigFile(normalizedConfig, true, verbose, stampAuthor);
            if (!"default".equals(normalizedConfig)) {
                persistDefaultConfig();
            }
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[ConfigManager] Error saving config " + normalizedConfig + ":", e);
            throw e;
        }
    }

    public boolean loadConfig(String config) throws Exception {
        String normalizedConfig = normalizeConfigName(config);
        LinkenVisuals instance = LinkenVisuals.getInstance();
        boolean prevSuppress = instance != null && instance.isSuppressToggleFeedback();
        boolean prevConfigLoading = instance != null && instance.isConfigLoading();
        boolean loadedSuccessfully = false;
        if (instance != null) {
            instance.setSuppressToggleFeedback(true);
            instance.setConfigLoading(true);
        }
        try {
            String configPath = resolveExistingConfigPath(LinkenVisuals.getInstance().getConfigsDir(), normalizedConfig);

            if (configPath == null) {
                LinkenVisuals.LOGGER.warn("[ConfigManager] Config file not found: " + normalizedConfig + extension);
                return false;
            }

            LinkenVisuals.LOGGER.info("[ConfigManager] Loading config: " + configPath);

            JsonObject object;
            try (InputStream stream = Files.newInputStream(Paths.get(configPath));
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                object = JsonParser.parseReader(reader).getAsJsonObject();
            }

            loadedConfigFormatVersion = object.has("formatVersion") ? object.get("formatVersion").getAsInt() : 1;

            if (object.has("modules")) {
                deserializeModules(object.get("modules").getAsJsonObject());
            }
            if (object.has("hudOrder") && object.get("hudOrder").isJsonArray()) {
                deserializeHudOrder(object.getAsJsonArray("hudOrder"));
            }
            if (object.has("linkGuiTheme")) {
                LinkGuiThemeManager.loadFromName(object.get("linkGuiTheme").getAsString());
            }

            this.currentConfig = normalizedConfig;
            LinkenVisuals.LOGGER.info("[ConfigManager] Config loaded: " + normalizedConfig);
            if (!"default".equals(normalizedConfig)) {
                persistDefaultConfig();
            }
            loadedSuccessfully = true;
            return true;
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[ConfigManager] Error loading config " + normalizedConfig + ":", e);
            throw e;
        } finally {
            if (instance != null) {
                instance.setSuppressToggleFeedback(prevSuppress);
                instance.setConfigLoading(prevConfigLoading);
            }
            if (loadedSuccessfully) {
                LinkGuiThemeManager.reapplyAfterConfigLoad();
                DynamicIslandNotificationManager.getInstance().addInfoNotification("Конфиг загружен!");
            }
        }
    }

    /** Имена всех сохранённых конфигов (без расширения), без дублей, по алфавиту. */
    public java.util.List<String> listConfigNames() {
        File dir = LinkenVisuals.getInstance().getConfigsDir();
        File[] files = dir == null ? null : dir.listFiles(f -> f.isFile()
                && (f.getName().toLowerCase(Locale.ROOT).endsWith(CONFIG_EXTENSION)
                    || f.getName().toLowerCase(Locale.ROOT).endsWith(LEGACY_CONFIG_EXTENSION)));
        if (files == null || files.length == 0) return java.util.List.of();
        java.util.TreeSet<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (File f : files) {
            String n = f.getName();
            int dot = n.lastIndexOf('.');
            if (dot > 0) n = n.substring(0, dot);
            if (!n.isBlank()) names.add(n);
        }
        return new java.util.ArrayList<>(names);
    }

    /** Ник игрока, создавшего/обновившего этот конфиг («by …» в панели). Пусто, если не записан. */
    public String getConfigAuthor(String config) {
        String name = normalizeConfigName(config);
        String cached = authorCache.get(name);
        if (cached != null) return cached;
        String author = "";
        try {
            String path = resolveExistingConfigPath(LinkenVisuals.getInstance().getConfigsDir(), name);
            if (path != null) {
                try (InputStream stream = Files.newInputStream(Paths.get(path));
                     InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    if (obj.has("author")) author = obj.get("author").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        authorCache.put(name, author);
        return author;
    }

    /** Ник текущего игрока (для штампа автора конфига), обрезанный до 15 символов. */
    private String currentNick() {
        net.minecraft.client.MinecraftClient c = net.minecraft.client.MinecraftClient.getInstance();
        String nick = "";
        if (c != null) {
            if (c.player != null && c.player.getGameProfile() != null) nick = c.player.getGameProfile().name();
            else if (c.getSession() != null) nick = c.getSession().getUsername();
        }
        if (nick == null) nick = "";
        return nick.length() > 15 ? nick.substring(0, 15) : nick;
    }

    /** Удаляет файл конфига. Активный и default удалить нельзя. */
    public boolean removeConfig(String config) {
        String name = normalizeConfigName(config);
        if (name.equalsIgnoreCase(currentConfig) || "default".equalsIgnoreCase(name)) return false;
        String path = resolveExistingConfigPath(LinkenVisuals.getInstance().getConfigsDir(), name);
        if (path == null) return false;
        try {
            boolean removed = Files.deleteIfExists(Paths.get(path));
            if (removed) { LinkenVisuals.LOGGER.info("[ConfigManager] Config removed: " + name); authorCache.remove(name); }
            return removed;
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[ConfigManager] Error removing config " + name + ":", e);
            return false;
        }
    }

    public String normalizeConfigName(String config) {
        String normalized = config == null ? "" : config.trim();
        if (normalized.toLowerCase(Locale.ROOT).endsWith(extension)) {
            normalized = normalized.substring(0, normalized.length() - extension.length());
        } else if (normalized.toLowerCase(Locale.ROOT).endsWith(LEGACY_CONFIG_EXTENSION)) {
            normalized = normalized.substring(0, normalized.length() - LEGACY_CONFIG_EXTENSION.length());
        }
        normalized = sanitizeFileName(normalized);
        return normalized.isBlank() ? currentConfig : normalized;
    }

    public String normalizeThemeName(String theme) {
        String normalized = theme == null ? "" : theme.trim();
        if (normalized.toLowerCase(Locale.ROOT).endsWith(THEME_EXTENSION)) {
            normalized = normalized.substring(0, normalized.length() - THEME_EXTENSION.length());
        }
        normalized = sanitizeFileName(normalized);
        return normalized.isBlank() ? "default" : normalized;
    }

    private String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= 'А' && c <= 'я') || c == 'Ё' || c == 'ё'
                    || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public void saveMacros() throws Exception {
        saveMacros(true);
    }

    private void saveMacros(boolean verbose) throws Exception {
        try {
            String macrosPath = LinkenVisuals.getInstance().getGlobalsDir() + "/" + "macro" + extension;
            if (verbose) {
                LinkenVisuals.LOGGER.info("[ConfigManager] Saving macros: " + macrosPath);
            }

            FileUtils.reset(macrosPath);
            JsonObject object = new JsonObject();

            JsonArray macrosArray = new JsonArray();
            LinkenVisuals.getInstance().getMacroManager().getMacros().forEach(macro -> {
                JsonObject macroObject = new JsonObject();
                macroObject.add("name", new JsonPrimitive(macro.getName()));
                macroObject.add("command", new JsonPrimitive(macro.getCommand()));
                macroObject.add("key", new JsonPrimitive(macro.getBind().getKey()));
                macroObject.add("mouse", new JsonPrimitive(macro.getBind().isMouse()));
                macrosArray.add(macroObject);
            });
            object.add("macros", macrosArray);

            @Cleanup OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(macrosPath), StandardCharsets.UTF_8);
            writer.write(new GsonBuilder().setPrettyPrinting().create()
                    .toJson(JsonParser.parseString(object.toString())));

            if (verbose) {
                LinkenVisuals.LOGGER.info("[ConfigManager] Macros saved successfully");
            }
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[ConfigManager] Error saving macros:", e);
            throw e;
        }
    }

    public void loadMacros() throws Exception {
        try {
            String macrosPath = resolveExistingConfigPath(LinkenVisuals.getInstance().getGlobalsDir(), "macro");

            if (macrosPath == null) {
                LinkenVisuals.LOGGER.warn("[ConfigManager] Macros file not found: macro" + extension);
                return;
            }

            LinkenVisuals.LOGGER.info("[ConfigManager] Loading macros: " + macrosPath);

            LinkenVisuals.getInstance().getMacroManager().clear();

            JsonObject object;
            try (InputStream stream = Files.newInputStream(Paths.get(macrosPath));
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                object = JsonParser.parseReader(reader).getAsJsonObject();
            }

            if (object.has("macros")) {
                for (JsonElement element : object.get("macros").getAsJsonArray()) {
                    JsonObject macroObject = element.getAsJsonObject();

                    String name = macroObject.get("name").getAsString();
                    String command = macroObject.get("command").getAsString();
                    int key = macroObject.get("key").getAsInt();
                    boolean mouse = macroObject.has("mouse") ? macroObject.get("mouse").getAsBoolean() : false;

                    Bind bind = new Bind(key, mouse);
                    LinkenVisuals.getInstance().getMacroManager().add(new Macro(name, command, bind));
                }
            }

            LinkenVisuals.LOGGER.info("[ConfigManager] Macros loaded successfully");
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[ConfigManager] Error loading macros:", e);
            throw e;
        }
    }

    public void saveGlobals() throws Exception {
        saveGlobals(true);
    }

    private void saveGlobals(boolean verbose) throws Exception {
        try {
            String globalsPath = LinkenVisuals.getInstance().getGlobalsDir() + "/" + "globals" + extension;
            if (verbose) {
                LinkenVisuals.LOGGER.info("[ConfigManager] Saving globals: " + globalsPath);
            }

            FileUtils.reset(globalsPath);
            JsonObject object = new JsonObject();
            object.add("config", new JsonPrimitive(currentConfig));

            JsonArray friendsArray = new JsonArray();
            LinkenVisuals.getInstance().getFriendManager().getFriends().forEach(friendsArray::add);
            object.add("friends", friendsArray);

            UserSyncManager syncManager = LinkenVisuals.getInstance().getUserSyncManager();
            if (syncManager != null) {
                JsonObject syncObject = new JsonObject();
                syncObject.addProperty("enabled", syncManager.isEnabled());
                syncObject.addProperty("url", syncManager.getEndpointUrl());
                syncObject.addProperty("apiKey", syncManager.getApiKey());
                object.add("sync", syncObject);
            }

            CrashLogTelegramManager crashLogTelegramManager = LinkenVisuals.getInstance().getCrashLogTelegramManager();
            if (crashLogTelegramManager != null) {
                JsonObject crashTelegramObject = new JsonObject();
                crashTelegramObject.addProperty("enabled", crashLogTelegramManager.isEnabled());
                crashTelegramObject.addProperty("botToken", crashLogTelegramManager.getBotToken());
                crashTelegramObject.addProperty("chatId", crashLogTelegramManager.getChatId());
                object.add("crashTelegram", crashTelegramObject);
            }

            UsageStatsManager usageStatsManager = LinkenVisuals.getInstance().getUsageStatsManager();
            if (usageStatsManager != null) {
                JsonObject usageStatsObject = new JsonObject();
                usageStatsObject.addProperty("enabled", usageStatsManager.isEnabled());
                usageStatsObject.addProperty("endpoint", usageStatsManager.getEndpointUrl());
                object.add("usageStats", usageStatsObject);
            }

            CosmeticsService cosmeticsService = LinkenVisuals.getInstance().getCosmeticsService();
            if (cosmeticsService != null) {
                object.add("cosmetics", cosmeticsService.repository().localProfileJson());
            }

            @Cleanup OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(globalsPath), StandardCharsets.UTF_8);
            writer.write(new GsonBuilder().setPrettyPrinting().create()
                    .toJson(JsonParser.parseString(object.toString())));

            if (verbose) {
                LinkenVisuals.LOGGER.info("[ConfigManager] Globals saved successfully");
            }
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[ConfigManager] Error saving globals:", e);
            throw e;
        }
    }

    public void loadGlobals() throws Exception {
        try {
            String globalsPath = resolveExistingConfigPath(LinkenVisuals.getInstance().getGlobalsDir(), "globals");

            if (globalsPath == null) {
                LinkenVisuals.LOGGER.warn("[ConfigManager] Globals file not found: globals" + extension);
                return;
            }

            LinkenVisuals.LOGGER.info("[ConfigManager] Loading globals: " + globalsPath);

            JsonObject object;
            try (InputStream stream = Files.newInputStream(Paths.get(globalsPath));
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                object = JsonParser.parseReader(reader).getAsJsonObject();
            }

            if (object.has("config")) currentConfig = object.get("config").getAsString();

            if (object.has("friends")) {
                for (JsonElement element : object.get("friends").getAsJsonArray()) {
                    if (LinkenVisuals.getInstance().getFriendManager().isFriend(element.getAsString())) continue;
                    LinkenVisuals.getInstance().getFriendManager().add(element.getAsString());
                }
            }

            UserSyncManager syncManager = LinkenVisuals.getInstance().getUserSyncManager();
            if (syncManager != null) {
                boolean enabled = syncManager.isEnabled();
                String url = syncManager.getEndpointUrl();
                String apiKey = syncManager.getApiKey();
                if (object.has("sync") && object.get("sync").isJsonObject()) {
                    JsonObject syncObject = object.getAsJsonObject("sync");
                    if (syncObject.has("enabled")) {
                        enabled = syncObject.get("enabled").getAsBoolean();
                    }
                    if (syncObject.has("url")) {
                        url = syncObject.get("url").getAsString();
                    }
                    if (syncObject.has("apiKey")) {
                        apiKey = syncObject.get("apiKey").getAsString();
                    }
                }
                syncManager.applySettings(enabled, url, apiKey);
            }

            CrashLogTelegramManager crashLogTelegramManager = LinkenVisuals.getInstance().getCrashLogTelegramManager();
            if (crashLogTelegramManager != null) {
                boolean crashEnabled = crashLogTelegramManager.isEnabled();
                String crashBotToken = crashLogTelegramManager.getBotToken();
                String crashChatId = crashLogTelegramManager.getChatId();
                if (object.has("crashTelegram") && object.get("crashTelegram").isJsonObject()) {
                    JsonObject crashObject = object.getAsJsonObject("crashTelegram");
                    if (crashObject.has("enabled")) {
                        crashEnabled = crashObject.get("enabled").getAsBoolean();
                    }
                    if (crashObject.has("botToken")) {
                        crashBotToken = crashObject.get("botToken").getAsString();
                    }
                    if (crashObject.has("chatId")) {
                        crashChatId = crashObject.get("chatId").getAsString();
                    }
                }
                crashLogTelegramManager.applySettings(crashEnabled, crashBotToken, crashChatId);
            }

            UsageStatsManager usageStatsManager = LinkenVisuals.getInstance().getUsageStatsManager();
            if (usageStatsManager != null) {
                boolean statsEnabled = usageStatsManager.isEnabled();
                String statsEndpoint = usageStatsManager.getEndpointUrl();
                if (object.has("usageStats") && object.get("usageStats").isJsonObject()) {
                    JsonObject usageStatsObject = object.getAsJsonObject("usageStats");
                    if (usageStatsObject.has("enabled")) {
                        statsEnabled = usageStatsObject.get("enabled").getAsBoolean();
                    }
                    if (usageStatsObject.has("endpoint")) {
                        statsEndpoint = usageStatsObject.get("endpoint").getAsString();
                    }
                }
                usageStatsManager.applySettings(statsEnabled, statsEndpoint);
            }

            CosmeticsService cosmeticsService = LinkenVisuals.getInstance().getCosmeticsService();
            if (cosmeticsService != null && object.has("cosmetics")) {
                cosmeticsService.repository().loadLocal(object.get("cosmetics"));
            }

            LinkenVisuals.LOGGER.info("[ConfigManager] Globals loaded successfully");
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[ConfigManager] Error loading globals:", e);
            throw e;
        }
    }

    public void saveTheme(String theme) throws Exception {
        String normalizedTheme = normalizeThemeName(theme);
        File themesDir = LinkenVisuals.getInstance().getThemesDir();
        if (!themesDir.exists()) {
            themesDir.mkdirs();
        }

        Module ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        if (ui == null) {
            throw new IllegalStateException("UI module is not available");
        }

        JsonObject object = new JsonObject();
        object.addProperty("theme", normalizedTheme);
        object.addProperty("formatVersion", 1);
        JsonObject settings = new JsonObject();
        for (Setting<?> setting : ui.getSettings()) {
            JsonElement value = serializeSetting(setting);
            if (value != null) {
                settings.add(setting.getName(), value);
            }
        }
        object.add("settings", settings);

        File target = new File(themesDir, normalizedTheme + THEME_EXTENSION);
        FileUtils.reset(target.getAbsolutePath());
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(object));
        }
    }

    public boolean loadTheme(String theme) throws Exception {
        String normalizedTheme = normalizeThemeName(theme);
        File target = resolveThemeFile(normalizedTheme);
        if (target == null || !target.exists()) {
            return false;
        }

        Module ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        if (ui == null) {
            throw new IllegalStateException("UI module is not available");
        }

        JsonObject object;
        try (InputStream stream = Files.newInputStream(target.toPath());
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            object = JsonParser.parseReader(reader).getAsJsonObject();
        }

        if (!object.has("settings") || !object.get("settings").isJsonObject()) {
            return false;
        }

        JsonObject settings = object.getAsJsonObject("settings");
        for (Setting<?> setting : ui.getSettings()) {
            if (settings.has(setting.getName())) {
                deserializeSetting(setting, settings.get(setting.getName()));
            }
        }
        forceSave();
        return true;
    }

    public boolean removeTheme(String theme) throws IOException {
        String normalizedTheme = normalizeThemeName(theme);
        File target = resolveThemeFile(normalizedTheme);
        return target != null && Files.deleteIfExists(target.toPath());
    }

    public File[] listThemeFiles() {
        File themesDir = LinkenVisuals.getInstance().getThemesDir();
        if (!themesDir.exists()) {
            themesDir.mkdirs();
        }
        return themesDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(THEME_EXTENSION));
    }

    public File resolveThemeFile(String theme) {
        File themesDir = LinkenVisuals.getInstance().getThemesDir();
        File target = new File(themesDir, normalizeThemeName(theme) + THEME_EXTENSION);
        return target.exists() ? target : null;
    }

    private void persistDefaultConfig() {
        try {
            writeConfigFile("default", false, false, false);
            LinkenVisuals.LOGGER.info("[ConfigManager] Synced default config with the last used config");
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[ConfigManager] Failed to sync default config:", e);
        }
    }

    private void writeConfigFile(String config, boolean updateCurrent, boolean verbose, boolean stampAuthor) throws Exception {
        String configPath = LinkenVisuals.getInstance().getConfigsDir() + "/" + config + extension;
        if (verbose) {
            LinkenVisuals.LOGGER.info("[ConfigManager] Saving config: " + configPath);
        }

        // Автор конфига: при ЯВНОМ сохранении/обновлении (кнопки/команда) — ник текущего игрока;
        // при автосейве — сохраняем уже записанного автора (иначе чужой конфиг перепишется на тебя).
        String author = stampAuthor ? currentNick() : getConfigAuthor(config);

        FileUtils.reset(configPath);
        JsonObject object = new JsonObject();
        object.add("config", new JsonPrimitive(config));
        object.add("formatVersion", new JsonPrimitive(CONFIG_FORMAT_VERSION));
        if (author != null && !author.isBlank()) object.addProperty("author", author);
        object.addProperty("linkGuiTheme", LinkGuiThemeManager.getSelected().getName());
        object.add("modules", serializeModules());
        object.add("hudOrder", serializeHudOrder());
        authorCache.put(config, author == null ? "" : author);

        @Cleanup OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(configPath), StandardCharsets.UTF_8);
        writer.write(new GsonBuilder().setPrettyPrinting().create()
                .toJson(JsonParser.parseString(object.toString())));

        if (updateCurrent) {
            this.currentConfig = config;
        }
        if (verbose) {
            LinkenVisuals.LOGGER.info("[ConfigManager] Config saved: " + config);
        }
    }

    private String resolveExistingConfigPath(File directory, String configName) {
        String normalized = normalizeConfigName(configName);
        String currentPath = directory + "/" + normalized + extension;
        if (FileUtils.exists(currentPath)) {
            return currentPath;
        }

        String legacyPath = directory + "/" + normalized + LEGACY_CONFIG_EXTENSION;
        if (FileUtils.exists(legacyPath)) {
            return legacyPath;
        }

        return null;
    }

    private void scheduleAutoSave() {
        boolean saveNow = false;
        synchronized (autoSaveLock) {
            long now = System.currentTimeMillis();
            long sinceLastSave = now - lastAutoSaveAt;
            if (!autoSaveWorkerScheduled && sinceLastSave >= AUTO_SAVE_COOLDOWN_MS) {
                lastAutoSaveAt = now;
                saveNow = true;
            } else {
                autoSavePending = true;
                if (!autoSaveWorkerScheduled) {
                    autoSaveWorkerScheduled = true;
                    long delay = Math.max(0L, AUTO_SAVE_COOLDOWN_MS - sinceLastSave);
                    startAutoSaveWorker(delay);
                }
            }
        }

        if (saveNow) {
            performAutoSave();
        }
    }

    private void startAutoSaveWorker(long initialDelayMs) {
        Thread worker = new Thread(() -> {
            long delay = initialDelayMs;
            while (true) {
                if (delay > 0L) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        synchronized (autoSaveLock) {
                            autoSaveWorkerScheduled = false;
                        }
                        return;
                    }
                }

                synchronized (autoSaveLock) {
                    if (!autoSavePending) {
                        autoSaveWorkerScheduled = false;
                        return;
                    }
                    autoSavePending = false;
                    lastAutoSaveAt = System.currentTimeMillis();
                }

                performAutoSave();

                synchronized (autoSaveLock) {
                    if (!autoSavePending) {
                        autoSaveWorkerScheduled = false;
                        return;
                    }
                    long now = System.currentTimeMillis();
                    delay = Math.max(0L, AUTO_SAVE_COOLDOWN_MS - (now - lastAutoSaveAt));
                }
            }
        }, "LinkenVisuals-ConfigAutoSave");
        worker.setDaemon(true);
        worker.start();
    }

    private void performAutoSave() {
        try {
            saveAll(false);
        } catch (Exception e) {
            LinkenVisuals.LOGGER.error("[ConfigManager] Error during auto-save:", e);
        }
    }

    private boolean shouldIncludeSettingInConfig(Module module, Setting<?> setting) {
        return !(module instanceof UI && setting instanceof ColorSetting);
    }

    private void resetSettingForConfig(Module module, Setting<?> setting) {
        if (shouldIncludeSettingInConfig(module, setting)) {
            setting.reset();
        }
    }

    private JsonObject serializeModules() {
        JsonObject modules = new JsonObject();

        for (Module module : LinkenVisuals.getInstance().getModuleManager().getModules()) {
            JsonObject object = new JsonObject();
            object.add("toggled", new JsonPrimitive(module.isToggled()));
            object.add("bind", new JsonPrimitive(module.getBind().getKey() + ", " + module.getBind().isMouse()));

            JsonObject settings = new JsonObject();
            for (Setting<?> s : module.getSettings()) {
                if (!shouldIncludeSettingInConfig(module, s)) {
                    continue;
                }
                if (s instanceof BooleanSetting) settings.add(s.getName(), new JsonPrimitive((Boolean) s.getValue()));
                else if (s instanceof NumberSetting) settings.add(s.getName(), new JsonPrimitive((Float) s.getValue()));
                else if (s instanceof StringSetting) settings.add(s.getName(), new JsonPrimitive((String) s.getValue()));
                else if (s instanceof EnumSetting<?> enums) settings.add(s.getName(), new JsonPrimitive(((Nameable) enums.getValue()).getName()));
                else if (s instanceof ColorSetting color) {
                    Color value = color.getValue();
                    String serialized = value.getRed() + ", " + value.getGreen() + ", " + value.getBlue() + ", " + value.getAlpha();
                    settings.add(s.getName(), new JsonPrimitive(serialized));
                }
                else if (s instanceof BindSetting bind) settings.add(s.getName(), new JsonPrimitive(bind.getValue().getKey() + ", " + bind.getValue().isMouse()));
                else if (s instanceof ListSetting list) {
                    JsonObject list2 = new JsonObject();
                    for (BooleanSetting setting : list.getValue()) list2.add(setting.getName(), new JsonPrimitive(setting.getValue()));
                    settings.add(list.getName(), list2);
                }
            }

            object.add("settings", settings);
            modules.add(module.getName(), object);
        }

        for (HudElement element : LinkenVisuals.getInstance().getHudManager().getHudElements()) {
            JsonObject object = new JsonObject();
            object.add("toggled", new JsonPrimitive(element.isToggled()));

            JsonObject settings = new JsonObject();
            for (Setting<?> s : element.getSettings()) {
                if (s instanceof BooleanSetting) {
                    settings.add(s.getName(), new JsonPrimitive((Boolean) s.getValue()));
                } else if (s instanceof PositionSetting pos) {
                    settings.add(s.getName(), new JsonPrimitive(pos.getValue().getX() + ", " + pos.getValue().getY()));
                } else if (s instanceof NumberSetting) {
                    settings.add(s.getName(), new JsonPrimitive((Float) s.getValue()));
                } else if (s instanceof StringSetting) {
                    settings.add(s.getName(), new JsonPrimitive((String) s.getValue()));
                } else if (s instanceof EnumSetting<?> enums) {
                    settings.add(s.getName(), new JsonPrimitive(((Nameable) enums.getValue()).getName()));
                } else if (s instanceof ColorSetting color) {
                    Color value = color.getValue();
                    String serialized = value.getRed() + ", " + value.getGreen() + ", " + value.getBlue() + ", " + value.getAlpha();
                    settings.add(s.getName(), new JsonPrimitive(serialized));
                } else if (s instanceof ListSetting list) {
                    JsonObject list2 = new JsonObject();
                    for (BooleanSetting setting : list.getValue()) {
                        list2.add(setting.getName(), new JsonPrimitive(setting.getValue()));
                    }
                    settings.add(list.getName(), list2);
                }
            }
            object.add("settings", settings);
            modules.add("hud_" + element.getName(), object);
        }

        return modules;
    }

    private JsonArray serializeHudOrder() {
        JsonArray order = new JsonArray();
        for (HudElement element : LinkenVisuals.getInstance().getHudManager().getHudElements()) {
            order.add(element.getName());
        }
        return order;
    }

    private void deserializeHudOrder(JsonArray order) {
        if (order == null || LinkenVisuals.getInstance().getHudManager() == null) return;
        java.util.List<HudElement> elements = LinkenVisuals.getInstance().getHudManager().getHudElements();
        java.util.List<HudElement> reordered = new java.util.ArrayList<>();
        for (JsonElement entry : order) {
            String name = entry.getAsString();
            for (HudElement element : elements) {
                if (element.getName().equalsIgnoreCase(name) && !reordered.contains(element)) {
                    reordered.add(element);
                    break;
                }
            }
        }
        for (HudElement element : elements) {
            if (!reordered.contains(element)) {
                reordered.add(element);
            }
        }
        elements.clear();
        elements.addAll(reordered);
    }

    private JsonElement serializeSetting(Setting<?> setting) {
        if (setting instanceof BooleanSetting) return new JsonPrimitive((Boolean) setting.getValue());
        if (setting instanceof NumberSetting) return new JsonPrimitive((Float) setting.getValue());
        if (setting instanceof StringSetting) return new JsonPrimitive((String) setting.getValue());
        if (setting instanceof EnumSetting<?> enums) return new JsonPrimitive(((Nameable) enums.getValue()).getName());
        if (setting instanceof ColorSetting color) {
            Color value = color.getValue();
            return new JsonPrimitive(value.getRed() + ", " + value.getGreen() + ", " + value.getBlue() + ", " + value.getAlpha());
        }
        if (setting instanceof BindSetting bind) return new JsonPrimitive(bind.getValue().getKey() + ", " + bind.getValue().isMouse());
        if (setting instanceof PositionSetting pos) return new JsonPrimitive(pos.getValue().getX() + ", " + pos.getValue().getY());
        if (setting instanceof ListSetting list) {
            JsonObject object = new JsonObject();
            for (BooleanSetting booleanSetting : list.getValue()) {
                object.add(booleanSetting.getName(), new JsonPrimitive(booleanSetting.getValue()));
            }
            return object;
        }
        return null;
    }

    private void deserializeSetting(Setting<?> setting, JsonElement value) {
        try {
            if (setting instanceof BooleanSetting booleanSetting) {
                booleanSetting.setValue(value.getAsBoolean());
            } else if (setting instanceof NumberSetting numberSetting) {
                numberSetting.setValue(value.getAsFloat());
            } else if (setting instanceof StringSetting stringSetting) {
                stringSetting.setValue(value.getAsString());
            } else if (setting instanceof EnumSetting<?> enumSetting) {
                enumSetting.setEnumValue(value.getAsString());
            } else if (setting instanceof ColorSetting colorSetting) {
                String[] data = value.getAsString().split(", ");
                if (data.length >= 3) {
                    int r = Integer.parseInt(data[0]);
                    int g = Integer.parseInt(data[1]);
                    int b = Integer.parseInt(data[2]);
                    int a = data.length > 3 ? Integer.parseInt(data[3]) : 255;
                    colorSetting.setValue(new Color(r, g, b, a));
                }
            } else if (setting instanceof BindSetting bindSetting) {
                String[] data = value.getAsString().split(", ");
                if (data.length == 2) {
                    bindSetting.setValue(new Bind(Integer.parseInt(data[0]), Boolean.parseBoolean(data[1])));
                }
            } else if (setting instanceof PositionSetting positionSetting) {
                String[] data = value.getAsString().split(", ");
                if (data.length == 2) {
                    positionSetting.setValue(new Position(Float.parseFloat(data[0]), Float.parseFloat(data[1])));
                }
            } else if (setting instanceof ListSetting list) {
                JsonObject object = value.getAsJsonObject();
                for (BooleanSetting booleanSetting : list.getValue()) {
                    if (object.has(booleanSetting.getName())) {
                        booleanSetting.setValue(object.get(booleanSetting.getName()).getAsBoolean());
                    }
                }
            }
        } catch (Exception exception) {
            LinkenVisuals.LOGGER.warn("[ConfigManager] Failed to load setting {}", setting.getName(), exception);
        }
    }

    private void deserializeModules(JsonObject modules) {
        for (Module module : LinkenVisuals.getInstance().getModuleManager().getModules()) {
            JsonObject object = resolveModuleConfig(modules, module);
            if (object == null) {
                if (!(module instanceof UI)) {
                    // Cosmetics включён по умолчанию (иначе дефолтный плащ не виден никому); остальные — выкл.
                    module.setToggled(module instanceof ru.method.linkenvisuals.modules.impl.player.CosmeticsModule);
                } else {
                    ((UI) module).ensureDefaultBind();
                }
                for (Setting<?> setting : module.getSettings()) resetSettingForConfig(module, setting);
                continue;
            }

            boolean toggledState = object.has("toggled")
                    ? object.get("toggled").getAsBoolean()
                    : module instanceof ru.method.linkenvisuals.modules.impl.player.CosmeticsModule; // нет ключа → Cosmetics вкл.
            if (module instanceof DamageParticles) {
                JsonObject migratedParticles = getModuleObject(modules, "Particles");
                if (hasLegacyParticlesSettings(migratedParticles)) {
                    toggledState = toggledState || (migratedParticles.has("toggled") && migratedParticles.get("toggled").getAsBoolean());
                }
            }
            if (!(module instanceof UI)) module.setToggled(toggledState);

            if (object.has("bind")) {
                // Битый бинд не должен прерывать загрузку всего конфига — логируем и пропускаем.
                try {
                    String[] data = object.get("bind").getAsString().split("\\s*,\\s*");
                    if (data.length == 2) {
                        int key = Integer.parseInt(data[0]);
                        boolean mouse = Boolean.parseBoolean(data[1]);
                        module.setBind(new Bind(key, mouse));
                    }
                } catch (Exception exception) {
                    LinkenVisuals.LOGGER.warn("[ConfigManager] Failed to load bind for module {}", module.getName(), exception);
                }
            }
            if (module instanceof UI uiModule) {
                uiModule.ensureDefaultBind();
            }

            JsonObject settings = getSettingsObject(object);
            JsonObject fallbackSettings = resolveFallbackSettings(modules, module);

            if (settings == null && fallbackSettings == null) {
                for (Setting<?> setting : module.getSettings()) resetSettingForConfig(module, setting);
                continue;
            }

            for (Setting<?> s : module.getSettings()) {
                if (!shouldIncludeSettingInConfig(module, s)) {
                    continue;
                }
                JsonElement element = getSettingElement(settings, fallbackSettings, s.getName());
                if (element == null) {
                    s.reset();
                    continue;
                }
                // Каждую настройку обрабатываем в своём try/catch: одно битое значение
                // логируется и пропускается (настройка сбрасывается), остальной конфиг грузится дальше.
                try {
                    if (s instanceof BooleanSetting) ((BooleanSetting) s).setValue(element.getAsBoolean());
                    else if (s instanceof NumberSetting numberSetting) {
                        float value = element.getAsFloat();
                        // Сохраняем миграцию формата Zoom (formatVersion < 2).
                        if (loadedConfigFormatVersion < 2 && module instanceof Zoom) {
                            if ("settings.zoom.multiplier".equals(s.getName())) {
                                value = clampFloat(1.05f - value, 0.05f, 1.0f);
                            } else if ("settings.zoom.smooth_speed".equals(s.getName())) {
                                value = clampFloat(1.01f - value, 0.01f, 1.0f);
                            }
                        }
                        numberSetting.setValue(value);
                    }
                    else if (s instanceof EnumSetting<?>) ((EnumSetting<?>) s).setEnumValue(element.getAsString());
                    else if (s instanceof ColorSetting colorSetting) {
                        String[] data = element.getAsString().split(", ");
                        if (data.length >= 3) {
                            int r = Integer.parseInt(data[0]);
                            int g = Integer.parseInt(data[1]);
                            int b = Integer.parseInt(data[2]);
                            int a = data.length > 3 ? Integer.parseInt(data[3]) : 255;
                            colorSetting.setValue(new Color(r, g, b, a));
                        }
                    }
                    else if (s instanceof StringSetting) ((StringSetting) s).setValue(element.getAsString());
                    else if (s instanceof BindSetting) {
                        String[] data = element.getAsString().split(", ");
                        if (data.length == 2) {
                            int key = Integer.parseInt(data[0]);
                            boolean mouse = Boolean.parseBoolean(data[1]);
                            ((BindSetting) s).setValue(new Bind(key, mouse));
                        }
                    } else if (s instanceof ListSetting list) {
                        JsonObject list2 = element.getAsJsonObject();
                        for (BooleanSetting setting : list.getValue()) {
                            if (list2.has(setting.getName())) {
                                setting.setValue(list2.get(setting.getName()).getAsBoolean());
                            }
                        }
                    }
                } catch (Exception exception) {
                    LinkenVisuals.LOGGER.warn("[ConfigManager] Failed to load setting {} for module {}", s.getName(), module.getName(), exception);
                    s.reset();
                }
            }
        }

        for (HudElement element : LinkenVisuals.getInstance().getHudManager().getHudElements()) {
            String hudKey = "hud_" + element.getName();

            if (!modules.has(hudKey)) {
                element.setToggled(false);
                for (Setting<?> setting : element.getSettings()) {
                    setting.reset();
                }
                continue;
            }

            JsonObject object = modules.get(hudKey).getAsJsonObject();

            element.setToggled(object.has("toggled") && object.get("toggled").getAsBoolean());

            if (!object.has("settings")) {
                for (Setting<?> setting : element.getSettings()) {
                    setting.reset();
                }
                continue;
            }

            JsonObject settings = object.get("settings").getAsJsonObject();
            for (Setting<?> s : element.getSettings()) {
                if (!settings.has(s.getName())) {
                    s.reset();
                    continue;
                }

                JsonElement element2 = settings.get(s.getName());
                try {
                    if (s instanceof BooleanSetting) {
                        ((BooleanSetting) s).setValue(element2.getAsBoolean());
                    } else if (s instanceof PositionSetting) {
                        String[] data = element2.getAsString().split(", ");
                        if (data.length == 2) {
                            float x = Float.parseFloat(data[0]);
                            float y = Float.parseFloat(data[1]);
                            ((PositionSetting) s).setValue(new Position(x, y));
                        }
                    } else if (s instanceof NumberSetting) {
                        ((NumberSetting) s).setValue(element2.getAsFloat());
                    } else if (s instanceof StringSetting) {
                        ((StringSetting) s).setValue(element2.getAsString());
                    } else if (s instanceof EnumSetting<?>) {
                        ((EnumSetting<?>) s).setEnumValue(element2.getAsString());
                    } else if (s instanceof ColorSetting colorSetting) {
                        String[] data = element2.getAsString().split(", ");
                        if (data.length >= 3) {
                            int r = Integer.parseInt(data[0]);
                            int g = Integer.parseInt(data[1]);
                            int b = Integer.parseInt(data[2]);
                            int a = data.length > 3 ? Integer.parseInt(data[3]) : 255;
                            colorSetting.setValue(new Color(r, g, b, a));
                        }
                    } else if (s instanceof ListSetting list) {
                        JsonObject list2 = element2.getAsJsonObject();
                        for (BooleanSetting setting : list.getValue()) {
                            if (list2.has(setting.getName())) {
                                setting.setValue(list2.get(setting.getName()).getAsBoolean());
                            }
                        }
                    }
                } catch (Exception e) {
                    LinkenVisuals.LOGGER.warn("[ConfigManager] Error loading setting " + s.getName() + " for " + element.getName() + ": " + e.getMessage());
                    s.reset();
                }
            }
        }
    }

    private JsonObject resolveModuleConfig(JsonObject modules, Module module) {
        if (module instanceof DamageParticles) {
            JsonObject damage = getModuleObject(modules, "DamageParticles");
            if (damage != null) {
                return damage;
            }

            JsonObject particles = getModuleObject(modules, "Particles");
            return hasLegacyParticlesSettings(particles) ? particles : null;
        }

        if (module instanceof Particles) {
            return getModuleObject(modules, "Particles");
        }

        return getModuleObject(modules, module.getName());
    }

    private JsonObject resolveFallbackSettings(JsonObject modules, Module module) {
        if (module instanceof DamageParticles) {
            JsonObject particles = getModuleObject(modules, "Particles");
            return hasLegacyParticlesSettings(particles) ? getSettingsObject(particles) : null;
        }

        if (module instanceof Particles) {
            return null;
        }

        return null;
    }

    private JsonObject getModuleObject(JsonObject modules, String name) {
        if (modules == null || name == null || !modules.has(name)) {
            return null;
        }
        return modules.get(name).getAsJsonObject();
    }

    private JsonObject getSettingsObject(JsonObject moduleObject) {
        if (moduleObject == null || !moduleObject.has("settings")) {
            return null;
        }
        return moduleObject.get("settings").getAsJsonObject();
    }

    private JsonElement getSettingElement(JsonObject primarySettings, JsonObject fallbackSettings, String settingName) {
        if (primarySettings != null && primarySettings.has(settingName)) {
            return primarySettings.get(settingName);
        }
        if (fallbackSettings != null && fallbackSettings.has(settingName)) {
            return fallbackSettings.get(settingName);
        }
        return null;
    }

    private boolean hasLegacyParticlesSettings(JsonObject moduleObject) {
        JsonObject settings = getSettingsObject(moduleObject);
        return settings != null && settings.has("settings.damageparticles.count");
    }

    private float clampFloat(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
