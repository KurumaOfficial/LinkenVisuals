package ru.method.linkenvisuals.hud.impl.island;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.hud.impl.DynamicIsland;
import ru.method.linkenvisuals.managers.system.HudManager;
import lombok.Getter;

import java.awt.Color;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Getter

public class DynamicIslandNotificationManager {
    private static final DynamicIslandNotificationManager INSTANCE = new DynamicIslandNotificationManager();
    private final Queue<DynamicIslandNotification> notificationQueue = new ConcurrentLinkedQueue<>();
    private DynamicIslandNotification currentNotification;

    public static DynamicIslandNotificationManager getInstance() {
        return INSTANCE;
    }

    public void addNotification(String moduleName, boolean enabled) {
        if (!isIslandActive()) return;
        currentNotification = new DynamicIslandNotification(moduleName, enabled, 1000);
        notificationQueue.clear();
    }

    public void addInfoNotification(String message) {
        if (!isIslandActive()) return;
        notificationQueue.offer(new DynamicIslandNotification(null, true, 1400, message));
    }

    public void addStatusNotification(String message, Color statusColor, long duration) {
        if (!isIslandActive() || message == null || message.isBlank()) return;
        if (currentNotification != null
                && !currentNotification.isExpired()
                && message.equals(currentNotification.getDisplayText())) {
            return;
        }
        notificationQueue.removeIf(notification -> message.equals(notification.getDisplayText()));
        notificationQueue.offer(new DynamicIslandNotification(null, true, duration, message, statusColor));
    }

    public void clearAll() {
        currentNotification = null;
        notificationQueue.clear();
    }

    public void onTick() {
        if (!isIslandActive()) {
            clearAll();
            return;
        }

        if (currentNotification != null && currentNotification.isExpired()) {
            currentNotification = null;
        }

        if (currentNotification == null && !notificationQueue.isEmpty()) {
            currentNotification = notificationQueue.poll();
        }
    }

    private boolean isIslandActive() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) return false;
        HudManager hudManager = instance.getHudManager();
        if (hudManager == null) return false;
        DynamicIsland island = hudManager.getHudElement(DynamicIsland.class);
        return island != null && island.isToggled();
    }

}
