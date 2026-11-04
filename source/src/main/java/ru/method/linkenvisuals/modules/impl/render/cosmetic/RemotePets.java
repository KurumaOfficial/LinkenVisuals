package ru.method.linkenvisuals.modules.impl.render.cosmetic;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.PufferfishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.cosmetics.service.CosmeticsService;
import ru.method.linkenvisuals.managers.misc.UserSyncManager;
import ru.method.linkenvisuals.managers.misc.UserSyncManager.RemotePetInfo;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.Wrapper;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Рендер питомцев ДРУГИХ синхронизированных игроков (свои рисует {@link Raven} внутри
 * {@link CosmeticEffects}). Данные приходят из sync-сервера (cosmetics.pet), см.
 * {@link UserSyncManager#getRemotePet}. Упрощённая орбита вокруг игрока: летуны кружат
 * на заданной высоте, наземные — у ног; вороны получают текстуру ворона, попугаи —
 * анимацию крыльев (та же рефлексия по полям, что у {@link Raven}).
 */
public final class RemotePets implements Wrapper {

    private static final double MAX_RENDER_DISTANCE_SQ = 64.0 * 64.0;
    private static final Set<String> GROUNDED_SKINS = Set.of("FOX", "PIG", "FROG", "SLIME");
    private static final net.minecraft.util.Identifier RAVEN_TEXTURE =
            LinkenVisuals.id("cosmetic/textures/voron.png");

    private final Map<UUID, PetInstance> pets = new ConcurrentHashMap<>();
    private OrderedRenderCommandQueue frameQueue;
    private CameraRenderState frameCameraState;

    public RemotePets() {
        LinkenVisuals.getInstance().getEventHandler().subscribe(this);
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        if (Module.fullNullCheck()) return;
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.isPanic()) return;
        CosmeticsService service = instance.getCosmeticsService();
        UserSyncManager syncManager = instance.getUserSyncManager();
        if (service == null || syncManager == null) return;
        if (!service.isEnabled() || !service.isShowSyncedUsers()) {
            pets.clear();
            return;
        }
        frameQueue = event.getCommandQueue();
        frameCameraState = event.getCameraState();

        Set<UUID> active = new HashSet<>();
        MatrixStack matrices = event.getMatrixStack();
        net.minecraft.client.render.Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        float tickDelta = event.getTickCounter().getTickProgress(false);
        long now = System.currentTimeMillis();

        CosmeticEffects effectsModule = instance.getModuleManager() == null
                ? null
                : instance.getModuleManager().getModule(CosmeticEffects.class);
        Nimb nimb = effectsModule == null ? null : effectsModule.nimbRenderer();

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        try {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == null || player == mc.player || player.isInvisible()) continue;
                if (player.squaredDistanceTo(mc.player) > MAX_RENDER_DISTANCE_SQ) continue;
                if (player.getGameProfile() == null) continue;

                UUID uuid = player.getGameProfile().id();
                String name = player.getGameProfile().name();

                // Фильтр «чья косметика видна» (все/друзья/пати) — питомцы и нимбы тоже подчиняются.
                if (!service.shouldRender(uuid, name)) continue;

                UserSyncManager.RemoteHaloInfo halo = syncManager.getRemoteHalo(uuid, name);
                if (halo != null && nimb != null) {
                    nimb.renderRemoteHalo(matrices, camera, player, tickDelta, halo);
                }

                RemotePetInfo info = syncManager.getRemotePet(uuid, name);
                if (info == null || uuid == null) continue;

                PetInstance pet = pets.get(uuid);
                if (pet == null || !pet.matches(info) || pet.entity.getEntityWorld() != mc.world) {
                    LivingEntity entity = createPetEntity(info.skin);
                    if (entity == null) continue;
                    pet = new PetInstance(info, entity);
                    pets.put(uuid, pet);
                }
                active.add(uuid);
                renderPet(event, matrices, pet, player, tickDelta, now);
            }
        } finally {
            matrices.pop();
        }

        // Убираем питомцев ушедших игроков/сброшенных настроек.
        Iterator<Map.Entry<UUID, PetInstance>> iterator = pets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!active.contains(iterator.next().getKey())) {
                iterator.remove();
            }
        }
    }

    private void renderPet(EventRender3D.Game event, MatrixStack matrices, PetInstance pet,
                           PlayerEntity player, float tickDelta, long now) {
        RemotePetInfo info = pet.info;

        float dt = pet.lastMs == 0L ? 0.016f : MathHelper.clamp((now - pet.lastMs) / 1000.0f, 0.0f, 0.06f);
        pet.lastMs = now;
        pet.orbitAngle += dt * (1.6f * info.speed);

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        boolean grounded = GROUNDED_SKINS.contains(info.skin);

        Vec3d target;
        if (grounded) {
            // Наземные: точка за спиной-слева владельца + земля под ней (общий сэмплер, как у своих питомцев),
            // а не орбита на высоте ног сквозь рельеф.
            float byaw = player.bodyYaw;
            double yawRad = Math.toRadians(byaw);
            Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
            Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);
            Vec3d tgtXZ = new Vec3d(px, py, pz).add(forward.multiply(-0.85)).add(right.multiply(-0.55));
            double g = PetGroundSampler.sampleGroundYSmooth(mc.world, tgtXZ.x, tgtXZ.z, py);
            double y0 = (!Double.isNaN(g) && Math.abs(g - py) <= 1.5) ? g : py;
            target = new Vec3d(tgtXZ.x, y0, tgtXZ.z);
        } else {
            double bob = Math.sin(now / 1000.0 * 2.1 + pet.orbitAngle) * 0.05;
            double targetY = py + info.height + bob;
            target = new Vec3d(
                    px + Math.cos(pet.orbitAngle) * info.radius,
                    targetY,
                    pz + Math.sin(pet.orbitAngle) * info.radius
            );
        }

        Vec3d prevPos = pet.smoothPos;
        if (pet.smoothPos == null) {
            pet.smoothPos = target;
        } else if (pet.smoothPos.squaredDistanceTo(target) > 256.0) {
            pet.smoothPos = target;
        } else {
            float follow = 1.0f - (float) Math.exp(-dt / (grounded ? 0.16f : 0.12f));
            pet.smoothPos = pet.smoothPos.add(target.subtract(pet.smoothPos).multiply(follow));
        }
        Vec3d pos = pet.smoothPos;

        float yaw;
        if (grounded) {
            // Yaw по направлению движения (не «скользит» боком на разворотах), иначе держим прошлый.
            Vec3d mv = prevPos == null ? Vec3d.ZERO : pos.subtract(prevPos);
            double mvSp = Math.sqrt(mv.x * mv.x + mv.z * mv.z) / Math.max(dt, 1.0e-4f);
            if (mvSp > 0.35 && (mv.x * mv.x + mv.z * mv.z) > 1.0e-8) {
                pet.smoothYaw = (float) (MathHelper.atan2(mv.z, mv.x) * (180.0 / Math.PI)) - 90.0f;
            }
            yaw = pet.smoothYaw;
        } else {
            // Смотрим по касательной к орбите (движение против часовой — производная от (cos, sin)).
            Vec3d tangent = new Vec3d(-Math.sin(pet.orbitAngle), 0.0, Math.cos(pet.orbitAngle));
            yaw = (float) Math.toDegrees(Math.atan2(-tangent.x, tangent.z));
        }

        LivingEntity entity = pet.entity;
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setYaw(yaw);
        entity.bodyYaw = yaw;
        entity.headYaw = yaw;
        entity.setPitch(0.0f);
        entity.setOnGround(grounded);
        entity.setPose(EntityPose.STANDING);
        entity.age = player.age;

        if (entity instanceof ParrotEntity parrot) {
            boolean raven = "RAVEN".equals(info.skin);
            animateParrotWings(parrot, now / 1000.0, raven ? 1.15f : 5.25f);
        }

        Object renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof EntityRenderer)) return;

        VertexConsumerProvider.Immediate vcp = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumerProvider useVcp = "RAVEN".equals(info.skin)
                ? new Raven.TextureSwapVcp(vcp, RAVEN_TEXTURE)
                : vcp;

        float scale = info.scale * ("RAVEN".equals(info.skin) ? 1.28f : 1.0f);

        matrices.push();
        matrices.translate(pos.x, pos.y, pos.z);
        matrices.scale(scale, scale, scale);
        try {
            renderEntityCompat((EntityRenderer) renderer, entity, tickDelta, matrices, useVcp);
        } finally {
            matrices.pop();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void renderEntityCompat(EntityRenderer renderer, LivingEntity entity, float tickDelta,
                                    MatrixStack matrices, VertexConsumerProvider vcp) {
        if (frameQueue == null || frameCameraState == null) {
            return;
        }
        try {
            var manager = mc.getEntityRenderDispatcher();
            EntityRenderState state = manager.getAndUpdateRenderState(entity, tickDelta);
            if (state == null) {
                return;
            }
            state.invisible = false;
            state.displayName = null;
            state.nameLabelPos = null;
            state.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            state.shadowRadius = 0f;
            state.shadowPieces.clear();
            if (vcp instanceof Raven.TextureSwapVcp swap && renderer instanceof LivingEntityRenderer living) {
                frameQueue.submitModel(living.getModel(), state, matrices,
                        RenderLayers.entityCutoutNoCull(swap.tex),
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF, null);
            } else {
                manager.render(state, frameCameraState, 0.0, 0.0, 0.0, matrices, frameQueue);
            }
        } catch (Throwable ignored) {
        }
    }

    // БЕЗ switch-выражения по строке: ProGuard ломает его StackMapTable в релизном jar
    // (VerifyError "Bad type on operand stack" при загрузке класса) — тот же краш уже
    // дважды чинили в пет-фабрике превью (см. коммиты 3e86ec4, 0032ebe). Object-локаль
    // + instanceof + цепочка if — проверенный обфускацией вариант.
    private LivingEntity createPetEntity(String skin) {
        if (mc.world == null || skin == null) return null;
        Object created = null;
        try {
            if ("BAT".equals(skin)) {
                created = new BatEntity(EntityType.BAT, mc.world);
            } else if ("PARROT".equals(skin) || "RAVEN".equals(skin)) {
                created = new ParrotEntity(EntityType.PARROT, mc.world);
            } else if ("FAIRY".equals(skin)) {
                created = new AllayEntity(EntityType.ALLAY, mc.world);
            } else if ("BEE".equals(skin)) {
                created = new BeeEntity(EntityType.BEE, mc.world);
            } else if ("VEX".equals(skin)) {
                created = new VexEntity(EntityType.VEX, mc.world);
            } else if ("FOX".equals(skin)) {
                created = new FoxEntity(EntityType.FOX, mc.world);
            } else if ("PIG".equals(skin)) {
                created = new PigEntity(EntityType.PIG, mc.world);
            } else if ("FROG".equals(skin)) {
                created = new FrogEntity(EntityType.FROG, mc.world);
            } else if ("PUFFERFISH".equals(skin)) {
                created = new PufferfishEntity(EntityType.PUFFERFISH, mc.world);
            } else if ("SLIME".equals(skin)) {
                created = new SlimeEntity(EntityType.SLIME, mc.world);
            }
        } catch (Throwable ignored) {
            return null;
        }
        if (!(created instanceof LivingEntity entity)) return null;
        entity.setNoGravity(true);
        entity.setSilent(true);
        entity.setInvisible(false);
        entity.setPose(EntityPose.STANDING);
        try {
            entity.setCustomNameVisible(false);
            entity.setCustomName(null);
        } catch (Throwable ignored) {
        }
        try {
            entity.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return entity;
    }

    private static final class PetInstance {
        final RemotePetInfo info;
        final LivingEntity entity;
        float orbitAngle;
        float smoothYaw;
        long lastMs;
        Vec3d smoothPos;

        PetInstance(RemotePetInfo info, LivingEntity entity) {
            this.info = info;
            this.entity = entity;
        }

        boolean matches(RemotePetInfo other) {
            return other != null
                    && info.skin.equals(other.skin)
                    && info.radius == other.radius
                    && info.height == other.height
                    && info.speed == other.speed
                    && info.scale == other.scale;
        }
    }

    /**
     * Анимация крыльев попугая прямой записью в публичные поля (та же формула, что у
     * {@link Raven}); ремапится компилятором и работает и в dev, и в production-jar.
     */
    private static void animateParrotWings(ParrotEntity parrot, double t, float flapSpeed) {
        float flap = (float) (Math.sin(t * flapSpeed * 6.2) * 0.5 + 0.5);
        parrot.maxWingDeviation = 0.10f + flap * 0.24f;
        parrot.flapProgress = (float) (t * flapSpeed * 3.6);
    }
}
