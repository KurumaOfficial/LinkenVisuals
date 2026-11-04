package ru.method.linkenvisuals.screen.world;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.EditWorldScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.world.level.storage.LevelSummary;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.screen.multiplayer.CustomMultiplayerScreen;

import java.io.InputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Singleplayer world list rendered by the exact same multiplayer-screen chrome: same backdrop,
 * top bar, search, row material, animations, footer and window corners. Only the data source and
 * the click actions differ (integrated-server loader / EditWorldScreen / CreateWorldScreen).
 */
public final class CustomSingleplayerScreen extends CustomMultiplayerScreen {

    private final Map<ServerInfo, LevelSummary> worldByInfo = new IdentityHashMap<>();
    private final Map<LevelSummary, NativeImageBackedTexture> icons = new IdentityHashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT);

    public CustomSingleplayerScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected boolean worldsMode() {
        return true;
    }

    @Override
    protected String titleText() {
        return "ОДИНОЧНАЯ ИГРА";
    }

    @Override
    protected String searchPlaceholder() {
        return "Поиск по мирам...";
    }

    @Override
    protected String footerAddLabel() {
        return "СОЗДАТЬ МИР";
    }

    @Override
    protected String footerJoinLabel() {
        return "ИГРАТЬ";
    }

    @Override
    protected String footerDirectLabel() {
        return "ОБНОВИТЬ";
    }

    @Override
    protected String emptyTitle() {
        return "Миров пока нет";
    }

    @Override
    protected String emptySearchTitle() {
        return "Ничего не найдено";
    }

    @Override
    protected boolean showAccountPill() {
        return false;
    }

    @Override
    protected boolean showStatus() {
        return false;
    }

    @Override
    protected boolean usePinnedServers() {
        return false;
    }

    @Override
    protected boolean pingServers() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        loadWorlds();
    }

    private void loadWorlds() {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            var storage = client.getLevelStorage();
            storage.loadSummaries(storage.getLevelList()).whenComplete((list, error) -> client.execute(() -> {
                List<ServerInfo> entries = new ArrayList<>();
                worldByInfo.clear();
                icons.clear();
                if (list != null) {
                    for (LevelSummary world : list) {
                        if (world == null || !world.isSelectable()) {
                            continue;
                        }
                        String details = world.getDetails() == null ? "" : world.getDetails().getString();
                        String when = dateFormat.format(new Date(world.getLastPlayed()));
                        ServerInfo info = new ServerInfo(world.getDisplayName(), world.getName(), ServerInfo.ServerType.OTHER);
                        info.label = Text.literal(details + "  •  " + when);
                        info.playerCountLabel = Text.empty();
                        info.version = Text.literal("1.21.11");
                        info.ping = 1L;
                        worldByInfo.put(info, world);
                        entries.add(info);
                    }
                }
                setServerEntries(entries);
            }));
        } catch (Throwable error) {
            LinkenVisuals.LOGGER.warn("[Singleplayer] Could not load worlds: {}", error.toString());
        }
    }

    @Override
    protected void connectSelected() {
        LevelSummary world = selectedWorld();
        if (world == null) {
            return;
        }
        try {
            mc.createIntegratedServerLoader().start(world.getName(), () -> mc.setScreen(this));
        } catch (Throwable error) {
            LinkenVisuals.LOGGER.warn("[Singleplayer] Could not start {}: {}", world.getName(), error.toString());
        }
    }

    @Override
    protected void openAdd() {
        CreateWorldScreen.show(mc, () -> mc.setScreen(this));
    }

    @Override
    protected void openDirect() {
        loadWorlds();
    }

    @Override
    protected void openEdit() {
        LevelSummary world = selectedWorld();
        if (world == null) {
            return;
        }
        try {
            mc.setScreen(EditWorldScreen.create(mc, mc.getLevelStorage().createSession(world.getName()),
                    confirmed -> mc.setScreen(this)));
        } catch (Throwable error) {
            LinkenVisuals.LOGGER.warn("[Singleplayer] Could not edit {}: {}", world.getName(), error.toString());
        }
    }

    @Override
    protected void askDelete() {
        // EditWorldScreen contains the vanilla delete flow, including the confirmation prompt.
        openEdit();
    }

    @Override
    protected void openAccountSwitcher() {
        // Accounts are intentionally not part of the singleplayer/settings UI.
    }

    @Override
    protected NativeImageBackedTexture rowIcon(ServerInfo info) {
        LevelSummary world = worldByInfo.get(info);
        if (world == null || world.getIconPath() == null) {
            return null;
        }
        return icons.computeIfAbsent(world, value -> {
            try (InputStream in = Files.newInputStream(value.getIconPath())) {
                NativeImage image = NativeImage.read(in);
                return new NativeImageBackedTexture(() -> "singleplayer_world_icon", image);
            } catch (Throwable ignored) {
                return null;
            }
        });
    }

    private LevelSummary selectedWorld() {
        ServerInfo info = selectedServerInfo();
        return info == null ? null : worldByInfo.get(info);
    }
}
