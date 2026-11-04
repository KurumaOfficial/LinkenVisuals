package ru.method.linkenvisuals;


import ru.method.linkenvisuals.cosmetics.render.CosmeticTextureRegistry;
import ru.method.linkenvisuals.cosmetics.render.CosmeticEffectRenderer;
import ru.method.linkenvisuals.cosmetics.service.CosmeticsService;
import ru.method.linkenvisuals.managers.misc.*;
import ru.method.linkenvisuals.managers.system.*;

import ru.method.linkenvisuals.screen.clickgui.ClickGui;
import ru.method.linkenvisuals.screen.clickgui.SettingsWindow;
import ru.method.linkenvisuals.utils.client.ClientTasks;
import ru.method.linkenvisuals.utils.CommunityLinks;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.client.discord.DiscordManager;
import ru.method.linkenvisuals.utils.server.ServerHandler;

import meteordevelopment.orbit.IEventBus;
import ru.method.linkenvisuals.utils.debug.ProfiledEventBus;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;
import lombok.*;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;

@Getter

public class LinkenVisuals implements ModInitializer, Wrapper {

    public static final String MOD_ID = "linkenvisuals";
    private static final boolean CRASH_ON_STARTUP_TEST_ENABLED =
            Boolean.parseBoolean(System.getenv().getOrDefault("LV_STARTUP_CRASH_TEST", "false"));
    private static final String CRASH_ON_STARTUP_MARKER = "startup-crash-test.done";
    // Автооткрытие ссылок сообщества — ТОЛЬКО при первом запуске: факт фиксируется маркер-файлом
    // (env LV_OPEN_STARTUP_LINKS=false — выключить совсем).
    private static final boolean OPEN_STARTUP_LINKS =
            Boolean.parseBoolean(System.getenv().getOrDefault("LV_OPEN_STARTUP_LINKS", "true"));
    private static final String STARTUP_LINKS_MARKER = "startup-links.done";
    // Стартовые ссылки берутся из централизованного CommunityLinks (эталонные slug'и).
    private static final String STARTUP_DISCORD_URL = CommunityLinks.DISCORD_URL;

    @Getter
    private static LinkenVisuals instance;

    private IEventBus eventHandler;
    private SoundManager soundManager;
    private NotifyManager notifyManager;
    private FriendManager friendManager;
    private UserSyncManager userSyncManager;
    private CosmeticsService cosmeticsService;
    private ModuleManager moduleManager;
    private ServerHandler serverHandler;
    private CommandManager commandManager;
    private ServerManager serverManager;
    private MacroManager macroManager;
    private HudManager hudManager;
    private ConfigManager configManager;
    private CrashLogTelegramManager crashLogTelegramManager;
    private UsageStatsManager usageStatsManager;
    private GpsManager gpsManager;
    private ClickGui clickGui;
    private WaypointManager waypointManager;
    private DiscordManager discordManager;
    private ru.method.linkenvisuals.modules.impl.render.cosmetic.RemotePets remotePets;
    @Setter
    private boolean panic = false;
    @Setter
    private boolean suppressToggleFeedback = false;
    @Setter
    private boolean configLoading = false;
    private long initTime;

    private final List<SettingsWindow> settingsWindows = new CopyOnWriteArrayList<>();

    // Инициализируется один раз, переприсваивание не требуется — потому final.
    public static final Logger LOGGER = LogManager.getLogger("LinkenVisuals");
    private static final String STARTUP_TELEGRAM_URL = CommunityLinks.TELEGRAM_URL;
    private static final String STARTUP_DEV_URL = CommunityLinks.DEV_URL;

    public File getGlobalsDir() {
        return new File(mc.runDirectory, "linkenvisuals");
    }

    public File getConfigsDir() {
        return new File(getGlobalsDir(), "configs");
    }

    public File getThemesDir() {
        return new File(getGlobalsDir(), "themes");
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[LinkenVisuals] Starting initialization.");
        initTime = System.currentTimeMillis();
        instance = this;
        createDirs(getGlobalsDir(), getConfigsDir(), getThemesDir());
        RemoteBuildControl.checkStartupAllowed();
        eventHandler = new ProfiledEventBus();
        eventHandler.registerLambdaFactory(LinkenVisuals.class.getPackageName(), (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
        registerWorldHooks();
        ClientTasks.register();
        soundManager = new SoundManager();
        notifyManager = new NotifyManager();
        friendManager = new FriendManager();
        cosmeticsService = new CosmeticsService();
        userSyncManager = new UserSyncManager();
        moduleManager = new ModuleManager();
        serverHandler = new ServerHandler();
        commandManager = new CommandManager();
        serverManager = new ServerManager();
        waypointManager = new WaypointManager();
        macroManager = new MacroManager();
        hudManager = new HudManager();
        crashLogTelegramManager = new CrashLogTelegramManager();
        usageStatsManager = new UsageStatsManager();
        configManager = new ConfigManager();
        gpsManager = new GpsManager();
        clickGui = new ClickGui();
        discordManager = new DiscordManager();
        // Punchy (Punch Mode): портированные анимации от первого лица. Регистрирует тик/ресурс-листенеры;
        // рантайм полностью гейтится настройкой ViewModel → Punch Mode (PunchyConfig.isModEnabled).
        try {
            // Встроенный ресурспак панчи: в нём ВСЕ клипы экшенов (player.animation.json, 1.8 МБ) и звуки
            // анимаций — без него работают только процедурные позы (холд), а атака/трезубец/лук молчат.
            net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer("linkenvisuals")
                    .ifPresent(container -> net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
                            net.minecraft.util.Identifier.of("punchy", "punchy"),
                            container,
                            net.minecraft.text.Text.literal("Punchy"),
                            net.fabricmc.fabric.api.resource.ResourcePackActivationType.ALWAYS_ENABLED));
            ru.method.linkenvisuals.punchy.client.PunchyClient.init();
        } catch (Throwable error) {
            LOGGER.error("[LinkenVisuals] Punchy (Punch Mode) init failed; continuing without it.", error);
        }
        // Рендер эффект-косметик (нимб/сферы/шлейф) в мировом пространстве.
        eventHandler.subscribe(CosmeticEffectRenderer.getInstance());
        // Отложенный рендер ников-голограмм (text display) поверх мира — фикс пропадания за шалкером/табличкой.
        eventHandler.subscribe(new ru.method.linkenvisuals.utils.render.HologramLateRenderer());
        // Питомцы других синхронизированных игроков (подписывается на шину сам).
        // LinkageError ловим отдельно: если класс не пройдёт верификацию в обфусцированном
        // билде, клиент должен запуститься без чужих питомцев, а не падать на старте.
        try {
            remotePets = new ru.method.linkenvisuals.modules.impl.render.cosmetic.RemotePets();
        } catch (Throwable error) {
            LOGGER.error("[LinkenVisuals] RemotePets unavailable; continuing without remote pets.", error);
        }
        configManager.initialize();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            CosmeticTextureRegistry.register();
            ru.method.linkenvisuals.network.RewardNetworking.init();
            ru.method.linkenvisuals.utils.render.BlockOverlayPipelines.init();
            // The donor's glass/blur/rounded pipelines were re-implemented on the
            // 1.21.11 RenderPipeline API. Warm them once the shader resources are
            // available so the first GUI frame does not stutter or fall back.
            // Pipelines are compiled lazily on the first draw: at that point the
            // client shader loader is guaranteed to have imported custom core shaders.
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client ->
                    ru.method.linkenvisuals.utils.debug.VisualCaptureDriver.tick(client));
        }
        usageStatsManager.start();
        maybeCrashOnStartupForTest();
        if (OPEN_STARTUP_LINKS && consumeFirstLaunchLinks()) {
            openStartupLinksAsync();
        }
        crashLogTelegramManager.sendPendingCrashReportsAsync();

        LOGGER.info("[LinkenVisuals] Successfully initialized for {} ms.", System.currentTimeMillis() - initTime);
    }

    private void registerWorldHooks() {
        // Donor BlockOverlay: cancels the vanilla outline and draws the animated
        // shader fill plus glow outline during the main world pass.
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register(
                ru.method.linkenvisuals.utils.render.BlockOverlayRenderer::onBeforeBlockOutline);
        // Donor timing: all custom 3D is submitted after entities, block entities
        // AND translucent terrain. Submitting at AFTER_ENTITIES put every ESP /
        // JumpCircle / ChinaHat behind the translucent pass, so ice and water
        // blended over them and made them look almost transparent. END_MAIN is
        // the 1.21.11 equivalent of the donor's pre-hand draw.
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_MAIN.register(context -> {
            net.minecraft.client.util.math.MatrixStack matrices = context.matrices();
            matrices.push();
            try {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                ru.method.linkenvisuals.utils.render.Render3D.prepare();
                ru.method.linkenvisuals.utils.render.Render3D.setLastWorldSpaceMatrix(matrices.peek());
                ru.method.linkenvisuals.utils.world.WorldUtils.lastWorld.get().set(matrices.peek().getPositionMatrix());
                ru.method.linkenvisuals.utils.world.WorldUtils.lastModelView.get().set(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());

                float fov = ((ru.method.linkenvisuals.api.mixins.accessors.IGameRenderer) client.gameRenderer)
                        .getFov$drug(client.gameRenderer.getCamera(), client.getRenderTickCounter().getTickProgress(true), true);
                org.joml.Matrix4f projectionMatrix = client.gameRenderer.getBasicProjectionMatrix(fov);
                ru.method.linkenvisuals.utils.world.WorldUtils.lastProj.get().set(projectionMatrix);
                ru.method.linkenvisuals.utils.render.Render3D.setLastProjectionMatrix(projectionMatrix);

                eventHandler.post(new ru.method.linkenvisuals.api.events.impl.EventRender3D.World(
                        client.gameRenderer.getCamera(), matrices.peek().getPositionMatrix(),
                        client.getRenderTickCounter()));
                eventHandler.post(new ru.method.linkenvisuals.api.events.impl.EventRender3D.Game(
                        client.getRenderTickCounter(), matrices,
                        context.commandQueue(),
                        context.worldState() == null ? null : context.worldState().cameraRenderState,
                        context.consumers()));

                ru.method.linkenvisuals.utils.render.Render3D.drawDepth(
                        ru.method.linkenvisuals.utils.render.Render3D.DEPTH_QUADS,
                        ru.method.linkenvisuals.utils.render.Render3D.DEPTH_DEBUG_LINES, false);
                ru.method.linkenvisuals.utils.render.Render3D.drawLightning();
                ru.method.linkenvisuals.utils.render.Render3D.draw(
                        ru.method.linkenvisuals.utils.render.Render3D.QUADS,
                        ru.method.linkenvisuals.utils.render.Render3D.DEBUG_LINES, false);
                ru.method.linkenvisuals.utils.render.Render3D.drawVariableWidthLines(true);
                ru.method.linkenvisuals.utils.render.Render3D.drawVariableWidthLines(false);
                ru.method.linkenvisuals.utils.render.Render3D.drawTextures();
                ru.method.linkenvisuals.utils.render.Render3D.drawDepth(
                        ru.method.linkenvisuals.utils.render.Render3D.DEPTH_SHINE_QUADS,
                        ru.method.linkenvisuals.utils.render.Render3D.DEPTH_SHINE_DEBUG_LINES, true);
                ru.method.linkenvisuals.utils.render.Render3D.draw(
                        ru.method.linkenvisuals.utils.render.Render3D.SHINE_QUADS,
                        ru.method.linkenvisuals.utils.render.Render3D.SHINE_DEBUG_LINES, true);
            } catch (Throwable error) {
                LOGGER.error("[LinkenVisuals] World render event failed", error);
            } finally {
                matrices.pop();
            }
        });
        // Draw the block overlay after custom 3D so its animated fill and glow
        // outline stay on top of ESP geometry aimed at the same block.
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_MAIN.register(
                ru.method.linkenvisuals.utils.render.BlockOverlayRenderer::onEndMain);
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_MAIN.register(context -> {
            net.minecraft.client.util.math.MatrixStack matrices = context.matrices();
            matrices.push();
            try {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                eventHandler.post(new ru.method.linkenvisuals.api.events.impl.EventRenderWorld(
                        client.getRenderTickCounter(), matrices));
            } catch (Throwable error) {
                LOGGER.error("[LinkenVisuals] End main render event failed", error);
            } finally {
                matrices.pop();
            }
        });
    }

    private void maybeCrashOnStartupForTest() {        if (!CRASH_ON_STARTUP_TEST_ENABLED) return;

        File marker = new File(getGlobalsDir(), CRASH_ON_STARTUP_MARKER);
        if (marker.exists()) return;

        try {
            Files.writeString(marker.toPath(), "created=" + System.currentTimeMillis(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }

        throw new RuntimeException("[LinkenVisuals] Startup crash test is enabled (one-time).");
    }

    /**
     * Ссылки сообщества открываем ТОЛЬКО при первом запуске: факт открытия фиксируем маркер-файлом
     * {@code linkenvisuals/startup-links.done} (тот же приём, что и у crash-test маркера). Если маркер
     * уже есть — больше не открываем. Чтобы показать ссылки снова, достаточно удалить этот файл.
     *
     * <p>Если маркер записать не удалось — ссылки НЕ открываем: иначе они полезли бы при каждом
     * запуске, а это ровно то поведение, от которого уходим.</p>
     */
    private boolean consumeFirstLaunchLinks() {
        File marker = new File(getGlobalsDir(), STARTUP_LINKS_MARKER);
        if (marker.exists()) return false;
        try {
            Files.writeString(marker.toPath(), "opened=" + System.currentTimeMillis(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("[LinkenVisuals] Failed to write startup-links marker, skipping links", exception);
            return false;
        }
        return true;
    }

    private void openStartupLinksAsync() {
        CompletableFuture.runAsync(() -> {
            // Три ссылки сообщества с паузами между ними, чтобы браузер не «склеил»/не потерял вкладки.
            openStartupLink(STARTUP_TELEGRAM_URL);   // https://t.me/linkenvisuals
            startupLinkDelay();
            openStartupLink(STARTUP_DEV_URL);        // https://t.me/linkenvisualsdev
            startupLinkDelay();
            openStartupLink(STARTUP_DISCORD_URL);    // https://dsc.gg/linkenvisuals
        });
    }

    private void startupLinkDelay() {
        try {
            Thread.sleep(300L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void openStartupLink(String url) {
        try {
            Util.getOperatingSystem().open(url);
        } catch (Exception exception) {
            LOGGER.warn("[LinkenVisuals] Failed to open startup link {}", url, exception);
        }
    }

    private void createDirs(File... file) {
        for (File f : file) f.mkdirs();
    }

    public static Identifier id(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        Identifier parsed = Identifier.tryParse(MOD_ID + ":" + normalized);
        if (parsed != null) return parsed;

        String sanitized = sanitizeIdentifierPath(normalized);
        Identifier sanitizedId = Identifier.tryParse(MOD_ID + ":" + sanitized);
        if (sanitizedId != null) {
            LOGGER.warn("Invalid Identifier path '{}' (sanitized to '{}')", path, sanitized);
            return sanitizedId;
        }

        LOGGER.error("Failed to create Identifier from path '{}'", path);
        return Identifier.of(MOD_ID, "invalid");
    }

    private static String sanitizeIdentifierPath(String raw) {
        if (raw == null) return "invalid";

        String input = raw.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (input.isEmpty()) return "invalid";

        StringBuilder builder = new StringBuilder(input.length());
        boolean lastWasUnderscore = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '/'
                    || c == '.'
                    || c == '_'
                    || c == '-';

            if (allowed) {
                builder.append(c);
                lastWasUnderscore = false;
            } else if (!lastWasUnderscore) {
                builder.append('_');
                lastWasUnderscore = true;
            }
        }

        String out = builder.toString();
        int start = 0;
        while (start < out.length() && (out.charAt(start) == '/' || out.charAt(start) == '_')) start++;
        int end = out.length();
        while (end > start && out.charAt(end - 1) == '_') end--;
        out = out.substring(start, end);

        return out.isEmpty() ? "invalid" : out;
    }

    public List<SettingsWindow> getSettingsWindows() {
        return settingsWindows;
    }
}
