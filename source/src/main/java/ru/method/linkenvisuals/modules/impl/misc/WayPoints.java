package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.managers.misc.WaypointManager;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.waypoint.Waypoint;

public class WayPoints extends Module {
    private final BindSetting quickWaypointBind = new BindSetting("modules.settings.waypoints.quick_bind", new Bind(-1, false));
    private final BooleanSetting autoWaypointOnEvent = new BooleanSetting("modules.settings.waypoints.auto_event", true);
    private final BooleanSetting autoWaypointOnFtEvent = new BooleanSetting("modules.settings.waypoints.auto_event_ft", true);
    private final BooleanSetting autoWaypointOnDeath = new BooleanSetting("modules.settings.waypoints.auto_death", true);

    private boolean sentDeathWaypoint;
    private int quickIndex = 1;

    public WayPoints() {
        super("WayPoints", Category.Player);
        getSettings().add(quickWaypointBind);
        getSettings().add(autoWaypointOnEvent);
        getSettings().add(autoWaypointOnFtEvent);
        getSettings().add(autoWaypointOnDeath);
    }

    @EventHandler
    private void onKey(EventKey event) {
        if (fullNullCheck() || mc.currentScreen != null || event.getAction() != 1) return;
        if (quickWaypointBind.getValue().matchesKey(event.getKey())) addQuickWaypoint();
    }

    @EventHandler
    private void onMouse(EventMouse event) {
        if (fullNullCheck() || mc.currentScreen != null || event.getAction() != 1) return;
        if (quickWaypointBind.getValue().matchesMouse(event.getButton())) addQuickWaypoint();
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) return;

        WaypointManager manager = LinkenVisuals.getInstance().getWaypointManager();
        if (manager != null) {
            if (autoWaypointOnFtEvent.getValue() && manager.hasPendingFtEventWaypoint()) {
                manager.placeEventWaypoint();
            } else if (autoWaypointOnEvent.getValue() && manager.hasPendingEventWaypoint() && !manager.hasPendingFtEventWaypoint()) {
                manager.placeEventWaypoint();
            }
        }

        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0.0f;
        if (!dead) {
            sentDeathWaypoint = false;
            return;
        }

        if (!autoWaypointOnDeath.getValue() || sentDeathWaypoint) return;
        BlockPos pos = mc.player.getBlockPos();
        addWaypoint(uniqueName("Death"), pos);
        sentDeathWaypoint = true;
    }

    private void addQuickWaypoint() {
        if (fullNullCheck()) return;
        addWaypoint(uniqueName("Way"), lookedAtPos());
    }

    /** Блок, на который смотрит игрок (рейкаст до 128 блоков). Если небо/промах — позиция игрока. */
    private BlockPos lookedAtPos() {
        HitResult hit = mc.player.raycast(128.0, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos();
        }
        return mc.player.getBlockPos();
    }

    private void addWaypoint(String name, BlockPos pos) {
        WaypointManager manager = LinkenVisuals.getInstance().getWaypointManager();
        if (manager == null) return;
        manager.add(new Waypoint(name, pos.getX(), pos.getY(), pos.getZ()));
        ChatUtils.sendMessage("Метка создана: " + name + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    private String uniqueName(String prefix) {
        WaypointManager manager = LinkenVisuals.getInstance().getWaypointManager();
        if (manager == null) return prefix;

        String name;
        do {
            name = prefix + " " + quickIndex++;
        } while (manager.getWaypoint(name) != null);
        return name;
    }
}
