package ru.method.linkenvisuals.managers.system;

import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.api.events.impl.EventMouseScroll;
import ru.method.linkenvisuals.modules.impl.client.UiKeyHandler;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.client.DiscordRPC;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.modules.impl.client.Elements;
import ru.method.linkenvisuals.screen.clickgui.ClickGui;
import ru.method.linkenvisuals.modules.impl.combat.AutoSwap;
import ru.method.linkenvisuals.modules.impl.combat.CrystalOptimizer;
import ru.method.linkenvisuals.modules.impl.player.HandSwap;
import ru.method.linkenvisuals.modules.impl.combat.FriendHelper;
import ru.method.linkenvisuals.modules.impl.combat.ShiftTap;
import ru.method.linkenvisuals.modules.impl.misc.*;
import ru.method.linkenvisuals.modules.impl.player.CosmeticsModule;
import ru.method.linkenvisuals.modules.impl.player.Crasher;
import ru.method.linkenvisuals.modules.impl.movement.ClickGuiWalk;
import ru.method.linkenvisuals.modules.impl.movement.MovementHelper;
import ru.method.linkenvisuals.modules.impl.movement.Sprint;
import ru.method.linkenvisuals.modules.impl.render.*;
import ru.method.linkenvisuals.modules.settings.SettingsCollector;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.utils.Wrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter

public class ModuleManager implements Wrapper {

    private final List<Module> modules = new ArrayList<>();
    private final Map<Class<? extends Module>, Module> moduleLookup = new HashMap<>();
    private final Map<Category, List<Module>> modulesByCategory = new LinkedHashMap<>();
    private final List<Category> categories = List.of(Category.values());

    public ModuleManager() {
        LinkenVisuals.getInstance().getEventHandler().subscribe(this);

        for (Category category : Category.values()) {
            modulesByCategory.put(category, new ArrayList<>());
        }

        addModules(
                new Sprint(),
                new Trajectories(),
                new ChinaHat(),
                new MovementHelper(),
                new UI(),
                new DiscordRPC(),
                new NoRender(),
                new ItemScroller(),
                new TargetEsp(),
                new AllSkins(),
                new HitRange(),
                new SelfNametags(),
                new AutoSwap(),
                new CrystalOptimizer(),
                new HandSwap(),
                new ShiftTap(),
                new AspectRatio(),
                new CustomHitBox(),
                // TEMPORARILY DISABLED: BlockOverlay is hidden from the GUI.
                // Code kept in modules/impl/render/BlockOverlay.java and
                // utils/render/BlockOverlayRenderer.java; add `new BlockOverlay()`
                // back after the overlay rendering bugs are fixed.
                new Hands(),
                new ItemHelper(),
                new ElytraSwap(),
                new DamageParticles(),
                new Particles(),
                new FogBlur(),
                new MotionBlur(),
                new Trails(),
                new TimeChanger(),
                new SwingAnimation(),
                new Fullbright(),
                new Zoom(),
                new Freelook(),
                new CameraSettings(),
                new Optimization(),
                new JumpCircles(),
                new ItemPhysics(),
                new FtHelper(),
                new FriendHelper(),
                new RwHelper(),
                new AutoNear(),
                new AutoRelist(),
                new AutoResell(),
                new AutoDuel(),
                new Profiler(),
                new AuctionHelper(),
                new AutoSpec(),
                new CoordInvite(),
                new DeathCoords(),
                new ChatHelper(),
                new ClickPearl(),
                new CMDDelay(),
                new ItemPickup(),
                new LowArmorNotify(),
                new LockSlot(),
                new MineHelper(),
                new PlayerUtils(),
                new PvpSave(),
                new TapeMouse(),
                new WayPoints(),
                new PartyModule(),
                new AutoAccept(),
                new HwHelper(),
                new PotionTracker(),
                new UseTracker(),
                new HitSound(),
                new Sounds(),
                new KillEffect(),
                new KillStreak(),
                new ScoreboardHealth(),
                new StreamerMode(),
                new ClickGuiWalk(),
                new TotemTracker(),
                new TotemCounter(),
                new ShulkerTooltip(),
                new ItemRadius(),
                new Drochka(),
                new SkyChanger(),
                new HitColor(),
                new Crosshair(),
                new FortniteDamage(),
                new CustomVitals(),
                new FastExp(),
                new AutoReply(),
                new FakePlayer(),
                new FakeCosmetic(),
                new Elements(),
                new CosmeticsModule(),
                new Crasher()
        );

        for (Module module : modules) {
            SettingsCollector.collectDeclaredSettings(module, module.getSettings(), module.getName());
        }
    }

    private void addModules(Module... newModules) {
        for (Module module : newModules) {
            modules.add(module);
            moduleLookup.put(module.getClass(), module);
            modulesByCategory.computeIfAbsent(module.getCategory(), c -> new ArrayList<>()).add(module);
        }
    }

    @EventHandler
    public void onKey(EventKey e) {
        if (LinkenVisuals.getInstance().isPanic()) return;

        if (e.getAction() == 1) {
            int key = e.getKey();
            if (key < 0) return;

            UI ui = getModule(UI.class);
            if (ui != null) {
                Bind uiBind = ui.getBind();
                if (uiBind != null && (UiKeyHandler.matchesUiBind(ui, key) || uiBind.matchesKey(key))) {
                    return;
                }
            }

            if (Module.fullNullCheck() || mc.currentScreen != null) return;

            for (Module module : modules) {
                if (module instanceof UI) continue;
                Bind bind = module.getBind();
                if (bind.isEmpty() || bind.isMouse()) continue;

                if (bind.getKey() == key) module.toggle();
            }
        }
    }

    @EventHandler
    public void onMouse(EventMouse e) {
        if (Module.fullNullCheck() || mc.currentScreen != null || LinkenVisuals.getInstance().isPanic()) return;

        if (e.getAction() == 1) {
            int button = e.getButton();
            if (button < 0) return;

            for (Module module : modules) {
                Bind bind = module.getBind();
                if (bind.isEmpty() || !bind.isMouse()) continue;

                if (bind.getKey() == button) module.toggle();
            }
        }
    }

    @EventHandler
    public void onScroll(EventMouseScroll e) {
        if (Module.fullNullCheck() || mc.currentScreen != null || LinkenVisuals.getInstance().isPanic()) return;

        double vertical = e.getVerticalAmount();
        if (vertical == 0.0) return;

        for (Module module : modules) {
            if (module instanceof UI) continue;
            if (module.getBind().matchesMouseScroll(vertical)) module.toggle();
        }
    }

    /**
     * Removes a module from the legacy UI/keybind/config lists without firing
     * toggle feedback. Used when a module has moved to the object-based
     * registry and must not be exposed twice.
     */
    public void removeModule(Class<? extends Module> clazz) {
        Module module = moduleLookup.remove(clazz);
        if (module == null) {
            return;
        }
        modules.remove(module);
        for (List<Module> categoryModules : modulesByCategory.values()) {
            categoryModules.remove(module);
        }
    }

    public List<Module> getModules(Category category) {
        return modulesByCategory.getOrDefault(category, List.of());
    }

    public List<Category> getCategories() {
        return categories;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        return (T) moduleLookup.get(clazz);
    }
}
