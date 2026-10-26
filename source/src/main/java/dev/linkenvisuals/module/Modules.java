package dev.linkenvisuals.module;

import dev.linkenvisuals.clickgui.ModuleRegistry;
import dev.linkenvisuals.module.impl.BlockOverlayModule;
import dev.linkenvisuals.module.impl.ChatHelperModule;
import dev.linkenvisuals.module.impl.CrosshairModule;
import dev.linkenvisuals.module.impl.CustomHitBoxModule;
import dev.linkenvisuals.module.impl.CustomVitalsModule;
import dev.linkenvisuals.module.impl.DamageParticlesModule;
import dev.linkenvisuals.module.impl.DeathCoordsModule;
import dev.linkenvisuals.module.impl.ElytraSwapModule;
import dev.linkenvisuals.module.impl.FortniteDamageModule;
import dev.linkenvisuals.module.impl.FtHelperModule;
import dev.linkenvisuals.module.impl.HwHelperModule;
import dev.linkenvisuals.module.impl.RwHelperModule;
import dev.linkenvisuals.module.impl.FastExpModule;
import dev.linkenvisuals.module.impl.FreelookModule;
import dev.linkenvisuals.module.impl.FullbrightModule;
import dev.linkenvisuals.module.impl.HandSwapModule;
import dev.linkenvisuals.module.impl.HitColorModule;
import dev.linkenvisuals.module.impl.HitRangeModule;
import dev.linkenvisuals.module.impl.HitSoundModule;
import dev.linkenvisuals.module.impl.ItemPhysicsModule;
import dev.linkenvisuals.module.impl.ItemRadiusModule;
import dev.linkenvisuals.module.impl.ItemScrollerModule;
import dev.linkenvisuals.module.impl.JumpCirclesModule;
import dev.linkenvisuals.module.impl.KillStreakModule;
import dev.linkenvisuals.module.impl.MineHelperModule;
import dev.linkenvisuals.module.impl.MotionBlurModule;
import dev.linkenvisuals.module.impl.NoRenderModule;
import dev.linkenvisuals.module.impl.OptimizationModule;
import dev.linkenvisuals.module.impl.ParticlesModule;
import dev.linkenvisuals.module.impl.SelfNametagsModule;
import dev.linkenvisuals.module.impl.SoundsModule;
import dev.linkenvisuals.module.impl.StreamerModeModule;
import dev.linkenvisuals.module.impl.TrajectoriesModule;
import dev.linkenvisuals.module.impl.SwingAnimationModule;
import dev.linkenvisuals.module.impl.TargetEspModule;
import dev.linkenvisuals.module.impl.TimeChangerModule;
import dev.linkenvisuals.module.impl.TotemCounterModule;
import dev.linkenvisuals.module.impl.TotemTrackerModule;
import dev.linkenvisuals.module.impl.TrailsModule;
import dev.linkenvisuals.module.impl.UseTrackerModule;
import dev.linkenvisuals.module.impl.ViewModelModule;
import dev.linkenvisuals.module.impl.WaypointsModule;
import dev.linkenvisuals.module.impl.ZoomModule;

/**
 * Typed facade over object-based modules. Per-module accessors are added here
 * as modules are ported (Phase 5), e.g. {@code wayPoints()}. Legacy static
 * accessors stay in {@link ModuleRegistry} until migrated one by one.
 */
public final class Modules {
    private static BlockOverlayModule blockOverlay;
    private static MotionBlurModule motionBlur;
    private static FullbrightModule fullbright;
    private static TrailsModule trails;
    private static JumpCirclesModule jumpCircles;
    private static TargetEspModule targetEsp;
    private static CrosshairModule crosshair;
    private static ZoomModule zoom;
    private static TimeChangerModule timeChanger;
    private static HitSoundModule hitSound;
    private static HandSwapModule handSwap;
    private static FastExpModule fastExp;
    private static MineHelperModule mineHelper;
    private static ChatHelperModule chatHelper;
    private static TotemCounterModule totemCounter;
    private static TotemTrackerModule totemTracker;
    private static WaypointsModule waypoints;
    private static UseTrackerModule useTracker;
    private static SwingAnimationModule swingAnimation;
    private static HitColorModule hitColor;
    private static FreelookModule freelook;
    private static ItemPhysicsModule itemPhysics;
    private static ItemScrollerModule itemScroller;
    private static ElytraSwapModule elytraSwap;
    private static StreamerModeModule streamerMode;
    private static SelfNametagsModule selfNametags;
    private static SoundsModule sounds;
    private static NoRenderModule noRender;
    private static HitRangeModule hitRange;
    private static CustomHitBoxModule customHitBox;
    private static OptimizationModule optimization;
    private static ParticlesModule particles;
    private static TrajectoriesModule trajectories;
    private static KillStreakModule killStreak;
    private static ViewModelModule viewModel;
    private static CustomVitalsModule customVitals;
    private static ItemRadiusModule itemRadius;
    private static DamageParticlesModule damageParticles;
    private static FortniteDamageModule fortniteDamage;
    private static DeathCoordsModule deathCoords;
    private static FtHelperModule ftHelper;
    private static HwHelperModule hwHelper;
    private static RwHelperModule rwHelper;

    private Modules() {
    }

    /** Keyboard dispatch for the donor helper modules (key or mouse binds). */
    public static void onHelperKey(int key) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.currentScreen != null) {
            return;
        }
        if (ftHelper != null) ftHelper.onKey(client, key);
        if (hwHelper != null) hwHelper.onKey(client, key);
        if (rwHelper != null) rwHelper.onKey(client, key);
    }

    /** Mouse dispatch for the donor helper LMB/RMB binds. */
    public static void onHelperMouse(int button) {
        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.currentScreen != null) {
            return;
        }
        if (ftHelper != null) ftHelper.onMouse(client, button);
        if (hwHelper != null) hwHelper.onMouse(client, button);
        if (rwHelper != null) rwHelper.onMouse(client, button);
    }

    public static void register(Module module) {
        ModuleRegistry.registerModule(module);
    }

    public static Module get(String id) {
        return ModuleRegistry.module(id);
    }

    public static BlockOverlayModule blockOverlay() {
        return blockOverlay;
    }

    public static MotionBlurModule motionBlur() {
        return motionBlur;
    }

    public static FullbrightModule fullbright() {
        return fullbright;
    }

    public static TrailsModule trails() {
        return trails;
    }

    public static JumpCirclesModule jumpCircles() {
        return jumpCircles;
    }

    public static TargetEspModule targetEsp() {
        return targetEsp;
    }

    public static CrosshairModule crosshair() {
        return crosshair;
    }

    public static ZoomModule zoom() {
        return zoom;
    }

    public static TimeChangerModule timeChanger() {
        return timeChanger;
    }

    public static HitSoundModule hitSound() {
        return hitSound;
    }

    public static HandSwapModule handSwap() {
        return handSwap;
    }

    public static FastExpModule fastExp() {
        return fastExp;
    }

    public static MineHelperModule mineHelper() {
        return mineHelper;
    }

    public static ChatHelperModule chatHelper() {
        return chatHelper;
    }

    public static TotemCounterModule totemCounter() {
        return totemCounter;
    }

    public static TotemTrackerModule totemTracker() {
        return totemTracker;
    }

    public static WaypointsModule waypoints() {
        return waypoints;
    }

    public static UseTrackerModule useTracker() {
        return useTracker;
    }

    public static SwingAnimationModule swingAnimation() {
        return swingAnimation;
    }

    public static HitColorModule hitColor() {
        return hitColor;
    }

    public static FreelookModule freelook() {
        return freelook;
    }

    public static ItemPhysicsModule itemPhysics() {
        return itemPhysics;
    }

    public static ItemScrollerModule itemScroller() {
        return itemScroller;
    }

    public static ElytraSwapModule elytraSwap() {
        return elytraSwap;
    }

    public static StreamerModeModule streamerMode() {
        return streamerMode;
    }

    public static SelfNametagsModule selfNametags() {
        return selfNametags;
    }

    public static SoundsModule sounds() {
        return sounds;
    }

    public static NoRenderModule noRender() {
        return noRender;
    }

    public static HitRangeModule hitRange() {
        return hitRange;
    }

    public static CustomHitBoxModule customHitBox() {
        return customHitBox;
    }

    public static OptimizationModule optimization() {
        return optimization;
    }

    public static ParticlesModule particles() {
        return particles;
    }

    public static TrajectoriesModule trajectories() {
        return trajectories;
    }

    public static KillStreakModule killStreak() {
        return killStreak;
    }

    public static ViewModelModule viewModel() {
        return viewModel;
    }

    public static CustomVitalsModule customVitals() {
        return customVitals;
    }

    public static ItemRadiusModule itemRadius() {
        return itemRadius;
    }

    public static DamageParticlesModule damageParticles() {
        return damageParticles;
    }

    public static FortniteDamageModule fortniteDamage() {
        return fortniteDamage;
    }

    public static DeathCoordsModule deathCoords() {
        return deathCoords;
    }

    public static FtHelperModule ftHelper() {
        return ftHelper;
    }

    public static HwHelperModule hwHelper() {
        return hwHelper;
    }

    public static RwHelperModule rwHelper() {
        return rwHelper;
    }

    /**
     * Registers built-in object modules. Called once from mod init.
     */
    public static void init() {
        blockOverlay = new BlockOverlayModule();
        register(blockOverlay);
        motionBlur = new MotionBlurModule();
        register(motionBlur);
        fullbright = new FullbrightModule();
        register(fullbright);
        trails = new TrailsModule();
        register(trails);
        jumpCircles = new JumpCirclesModule();
        register(jumpCircles);
        targetEsp = new TargetEspModule();
        register(targetEsp);
        crosshair = new CrosshairModule();
        register(crosshair);
        zoom = new ZoomModule();
        register(zoom);
        timeChanger = new TimeChangerModule();
        register(timeChanger);
        hitSound = new HitSoundModule();
        register(hitSound);
        handSwap = new HandSwapModule();
        register(handSwap);
        fastExp = new FastExpModule();
        register(fastExp);
        mineHelper = new MineHelperModule();
        register(mineHelper);
        chatHelper = new ChatHelperModule();
        register(chatHelper);
        totemCounter = new TotemCounterModule();
        register(totemCounter);
        totemTracker = new TotemTrackerModule();
        register(totemTracker);
        waypoints = new WaypointsModule();
        register(waypoints);
        useTracker = new UseTrackerModule();
        register(useTracker);
        swingAnimation = new SwingAnimationModule();
        register(swingAnimation);
        hitColor = new HitColorModule();
        register(hitColor);
        freelook = new FreelookModule();
        register(freelook);
        itemPhysics = new ItemPhysicsModule();
        register(itemPhysics);
        itemScroller = new ItemScrollerModule();
        register(itemScroller);
        elytraSwap = new ElytraSwapModule();
        register(elytraSwap);
        streamerMode = new StreamerModeModule();
        register(streamerMode);
        selfNametags = new SelfNametagsModule();
        register(selfNametags);
        sounds = new SoundsModule();
        register(sounds);
        noRender = new NoRenderModule();
        register(noRender);
        hitRange = new HitRangeModule();
        register(hitRange);
        customHitBox = new CustomHitBoxModule();
        register(customHitBox);
        optimization = new OptimizationModule();
        register(optimization);
        particles = new ParticlesModule();
        register(particles);
        trajectories = new TrajectoriesModule();
        register(trajectories);
        killStreak = new KillStreakModule();
        register(killStreak);
        viewModel = new ViewModelModule();
        register(viewModel);
        customVitals = new CustomVitalsModule();
        register(customVitals);
        itemRadius = new ItemRadiusModule();
        register(itemRadius);
        damageParticles = new DamageParticlesModule();
        register(damageParticles);
        fortniteDamage = new FortniteDamageModule();
        register(fortniteDamage);
        deathCoords = new DeathCoordsModule();
        register(deathCoords);
        ftHelper = new FtHelperModule();
        register(ftHelper);
        hwHelper = new HwHelperModule();
        register(hwHelper);
        rwHelper = new RwHelperModule();
        register(rwHelper);
    }
}
