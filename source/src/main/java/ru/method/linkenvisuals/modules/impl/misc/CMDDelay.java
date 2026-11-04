package ru.method.linkenvisuals.modules.impl.misc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.ActionSetting;
import ru.method.linkenvisuals.modules.settings.impl.StringSetting;
import ru.method.linkenvisuals.screen.cmddelay.CMDDelayScreen;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class CMDDelay extends Module {
    private static final Gson GSON = new Gson();

    private final StringSetting entriesJson = new StringSetting("modules.settings.cmddelay.entries", "[]", "", "", 32767, () -> false, (oldValue, newValue) -> loadEntries());
    private final ActionSetting openSettings = new ActionSetting("modules.settings.cmddelay.open", "modules.settings.cmddelay.open", this::openSettingsScreen);
    private final List<CommandEntry> entries = new ArrayList<>();
    private int tickCounter;
    private boolean internalEntriesUpdate;

    public CMDDelay() {
        super("CMDDelay", Category.Player);
        loadEntries();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        long now = System.currentTimeMillis();
        for (CommandEntry entry : entries) {
            entry.lastRunMs = now;
        }
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck() || ++tickCounter % 10 != 0) return;
        long now = System.currentTimeMillis();
        for (CommandEntry entry : entries) {
            if (entry.command.isBlank() || entry.delaySeconds <= 0) continue;
            long delayMs = Math.max(1L, entry.delaySeconds) * 1000L;
            if (now - entry.lastRunMs < delayMs) continue;
            sendCommand(entry.command);
            entry.lastRunMs = now;
        }
    }

    public List<CommandEntry> getEntries() {
        return entries;
    }

    public void addEntry(String command, int delaySeconds) {
        entries.add(new CommandEntry(normalizeCommand(command), Math.max(1, delaySeconds), System.currentTimeMillis()));
        saveEntries();
    }

    public void updateEntry(int index, String command, int delaySeconds) {
        if (index < 0 || index >= entries.size()) return;
        CommandEntry entry = entries.get(index);
        entry.command = normalizeCommand(command);
        entry.delaySeconds = Math.max(1, delaySeconds);
        entry.lastRunMs = System.currentTimeMillis();
        saveEntries();
    }

    public void removeEntry(int index) {
        if (index < 0 || index >= entries.size()) return;
        entries.remove(index);
        saveEntries();
    }

    public void saveEntries() {
        JsonArray array = new JsonArray();
        for (CommandEntry entry : entries) {
            if (entry.command.isBlank()) continue;
            JsonObject object = new JsonObject();
            object.addProperty("command", entry.command);
            object.addProperty("delay", entry.delaySeconds);
            array.add(object);
        }
        String json = GSON.toJson(array);
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        internalEntriesUpdate = true;
        try {
            entriesJson.setValue(encoded);
        } finally {
            internalEntriesUpdate = false;
        }
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance != null && !instance.isConfigLoading()) {
            instance.getConfigManager().forceSave();
        }
    }

    private void loadEntries() {
        if (internalEntriesUpdate) {
            return;
        }
        entries.clear();
        try {
            JsonElement root = GSON.fromJson(readEntriesPayload(), JsonElement.class);
            if (root == null || !root.isJsonArray()) return;
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String command = object.has("command") ? object.get("command").getAsString() : "";
                int delay = object.has("delay") ? object.get("delay").getAsInt() : 60;
                if (!command.isBlank()) {
                    entries.add(new CommandEntry(normalizeCommand(command), Math.max(1, delay), System.currentTimeMillis()));
                }
            }
        } catch (Exception ignored) {
            entries.clear();
        }
    }

    private String readEntriesPayload() {
        String stored = entriesJson.getValue();
        if (stored == null || stored.isBlank()) {
            return "[]";
        }
        String trimmed = stored.trim();
        if (trimmed.startsWith("[")) {
            return trimmed;
        }
        try {
            return new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return trimmed;
        }
    }

    private void openSettingsScreen() {
        if (mc == null) return;
        mc.setScreen(new CMDDelayScreen(this, mc.currentScreen));
    }

    private void sendCommand(String command) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        String normalized = normalizeCommand(command);
        if (normalized.isBlank()) return;
        mc.player.networkHandler.sendChatCommand(normalized);
    }

    private String normalizeCommand(String command) {
        if (command == null) return "";
        String normalized = command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    public static class CommandEntry {
        private String command;
        private int delaySeconds;
        private long lastRunMs;

        public CommandEntry(String command, int delaySeconds, long lastRunMs) {
            this.command = command;
            this.delaySeconds = delaySeconds;
            this.lastRunMs = lastRunMs;
        }

        public String getCommand() {
            return command;
        }

        public int getDelaySeconds() {
            return delaySeconds;
        }
    }
}
