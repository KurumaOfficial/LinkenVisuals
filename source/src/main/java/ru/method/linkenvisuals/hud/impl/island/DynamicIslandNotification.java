package ru.method.linkenvisuals.hud.impl.island;


import java.awt.Color;
import lombok.Getter;

@Getter

public class DynamicIslandNotification {
    private final String moduleName;
    private final boolean enabled;
    private final long startTime;
    private final long duration;
    private final String customMessage;
    private final Color statusColor;

    public DynamicIslandNotification(String moduleName, boolean enabled, long duration) {
        this(moduleName, enabled, duration, null, null);
    }

    public DynamicIslandNotification(String moduleName, boolean enabled, long duration, String customMessage) {
        this(moduleName, enabled, duration, customMessage, null);
    }

    public DynamicIslandNotification(String moduleName, boolean enabled, long duration, String customMessage, Color statusColor) {
        this.moduleName = moduleName;
        this.enabled = enabled;
        this.duration = duration;
        this.customMessage = customMessage;
        this.statusColor = statusColor;
        this.startTime = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - startTime > duration;
    }

    public String getStatus() {
        return enabled ? "enabled" : "disabled";
    }

    public String getDisplayText() {
        if (customMessage != null && !customMessage.isEmpty()) {
            return customMessage;
        }
        return (moduleName != null ? moduleName + " " : "") + getStatus();
    }
}
