package ru.method.linkenvisuals.modules.impl.render;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render3D;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ru.method.linkenvisuals.utils.server.Server;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


public class Trajectories extends Module {

    private static final Identifier HIT_TEXTURE = LinkenVisuals.id("client/xuynya/hit.png");
    private static final float HIT_MARKER_SIZE = 0.85f;
    private static final double HIT_MARKER_OFFSET = 0.02;
    private static final double EXPLOSIVE_RADIUS = 4.0;

    private static final String[] EXPLOSIVE_KEYWORDS = {
            "\u0432\u0437\u0440\u044b\u0432",
            "\u0432\u0437\u0440\u044b\u0432\u043d",
            "\u0431\u043e\u043c\u0431",
            "\u0432\u0437\u0440\u044b\u0432\u0447\u0430\u0442",
            "boom",
            "blast",
            "explos",
            "tnt"
    };

    private final BooleanSetting explosiveCircle = new BooleanSetting("settings.trajectories.explosive_circle", true);

    private record Impact(Vec3d point, Direction side) {}

    // Кэш симуляции (до 300 raycast'ов за проход): пересчёт раз в тик либо при повороте
    // прицела > 0.1°; ключ — yawOffset (мультишот даёт 3 траектории за кадр).
    private final Map<Float, Impact> trajectoryCache = new HashMap<>();
    private int trajectoryCacheAge = -1;
    private float trajectoryCacheYaw = Float.NaN;
    private float trajectoryCachePitch = Float.NaN;
    private Item trajectoryCacheItem;

    private boolean explosiveBuffCached;
    private long explosiveBuffCacheExpiresMs;
    private net.minecraft.item.Item explosiveBuffCacheItem;

    public Trajectories() {
        super("Trajectories", Category.Visuals);
    }

    private boolean isThrowable(Item item) {
        return item instanceof EnderPearlItem ||
                item instanceof TridentItem ||
                item instanceof CrossbowItem;
    }

    private float getDistance(Item item) {
        return item instanceof BowItem ? 1.0f : 0.4f;
    }

    private float getThrowVelocity(Item item) {
        if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem) return 0.5f;
        if (item instanceof ExperienceBottleItem) return 0.59f;
        if (item instanceof TridentItem) return 2f;
        return 1.5f;
    }

    private int getThrowPitch(Item item) {
        if (item instanceof SplashPotionItem ||
                item instanceof LingeringPotionItem ||
                item instanceof ExperienceBottleItem) return 20;
        return 0;
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck() || mc.options.hudHidden) return;
        if (!mc.options.getPerspective().isFirstPerson()) return;

        Hand hand = getValidHand();
        if (hand == null) return;

        boolean prevBob = mc.options.getBobView().getValue();
        mc.options.getBobView().setValue(false);

        ItemStack handStack = hand == Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack();
        Item item = handStack.getItem();

        if (item instanceof BowItem) {
            if (mc.player.isUsingItem() && mc.player.getActiveItem().getItem() == item) {
                calcTrajectory(handStack, item, 0.0f, event);
            }
        } else if (item instanceof CrossbowItem && hasMultishot(handStack)) {
            calcTrajectory(handStack, item, -10.0f, event);
            calcTrajectory(handStack, item, 0.0f, event);
            calcTrajectory(handStack, item, 10.0f, event);
        } else {
            calcTrajectory(handStack, item, 0.0f, event);
        }
        mc.options.getBobView().setValue(prevBob);
    }


    private Hand getValidHand() {
        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();

        if (isValidProjectile(mainHand.getItem())) {
            return Hand.MAIN_HAND;
        } else if (isValidProjectile(offHand.getItem())) {
            return Hand.OFF_HAND;
        }
        return null;
    }

    private boolean isValidProjectile(Item item) {
        // BowItem обрабатывается ниже отдельной веткой (рисуем только при натяжении), но раньше
        // getValidHand() отсекал лук ещё до неё — вся симуляция лука была мёртвым кодом. Пускаем.
        return isThrowable(item) || item instanceof BowItem;
    }

    private boolean hasMultishot(ItemStack stack) {
        if (!(stack.getItem() instanceof CrossbowItem)) return false;

        try {
            var enchantments = stack.getEnchantments();

            for (var enchantmentEntry : enchantments.getEnchantments()) {

                if (enchantmentEntry.getKey().equals(Optional.of(Enchantments.MULTISHOT))) {
                    return enchantments.getLevel(enchantmentEntry) > 0;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }


    private void calcTrajectory(ItemStack stack, Item item, float yawOffset, EventRender3D.Game event) {
        float tickDelta = event.getTickCounter().getTickProgress(true);
        float rawYaw = MathHelper.lerpAngleDegrees(tickDelta, mc.player.lastYaw, mc.player.getYaw());
        float pitch = MathHelper.lerp(tickDelta, mc.player.lastPitch, mc.player.getPitch());

        boolean cacheValid = mc.player.age == trajectoryCacheAge
                && item == trajectoryCacheItem
                && Math.abs(MathHelper.wrapDegrees(rawYaw - trajectoryCacheYaw)) <= 0.1f
                && Math.abs(pitch - trajectoryCachePitch) <= 0.1f;
        if (!cacheValid) {
            trajectoryCache.clear();
            trajectoryCacheAge = mc.player.age;
            trajectoryCacheItem = item;
            trajectoryCacheYaw = rawYaw;
            trajectoryCachePitch = pitch;
        }

        Impact impact = trajectoryCache.get(yawOffset);
        if (impact == null) {
            impact = simulateTrajectory(item, rawYaw + yawOffset, pitch, tickDelta);
            trajectoryCache.put(yawOffset, impact);
        }
        if (impact.point() == null || impact.side() == null) return;

        Color markerColor = ColorUtils.getGlobalColor();
        renderHitMarker(impact.point(), impact.side(), markerColor);
        if (explosiveCircle.getValue() && hasExplosiveBuffCached(stack)) {
            renderExplosiveRadius(event, impact.point(), impact.side(), markerColor);
        }
    }

    private Impact simulateTrajectory(Item item, float yaw, float pitch, float tickDelta) {
        double x = MathHelper.lerp(tickDelta, mc.player.lastX, mc.player.getX());
        double y = MathHelper.lerp(tickDelta, mc.player.lastY, mc.player.getY());
        double z = MathHelper.lerp(tickDelta, mc.player.lastZ, mc.player.getZ());

        y += mc.player.getEyeHeight(mc.player.getPose()) - 0.1;

        float maxDist = getDistance(item);
        double motionX = -MathHelper.sin(yaw * 0.017453292f) * MathHelper.cos(pitch * 0.017453292f) * maxDist;
        double motionY = -MathHelper.sin((pitch - getThrowPitch(item)) * 0.017453292f) * maxDist;
        double motionZ = MathHelper.cos(yaw * 0.017453292f) * MathHelper.cos(pitch * 0.017453292f) * maxDist;

        float power = 1.0f;
        if (item instanceof BowItem) {
            float useTime = mc.player.getItemUseTime() / 20.0f;
            power = (useTime * useTime + useTime * 2.0f) / 3.0f;
            if (power > 1.0f) power = 1.0f;
        }

        final float distance = MathHelper.sqrt((float) (motionX * motionX + motionY * motionY + motionZ * motionZ));
        if (distance == 0) return new Impact(null, null);

        motionX /= distance;
        motionY /= distance;
        motionZ /= distance;

        final float velocity = (item instanceof BowItem ? (power * 2.0f) :
                item instanceof CrossbowItem ? 2.2f : 1.0f) * getThrowVelocity(item);

        motionX *= velocity;
        motionY *= velocity;
        motionZ *= velocity;

        Vec3d playerVelocity = mc.player.getVelocity();
        motionX += playerVelocity.x;
        motionY += playerVelocity.y;
        motionZ += playerVelocity.z;

        Vec3d impactPoint = null;
        Direction impactSide = null;

        for (int i = 0; i < 300; i++) {
            Vec3d currentPos = new Vec3d(x, y, z);

            x += motionX;
            y += motionY;
            z += motionZ;

            if (mc.world.getBlockState(new BlockPos((int) x, (int) y, (int) z)).getBlock() == Blocks.WATER) {
                motionX *= 0.8;
                motionY *= 0.8;
                motionZ *= 0.8;
            } else {
                motionX *= 0.99;
                motionY *= 0.99;
                motionZ *= 0.99;
            }

            if (item instanceof BowItem || item instanceof CrossbowItem) {
                motionY -= 0.05000000074505806;
            } else {
                motionY -= 0.03f;
            }

            Vec3d nextPos = new Vec3d(x, y, z);

            BlockHitResult hitResult = mc.world.raycast(new RaycastContext(
                    currentPos, nextPos,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            ));

            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                impactPoint = adjustImpactPosition(hitResult);
                impactSide = hitResult.getSide();
                break;
            }

            if (y <= -64) break;
        }

        return new Impact(impactPoint, impactSide);
    }

    private boolean hasExplosiveBuffCached(ItemStack stack) {
        long now = System.currentTimeMillis();
        // Инвалидация и по ПРЕДМЕТУ, не только по времени: при смене лука на взрывной арбалет кэш
        // показывал результат чужого стака. TTL 200 мс — смена баффа/босс-бара видна без задержки.
        if (stack.getItem() != explosiveBuffCacheItem || now >= explosiveBuffCacheExpiresMs) {
            explosiveBuffCached = hasExplosiveBuff(stack);
            explosiveBuffCacheItem = stack.getItem();
            explosiveBuffCacheExpiresMs = now + 200;
        }
        return explosiveBuffCached;
    }

    private boolean hasExplosiveBuff(ItemStack stack) {
        if (fullNullCheck() || stack == null) return false;

        String itemName = stack.getName().getString();
        if (containsExplosiveKeyword(itemName)) return true;

        String bossBarText = Server.getBossBarText();
        if (containsExplosiveKeyword(bossBarText)) return true;

        for (StatusEffectInstance instance : mc.player.getStatusEffects()) {
            String effectName = I18n.translate(instance.getEffectType().value().getTranslationKey());
            if (containsExplosiveKeyword(effectName)) return true;
        }

        return false;
    }

    private boolean containsExplosiveKeyword(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase();
        for (String keyword : EXPLOSIVE_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    private void renderExplosiveRadius(EventRender3D.Game event, Vec3d impactPoint, Direction impactSide, Color baseColor) {
        float alpha = 120;
        Color circleColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), (int) alpha);

        double radius = EXPLOSIVE_RADIUS;
        double y = impactPoint.y + (impactSide == Direction.UP ? HIT_MARKER_OFFSET : 0.01);
        Vec3d center = new Vec3d(impactPoint.x, y, impactPoint.z);

        Render3D.renderCircle(event.getMatrixStack(), center, radius, circleColor);
    }

    private Vec3d adjustImpactPosition(BlockHitResult hitResult) {
        Direction side = hitResult.getSide();
        Vec3d normal = new Vec3d(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());

        Vec3d hitPos = hitResult.getPos();
        return hitPos.add(normal.multiply(HIT_MARKER_OFFSET));
    }

    private void renderHitMarker(Vec3d position, Direction side, Color tint) {
        Render3D.renderOrientedTexture(position, HIT_MARKER_SIZE, HIT_TEXTURE, tint.getRGB(), side, true);
    }
}
