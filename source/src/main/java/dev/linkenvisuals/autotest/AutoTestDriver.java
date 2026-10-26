package dev.linkenvisuals.autotest;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.clickgui.ClickGuiScreen;
import dev.linkenvisuals.clickgui.ModuleInfo;
import dev.linkenvisuals.clickgui.ModuleRegistry;
import dev.linkenvisuals.clickgui.classic.ClassicGuiScreen;
import dev.linkenvisuals.clickgui.link.LinkGuiConfig;
import dev.linkenvisuals.clickgui.link.LinkGuiScreen;
import dev.linkenvisuals.clickgui.link.LinkGuiTheme;
import dev.linkenvisuals.ui.linkmenu.LinkenMainMenuScreen;
import dev.linkenvisuals.clickgui.setting.BooleanSetting;
import dev.linkenvisuals.clickgui.setting.ColorSetting;
import dev.linkenvisuals.clickgui.setting.ModeSetting;
import dev.linkenvisuals.clickgui.setting.Setting;
import dev.linkenvisuals.clickgui.setting.SliderSetting;
import dev.linkenvisuals.hud.ArmorHud;
import dev.linkenvisuals.hud.CooldownsHud;
import dev.linkenvisuals.hud.CoordinatesHud;
import dev.linkenvisuals.hud.CustomVitalsHud;
import dev.linkenvisuals.hud.HelperItemsHud;
import dev.linkenvisuals.hud.InventoryHud;
import dev.linkenvisuals.hud.KillStreakHud;
import dev.linkenvisuals.hud.LowArmorNotificationsHud;
import dev.linkenvisuals.hud.HudManager;
import dev.linkenvisuals.hud.TargetHud;
import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.ui.Theme;
import dev.linkenvisuals.module.impl.KillStreakModule;
import dev.linkenvisuals.module.impl.HitSoundModule;
import dev.linkenvisuals.module.impl.ViewModelModule;
import dev.linkenvisuals.player.FakePlayer;
import dev.linkenvisuals.ui.linkmenu.LinkenPauseScreen;
import dev.linkenvisuals.render.CustomHitBoxRenderer;
import dev.linkenvisuals.render.DamageParticlesRenderer;
import dev.linkenvisuals.render.FortniteDamageRenderer;
import dev.linkenvisuals.render.HitRangeRenderer;
import dev.linkenvisuals.render.ItemRadiusRenderer;
import dev.linkenvisuals.render.ParticlesRenderer;
import dev.linkenvisuals.render.TrajectoriesRenderer;
import dev.linkenvisuals.ui.linkmenu.LinkenMultiplayerScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TabButtonWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * In-game smoke test driven through the game's own API.
 *
 * <p>Start it from a world with {@code /lvtest start} (or with
 * {@code -Dlinkenvisuals.autotest=true}, which auto-starts shortly after a world
 * loads). The driver opens both ClickGUIs and the inventory, spawns tagged
 * no-AI mobs when the player has command permission, attacks them, presses the
 * movement keys, then enables every module and sweeps every render setting.
 * Afterwards it restores the exact module/setting state, scans
 * {@code logs/latest.log} for new errors and writes a report next to the log.
 *
 * <p>Everything runs on the client thread from {@link ClientTickEvents}; there
 * is no extra dependency and the driver also works in a normal release jar.
 */
public final class AutoTestDriver {
    public static final String AUTOSTART_PROPERTY = "linkenvisuals.autotest";
    public static final String QUIT_PROPERTY = "linkenvisuals.autotest.quit";

    private static final String CHAT_PREFIX = "[LVTest] ";
    private static final String MOB_TAG = "lvt";
    private static final int AUTOSTART_DELAY_TICKS = 40;
    private static final int QUIT_DELAY_TICKS = 60;

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static final List<ModuleInfo> MODULES = new ArrayList<>();
    private static final List<Runnable> RESTORES = new ArrayList<>();
    /** Entity ids that existed before the summon; command tags are server-side only. */
    private static final Set<Integer> KNOWN_ENTITY_IDS = new HashSet<>();

    private static AutoTestReport report = new AutoTestReport();
    private static AutoTestReport lastReport;
    private static boolean running;
    private static boolean stopRequested;
    private static String stopReason = "";
    private static int stepDelay;
    private static int currentStep;
    private static int totalSteps;
    private static String lastStep = "";
    private static int autoStartTicks;
    private static boolean autoStartDone;
    private static int quitTicks = -1;
    private static int forceTabTicks;

    private static Path logFile;
    private static int logLineOffset = -1;
    private static int screenshotId;
    private static Vec3d returnPos;
    private static float returnYaw;
    private static float returnPitch;
    private static int returnSlot;
    private static boolean commandsWorked;
    private static boolean mobsSpawned;
    private static boolean blockPlaced;
    private static String blockCoords;
    private static ItemStack trajectoryReturnStack = ItemStack.EMPTY;
    private static ItemStack helperReturnSlot0 = ItemStack.EMPTY;
    private static ItemStack helperReturnSlot1 = ItemStack.EMPTY;
    private static int helperReturnSelected;
    private static boolean trajectoryBlockOverlay = true;

    private AutoTestDriver() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoTestDriver::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (running) {
                stop("дисконнект");
            }
        });
    }

    /** Starts the test in the current world. Returns a short status message. */
    public static String start(MinecraftClient client) {
        if (running) {
            return "уже идёт: " + statusLine();
        }
        if (client.player == null || client.world == null || client.interactionManager == null) {
            return "сначала зайди в мир";
        }
        autoStartDone = true;
        autoStartTicks = 0;
        quitTicks = -1;
        stopRequested = false;
        stopReason = "";
        stepDelay = 0;
        currentStep = 0;
        lastStep = "подготовка";
        report = new AutoTestReport();
        lastReport = null;
        screenshotId = 0;
        commandsWorked = false;
        mobsSpawned = false;
        blockPlaced = false;
        blockCoords = null;
        KNOWN_ENTITY_IDS.clear();

        ClientPlayerEntity player = client.player;
        returnPos = player.getEntityPos();
        returnYaw = player.getYaw();
        returnPitch = player.getPitch();
        returnSlot = player.getInventory().getSelectedSlot();

        logFile = client.runDirectory.toPath().resolve("logs").resolve("latest.log");
        logLineOffset = countLogLines(logFile);

        if (client.currentScreen != null) {
            client.setScreen(null);
        }

        try {
            snapshotConfiguration();
            buildPlan(client);
        } catch (Throwable t) {
            LinkenVisuals.LOGGER.error("[AutoTest] failed to build the test plan", t);
            restoreConfiguration();
            STEPS.clear();
            return "не удалось запустить: " + AutoTestReport.describe(t);
        }

        totalSteps = STEPS.size();
        lastStep = "старт";
        running = true;
        LinkenVisuals.LOGGER.info("[AutoTest] started with {} steps", totalSteps);
        return "старт: " + totalSteps + " шагов (мобы, GUI, все модули и настройки). "
            + "Отчёт — в чате и logs/linkenvisuals-autotest-*.log";
    }

    /** Requests a graceful stop; the current step is allowed to finish. */
    public static String stop(String reason) {
        if (!running) {
            return "не запущен";
        }
        stopRequested = true;
        stopReason = reason;
        return "останов: " + reason;
    }

    public static String statusLine() {
        if (running) {
            return "идёт " + currentStep + "/" + totalSteps + " — " + lastStep;
        }
        if (lastReport != null) {
            return "последний запуск: " + lastReport.summary();
        }
        return "не запускался; /lvtest start (нужен мир, для мобов — читы)";
    }

    private static void tick(MinecraftClient client) {
        if (forceTabTicks > 0) {
            forceTabTicks--;
            if (client.options != null) {
                client.options.playerListKey.setPressed(true);
            }
        }
        if (!running) {
            if (quitTicks > 0 && --quitTicks == 0) {
                client.scheduleStop();
                return;
            }
            maybeAutoStart(client);
            return;
        }
        if (client.player == null || client.world == null) {
            finish(client, "мир закрыт");
            return;
        }
        if (stopRequested) {
            finish(client, stopReason);
            return;
        }
        if (stepDelay > 0) {
            stepDelay--;
            return;
        }
        Step step = STEPS.poll();
        if (step == null) {
            finish(client, "все шаги выполнены");
            return;
        }
        lastStep = step.label();
        currentStep++;
        try {
            step.action().run();
            report.pass();
        } catch (SkipStep skipped) {
            report.skip(step.label(), skipped.getMessage());
        } catch (Throwable t) {
            report.fail(step.label(), t);
            LinkenVisuals.LOGGER.error("[AutoTest] step '{}' failed", step.label(), t);
        }
        stepDelay = Math.max(0, step.delayTicks());
    }

    private static void maybeAutoStart(MinecraftClient client) {
        if (autoStartDone || !Boolean.getBoolean(AUTOSTART_PROPERTY)) {
            return;
        }
        if (client.player == null || client.world == null || client.currentScreen != null) {
            autoStartTicks = 0;
            return;
        }
        if (++autoStartTicks < AUTOSTART_DELAY_TICKS) {
            return;
        }
        autoStartDone = true;
        chat(client, "auto: " + start(client));
    }

    private static void finish(MinecraftClient client, String reason) {
        if (!running) {
            return;
        }
        running = false;
        stopRequested = false;
        releaseKeys(client);
        if (client.currentScreen != null) {
            client.setScreen(null);
        }
        restoreConfiguration();
        cleanupWorld(client);

        LogScan scan = scanLog();
        String reportPath = writeReportFile(client, reason, scan);
        lastReport = report;
        announce(client, reason, scan, reportPath);

        STEPS.clear();
        stepDelay = 0;
        if (Boolean.getBoolean(QUIT_PROPERTY)) {
            quitTicks = QUIT_DELAY_TICKS;
        }
    }

    // ------------------------------------------------------------------
    // Plan
    // ------------------------------------------------------------------

    private static void buildPlan(MinecraftClient client) {
        STEPS.clear();

        add("screenshot world", 10, () -> screenshot(client, "world"));
        String[] hudStyles = {"card", "minimal", "neon", "aurora", "glass", "solid", "gradient", "outline"};
        for (int i = 0; i < hudStyles.length; i++) {
            int style = i;
            String label = hudStyles[i];
            add("set hud " + label, 10, () -> ModuleRegistry.hudStyle().select(style));
            add("screenshot hud " + label, 10, () -> screenshot(client, "hud-" + label));
        }
        add("restore hud style", 10, () -> ModuleRegistry.hudStyle().select(0));
        add("enable target hud", 1, () -> {
            if (ModuleRegistry.targetHud() != null) {
                ModuleRegistry.targetHud().setValue(true);
            }
        });
        add("open target hud preview", 8, () -> client.setScreen(new ChatScreen("", false)));
        add("check target hud", 5, () -> {
            if (!TargetHud.lastRenderVisible()) {
                throw new IllegalStateException("TargetHUD не отрисовался в preview");
            }
        });
        add("screenshot target hud", 1, () -> screenshot(client, "target-hud"));
        add("close target hud preview", 3, () -> client.setScreen(null));
        add("enable coordinates hud", 1, () -> {
            if (ModuleRegistry.coordinatesHud() != null) {
                ModuleRegistry.coordinatesHud().setValue(true);
            }
        });
        add("open coordinates hud preview", 10, () -> client.setScreen(new ChatScreen("", false)));
        add("check coordinates hud", 1, () -> {
            if (!CoordinatesHud.lastRenderVisible()) {
                throw new IllegalStateException("CoordinatesHud не отрисовался в preview");
            }
        });
        add("screenshot coordinates hud", 1, () -> screenshot(client, "coordinates-hud"));
        add("close coordinates hud preview", 3, () -> client.setScreen(null));
        add("enable cooldowns hud", 1, () -> {
            if (ModuleRegistry.cooldownsHud() != null) {
                ModuleRegistry.cooldownsHud().setValue(true);
            }
        });
        add("open cooldowns hud preview", 8, () -> client.setScreen(new ChatScreen("", false)));
        add("check cooldowns hud", 5, () -> {
            if (!CooldownsHud.lastRenderVisible()) {
                throw new IllegalStateException("CooldownsHud не отрисовался в preview");
            }
        });
        add("screenshot cooldowns hud", 1, () -> screenshot(client, "cooldowns-hud"));
        add("close cooldowns hud preview", 3, () -> client.setScreen(null));
        add("enable armor hud", 1, () -> {
            if (ModuleRegistry.armorHud() != null) {
                ModuleRegistry.armorHud().setValue(true);
            }
        });
        add("enable inventory hud", 1, () -> {
            if (ModuleRegistry.inventoryHud() != null) {
                ModuleRegistry.inventoryHud().setValue(true);
            }
        });
        add("enable low armor notifications", 1, () -> {
            if (ModuleRegistry.lowArmorNotifications() != null) {
                ModuleRegistry.lowArmorNotifications().setValue(true);
            }
        });
        add("open armor/inventory preview", 8, () -> {
            client.setScreen(null);
            HudManager.setEditing(true);
        });
        add("check armor hud", 5, () -> {
            if (!ArmorHud.lastRenderVisible()) {
                throw new IllegalStateException("ArmorHud не отрисовался в preview");
            }
        });
        add("check inventory hud", 2, () -> {
            if (!InventoryHud.lastRenderVisible()) {
                throw new IllegalStateException("InventoryHud не отрисовался в preview");
            }
        });
        add("check low armor hud", 2, () -> {
            if (!LowArmorNotificationsHud.lastRenderVisible()) {
                throw new IllegalStateException("LowArmorNotificationsHud не отрисовался в preview");
            }
        });
        add("switch armor to v2", 2, () -> {
            if (ModuleRegistry.armorHudMode() != null) {
                ModuleRegistry.armorHudMode().select(1);
            }
        });
        add("check armor v2", 3, () -> {
            if (!ArmorHud.lastV2Visible()) {
                throw new IllegalStateException("ArmorHud V2 не отрисовался в preview");
            }
        });
        add("screenshot armor hud v2", 1, () -> screenshot(client, "armor-hud-v2"));
        add("restore armor v1", 1, () -> {
            if (ModuleRegistry.armorHudMode() != null) {
                ModuleRegistry.armorHudMode().select(0);
            }
        });
        add("screenshot armor hud", 1, () -> screenshot(client, "armor-hud"));
        add("screenshot inventory hud", 1, () -> screenshot(client, "inventory-hud"));
        add("screenshot low armor hud", 1, () -> screenshot(client, "low-armor-hud"));
        add("close armor/inventory preview", 3, () -> HudManager.setEditing(false));
        add("enable custom vitals", 1, () -> {
            if (Modules.customVitals() != null) {
                Modules.customVitals().info().toggleSilently(true);
            }
        });
        add("open custom vitals preview", 8, () -> client.setScreen(new ChatScreen("", false)));
        add("check custom vitals", 5, () -> {
            if (!CustomVitalsHud.lastRenderVisible()) {
                throw new IllegalStateException("CustomVitalsHud did not render");
            }
        });
        add("screenshot custom vitals", 1, () -> screenshot(client, "custom-vitals"));
        add("close custom vitals preview", 2, () -> client.setScreen(null));
        add("disable custom vitals", 1, () -> {
            if (Modules.customVitals() != null) {
                Modules.customVitals().info().toggleSilently(false);
            }
        });
        add("open linken multiplayer", 5, () -> client.setScreen(new LinkenMultiplayerScreen(null)));
        add("screenshot linken multiplayer", 2, () -> screenshot(client, "multiplayer"));
        add("open multiplayer add modal", 2, () -> {
            if (client.currentScreen instanceof LinkenMultiplayerScreen screen) {
                screen.openAddModalForTest();
            }
        });
        add("screenshot multiplayer add", 1, () -> screenshot(client, "multiplayer-add"));
        add("close linken multiplayer", 2, () -> client.setScreen(null));
        add("clear tutorial toasts", 1, () -> client.getToastManager().clear());
        add("open create world", 12, () ->
            net.minecraft.client.gui.screen.world.CreateWorldScreen.show(client, () -> client.setScreen(null)));
        add("screenshot create world", 2, () -> screenshot(client, "create-world"));
        add("switch create world tab world", 5, () -> clickCreateWorldTab(client, 1));
        add("screenshot create world world", 2, () -> screenshot(client, "create-world-world"));
        add("switch create world tab more", 5, () -> clickCreateWorldTab(client, 2));
        add("screenshot create world more", 2, () -> screenshot(client, "create-world-more"));
        add("close create world", 2, () -> client.setScreen(null));
        add("open loading preview", 3, () -> {
            net.minecraft.client.gui.screen.ProgressScreen screen =
                new net.minecraft.client.gui.screen.ProgressScreen(false);
            screen.setTitleAndTask(Text.translatable("menu.savingLevel"));
            screen.progressStagePercentage(62);
            client.setScreen(screen);
        });
        add("screenshot loading", 2, () -> screenshot(client, "loading"));
        add("close loading preview", 2, () -> client.setScreen(null));
        add("create test scoreboard", 10, () -> {
            command(client, "scoreboard objectives remove lvtest");
            command(client, "scoreboard objectives add lvtest dummy");
            command(client, "scoreboard objectives setdisplay sidebar lvtest");
            command(client, "scoreboard objectives setdisplay list lvtest");
            command(client, "scoreboard players set Alpha lvtest 10");
            command(client, "scoreboard players set Bravo lvtest 7");
            ModuleRegistry.customScoreboard().setValue(true);
        });
        add("screenshot custom scoreboard", 10, () -> screenshot(client, "scoreboard"));
        add("open custom tab", 2, () -> {
            ModuleRegistry.customTab().setValue(true);
            forceTabTicks = 40;
            client.options.playerListKey.setPressed(true);
        });
        add("screenshot custom tab", 10, () -> screenshot(client, "tab"));
        add("close custom tab", 2, () -> {
            forceTabTicks = 0;
            client.options.playerListKey.setPressed(false);
            ModuleRegistry.customTab().setValue(false);
        });
        add("cleanup test scoreboard", 4, () -> {
            ModuleRegistry.customScoreboard().setValue(false);
            command(client, "scoreboard objectives remove lvtest");
        });
        add("summon test mobs", 10, () -> summonMobs(client));
        add("check summoned mobs", 1, () -> {
            mobsSpawned = !taggedMobs(client).isEmpty();
            commandsWorked = mobsSpawned;
            if (!mobsSpawned) {
                throw new SkipStep("мобы не появились: нужны читы/op");
            }
        });

        add("enable hit range", 1, () -> {
            if (ModuleRegistry.module("hitrange") != null) {
                ModuleRegistry.module("hitrange").info().toggleSilently(true);
            }
        });
        add("enable custom hitbox", 1, () -> {
            if (ModuleRegistry.module("customhitbox") != null) {
                ModuleRegistry.module("customhitbox").info().toggleSilently(true);
            }
        });
        add("look forward for hit range", 2, () -> {
            if (client.player != null) {
                // The ring lies at the player's feet; a steep down-pitch pushes it
                // above the viewport, so keep the horizon in frame.
                client.player.setPitch(28.0f);
            }
        });
        add("check hit range render", 3, () -> {
            if (!HitRangeRenderer.lastRenderVisible()) {
                throw new IllegalStateException("HitRange did not render");
            }
        });
        add("screenshot hit range", 1, () -> screenshot(client, "hitrange"));
        add("harden custom hitbox preview", 1, () -> {
            if (Modules.customHitBox() != null) {
                Modules.customHitBox().fillAlpha.setValue(180.0f);
                Modules.customHitBox().lineWidth.setValue(3.0f);
            }
        });
        add("summon hitbox preview cow", 2, () -> {
            if (!commandsWorked || client.player == null) {
                return;
            }
            Vec3d spawn = hitboxPreviewFeet(client.player);
            command(client, String.format(Locale.ROOT,
                "summon minecraft:cow %.2f %.2f %.2f "
                    + "{NoAI:1b,NoGravity:1b,Silent:1b,Health:1.0f,PersistenceRequired:1b,"
                    + "Tags:[\"lvt\",\"lvbox\"]}",
                spawn.x, spawn.y, spawn.z));
        });
        add("look at hitbox preview cow", 2, () -> {
            if (client.player == null) {
                return;
            }
            lookAt(client.player, hitboxPreviewFeet(client.player).add(0.0, 0.7, 0.0));
        });
        add("check custom hitbox render", 3, () -> {
            if (!mobsSpawned) {
                throw new SkipStep("no summoned mobs for CustomHitBox");
            }
            if (!CustomHitBoxRenderer.lastRenderVisible()) {
                throw new IllegalStateException("CustomHitBox did not render");
            }
        });
        add("screenshot custom hitbox", 1, () -> screenshot(client, "custom-hitbox"));
        add("restore custom hitbox preview", 1, () -> {
            if (Modules.customHitBox() != null) {
                Modules.customHitBox().fillAlpha.setValue(90.0f);
                Modules.customHitBox().lineWidth.setValue(1.5f);
            }
        });
        add("restore hit range view", 2, () -> {
            if (client.player != null) {
                client.player.setPitch(returnPitch);
                client.player.setYaw(returnYaw);
            }
        });
        add("disable hit range", 1, () -> {
            if (ModuleRegistry.module("hitrange") != null) {
                ModuleRegistry.module("hitrange").info().toggleSilently(false);
            }
        });
        add("disable custom hitbox", 1, () -> {
            if (ModuleRegistry.module("customhitbox") != null) {
                ModuleRegistry.module("customhitbox").info().toggleSilently(false);
            }
        });

        add("enable particles", 1, () -> {
            if (Modules.particles() != null) {
                Modules.particles().info().toggleSilently(true);
            }
        });
        add("look forward for particles", 2, () -> {
            if (client.player != null) {
                client.player.setPitch(18.0f);
            }
        });
        add("enlarge particles preview", 1, () -> {
            if (Modules.particles() != null) {
                Modules.particles().particleSize.setValue(1.2f);
                Modules.particles().maxParticles.setValue(800.0f);
            }
        });
        add("wait particles", 40, () -> {
        });
        add("check particles", 2, () -> {
            if (ParticlesRenderer.liveCount() == 0) {
                throw new IllegalStateException("Particles did not spawn");
            }
            if (!ParticlesRenderer.lastRenderVisible()) {
                throw new IllegalStateException("Particles did not render");
            }
        });
        add("screenshot particles", 1, () -> screenshot(client, "particles"));
        add("switch particles to cube", 1, () -> {
            if (Modules.particles() != null) {
                Modules.particles().mode.select(5);
            }
        });
        add("wait cube particles", 20, () -> {
        });
        add("screenshot cube particles", 1, () -> screenshot(client, "particles-cube"));
        add("restore particles preview", 1, () -> {
            if (Modules.particles() != null) {
                Modules.particles().mode.select(0);
                Modules.particles().particleSize.setValue(0.25f);
                Modules.particles().maxParticles.setValue(600.0f);
            }
        });
        add("disable particles", 1, () -> {
            if (Modules.particles() != null) {
                Modules.particles().info().toggleSilently(false);
            }
            ParticlesRenderer.clear();
        });
        add("restore particle view", 2, () -> {
            if (client.player != null) {
                client.player.setPitch(returnPitch);
                client.player.setYaw(returnYaw);
            }
        });

        add("enable trajectories", 1, () -> {
            if (Modules.trajectories() != null) {
                Modules.trajectories().info().toggleSilently(true);
            }
        });
        add("give trajectory pearl", 1, () -> {
            if (client.player != null) {
                trajectoryReturnStack = client.player.getMainHandStack().copy();
                client.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL));
                client.player.setPitch(55.0f);
            }
            if (Modules.blockOverlay() != null) {
                trajectoryBlockOverlay = Modules.blockOverlay().enabled();
                Modules.blockOverlay().info().toggleSilently(false);
            }
        });
        add("wait trajectory", 8, () -> {
        });
        add("check trajectory render", 1, () -> {
            if (!TrajectoriesRenderer.lastRenderVisible()) {
                throw new IllegalStateException("Trajectories did not render");
            }
        });
        add("screenshot trajectory", 1, () -> screenshot(client, "trajectories"));
        add("restore trajectory hand", 1, () -> {
            if (client.player != null) {
                client.player.setStackInHand(Hand.MAIN_HAND, trajectoryReturnStack);
                client.player.setPitch(returnPitch);
            }
            if (Modules.blockOverlay() != null) {
                Modules.blockOverlay().info().toggleSilently(trajectoryBlockOverlay);
            }
        });
        add("disable trajectories", 1, () -> {
            if (Modules.trajectories() != null) {
                Modules.trajectories().info().toggleSilently(false);
            }
        });

        add("enable item radius", 1, () -> {
            if (Modules.itemRadius() != null) {
                Modules.itemRadius().info().toggleSilently(true);
            }
        });
        add("give item radius eye", 1, () -> {
            if (client.player != null) {
                trajectoryReturnStack = client.player.getMainHandStack().copy();
                client.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.ENDER_EYE));
                client.player.setPitch(80.0f);
            }
        });
        add("wait item radius", 8, () -> {
        });
        add("check item radius render", 2, () -> {
            if (!ItemRadiusRenderer.lastRenderVisible()) {
                throw new IllegalStateException("ItemRadius did not render");
            }
        });
        add("screenshot item radius", 1, () -> screenshot(client, "item-radius"));
        add("aim flat for radius player", 1, () -> {
            if (client.player != null) {
                client.player.setPitch(0.0f);
            }
        });
        add("enable radius fake player", 1, () -> {
            if (ModuleRegistry.fakePlayer() != null) {
                ModuleRegistry.fakePlayer().toggleSilently(true);
            }
        });
        add("wait radius fake player", 20, () -> {
        });
        add("aim at radius player", 1, () -> {
            if (client.player != null && FakePlayer.entity() != null) {
                lookAt(client.player, FakePlayer.entity().getEyePos());
                client.player.setPitch(0.0f);
            }
        });
        add("give radius splash potion", 1, () -> {
            if (client.player != null) {
                trajectoryReturnStack = client.player.getMainHandStack().copy();
                client.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SPLASH_POTION));
            }
            if (Modules.itemRadius() != null) {
                Modules.itemRadius().explosivePotionRadius.setValue(30.0f);
            }
        });
        add("wait radius labels", 8, () -> {
        });
        add("check radius labels", 2, () -> {
            if (!ItemRadiusRenderer.lastLabelVisible()) {
                throw new IllegalStateException("ItemRadius splash proximity label did not render for the fake player"
                    + " (impact=" + ItemRadiusRenderer.lastPotionImpactValue()
                    + ", radius=" + ItemRadiusRenderer.lastPotionRadiusValue()
                    + ", pct=" + ItemRadiusRenderer.lastLabelPercent()
                    + ", candidates=" + ItemRadiusRenderer.lastLabelCandidates()
                    + ", depth=" + ItemRadiusRenderer.lastLabelDepth()
                    + ", player=" + (FakePlayer.entity() == null ? "missing" : FakePlayer.entity().getEntityPos()) + ")");
            }
        });
        add("screenshot radius labels", 1, () -> screenshot(client, "item-radius-labels"));
        add("restore radius labels", 1, () -> {
            FakePlayer.clear();
            if (ModuleRegistry.fakePlayer() != null) {
                ModuleRegistry.fakePlayer().toggleSilently(false);
            }
            if (client.player != null) {
                client.player.setStackInHand(Hand.MAIN_HAND, trajectoryReturnStack);
            }
            if (Modules.itemRadius() != null) {
                Modules.itemRadius().explosivePotionRadius.setValue(4.0f);
            }
        });
        add("restore item radius hand", 1, () -> {
            if (client.player != null) {
                client.player.setStackInHand(Hand.MAIN_HAND, trajectoryReturnStack);
                client.player.setPitch(returnPitch);
            }
        });
        add("disable item radius", 1, () -> {
            if (Modules.itemRadius() != null) {
                Modules.itemRadius().info().toggleSilently(false);
            }
        });

        add("enable view model", 1, () -> {
            if (Modules.viewModel() != null) {
                Modules.viewModel().info().toggleSilently(true);
            }
            ViewModelModule.resetDiagnostics();
        });
        add("pose view model", 2, () -> {
            if (Modules.viewModel() != null) {
                ViewModelModule vm = Modules.viewModel();
                vm.mainX.setValue(0.35f);
                vm.mainY.setValue(-0.05f);
                vm.mainZ.setValue(0.2f);
                vm.mainScaleX.setValue(0.8f);
                vm.mainScaleY.setValue(0.8f);
                vm.mainScaleZ.setValue(0.8f);
            }
        });
        add("wait view model", 6, () -> {
        });
        add("check view model", 2, () -> {
            ViewModelModule vm = Modules.viewModel();
            if (vm == null || !vm.enabled()) {
                throw new IllegalStateException("ViewModel module is not enabled");
            }
            if (!ViewModelModule.lastAppliedVisible()) {
                throw new IllegalStateException("ViewModel transform was not applied in first person");
            }
        });
        add("screenshot view model", 1, () -> screenshot(client, "viewmodel"));
        add("restore view model", 1, () -> {
            ViewModelModule vm = Modules.viewModel();
            if (vm != null) {
                vm.mainX.setValue(0.0f);
                vm.mainY.setValue(0.0f);
                vm.mainZ.setValue(0.0f);
                vm.mainScaleX.setValue(1.0f);
                vm.mainScaleY.setValue(1.0f);
                vm.mainScaleZ.setValue(1.0f);
                vm.offX.setValue(0.0f);
                vm.offY.setValue(0.0f);
                vm.offZ.setValue(0.0f);
                vm.offScaleX.setValue(1.0f);
                vm.offScaleY.setValue(1.0f);
                vm.offScaleZ.setValue(1.0f);
                vm.info().toggleSilently(false);
            }
            ViewModelModule.resetDiagnostics();
        });

        add("enable killstreak hud", 1, () -> {
            if (ModuleRegistry.killStreakHud() != null) {
                ModuleRegistry.killStreakHud().setValue(true);
            }
        });
        add("open killstreak preview", 3, () -> HudManager.setEditing(true));
        add("check killstreak hud", 5, () -> {
            if (!KillStreakHud.lastRenderVisible()) {
                throw new IllegalStateException("KillStreakHud did not render");
            }
        });
        add("screenshot killstreak hud", 1, () -> screenshot(client, "killstreak-hud"));
        add("close killstreak preview", 2, () -> HudManager.setEditing(false));

        add("open ClickGUI", 13, () -> client.setScreen(new ClickGuiScreen()));
        add("screenshot ClickGUI", 1, () -> screenshot(client, "clickgui"));
        add("close ClickGUI", 4, () -> client.setScreen(null));

        add("open editorial main menu", 13, () -> client.setScreen(new LinkenMainMenuScreen()));
        add("wait welcome", 20, () -> {
        });
        add("screenshot welcome", 1, () -> screenshot(client, "welcome"));
        add("skip welcome", 1, LinkenMainMenuScreen::skipWelcome);
        add("wait editorial main menu", 8, () -> {
        });
        add("screenshot editorial main menu", 1, () -> screenshot(client, "editorial-mainmenu"));
        add("close editorial main menu", 4, () -> client.setScreen(null));

        boolean pinWasSet = LinkGuiConfig.pinned("ClickGUI");
        add("pin link test", 1, () -> {
            if (!pinWasSet) {
                LinkGuiConfig.togglePin("ClickGUI");
            }
        });
        add("open link ClickGUI", 13, () -> client.setScreen(new LinkGuiScreen()));
        add("screenshot link ClickGUI", 1, () -> screenshot(client, "linkgui"));
        add("screenshot pinned ClickGUI", 1, () -> screenshot(client, "linkgui-pinned"));
        add("close link ClickGUI", 4, () -> client.setScreen(null));
        add("set UI palette", 1, () -> {
            ModuleRegistry.uiAccent().set(0xFF3366);
            ModuleRegistry.uiSecondary().set(0x33FF88);
            ModuleRegistry.themeFadeSetting().setValue(false);
            ModuleRegistry.guiBlurSetting().setValue(false);
        });
        add("check UI palette", 1, () -> {
            if (Theme.secondary() != Theme.accent()) {
                throw new IllegalStateException("Theme Fade off must collapse the secondary colour");
            }
        });
        add("open no-blur LinkGUI", 8, () -> client.setScreen(new LinkGuiScreen()));
        add("screenshot no-blur LinkGUI", 1, () -> screenshot(client, "linkgui-noblur"));
        add("close no-blur LinkGUI", 3, () -> client.setScreen(null));
        add("restore UI palette", 1, () -> {
            ModuleRegistry.uiAccent().set(Theme.DEFAULT_ACCENT);
            ModuleRegistry.uiSecondary().set(Theme.DEFAULT_SECONDARY);
            ModuleRegistry.themeFadeSetting().setValue(true);
            ModuleRegistry.guiBlurSetting().setValue(true);
        });
        add("enable custom UI colours", 1, () -> {
            ModuleRegistry.uiBackground().set(0x2A1030);
            ModuleRegistry.uiText().set(0xFFE0A0);
            ModuleRegistry.customColorsSetting().setValue(true);
            LinkGuiTheme.apply(LinkGuiTheme.preset().name());
        });
        add("check custom UI colours", 1, () -> {
            LinkGuiTheme.tick(1.0f);
            if (LinkGuiTheme.hudBase() != 0x2A1030 || LinkGuiTheme.TEXT != 0xFFE0A0) {
                throw new IllegalStateException("Custom UI colours were not applied");
            }
        });
        add("open custom-colour LinkGUI", 8, () -> client.setScreen(new LinkGuiScreen()));
        add("screenshot custom-colour LinkGUI", 1, () -> screenshot(client, "linkgui-custom-colours"));
        add("close custom-colour LinkGUI", 3, () -> client.setScreen(null));
        add("restore custom UI colours", 1, () -> {
            ModuleRegistry.customColorsSetting().setValue(false);
            ModuleRegistry.uiBackground().set(0x0F1217);
            ModuleRegistry.uiText().set(0xE6EBF2);
            LinkGuiTheme.apply(LinkGuiTheme.preset().name());
        });
        add("unpin link test", 1, () -> {
            if (!pinWasSet) {
                LinkGuiConfig.togglePin("ClickGUI");
            }
        });

        add("open classic ClickGUI", 13, () -> client.setScreen(new ClassicGuiScreen()));
        add("close classic ClickGUI", 4, () -> client.setScreen(null));

        add("open inventory", 5, () -> client.setScreen(new InventoryScreen(client.player)));
        add("close inventory", 3, () -> client.setScreen(null));

        add("enable ft helper with mouse bind", 1, () -> {
            if (Modules.ftHelper() != null) {
                Modules.ftHelper().desor.resolveMouse(0);
                Modules.ftHelper().snowball.resolveMouse(1);
                Modules.ftHelper().info().toggleSilently(true);
            }
        });
        add("prepare helper mouse selection", 1, () -> {
            if (client.player == null) {
                return;
            }
            helperReturnSelected = client.player.getInventory().getSelectedSlot();
            helperReturnSlot0 = client.player.getInventory().getStack(0).copy();
            helperReturnSlot1 = client.player.getInventory().getStack(1).copy();
            client.player.getInventory().setStack(0, new ItemStack(Items.ENDER_EYE));
            client.player.getInventory().setStack(1, new ItemStack(Items.SNOWBALL));
            client.player.getInventory().setSelectedSlot(8);
        });
        add("trigger helper LMB", 1, () -> {
            if (client.player == null) {
                return;
            }
            Modules.onHelperMouse(0);
            if (client.player.getInventory().getSelectedSlot() != 0) {
                throw new IllegalStateException("LMB helper bind did not select the ender-eye slot");
            }
        });
        add("trigger helper RMB", 1, () -> {
            if (client.player == null) {
                return;
            }
            Modules.onHelperMouse(1);
            if (client.player.getInventory().getSelectedSlot() != 1) {
                throw new IllegalStateException("RMB helper bind did not select the snowball slot");
            }
        });
        add("open helper preview", 3, () -> HudManager.setEditing(true));
        add("check helper hud", 5, () -> {
            if (!HelperItemsHud.lastRenderVisible()) {
                throw new IllegalStateException("HelperItemsHud did not render");
            }
            if (!HelperItemsHud.lastLabels().contains("LMB") || !HelperItemsHud.lastLabels().contains("RMB")) {
                throw new IllegalStateException("HelperItemsHud did not show LMB/RMB binds: "
                    + HelperItemsHud.lastLabels());
            }
        });
        add("screenshot helper hud", 1, () -> screenshot(client, "helper-items"));
        add("close helper preview", 2, () -> HudManager.setEditing(false));
        add("restore helper", 1, () -> {
            if (Modules.ftHelper() != null) {
                Modules.ftHelper().desor.clear();
                Modules.ftHelper().snowball.clear();
                Modules.ftHelper().info().toggleSilently(false);
            }
            if (client.player != null) {
                client.player.getInventory().setStack(0, helperReturnSlot0);
                client.player.getInventory().setStack(1, helperReturnSlot1);
                client.player.getInventory().setSelectedSlot(helperReturnSelected);
            }
        });

        add("enable self nametags", 1, () -> {
            if (Modules.streamerMode() != null) {
                Modules.streamerMode().customName.setValue("Hidden");
                Modules.streamerMode().info().toggleSilently(true);
            }
            if (Modules.selfNametags() != null) {
                Modules.selfNametags().info().toggleSilently(true);
            }
        });
        add("check self nametag", 2, () -> {
            if (client.player == null || !client.player.getDisplayName().getString().contains("Hidden")) {
                throw new IllegalStateException("SelfNametags did not sanitize the local display name: "
                    + (client.player == null ? "no player" : client.player.getDisplayName().getString()));
            }
        });
        add("restore self nametags", 1, () -> {
            if (Modules.selfNametags() != null) {
                Modules.selfNametags().info().toggleSilently(false);
            }
            if (Modules.streamerMode() != null) {
                Modules.streamerMode().customName.setValue("");
                Modules.streamerMode().info().toggleSilently(false);
            }
        });

        add("enable damage visuals", 1, () -> {
            if (Modules.damageParticles() != null) {
                Modules.damageParticles().info().toggleSilently(true);
            }
            if (Modules.fortniteDamage() != null) {
                Modules.fortniteDamage().info().toggleSilently(true);
                Modules.fortniteDamage().size.setValue(0.08f);
            }
        });
        add("spawn debug damage number", 1, () -> FortniteDamageRenderer.debugSpawn(client, 7));
        add("check debug damage number", 8, () -> {
            if (FortniteDamageRenderer.liveCount() <= 0) {
                throw new IllegalStateException("FortniteDamage debug number did not spawn");
            }
        });
        add("screenshot debug damage number", 1, () -> screenshot(client, "damage-number"));
        add("restore damage number size", 1, () -> {
            if (Modules.fortniteDamage() != null) {
                Modules.fortniteDamage().size.setValue(0.032f);
            }
        });
        for (int i = 0; i < 4; i++) {
            add("attack mob " + (i + 1), 3, () -> attackNearest(client));
        }
        add("check damage visuals", 4, () -> {
            if (!mobsSpawned) {
                throw new SkipStep("no summoned mobs for damage visuals");
            }
            if (DamageParticlesRenderer.liveCount() <= 0) {
                throw new IllegalStateException("DamageParticles did not spawn");
            }
            if (FortniteDamageRenderer.liveCount() <= 0) {
                throw new IllegalStateException("FortniteDamage did not spawn a number");
            }
        });
        add("screenshot damage visuals", 1, () -> screenshot(client, "damage-visuals"));
        add("check kill streak tracking", 10, () -> {
            if (!mobsSpawned) {
                throw new SkipStep("no summoned mobs for KillStreak");
            }
            if (KillStreakModule.streak() < 1) {
                throw new IllegalStateException("KillStreak did not count a kill");
            }
        });
        add("disable damage visuals", 1, () -> {
            if (Modules.damageParticles() != null) {
                Modules.damageParticles().info().toggleSilently(false);
            }
            if (Modules.fortniteDamage() != null) {
                Modules.fortniteDamage().info().toggleSilently(false);
            }
        });

        add("enable fake player", 1, () -> {
            if (ModuleRegistry.fakePlayer() != null) {
                ModuleRegistry.fakePlayer().toggleSilently(true);
            }
        });
        add("wait fake player", 20, () -> {
        });
        add("force fake-player crit", 1, () -> {
            if (client.player != null) {
                client.player.setSprinting(false);
                client.player.setOnGround(false);
                client.player.fallDistance = 1.0;
            }
        });
        add("hit fake player with crit", 2, () -> {
            if (client.player == null || FakePlayer.entity() == null) {
                throw new SkipStep("FakePlayer entity did not spawn");
            }
            if (!FakePlayer.hit(client, FakePlayer.entity(), true)) {
                throw new IllegalStateException("FakePlayer hit was not accepted");
            }
            if (!FakePlayer.lastCrit()) {
                throw new IllegalStateException("FakePlayer crit conditions were not detected: "
                    + HitSoundModule.critTrace(client.player, FakePlayer.entity()));
            }
        });
        add("restore fake player crit", 1, () -> {
            if (client.player != null) {
                client.player.fallDistance = 0.0;
                client.player.setOnGround(true);
            }
            if (ModuleRegistry.fakePlayer() != null) {
                ModuleRegistry.fakePlayer().toggleSilently(false);
            }
            FakePlayer.clear();
        });

        add("open alternative pause", 8, () -> client.setScreen(new GameMenuScreen(true)));
        add("check alternative pause route", 1, () -> {
            if (dev.linkenvisuals.BuildFlavor.STRANGE) {
                if (!(client.currentScreen instanceof GameMenuScreen)) {
                    throw new IllegalStateException("Alternative visuals modification build must keep the vanilla GameMenuScreen");
                }
            } else if (!(client.currentScreen instanceof LinkenPauseScreen)) {
                throw new IllegalStateException("GameMenuScreen was not routed to LinkenPauseScreen");
            }
        });
        add("screenshot alternative pause", 2, () -> screenshot(client, "pause"));
        add("close alternative pause", 2, () -> client.setScreen(null));

        add("jump pressed", 1, () -> client.options.jumpKey.setPressed(true));
        add("jump released", 4, () -> client.options.jumpKey.setPressed(false));
        add("sneak pressed", 1, () -> client.options.sneakKey.setPressed(true));
        add("sneak released", 6, () -> client.options.sneakKey.setPressed(false));
        add("sprint pressed", 1, () -> client.options.sprintKey.setPressed(true));
        add("sprint released", 8, () -> client.options.sprintKey.setPressed(false));
        add("use item and swing", 2, () -> useItem(client));

        buildBlockTest(client);

        for (ModuleInfo module : MODULES) {
            appendModuleSteps(module);
        }

        add("screenshot final", 2, () -> screenshot(client, "final"));
    }

    private static void buildBlockTest(MinecraftClient client) {
        BlockPos pos = findAirBlock(client);
        if (pos == null || client.player == null || !client.player.isCreative()) {
            add("block break test", 0, () -> {
                throw new SkipStep("нужен креатив и свободное место рядом");
            });
            return;
        }
        blockCoords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        add("place test block", 4, () -> {
            if (!commandsWorked) {
                throw new SkipStep("нет прав на /setblock");
            }
            command(client, "setblock " + blockCoords + " minecraft:stone");
            blockPlaced = true;
        });
        add("break test block", 3, () -> {
            if (!blockPlaced) {
                throw new SkipStep("блок не поставлен");
            }
            if (client.interactionManager == null) {
                throw new IllegalStateException("нет interactionManager");
            }
            client.interactionManager.breakBlock(pos);
            client.player.swingHand(Hand.MAIN_HAND);
        });
        add("cleanup test block", 2, () -> {
            if (blockPlaced) {
                command(client, "setblock " + blockCoords + " minecraft:air");
                blockPlaced = false;
            }
        });
    }

    private static void appendModuleSteps(ModuleInfo module) {
        boolean wasEnabled = module.enabled();
        if (!wasEnabled) {
            add("enable " + module.name(), 4, module::toggle);
        }
        add("render " + module.name(), 1, () -> {
        });
        for (Setting setting : settingsOf(module)) {
            appendSettingSteps(module, setting);
        }
        if (!wasEnabled) {
            add("disable " + module.name(), 3, module::toggle);
        }
    }

    private static void appendSettingSteps(ModuleInfo module, Setting setting) {
        String label = module.name() + "/" + setting.name();
        if (setting instanceof BooleanSetting s) {
            boolean original = s.value();
            add("bool " + label, 1, () -> s.setValue(!original));
            add("bool " + label, 1, () -> s.setValue(original));
        } else if (setting instanceof SliderSetting s) {
            float original = s.value();
            add("slider min " + label, 1, () -> s.setFraction(0.0f));
            add("slider max " + label, 1, () -> s.setFraction(1.0f));
            add("slider mid " + label, 1, () -> s.setFraction(0.5f));
            add("slider restore " + label, 1, () -> s.setValue(original));
        } else if (setting instanceof ModeSetting s) {
            int original = s.index();
            int size = s.options().size();
            for (int i = 0; i < size; i++) {
                if (i == original) {
                    continue;
                }
                int option = i;
                add("mode " + label + " #" + i, 1, () -> s.select(option));
            }
            if (size > 0) {
                add("mode restore " + label, 1, () -> s.select(original));
            }
        } else if (setting instanceof ColorSetting s) {
            int original = s.rgb();
            add("color " + label + " preset1", 1, () -> s.set(ColorSetting.PRESETS[1]));
            add("color " + label + " preset3", 1, () -> s.set(ColorSetting.PRESETS[3]));
            add("color restore " + label, 1, () -> s.set(original));
        }
        // KeybindSetting/StringSetting stay untouched: they hold user
        // bindings and free-form text rather than render parameters.
    }

    private static void add(String label, int delayTicks, Runnable action) {
        STEPS.add(new Step(label, delayTicks, action));
    }

    // ------------------------------------------------------------------
    // Game actions
    // ------------------------------------------------------------------

    private static void summonMobs(MinecraftClient client) {
        // Command tags never reach the client, so remember the entities that already
        // exist and treat the new cows/zombies as the test targets.
        KNOWN_ENTITY_IDS.clear();
        if (client.player != null && client.world != null) {
            client.world.getEntitiesByClass(LivingEntity.class,
                client.player.getBoundingBox().expand(32.0), entity -> true)
                .forEach(entity -> KNOWN_ENTITY_IDS.add(entity.getId()));
        }
        // Eight targets keep the four attack steps deterministic: a sweep or an
        // early kill can remove one mob without starving the next attack step.
        String[] offsets = {"~2 ~ ~", "~-2 ~ ~", "~ ~ ~2", "~ ~ ~-2",
            "~4 ~ ~", "~-4 ~ ~", "~ ~ ~4", "~ ~ ~-4"};
        String[] types = {"minecraft:cow", "minecraft:cow", "minecraft:zombie", "minecraft:zombie",
            "minecraft:cow", "minecraft:cow", "minecraft:zombie", "minecraft:zombie"};
        for (int i = 0; i < types.length; i++) {
            command(client, "summon " + types[i] + " " + offsets[i]
                + " {NoAI:1b,Silent:1b,Health:1.0f,PersistenceRequired:1b,Tags:[\"" + MOB_TAG + "\"]}");
        }
    }

    private static void attackNearest(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) {
            throw new IllegalStateException("нет игрока или interactionManager");
        }
        LivingEntity target = taggedMobs(client).stream()
            .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)))
            .orElse(null);
        if (target == null) {
            throw new SkipStep("нет живых мобов с тегом " + MOB_TAG);
        }
        lookAt(client.player, target.getEntityPos().add(0.0, target.getHeight() * 0.5, 0.0));
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    private static void useItem(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) {
            throw new IllegalStateException("нет игрока или interactionManager");
        }
        client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    /** Three blocks forward and high enough to clear the spawn ridge. */
    private static Vec3d hitboxPreviewFeet(ClientPlayerEntity player) {
        double yaw = Math.toRadians(player.getYaw());
        return player.getEntityPos().add(-Math.sin(yaw) * 3.0, 3.0, Math.cos(yaw) * 3.0);
    }

    private static void lookAt(ClientPlayerEntity player, Vec3d target) {
        Vec3d delta = target.subtract(player.getEyePos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        player.setYaw((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        player.setPitch((float) -Math.toDegrees(Math.atan2(delta.y, horizontal)));
    }

    private static List<LivingEntity> taggedMobs(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return List.of();
        }
        return client.world.getEntitiesByClass(LivingEntity.class,
            client.player.getBoundingBox().expand(24.0),
            entity -> entity.isAlive()
                && !KNOWN_ENTITY_IDS.contains(entity.getId())
                && (entity.getType() == EntityType.COW || entity.getType() == EntityType.ZOMBIE));
    }

    private static BlockPos findAirBlock(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return null;
        }
        BlockPos origin = client.player.getBlockPos();
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos candidate = origin.offset(direction, 2);
            if (client.world.getBlockState(candidate).isAir()
                && client.world.getBlockState(candidate.up()).isAir()
                && client.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(candidate)) <= 16.0) {
                return candidate;
            }
        }
        return null;
    }

    private static void command(MinecraftClient client, String command) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            throw new IllegalStateException("нет соединения с сервером");
        }
        handler.sendChatCommand(command);
    }

    private static void screenshot(MinecraftClient client, String label) {
        String name = "lv-autotest-" + (++screenshotId) + "-" + label + ".png";
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), 1,
            message -> {
            });
    }

    /** Clicks the LINKEN-aligned tab pill and proves the painted hit box is live. */
    private static void clickCreateWorldTab(MinecraftClient client, int index) {
        if (!(client.currentScreen instanceof net.minecraft.client.gui.screen.world.CreateWorldScreen screen)) {
            throw new IllegalStateException("Create World is not open");
        }
        net.minecraft.client.gui.widget.TabNavigationWidget tabs =
            ((dev.linkenvisuals.mixin.CreateWorldScreenAccessor) screen).linkenvisuals$tabNavigation();
        if (tabs == null) {
            throw new IllegalStateException("Create World has no tab navigation");
        }
        List<? extends Element> children = tabs.children();
        if (index < 0 || index >= children.size() || !(children.get(index) instanceof TabButtonWidget tab)) {
            throw new IllegalStateException("Create World has no tab " + index);
        }
        double centerX = tab.getX() + tab.getWidth() * 0.5;
        double centerY = tab.getY() + tab.getHeight() * 0.5;
        screen.mouseClicked(new Click(centerX, centerY, new MouseInput(0, 0)), false);
        if (!tab.isCurrentTab()) {
            throw new IllegalStateException("Create World tab " + index + " did not activate from a click");
        }
    }

    private static void releaseKeys(MinecraftClient client) {
        if (client.options == null) {
            return;
        }
        client.options.jumpKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.forwardKey.setPressed(false);
    }

    // ------------------------------------------------------------------
    // Configuration snapshot / restore
    // ------------------------------------------------------------------

    private static void snapshotConfiguration() {
        RESTORES.clear();
        MODULES.clear();
        MODULES.addAll(ModuleRegistry.all());

        for (ModuleInfo module : MODULES) {
            boolean enabled = module.enabled();
            RESTORES.add(() -> {
                if (module.enabled() != enabled) {
                    module.toggle();
                }
            });
            for (Setting setting : settingsOf(module)) {
                if (setting instanceof BooleanSetting s) {
                    boolean value = s.value();
                    RESTORES.add(() -> s.setValue(value));
                } else if (setting instanceof SliderSetting s) {
                    float value = s.value();
                    RESTORES.add(() -> s.setValue(value));
                } else if (setting instanceof ModeSetting s) {
                    int index = s.index();
                    RESTORES.add(() -> s.select(index));
                } else if (setting instanceof ColorSetting s) {
                    int rgb = s.rgb();
                    RESTORES.add(() -> s.set(rgb));
                }
            }
        }
    }

    private static void restoreConfiguration() {
        for (int i = RESTORES.size() - 1; i >= 0; i--) {
            try {
                RESTORES.get(i).run();
            } catch (Throwable t) {
                report.fail("restore config", t);
                LinkenVisuals.LOGGER.error("[AutoTest] failed to restore configuration", t);
            }
        }
        RESTORES.clear();
    }

    private static List<Setting> settingsOf(ModuleInfo module) {
        Set<Setting> unique = new LinkedHashSet<>(module.leftSettings());
        unique.addAll(module.rightSettings());
        return new ArrayList<>(unique);
    }

    private static void cleanupWorld(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        try {
            client.player.getInventory().setSelectedSlot(returnSlot);
            client.player.setYaw(returnYaw);
            client.player.setPitch(returnPitch);
        } catch (Throwable t) {
            LinkenVisuals.LOGGER.debug("[AutoTest] player state restore failed: {}", t.toString());
        }
        if (blockPlaced && blockCoords != null) {
            try {
                command(client, "setblock " + blockCoords + " minecraft:air");
            } catch (Throwable t) {
                LinkenVisuals.LOGGER.debug("[AutoTest] block cleanup failed: {}", t.toString());
            }
            blockPlaced = false;
        }
        if (!commandsWorked) {
            return;
        }
        try {
            command(client, "kill @e[tag=" + MOB_TAG + "]");
        } catch (Throwable t) {
            LinkenVisuals.LOGGER.debug("[AutoTest] mob cleanup failed: {}", t.toString());
        }
        if (returnPos != null) {
            try {
                command(client, String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f",
                    returnPos.x, returnPos.y, returnPos.z));
            } catch (Throwable t) {
                LinkenVisuals.LOGGER.debug("[AutoTest] position restore failed: {}", t.toString());
            }
        }
    }

    // ------------------------------------------------------------------
    // Report
    // ------------------------------------------------------------------

    private static int countLogLines(Path path) {
        if (path == null || !Files.exists(path)) {
            return -1;
        }
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            long count = lines.count();
            return (int) Math.min(count, Integer.MAX_VALUE);
        } catch (IOException | UncheckedIOException e) {
            LinkenVisuals.LOGGER.warn("[AutoTest] cannot read {}: {}", path, e.toString());
            return -1;
        }
    }

    private static LogScan scanLog() {
        if (logFile == null || logLineOffset < 0 || !Files.exists(logFile)) {
            return new LogScan(0, List.of());
        }
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int total = 0;
            List<String> samples = new ArrayList<>();
            for (int i = logLineOffset; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains("ERROR") || line.contains("Exception") || line.contains("Caused by:")) {
                    if (line.contains("[AutoTest]")) {
                        continue;
                    }
                    total++;
                    if (samples.size() < 25) {
                        samples.add(line);
                    }
                }
            }
            return new LogScan(total, samples);
        } catch (IOException | UncheckedIOException e) {
            LinkenVisuals.LOGGER.warn("[AutoTest] failed to scan {}: {}", logFile, e.toString());
            return new LogScan(-1, List.of());
        }
    }

    private static String writeReportFile(MinecraftClient client, String reason, LogScan scan) {
        try {
            Path dir = client.runDirectory.toPath().resolve("logs");
            Files.createDirectories(dir);
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
            Path file = dir.resolve("linkenvisuals-autotest-" + stamp + ".log");
            StringBuilder out = new StringBuilder(2048);
            out.append("Visuals Modification in-game auto-test\n");
            out.append("when: ").append(LocalDateTime.now()).append('\n');
            out.append("finish: ").append(reason).append('\n');
            out.append("dimension: ").append(client.world != null
                ? client.world.getRegistryKey().getValue().toString() : "?").append('\n');
            out.append(report.summary()).append('\n');
            if (!report.failures().isEmpty()) {
                out.append("\nFailures:\n");
                for (String failure : report.failures()) {
                    out.append("  ").append(failure).append('\n');
                }
            }
            if (!report.skips().isEmpty()) {
                out.append("\nSkipped:\n");
                for (String skip : report.skips()) {
                    out.append("  ").append(skip).append('\n');
                }
            }
            out.append("\nNew ERROR/exception lines in latest.log: ").append(scan.total()).append('\n');
            for (String sample : scan.samples()) {
                out.append("  ").append(sample).append('\n');
            }
            Files.writeString(file, out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return client.runDirectory.toPath().relativize(file).toString();
        } catch (IOException e) {
            LinkenVisuals.LOGGER.warn("[AutoTest] failed to write report: {}", e.toString());
            report.fail("report file", e);
            return null;
        }
    }

    private static void announce(MinecraftClient client, String reason, LogScan scan, String reportPath) {
        chat(client, "готово (" + reason + "): " + report.summary());
        if (!report.failures().isEmpty()) {
            chat(client, "первая ошибка: " + report.failures().get(0));
        }
        if (scan.total() > 0) {
            chat(client, "новых ERROR/исключений в latest.log: " + scan.total());
        }
        if (reportPath != null) {
            chat(client, "отчёт: " + reportPath);
        }
        if (Boolean.getBoolean(QUIT_PROPERTY)) {
            chat(client, "клиент закроется через " + (QUIT_DELAY_TICKS / 20) + " с");
        }
    }

    private static void chat(MinecraftClient client, String message) {
        String line = CHAT_PREFIX + message;
        LinkenVisuals.LOGGER.info(line);
        if (client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(Text.literal(line).formatted(Formatting.AQUA));
        }
    }

    private record Step(String label, int delayTicks, Runnable action) {
    }

    private record LogScan(int total, List<String> samples) {
    }

    private static final class SkipStep extends RuntimeException {
        private SkipStep(String message) {
            super(message);
        }
    }
}
