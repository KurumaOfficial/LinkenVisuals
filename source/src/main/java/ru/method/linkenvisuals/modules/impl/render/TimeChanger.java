package ru.method.linkenvisuals.modules.impl.render;


import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render3D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;


public class TimeChanger extends Module {

    private static final Identifier GLYPH_BLOOM = LinkenVisuals.id("client/xuynya/ghost_bloom.png");
    private static final int LINE_COUNT = 120;
    private static final float FALL_SPEED = 0.07f;
    private static final float LINE_LENGTH = 17f;
    private static final float SEGMENT_LENGTH = 2f;
    private static final float ZIGZAG_WIDTH = 5f;
    private static final long GLYPH_FADE_MS = 400L;

    private final EnumSetting<TimePreset> mode = new EnumSetting<>("settings.timechanger.mode", TimePreset.DAY);
    private final EnumSetting<WeatherPreset> weather = new EnumSetting<>("settings.timechanger.weather", WeatherPreset.SUNNY);
    // Эффект падающих глифов-линий (порт LineGlyphs).
    private final BooleanSetting glyphs = new BooleanSetting("settings.world.glyphs", false);
    private final ColorSetting glyphsColor = new ColorSetting("settings.world.glyphs.color", ColorUtils.getGlobalColor(), glyphs::getValue);
    private final ru.method.linkenvisuals.modules.settings.impl.NumberSetting glyphsVolume =
            new ru.method.linkenvisuals.modules.settings.impl.NumberSetting("settings.world.glyphs.volume", 120f, 20f, 400f, 10f, glyphs::getValue);
    private final ru.method.linkenvisuals.modules.settings.impl.NumberSetting glyphsThickness =
            new ru.method.linkenvisuals.modules.settings.impl.NumberSetting("settings.world.glyphs.thickness", 2.5f, 1f, 8f, 0.5f, glyphs::getValue);
    private final ru.method.linkenvisuals.modules.settings.impl.NumberSetting glyphsSpeed =
            new ru.method.linkenvisuals.modules.settings.impl.NumberSetting("settings.world.glyphs.speed", 1f, 0.1f, 3f, 0.1f, glyphs::getValue);
    // ponytail: свечение = ×5 проходов по всем сегментам, поэтому по умолчанию выключено
    // (у нас ~80 точек на линию против ~20 в оригинале); включать точечно.
    private final BooleanSetting glyphsGlow = new BooleanSetting("settings.world.glyphs.glow", false, glyphs::getValue);

    private final List<FallingLine> lines = new ArrayList<>();
    private final Random random = new Random();

    private volatile long previousTime = -1L;
    private float previousRainGradient = -1f;
    private float previousThunderGradient = -1f;

    public TimeChanger() {
        super("World", Category.Visuals);
        getSettings().add(mode);
        getSettings().add(weather);
        getSettings().add(glyphs);
        getSettings().add(glyphsColor);
        getSettings().add(glyphsVolume);
        getSettings().add(glyphsThickness);
        getSettings().add(glyphsSpeed);
        getSettings().add(glyphsGlow);
    }

    private int glyphCount() {
        return Math.max(1, Math.round(glyphsVolume.getValue()));
    }

    @Override
    public void onEnable() {
        if (!fullNullCheck() && mc.world != null) {
            previousTime = mc.world.getTimeOfDay();
            previousRainGradient = mc.world.getRainGradient(1.0f);
            previousThunderGradient = mc.world.getThunderGradient(1.0f);
        }
        generateLines();
        super.onEnable();
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) return;

        ClientWorld world = mc.world;
        if (world == null) return;

        long desiredTime = mode.getValue().time() % 24000L;
        if (desiredTime < 0L) desiredTime += 24000L;

        ClientWorld.Properties properties = (ClientWorld.Properties) world.getLevelProperties();
        if (properties != null) {
            properties.setTimeOfDay(desiredTime);
        }

        WeatherPreset desiredWeather = weather.getValue();
        world.setRainGradient(desiredWeather.rainGradient());
        world.setThunderGradient(desiredWeather.thunderGradient());

        updateGlyphs();
    }

    // Симуляция линий на тике (20 tps), а не на кадре: скорость не зависит от FPS,
    // а O(n) работа уходит из рендера. ×3 компенсирует прежний пер-кадровый шаг (~60 fps).
    private void updateGlyphs() {
        if (!glyphs.getValue() || mc.player == null) return;
        float step = FALL_SPEED * 3f * Math.max(0.1f, glyphsSpeed.getValue());
        int alive = 0;
        Iterator<FallingLine> it = lines.iterator();
        while (it.hasNext()) {
            FallingLine line = it.next();
            if (line.fading) {
                // Гаснущие линии не двигаем — только ждём конца анимации альфы.
                if (line.isDead()) it.remove();
                continue;
            }
            line.update(step, ZIGZAG_WIDTH, LINE_LENGTH);
            if (line.shouldRespawn(mc.player.getEntityPos())) line.fadeOut();
            else alive++;
        }
        int toSpawn = glyphCount() - alive;
        for (int i = 0; i < toSpawn; i++) lines.add(new FallingLine(random));
        // Лишние (после уменьшения «Количества») гасим, а не вырезаем на месте.
        for (int i = lines.size() - 1; i >= 0 && alive > glyphCount(); i--) {
            FallingLine line = lines.get(i);
            if (!line.fading) {
                line.fadeOut();
                alive--;
            }
        }
    }

    @EventHandler
    private void onPacketReceive(EventPacket.Receive event) {
        if (fullNullCheck()) return;

        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket packet) {
            long serverTime = packet.timeOfDay() % 24000L;
            if (serverTime < 0L) serverTime += 24000L;
            previousTime = serverTime;
            event.cancel();
        }
    }

    // ===== Падающие глифы-линии (порт LineGlyphs) =====

    private void generateLines() {
        lines.clear();
        if (fullNullCheck() || mc.player == null) return;
        int count = glyphCount();
        for (int i = 0; i < count; i++) {
            lines.add(new FallingLine(random));
        }
    }

    @EventHandler
    private void onRenderGlyphs(EventRender3D.Game event) {
        if (!glyphs.getValue() || fullNullCheck() || mc.player == null) return;
        renderLines(event.getMatrixStack());
    }

    private void renderLines(MatrixStack stack) {
        if (lines.isEmpty() || mc.gameRenderer == null) return;
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        Color color = glyphsColor.getValue() != null ? glyphsColor.getValue() : ColorUtils.getGlobalColor();
        int baseColor = color.getRGB();
        // Прозрачность из выбранного цвета (раньше альфа жёстко перезаписывалась segAlpha*255 → ползунок не работал).
        int userAlpha = color.getAlpha();

        if (glyphsGlow.getValue()) {
            // Гало: те же линии шире и полупрозрачнее (порт многопроходного свечения LineGlyphes).
            renderPass(stack, camPos, baseColor, userAlpha, 8.0f, 0.04f);
            renderPass(stack, camPos, baseColor, userAlpha, 5.0f, 0.08f);
            renderPass(stack, camPos, baseColor, userAlpha, 2.5f, 0.15f);
            renderPass(stack, camPos, baseColor, userAlpha, 1.35f, 0.22f);
        }
        renderPass(stack, camPos, baseColor, userAlpha, 1.0f, 1.0f);
    }

    private void renderPass(MatrixStack stack, Vec3d camPos, int baseColor, int userAlpha, float widthMul, float alphaMul) {
        float width = glyphsThickness.getValue() * widthMul;
        for (FallingLine line : lines) {
            if (line.points.size() < 2) continue;
            float lineAlpha = line.alpha() * alphaMul;
            if (lineAlpha <= 0.005f) continue;
            for (int i = 0; i < line.points.size() - 1; i++) {
                Vec3d start = line.points.get(i);
                Vec3d end = line.points.get(i + 1);
                if (start.distanceTo(camPos) > 60 || end.distanceTo(camPos) > 60) continue;
                float segAlpha = lineAlpha * (i + 1) / line.points.size();
                int segColor = withAlpha(baseColor, Math.round(segAlpha * userAlpha));
                // Сплошные линии глифов (пишут глубину → FogBlur их не размывает как далёкие).
                Render3D.drawLine(stack, start, end, segColor, width, true);
            }
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    @Override
    public void onDisable() {
        lines.clear();
        if (!fullNullCheck()) {
            ClientWorld world = mc.world;
            if (world != null && previousTime >= 0L) {
                long restored = previousTime % 24000L;
                if (restored < 0L) restored += 24000L;
                ClientWorld.Properties properties = (ClientWorld.Properties) world.getLevelProperties();
                if (properties != null) {
                    properties.setTimeOfDay(restored);
                }
                if (previousRainGradient >= 0f) {
                    world.setRainGradient(previousRainGradient);
                }
                if (previousThunderGradient >= 0f) {
                    world.setThunderGradient(previousThunderGradient);
                }
            }
        }
        previousTime = -1L;
        previousRainGradient = -1f;
        previousThunderGradient = -1f;
        super.onDisable();
    }

    private static final class FallingLine {
        final List<Vec3d> points = new ArrayList<>();
        final List<Vec3d> turnPoints = new ArrayList<>();
        Vec3d currentDirection;
        double distanceTraveled = 0;
        double currentSegmentLength;
        // Инкрементальная длина линии — вместо O(n) пересуммирования sqrt после каждого удаления.
        double cachedLength = 0;
        final Random random;
        // Плавное появление/угасание вместо резкого спавна и вырезания.
        final Animation fade = new Animation(GLYPH_FADE_MS, 1f, true, Easing.BOTH_SINE);
        boolean fading = false;

        FallingLine(Random random) {
            this.random = random;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) {
                points.add(Vec3d.ZERO);
            } else {
                Vec3d playerPos = mc.player.getEntityPos();
                float yaw = mc.player.getYaw();
                float pitch = mc.player.getPitch();
                double yawRad = Math.toRadians(yaw);
                double pitchRad = Math.toRadians(pitch);
                double distance = 2 + random.nextDouble() * 6;
                double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
                double dirY = -Math.sin(pitchRad);
                double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);
                Vec3d lookDir = new Vec3d(dirX, dirY, dirZ);
                Vec3d right = lookDir.crossProduct(new Vec3d(0, 1, 0)).normalize();
                Vec3d up = right.crossProduct(lookDir).normalize();
                double hSpread = (random.nextDouble() - 0.5) * 24;
                double vSpread = (random.nextDouble() - 0.5) * 18;
                double x = playerPos.x + dirX * distance + right.x * hSpread + up.x * vSpread;
                double y = playerPos.y + dirY * distance + right.y * hSpread + up.y * vSpread;
                double z = playerPos.z + dirZ * distance + right.z * hSpread + up.z * vSpread;
                points.add(new Vec3d(x, y, z));
            }
            currentDirection = getRandomDirection();
            currentSegmentLength = 0.5 + random.nextDouble() * 1.5;
        }

        void update(float fallSpeed, float zigzagWidth, float maxLineLength) {
            if (points.isEmpty()) return;
            Vec3d last = points.get(points.size() - 1);
            Vec3d next = last.add(currentDirection.multiply(fallSpeed));
            points.add(next);
            cachedLength += fallSpeed; // направление единичное — длина нового сегмента равна fallSpeed
            distanceTraveled += fallSpeed;
            if (distanceTraveled >= currentSegmentLength) {
                turnPoints.add(next);
                currentDirection = getRandomDirection();
                distanceTraveled = 0;
                currentSegmentLength = 0.5 + random.nextDouble() * 1.5;
            }
            while (cachedLength > maxLineLength && points.size() > 2) {
                Vec3d removed = points.remove(0);
                cachedLength -= removed.distanceTo(points.get(0));
                // turnPoints добавляются в том же порядке, что и points — устаревшие всегда в голове.
                if (!turnPoints.isEmpty() && turnPoints.get(0).equals(removed)) turnPoints.remove(0);
            }
        }

        private Vec3d getRandomDirection() {
            return switch (random.nextInt(6)) {
                case 0 -> new Vec3d(1, 0, 0);
                case 1 -> new Vec3d(-1, 0, 0);
                case 2 -> new Vec3d(0, 1, 0);
                case 3 -> new Vec3d(0, -1, 0);
                case 4 -> new Vec3d(0, 0, 1);
                default -> new Vec3d(0, 0, -1);
            };
        }

        float alpha() {
            return Math.max(0f, Math.min(1f, fade.getValue()));
        }

        void fadeOut() {
            fading = true;
            fade.update(false);
        }

        boolean isDead() {
            return fading && fade.getValue() <= 0.01f;
        }

        boolean shouldRespawn(Vec3d playerPos) {
            if (points.isEmpty()) return true;
            Vec3d last = points.get(points.size() - 1);
            if (last.distanceTo(playerPos) > 18) return true;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.gameRenderer != null) {
                Camera camera = mc.gameRenderer.getCamera();
                Vec3d camDir = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
                Vec3d toLine = last.subtract(camera.getCameraPos()).normalize();
                if (camDir.dotProduct(toLine) < 0.5) return true;
            }
            return false;
        }
    }

    private enum TimePreset implements Nameable {
        DAWN("settings.timechanger.mode.dawn", 23000L),
        DAY("settings.timechanger.mode.day", 1000L),
        SUNSET("settings.timechanger.mode.sunset", 13000L),
        NIGHT("settings.timechanger.mode.night", 14000L);

        private final String name;
        private final long time;

        TimePreset(String name, long time) {
            this.name = name;
            this.time = time;
        }

        @Override
        public String getName() {
            return name;
        }

        public long time() {
            return time;
        }
    }

    private enum WeatherPreset implements Nameable {
        SUNNY("settings.timechanger.weather.sunny", 0.0f, 0.0f),
        RAIN("settings.timechanger.weather.rain", 1.0f, 0.0f);

        private final String name;
        private final float rainGradient;
        private final float thunderGradient;

        WeatherPreset(String name, float rainGradient, float thunderGradient) {
            this.name = name;
            this.rainGradient = rainGradient;
            this.thunderGradient = thunderGradient;
        }

        @Override
        public String getName() {
            return name;
        }

        public float rainGradient() {
            return rainGradient;
        }

        public float thunderGradient() {
            return thunderGradient;
        }
    }
}
