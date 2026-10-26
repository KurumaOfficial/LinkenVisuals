package dev.linkenvisuals;

import dev.linkenvisuals.account.AccountStorage;
import dev.linkenvisuals.clickgui.ClickGuiScreen;
import dev.linkenvisuals.clickgui.classic.ClassicGuiScreen;
import dev.linkenvisuals.cosmetic.Cosmetics;
import dev.linkenvisuals.pet.PetRig;
import dev.linkenvisuals.hands.HandPose;
import dev.linkenvisuals.render.AmbienceRenderer;
import dev.linkenvisuals.render.ScreenFilterRenderer;
import dev.linkenvisuals.render.HitBubbleRenderer;
import dev.linkenvisuals.render.KillShaderRenderer;
import dev.linkenvisuals.render.RainFall;
import dev.linkenvisuals.render.RainRenderer;
import dev.linkenvisuals.render.TargetEspRenderer;
import dev.linkenvisuals.clickgui.MenuCloseEffect;
import dev.linkenvisuals.clickgui.ModuleInfo;
import dev.linkenvisuals.clickgui.ModuleRegistry;
import dev.linkenvisuals.mixin.FogRendererAccessor;
import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.render.BlockOverlayRenderer;
import dev.linkenvisuals.render.DamageParticlesRenderer;
import dev.linkenvisuals.render.FortniteDamageRenderer;
import dev.linkenvisuals.render.JumpCirclesRenderer;
import dev.linkenvisuals.render.ItemRadiusRenderer;
import dev.linkenvisuals.render.MotionBlurRenderer;
import dev.linkenvisuals.render.TrailsRenderer;
import dev.linkenvisuals.render.WaypointsRenderer;
import dev.linkenvisuals.player.FakePlayer;
import dev.linkenvisuals.player.HitEffects;
import dev.linkenvisuals.player.KillEffects;
import dev.linkenvisuals.hud.NameTags;
import dev.linkenvisuals.player.TargetMarks;
import dev.linkenvisuals.clickgui.ConfigManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleResourceReloader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.ResourceType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Core entry point for the visuals modification client mod (Minecraft 1.21.11 / Fabric).
 * <p>
 * Responsible for:
 * <ul>
 *   <li>Sound and keybinding registration</li>
 *   <li>Configuration, account, and hand pose persistence lifecycle</li>
 *   <li>World and post-processing render pipeline attachments</li>
 *   <li>Client tick event handling and module keybind polling</li>
 * </ul>
 */
public final class LinkenVisuals implements ClientModInitializer {
    public static final String MOD_ID = "linkenvisuals";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("VisualsModification");

    public static final Identifier OPEN_SOUND_ID = Identifier.of(MOD_ID, "ui.open");
    public static final Identifier BACK_SOUND_ID = Identifier.of(MOD_ID, "ui.back");
    public static final Identifier TOGGLE_ON_SOUND_ID = Identifier.of(MOD_ID, "ui.toggle_on");
    public static final Identifier TOGGLE_OFF_SOUND_ID = Identifier.of(MOD_ID, "ui.toggle_off");
    public static final SoundEvent OPEN_SOUND = SoundEvent.of(OPEN_SOUND_ID);
    public static final SoundEvent BACK_SOUND = SoundEvent.of(BACK_SOUND_ID);
    public static final SoundEvent TOGGLE_ON_SOUND = SoundEvent.of(TOGGLE_ON_SOUND_ID);
    public static final SoundEvent TOGGLE_OFF_SOUND = SoundEvent.of(TOGGLE_OFF_SOUND_ID);

    public static final KeyBinding.Category LINKENVISUALS_CATEGORY =
        KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

    private static KeyBinding clickGuiKey;
    private static boolean fogSuppressed;

    @Override
    public void onInitializeClient() {
        Registry.register(Registries.SOUND_EVENT, OPEN_SOUND_ID, OPEN_SOUND);
        Registry.register(Registries.SOUND_EVENT, BACK_SOUND_ID, BACK_SOUND);
        Registry.register(Registries.SOUND_EVENT, TOGGLE_ON_SOUND_ID, TOGGLE_ON_SOUND);
        Registry.register(Registries.SOUND_EVENT, TOGGLE_OFF_SOUND_ID, TOGGLE_OFF_SOUND);
        dev.linkenvisuals.audio.LinkenSounds.register();
        ResourceLoader resourceLoader = ResourceLoader.get(ResourceType.CLIENT_RESOURCES);
        Identifier shaderReadyId = id("shader_resources");
        resourceLoader.registerReloader(
            shaderReadyId,
            new SimpleResourceReloader<Void>() {
                @Override
                protected Void prepare(ResourceReloader.Store store) {
                    return null;
                }

                @Override
                protected void apply(Void prepared, ResourceReloader.Store store) {
                    dev.linkenvisuals.render.Pipelines.markResourcesReady();
                }
            });
        resourceLoader.addReloaderOrdering(ResourceReloaderKeys.Client.SHADERS, shaderReadyId);
        migrateLegacyPlayerData();
        AccountStorage.load();
        dev.linkenvisuals.macro.MacroManager.load();
        HandPose.load();
        // Object modules must exist before config restore, otherwise their
        // enabled state/settings are skipped by ConfigJson.apply().
        Modules.init();
        dev.linkenvisuals.waypoint.WaypointStorage.load();
        ConfigManager.load();
        ModuleRegistry.enforceFlavorStyles();
        dev.linkenvisuals.ui.editor.MainMenuConfig.load();
        ModuleRegistry.syncFromMenuConfig();

        LOGGER.info("[Visuals Modification] Initialized successfully. Active Style: {}",
            dev.linkenvisuals.ui.style.StyleManager.getStyle().displayName());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConfigManager.save();
            dev.linkenvisuals.macro.MacroManager.save();
            dev.linkenvisuals.ui.editor.MainMenuConfig.save();
            AccountStorage.save();
            HandPose.save();
            dev.linkenvisuals.waypoint.WaypointStorage.save();
        }, "visuals-modification-shutdown"));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TargetMarks.clear();
            KillEffects.clear();
            FakePlayer.clear();
            dev.linkenvisuals.render.HitRangeRenderer.clear();
            dev.linkenvisuals.render.CustomHitBoxRenderer.clear();
            dev.linkenvisuals.render.ParticlesRenderer.clear();
            dev.linkenvisuals.render.TrajectoriesRenderer.clear();
            ItemRadiusRenderer.clear();
            DamageParticlesRenderer.clear();
            FortniteDamageRenderer.clear();
            PetRig.clear();
            // Free fullscreen VRAM: every post pipeline holds its own scene-size
            // buffers (Ambience x4, Rain, Kill, Bubble, Filter, Hands x4, HUD glass).
            // Without this they survive world switches at 1440p/4K indefinitely.
            // DISCONNECT fires on the netty thread, but RenderSystem calls must
            // happen on the render thread, otherwise GL prints
            // "Rendersystem called from wrong thread".
            try {
                if (client.isOnThread()) {
                    shutdownWorldGpu();
                } else {
                    client.execute(LinkenVisuals::shutdownWorldGpu);
                }
            } catch (Throwable t) {
                LOGGER.warn("Failed to schedule world GPU shutdown: {}", t.getMessage());
            }
            ConfigManager.save();
        });

        clickGuiKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.linkenvisuals.clickgui", GLFW.GLFW_KEY_RIGHT_SHIFT, LINKENVISUALS_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(LinkenVisuals::onTick);
        WorldRenderEvents.AFTER_ENTITIES.register(MenuCloseEffect::render);
        WorldRenderEvents.AFTER_ENTITIES.register(Cosmetics::render);
        WorldRenderEvents.AFTER_ENTITIES.register(PetRig::render);
        WorldRenderEvents.AFTER_ENTITIES.register(RainFall::render);
        WorldRenderEvents.AFTER_ENTITIES.register(TargetMarks::render);
        WorldRenderEvents.AFTER_ENTITIES.register(HitEffects::render);
        WorldRenderEvents.AFTER_ENTITIES.register(KillEffects::render);
        WorldRenderEvents.AFTER_ENTITIES.register(TargetEspRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(NameTags::capture);
        WorldRenderEvents.END_MAIN.register(RainRenderer::render);
        WorldRenderEvents.END_MAIN.register(HitBubbleRenderer::render);
        WorldRenderEvents.END_MAIN.register(KillShaderRenderer::render);
        WorldRenderEvents.END_MAIN.register(AmbienceRenderer::render);
        WorldRenderEvents.END_MAIN.register(ScreenFilterRenderer::renderWorld);
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register(BlockOverlayRenderer::onBeforeBlockOutline);
        WorldRenderEvents.END_MAIN.register(BlockOverlayRenderer::onEndMain);
        WorldRenderEvents.END_MAIN.register(MotionBlurRenderer::onEndMain);
        WorldRenderEvents.END_MAIN.register(TrailsRenderer::render);
        WorldRenderEvents.END_MAIN.register(JumpCirclesRenderer::render);
        WorldRenderEvents.END_MAIN.register(dev.linkenvisuals.render.HitRangeRenderer::render);
        WorldRenderEvents.END_MAIN.register(dev.linkenvisuals.render.CustomHitBoxRenderer::render);
        WorldRenderEvents.END_MAIN.register(dev.linkenvisuals.render.ParticlesRenderer::render);
        WorldRenderEvents.END_MAIN.register(dev.linkenvisuals.render.TrajectoriesRenderer::render);
        WorldRenderEvents.END_MAIN.register(ItemRadiusRenderer::render);
        WorldRenderEvents.END_MAIN.register(DamageParticlesRenderer::render);
        WorldRenderEvents.END_MAIN.register(WaypointsRenderer::render);
        dev.linkenvisuals.command.WaypointCommand.register();
        dev.linkenvisuals.autotest.AutoTestDriver.register();
        dev.linkenvisuals.autotest.LiveTestDriver.register();
        dev.linkenvisuals.autotest.AutoTestCommand.register();
    }

    /** Releases all world-scoped GPU pipelines. Must run on the render thread. */
    private static void shutdownWorldGpu() {
        try {
            dev.linkenvisuals.render.AmbiencePipeline.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Failed to shutdown AmbiencePipeline: {}", t.getMessage());
        }
        try {
            dev.linkenvisuals.render.RainRenderer.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Failed to shutdown RainRenderer: {}", t.getMessage());
        }
        try {
            dev.linkenvisuals.render.KillShaderRenderer.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Failed to shutdown KillShaderRenderer: {}", t.getMessage());
        }
        try {
            dev.linkenvisuals.render.HitBubbleRenderer.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Failed to shutdown HitBubbleRenderer: {}", t.getMessage());
        }
        try {
            dev.linkenvisuals.render.BlockOverlayRenderer.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Failed to shutdown BlockOverlayRenderer: {}", t.getMessage());
        }
        try {
            dev.linkenvisuals.render.LiquidGlassPipeline.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Failed to shutdown LiquidGlassPipeline: {}", t.getMessage());
        }
        try {
            dev.linkenvisuals.render.MotionBlurRenderer.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Failed to shutdown MotionBlurRenderer: {}", t.getMessage());
        }
        try {
            dev.linkenvisuals.render.ScreenFilterRenderer.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Failed to shutdown ScreenFilterRenderer: {}", t.getMessage());
        }
        try {
            dev.linkenvisuals.render.ScreenCapture.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Failed to shutdown ScreenCapture: {}", t.getMessage());
        }
        try {
            dev.linkenvisuals.render.HudGlassCapture.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Failed to shutdown HudGlassCapture: {}", t.getMessage());
        }
        try {
            dev.linkenvisuals.hands.HandOutline.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Failed to shutdown HandOutline: {}", t.getMessage());
        }
    }

    private static void migrateLegacyPlayerData() {
        net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        LegacyConfigMigration.Result migration =
            LegacyConfigMigration.run(loader.getConfigDir(), loader.getGameDir());
        if (migration.copied() > 0) {
            LOGGER.info("[Visuals Modification] Migrated {} legacy {} data entr{} to {}",
                migration.copied(), LegacyConfigMigration.LEGACY_ID,
                migration.copied() == 1 ? "y" : "ies", LegacyConfigMigration.CURRENT_ID);
        }
        for (String failure : migration.failures()) {
            LOGGER.warn("[Visuals Modification] Legacy migration: {}", failure);
        }
    }

    private static void onTick(MinecraftClient client) {
        if (!dev.linkenvisuals.render.Pipelines.resourcesReady() && client.getOverlay() == null) {
            // Splash/resource overlay is gone; shader sources are now available.
            // Deferring this to the first tick avoids compiling custom core
            // shaders while the initial resource reload is still running.
            dev.linkenvisuals.render.Pipelines.markResourcesReady();
        }
        while (clickGuiKey.wasPressed()) {
            if (client.currentScreen instanceof ClassicGuiScreen classic) {
                classic.close();
            } else if (client.currentScreen instanceof ClickGuiScreen beta) {
                beta.close();
            } else if (client.currentScreen instanceof dev.linkenvisuals.clickgui.link.LinkGuiScreen linken) {
                linken.close();
            } else if (client.currentScreen == null) {
                if (ModuleRegistry.isLinkenGui()) {
                    client.setScreen(new dev.linkenvisuals.clickgui.link.LinkGuiScreen());
                } else if (ModuleRegistry.isClassicGui()) {
                    client.setScreen(new ClassicGuiScreen());
                } else {
                    client.setScreen(new ClickGuiScreen());
                }
            }
        }
        updateFog();
        FakePlayer.tick(client);
        dev.linkenvisuals.render.TrailsRenderer.tick();
        dev.linkenvisuals.render.JumpCirclesRenderer.tick();
        dev.linkenvisuals.render.ParticlesRenderer.tick();
        DamageParticlesRenderer.tick();
        FortniteDamageRenderer.tick();
        tickZoom(client);
        tickFreelook(client);
        dev.linkenvisuals.module.impl.TimeChangerModule.tick();
        tickHandSwap(client);
        dev.linkenvisuals.module.impl.FastExpModule.tick();
        dev.linkenvisuals.audio.LinkenMenuMusic.tick(client);
        dev.linkenvisuals.module.impl.SoundsModule sounds = dev.linkenvisuals.module.Modules.sounds();
        if (sounds != null) {
            sounds.tick(client);
        }
        dev.linkenvisuals.module.impl.KillStreakModule.tick(client);
        dev.linkenvisuals.module.impl.TotemCounterModule.tick();
        dev.linkenvisuals.module.impl.UseTrackerModule.tick();
        tickElytraSwap(client);
        tickTotemTracker(client);
        tickDeathCoords(client);
    }

    private static void tickDeathCoords(MinecraftClient client) {
        dev.linkenvisuals.module.impl.DeathCoordsModule module =
            dev.linkenvisuals.module.Modules.deathCoords();
        if (module != null) {
            module.tick(client);
        }
    }

    private static void tickElytraSwap(MinecraftClient client) {
        dev.linkenvisuals.module.impl.ElytraSwapModule module =
            dev.linkenvisuals.module.Modules.elytraSwap();
        if (module != null) {
            module.tick(client);
        }
    }

    private static void tickTotemTracker(MinecraftClient client) {
        dev.linkenvisuals.module.impl.TotemTrackerModule module =
            dev.linkenvisuals.module.Modules.totemTracker();
        if (module != null) {
            module.tick(client);
        }
    }

    private static void tickHandSwap(MinecraftClient client) {
        dev.linkenvisuals.module.impl.HandSwapModule module = dev.linkenvisuals.module.Modules.handSwap();
        if (module == null || !module.enabled() || client.currentScreen != null) {
            return;
        }
        int keyCode = module.key.code();
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }
        long handle = client.getWindow().getHandle();
        // Simple edge detection: track previous state.
        boolean down = org.lwjgl.glfw.GLFW.glfwGetKey(handle, keyCode) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (down && !handSwapWasDown) {
            dev.linkenvisuals.module.impl.HandSwapModule.onKeyPress();
        }
        handSwapWasDown = down;
    }

    private static boolean handSwapWasDown = false;

    private static void tickZoom(MinecraftClient client) {
        dev.linkenvisuals.module.impl.ZoomModule zoom = dev.linkenvisuals.module.Modules.zoom();
        if (zoom == null || !zoom.enabled() || client.currentScreen != null) {
            if (zoom != null) {
                zoom.tick(false);
            }
            return;
        }
        // Check if zoom key is down via GLFW.
        int keyCode = zoom.key.code();
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
            zoom.tick(false);
            return;
        }
        long handle = client.getWindow().getHandle();
        boolean down = org.lwjgl.glfw.GLFW.glfwGetKey(handle, keyCode) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        zoom.tick(down);
    }

    private static void tickFreelook(MinecraftClient client) {
        dev.linkenvisuals.module.impl.FreelookModule module =
            dev.linkenvisuals.module.Modules.freelook();
        if (module != null) {
            module.tick(client);
        }
    }

    private static void updateFog() {
        boolean wanted = ModuleRegistry.shaderSky().enabled()
            && !(ModuleRegistry.ambience().enabled() && ModuleRegistry.ambienceFog().value());
        if (wanted == fogSuppressed) {
            return;
        }
        fogSuppressed = wanted;
        FogRendererAccessor.setFogEnabled(!wanted);
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
