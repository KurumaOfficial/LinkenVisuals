package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.managers.misc.UserSyncManager;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.chat.ChatUtils;

public class PartyModule extends Module {

    private static final double MARKER_RANGE = 2048.0;

    private final BindSetting markerBind = new BindSetting("settings.party.marker_bind", new Bind(-1, false));
    private final NumberSetting markerLifetime = new NumberSetting("settings.party.marker_lifetime", 60f, 30f, 120f, 1f);
    public PartyModule() {
        super("Party", Category.Other);
    }

    public long getMarkerLifetimeMs() {
        return (long) (markerLifetime.getValue() * 1000f);
    }

    @EventHandler
    public void onKey(EventKey e) {
        if (fullNullCheck() || mc.currentScreen != null || LinkenVisuals.getInstance().isPanic()) return;
        if (e.getAction() != 1) return;
        if (markerBind.getValue().matchesKey(e.getKey())) {
            placeMarker();
        }
    }

    @EventHandler
    public void onMouse(EventMouse e) {
        if (fullNullCheck() || mc.currentScreen != null || LinkenVisuals.getInstance().isPanic()) return;
        if (e.getAction() != 1) return;
        if (markerBind.getValue().matchesMouse(e.getButton())) {
            placeMarker();
        }
    }

    private void placeMarker() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        UserSyncManager syncManager = instance == null ? null : instance.getUserSyncManager();
        if (syncManager == null || !syncManager.isEnabled() || syncManager.getEndpointUrl().isBlank()) {
            ChatUtils.sendMessage(I18n.translate("commands.party.unavailable"));
            return;
        }
        if (!syncManager.isInParty()) {
            ChatUtils.sendMessage(I18n.translate("commands.party.not_in_party"));
            return;
        }
        Vec3d target = resolveMarkerPosition();
        if (target == null) return;
        syncManager.sendPartyMarker(target);
    }

    private Vec3d resolveMarkerPosition() {
        if (mc.player == null || mc.world == null) return null;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d target = eye.add(look.multiply(MARKER_RANGE));
        RaycastContext context = new RaycastContext(
                eye,
                target,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );
        BlockHitResult hit = mc.world.raycast(context);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        return hit.getPos();
    }
}
