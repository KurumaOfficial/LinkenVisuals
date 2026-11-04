package ru.method.linkenvisuals.modules.impl.render;


import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventPlayerTick;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render3D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class JumpCircles extends Module {

    private static final Identifier CIRCLE_TEXTURE = LinkenVisuals.id("client/other/circle.png");
    private static final float GLOW_SCALE = 1.15f;
    private static final long FADE_IN_MS = 500L;
    private static final long FADE_OUT_MS = 500L;
    private static final long MIN_LIFETIME_MS = 600L;

    private final NumberSetting size = new NumberSetting("settings.jumpcircles.size", 1.5f, 1.0f, 2.0f, 0.1f);
    private final NumberSetting lifetime = new NumberSetting("settings.jumpcircles.lifetime", 4.0f, 1.0f, 10.0f, 0.1f);

    private final List<JumpCircle> circles = new ArrayList<>();
    private boolean wasOnGround;

    private static class JumpCircle {
        private final Vec3d pos;
        private final long spawnTime;
        private final Animation animation;
        private boolean fadingOut;

        private JumpCircle(Vec3d pos) {
            this.pos = pos;
            this.spawnTime = System.currentTimeMillis();
            this.animation = new Animation(FADE_IN_MS, 255f, true, Easing.BOTH_SINE);
        }
    }

    public JumpCircles() {
        super("JumpCircles", Category.Visuals);
    }

    @EventHandler
    private void onPlayerTick(EventPlayerTick event) {
        if (fullNullCheck()) return;

        boolean onGround = mc.player.isOnGround();
        boolean jumpPressed = mc.options.jumpKey.isPressed();
        if (wasOnGround && !onGround && jumpPressed) {
            Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getBlockPos().getY() + 0.01, mc.player.getZ());
            circles.add(new JumpCircle(pos));
        }
        wasOnGround = onGround;
    }

    @EventHandler
    private void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) return;
        if (circles.isEmpty()) return;

        long now = System.currentTimeMillis();
        Color baseColor = ColorUtils.getGlobalColor();

        Iterator<JumpCircle> iterator = circles.iterator();
        while (iterator.hasNext()) {
            JumpCircle circle = iterator.next();
            long age = now - circle.spawnTime;
            long lifetimeMs = Math.max(MIN_LIFETIME_MS, (long) (lifetime.getValue() * 1000f));
            long fadeOutAt = Math.max(0L, lifetimeMs - FADE_OUT_MS);

            if (age >= lifetimeMs) {
                iterator.remove();
                continue;
            }

            if (age >= fadeOutAt && !circle.fadingOut) {
                circle.animation.setDuration(FADE_OUT_MS);
                circle.animation.update(false);
                circle.fadingOut = true;
            }

            float alpha = circle.animation.getValue();
            if (alpha <= 1f && circle.fadingOut) continue;
            int alphaInt = MathHelper.clamp(Math.round(alpha), 0, 255);
            if (alpha <= 3) continue;

            float radius = Math.max(0.05f, size.getValue());
            Color ringColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alphaInt);
            Color glowColor = ColorUtils.fade(ringColor, new Color(255, 255, 255, alphaInt), 0.35f);

            Render3D.renderOrientedTexture(circle.pos, radius, CIRCLE_TEXTURE, ringColor.getRGB(), Direction.UP, true, true);
            Render3D.renderOrientedTexture(circle.pos, radius * GLOW_SCALE, CIRCLE_TEXTURE, glowColor.getRGB(), Direction.UP, true, true);
        }
    }

    @Override
    public void onDisable() {
        circles.clear();
        super.onDisable();
    }
}
