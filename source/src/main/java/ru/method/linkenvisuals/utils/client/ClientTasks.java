package ru.method.linkenvisuals.utils.client;

import meteordevelopment.orbit.EventHandler;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventTick;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs callbacks on the client thread after a number of game ticks.
 * Replaces arbitrary {@code Thread.sleep} delays that race with {@code mc} state.
 */
public final class ClientTasks {

    private static final CopyOnWriteArrayList<DelayedTask> TASKS = new CopyOnWriteArrayList<>();
    private static ClientTasks instance;

    private ClientTasks() {
    }

    public static void register() {
        if (instance != null) {
            return;
        }
        instance = new ClientTasks();
        LinkenVisuals.getInstance().getEventHandler().subscribe(instance);
    }

    public static void schedule(int ticks, Runnable action) {
        if (action == null) {
            return;
        }
        if (ticks <= 0) {
            action.run();
            return;
        }
        TASKS.add(new DelayedTask(ticks, action));
    }

    @EventHandler
    private void onTick(EventTick event) {
        Iterator<DelayedTask> iterator = TASKS.iterator();
        while (iterator.hasNext()) {
            DelayedTask task = iterator.next();
            if (--task.ticksRemaining > 0) {
                continue;
            }
            TASKS.remove(task);
            try {
                task.action.run();
            } catch (Exception exception) {
                LinkenVisuals.LOGGER.warn("[ClientTasks] Scheduled task failed", exception);
            }
        }
    }

    private static final class DelayedTask {
        private int ticksRemaining;
        private final Runnable action;

        private DelayedTask(int ticksRemaining, Runnable action) {
            this.ticksRemaining = ticksRemaining;
            this.action = action;
        }
    }
}
