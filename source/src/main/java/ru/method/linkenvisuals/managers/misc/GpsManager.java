package ru.method.linkenvisuals.managers.misc;


import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.gps.Gps;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import lombok.Getter;
import ru.method.linkenvisuals.modules.api.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;


public class GpsManager implements Wrapper {

    public GpsManager() {
        LinkenVisuals.getInstance().getEventHandler().subscribe(this);
    }

    @Getter private final List<Gps> gpsPoints = new ArrayList<>();

    public void add(Gps gps) {
        clear();
        gpsPoints.add(gps);
    }

    public void remove(Gps gps) {
        gpsPoints.remove(gps);
    }

    public void clear() {
        if (!gpsPoints.isEmpty()) gpsPoints.clear();
    }

    public boolean isEmpty() {
        return gpsPoints.isEmpty();
    }

    public Gps getGps(int x, int z) {
        for (Gps gps : gpsPoints) {
            if (gps.getX() == x && gps.getZ() == z) {
                return gps;
            }
        }
        return null;
    }

    public Gps getFirst() {
        return gpsPoints.isEmpty() ? null : gpsPoints.getFirst();
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        if (Module.fullNullCheck()) return;

        float x = mc.getWindow().getScaledWidth() / 2f;
        float y = mc.getWindow().getScaledHeight() / 4f + 35f;
        float size = 16f;
        float yOffset = 0f;
        for (Gps gps : gpsPoints) {
            e.getContext().getMatrices().pushMatrix();
            double x2 = gps.getX() - mc.player.getX();
            double z2 = gps.getZ() - mc.player.getZ();
            float distance = MathUtils.round(MathHelper.sqrt((float) (x2 * x2 + z2 * z2)));
            float yaw = (float) -(Math.atan2(x2, z2) * (180 / Math.PI)) - mc.gameRenderer.getCamera().getYaw();

            Render2D.drawFont(
                    e.getContext().getMatrices(),
                    Fonts.ROUNDBOLD.getFont(7f),
                    distance + "m",
                    x - Fonts.ROUNDBOLD.getWidth(distance + "m", 7f) / 2f,
                    y + yOffset + 6f,
                    Color.WHITE
            );

            e.getContext().getMatrices().translate(x, y + yOffset);
            e.getContext().getMatrices().rotate((float) Math.toRadians(yaw));
            e.getContext().getMatrices().translate(-x, -(y + yOffset));
            Render2D.drawTexture(e.getContext().getMatrices(), x - size / 2f, y - size / 2f, size, size, 5, LinkenVisuals.id("client/gps/arrow.png"), Color.WHITE);
            e.getContext().getMatrices().popMatrix();
            yOffset += 30f;
        }
    }
}
