package dev.linkenvisuals.clickgui;

import dev.linkenvisuals.BuildFlavor;
import dev.linkenvisuals.clickgui.setting.BooleanSetting;
import dev.linkenvisuals.clickgui.setting.ColorSetting;
import dev.linkenvisuals.clickgui.setting.KeybindSetting;
import dev.linkenvisuals.clickgui.setting.ModeSetting;
import dev.linkenvisuals.clickgui.setting.Setting;
import dev.linkenvisuals.clickgui.setting.SliderSetting;
import dev.linkenvisuals.module.Module;

import dev.linkenvisuals.ui.Theme;
import dev.linkenvisuals.ui.style.StyleFlavor;
import dev.linkenvisuals.ui.editor.MainMenuConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleRegistry {
    private static final List<ModuleInfo> MODULES = new ArrayList<>();
    private static final Map<String, Module> OBJECT_MODULES = new LinkedHashMap<>();

    private static ModeSetting guiStyle;
    private static ModeSetting closeAnimation;
    private static ModeSetting hudStyle;
    private static ModeSetting hudShape;
    private static BooleanSetting customScoreboard;
    private static BooleanSetting customTab;
    private static BooleanSetting targetHud;
    private static BooleanSetting coordinatesHud;
    private static BooleanSetting cooldownsHud;
    private static BooleanSetting armorHud;
    private static ModeSetting armorHudMode;
    private static BooleanSetting inventoryHud;
    private static BooleanSetting inventoryHotbar;
    private static BooleanSetting lowArmorNotifications;
    private static BooleanSetting killStreakHud;
    private static SliderSetting scoreboardRadius;
    private static SliderSetting scoreboardOpacity;
    private static SliderSetting tabOpacity;
    private static ColorSetting tabColor;

    private static ModuleInfo shaderSky;
    private static ModeSetting skyMode;
    private static SliderSetting skySpeed;
    private static SliderSetting skyIntensity;
    private static SliderSetting skyStars;
    private static SliderSetting skyScale;
    private static ModeSetting rainMode;
    private static SliderSetting rainStrength;
    private static ColorSetting rainColor;

    private static ModuleInfo targetMarks;
    private static ModeSetting targetStyle;
    private static ColorSetting targetColor;
    private static SliderSetting targetSize;
    private static BooleanSetting targetReact;
    private static SliderSetting targetHold;
    private static SliderSetting targetSpeed;

    private static ModuleInfo hitEffects;
    private static ModeSetting hitStyle;
    private static ColorSetting hitColor;
    private static SliderSetting hitSize;
    private static SliderSetting hitLife;

    private static ModuleInfo nameTags;
    private static SliderSetting nameTagScale;
    private static BooleanSetting nameTagItem;

    private static ModuleInfo killEffects;
    private static ModeSetting killStyle;
    private static ColorSetting killColor;
    private static SliderSetting killSize;
    private static SliderSetting killLife;

    private static ModuleInfo fakePlayer;
    private static ModeSetting fakeMode;
    private static SliderSetting fakeHealth;
    private static BooleanSetting fakeRespawn;
    private static BooleanSetting fakeNameTag;

    private static ModuleInfo ambience;
    private static ModeSetting ambienceTime;
    private static SliderSetting ambienceCustom;
    private static SliderSetting gradeExposure;
    private static ModeSetting gradeLook;
    private static SliderSetting gradeSaturation;
    private static SliderSetting gradeVignette;
    private static SliderSetting bloomStrength;
    private static BooleanSetting ambienceFog;
    private static SliderSetting fogDistance;
    private static ColorSetting fogColor;

    private static ModuleInfo filters;
    private static SliderSetting filterSaturation;
    private static SliderSetting filterBrightness;
    private static SliderSetting filterContrast;
    private static SliderSetting filterVignette;
    private static SliderSetting filterExposure;
    private static SliderSetting filterGamma;
    private static SliderSetting filterGrain;
    private static BooleanSetting filterUseTint;
    private static ColorSetting filterTintColor;
    private static SliderSetting filterTintStrength;

    private static SliderSetting uiRadius;
    private static ColorSetting uiAccent;
    private static ColorSetting uiSecondary;
    private static BooleanSetting themeFade;
    private static BooleanSetting guiBlur;
    private static BooleanSetting uiCustomColors;
    private static ColorSetting uiBackground;
    private static ColorSetting uiText;
    private static ColorSetting skyColor;
    private static ModuleInfo mediaPlayer;
    private static ModuleInfo betterAnimations;
    private static ModuleInfo aspectRatio;
    private static SliderSetting aspectRatioValue;
    private static BooleanSetting animChat;
    private static BooleanSetting animTab;
    private static BooleanSetting animInventory;
    private static BooleanSetting animPerspective;
    private static BooleanSetting animHand;
    private static BooleanSetting albumArt;
    private static BooleanSetting lyricsEnabled;
    private static ColorSetting lyricsColour;
    private static SliderSetting lyricsSize;
    private static SliderSetting lyricsSpacing;

    private static ModuleInfo cosmetics;
    private static ModeSetting cosmeticCrown;
    private static ModeSetting cosmeticBack;
    private static ModeSetting cosmeticAura;
    private static ModeSetting cosmeticCharacter;
    private static ColorSetting cosmeticColor;
    private static SliderSetting cosmeticSize;
    private static SliderSetting cosmeticSpeed;
    private static ModeSetting cosmeticPet;
    private static SliderSetting cosmeticPetRadius;
    private static SliderSetting cosmeticPetHeight;
    private static SliderSetting cosmeticPetSpeed;
    private static SliderSetting cosmeticPetScale;
    private static BooleanSetting cosmeticPetFirstPerson;
    private static SliderSetting cosmeticPetAlpha;
    private static SliderSetting cosmeticPetGlow;
    private static ColorSetting cosmeticPetGlowColor;
    private static KeybindSetting cosmeticPetUlt;

    private static ModuleInfo betterHands;
    private static ModeSetting handOutline;
    private static ModeSetting handMotion;
    private static ColorSetting handOutlineColor;

    private static SliderSetting handStrength;
    private static SliderSetting handTrailStrength;

    private static BooleanSetting tintWatermark;
    private static BooleanSetting tintHotbar;
    private static BooleanSetting tintPotions;
    private static BooleanSetting tintKeybinds;

    private static ModuleInfo customScreens;
    private static BooleanSetting screenLoading;
    private static BooleanSetting screenMultiplayer;
    private static BooleanSetting screenSettings;
    private static BooleanSetting screenPause;
    private static BooleanSetting screenTitle;
    private static BooleanSetting screenHotbar;
    private static BooleanSetting screenStatusEffects;

    private static ModuleInfo configModule;
    private static ModeSetting configProfile;
    private static BooleanSetting configAutoSave;
    private static SliderSetting linkUiScale;
    private static SliderSetting linkUiTransparency;
    private static BooleanSetting linkCursorTracker;

    static {
        guiStyle = new ModeSetting("GUI Style", StyleFlavor.guiStyleOptions(), 0, style -> {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null || mc.currentScreen == null) {
                return;
            }
            boolean onGui = mc.currentScreen instanceof ClickGuiScreen
                || mc.currentScreen instanceof dev.linkenvisuals.clickgui.classic.ClassicGuiScreen
                || mc.currentScreen instanceof dev.linkenvisuals.clickgui.link.LinkGuiScreen;
            if (!onGui) {
                return;
            }
            mc.setScreen(dev.linkenvisuals.ui.style.StyleRouting.resolveGuiScreen(style));
        });
        closeAnimation = new ModeSetting("Close Animation", List.of("Shatter", "Zoom Out"), 0);
        hudStyle = new ModeSetting("HUD Style", StyleFlavor.hudStyleOptions(), 0);
        hudShape = new ModeSetting("HUD Shape", StyleFlavor.hudShapeOptions(), 0);
        customScoreboard = new BooleanSetting("Custom Scoreboard", false);
        scoreboardRadius = new SliderSetting("Scoreboard Radius", 0.0f, 18.0f, 0.5f, 9.0f, null);
        scoreboardOpacity = new SliderSetting("Scoreboard Opacity", 0.0f, 100.0f, 1.0f, 80.0f, "%");
        customTab = new BooleanSetting("Custom Tab", false);
        targetHud = new BooleanSetting("Target HUD", true);
        coordinatesHud = new BooleanSetting("Coordinates HUD", false);
        cooldownsHud = new BooleanSetting("Cooldowns HUD", true);
        armorHud = new BooleanSetting("Armor HUD", true);
        armorHudMode = new ModeSetting("Armor HUD Mode", List.of("V1", "V2"), 0);
        inventoryHud = new BooleanSetting("Inventory HUD", false);
        inventoryHotbar = new BooleanSetting("Inventory Hotbar", false);
        lowArmorNotifications = new BooleanSetting("Low Armor Notifications", true);
        killStreakHud = new BooleanSetting("Kill Streak HUD", true);
        tabOpacity = new SliderSetting("Tab Opacity", 0.0f, 100.0f, 1.0f, 80.0f, "%");
        tabColor = new ColorSetting("Tab Colour", 0x0F1116);
        uiRadius = new SliderSetting("Corner Rounding", 0.0f, 10.0f, 1.0f, 5.0f, null);
        linkUiScale = new SliderSetting("Interface Scale", 0.75f, 1.5f, 0.05f, 1.0f, "x");
        linkUiTransparency = new SliderSetting("GUI Transparency", 30.0f, 100.0f, 1.0f, 100.0f, "%");
        linkCursorTracker = new BooleanSetting("Cursor Tracker", true);
        uiAccent = new ColorSetting("Accent Colour", Theme.DEFAULT_ACCENT);
        uiSecondary = new ColorSetting("Secondary Colour", Theme.DEFAULT_SECONDARY);
        themeFade = new BooleanSetting("Theme Fade", true);
        guiBlur = new BooleanSetting("GUI Blur", true);
        uiCustomColors = new BooleanSetting("Custom Theme Colours", false, value ->
            dev.linkenvisuals.clickgui.link.LinkGuiTheme.apply(
                dev.linkenvisuals.clickgui.link.LinkGuiTheme.preset().name()));
        uiBackground = new ColorSetting("Background Colour", 0x0F1217);
        uiText = new ColorSetting("Text Colour", 0xE6EBF2);
        tintWatermark = new BooleanSetting("Colour Watermark", true);
        tintHotbar = new BooleanSetting("Colour Hotbar", true);
        tintPotions = new BooleanSetting("Colour Potions", true);
        tintKeybinds = new BooleanSetting("Colour Keybinds", true);
        List<Setting> interfaceLeft = new ArrayList<>(List.of(
            guiStyle, linkUiScale, linkUiTransparency, linkCursorTracker, closeAnimation));
        if (!StyleFlavor.isHudStyleLocked()) {
            interfaceLeft.add(hudStyle);
            interfaceLeft.add(hudShape);
        }
        interfaceLeft.addAll(List.of(targetHud, coordinatesHud, cooldownsHud, armorHud, armorHudMode,
            inventoryHud, inventoryHotbar, lowArmorNotifications, killStreakHud, customScoreboard,
            scoreboardRadius, scoreboardOpacity, customTab, tabColor, tabOpacity));
        ModuleInfo clickGui = new ModuleInfo("ClickGUI", "interface look and feel (Ctrl+Left/Right to switch)", Category.VISUAL,
            interfaceLeft,
            List.of(uiRadius, uiAccent, uiSecondary, themeFade, guiBlur,
                uiCustomColors, uiBackground, uiText,
                tintWatermark, tintHotbar, tintPotions, tintKeybinds));
        clickGui.toggleSilently(true);
        clickGui.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.INTERFACE);
        MODULES.add(clickGui);

        screenLoading = new BooleanSetting("Loading Screens", true, val -> {
            MainMenuConfig.get().customLoadingScreen = val;
            MainMenuConfig.save();
            updateScreensModuleState();
        });
        screenMultiplayer = new BooleanSetting("Server List", true, val -> {
            MainMenuConfig.get().customMultiplayerScreen = val;
            MainMenuConfig.save();
            updateScreensModuleState();
        });
        screenSettings = new BooleanSetting("Settings Menu", true, val -> {
            MainMenuConfig.get().customSettingsScreen = val;
            MainMenuConfig.save();
            updateScreensModuleState();
        });
        screenPause = new BooleanSetting("Pause Menu", true, val -> {
            MainMenuConfig.get().customPauseScreen = val;
            MainMenuConfig.save();
            updateScreensModuleState();
        });
        screenTitle = new BooleanSetting("Main Menu", true, val -> {
            MainMenuConfig.get().useVanillaTitleScreen = !val;
            MainMenuConfig.save();
            updateScreensModuleState();
        });
        screenHotbar = new BooleanSetting("Custom Hotbar", true, val -> {
            MainMenuConfig.get().customHotbar = val;
            MainMenuConfig.save();
            updateScreensModuleState();
        });
        screenStatusEffects = new BooleanSetting("Status Effects", true, val -> {
            MainMenuConfig.get().customStatusEffects = val;
            MainMenuConfig.save();
            updateScreensModuleState();
        });

        customScreens = new ModuleInfo("Custom Screens", "custom interface and screen themes", Category.VISUAL,
            List.of(screenLoading, screenMultiplayer, screenSettings, screenPause),
            List.of(screenTitle, screenHotbar, screenStatusEffects),
            enabled -> {
                screenLoading.setValue(enabled);
                screenMultiplayer.setValue(enabled);
                screenSettings.setValue(enabled);
                screenPause.setValue(enabled);
                screenTitle.setValue(enabled);
                screenHotbar.setValue(enabled);
                screenStatusEffects.setValue(enabled);
                MainMenuConfig cfg = MainMenuConfig.get();
                cfg.setCustomStyleActive(enabled);
                MainMenuConfig.save();
            });
        customScreens.toggleSilently(true);
        customScreens.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.INTERFACE);
        // "Custom Screens" removed from GUI as requested

        skyMode = new ModeSetting("Style", List.of("Veil", "Facet", "Inkwell", "Halo", "Drift",
            "Cirrus", "Rift", "Lattice", "Tide", "Nebula", "Corona"), 0);
        skySpeed = new SliderSetting("Speed", 0.1f, 3.0f, 0.05f, 1.0f, "x");
        skyIntensity = new SliderSetting("Opacity", 0.0f, 100.0f, 1.0f, 100.0f, "%");
        skyStars = new SliderSetting("Stars", 0.0f, 100.0f, 1.0f, 75.0f, "%");
        skyScale = new SliderSetting("Pattern Size", 0.4f, 2.5f, 0.05f, 1.0f, "x");
        albumArt = new BooleanSetting("Album Art", true);
        lyricsEnabled = new BooleanSetting("3D Lyrics", false);
        lyricsColour = new ColorSetting("Lyrics Colour", Theme.DEFAULT_ACCENT);
        lyricsSize = new SliderSetting("Lyrics Size", 0.5f, 4.0f, 0.1f, 1.4f, "x");
        lyricsSpacing = new SliderSetting("Show Every", 1.0f, 6.0f, 1.0f, 1.0f, " line");
        mediaPlayer = new ModuleInfo("Media Player", "shows what is playing", Category.VISUAL,
            List.of(albumArt, lyricsEnabled, lyricsColour),
            List.of(lyricsSize, lyricsSpacing));
        mediaPlayer.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.INTERFACE);
        MODULES.add(mediaPlayer);

        animChat = new BooleanSetting("Chat", true);
        animTab = new BooleanSetting("Tab List", true);
        animInventory = new BooleanSetting("Inventory", true);
        animPerspective = new BooleanSetting("Camera (F5)", true);
        animHand = new BooleanSetting("Hand Motion", true);
        betterAnimations = new ModuleInfo("Better Animations", "eases what vanilla snaps", Category.VISUAL,
            List.of(animChat, animTab), List.of(animInventory, animPerspective, animHand));
        betterAnimations.toggleSilently(true);
        betterAnimations.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.INTERFACE);
        MODULES.add(betterAnimations);

        aspectRatioValue = new SliderSetting("Aspect Ratio", 0.1f, 5.0f, 0.05f, 1.75f, null);
        aspectRatio = new ModuleInfo("Aspect Ratio", "forces a custom projection aspect", Category.VISUAL,
            List.of(aspectRatioValue), List.of());
        aspectRatio.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.WORLD);
        MODULES.add(aspectRatio);

        List<String> crownOpts = List.of("None", "Nimb");
        List<String> backOpts = List.of("None", "WaveCape");
        List<String> auraOpts = List.of("None", "Spheres");
        List<String> charOpts = List.of("Default", "Rudy", "Astolfo");

        cosmeticCrown = new ModeSetting("Crown", crownOpts, crownOpts.indexOf("Nimb") >= 0 ? crownOpts.indexOf("Nimb") : 0);
        cosmeticBack = new ModeSetting("Back", backOpts, backOpts.indexOf("WaveCape") >= 0 ? backOpts.indexOf("WaveCape") : 0);
        cosmeticAura = new ModeSetting("Aura", auraOpts, 0);
        cosmeticCharacter = new ModeSetting("Character", charOpts, 0);
        cosmeticColor = new ColorSetting("Colour", Theme.DEFAULT_ACCENT);

        cosmeticSize = new SliderSetting("Size", 0.6f, 1.5f, 0.05f, 1.0f, "x");
        cosmeticSpeed = new SliderSetting("Speed", 0.2f, 3.0f, 0.05f, 1.0f, "x");
        cosmeticPet = new ModeSetting("Pet", dev.linkenvisuals.pet.PetSkins.labels(), 0);
        cosmeticPetRadius = new SliderSetting("Pet Radius", 0.15f, 2.5f, 0.05f, 0.65f, "m");
        cosmeticPetHeight = new SliderSetting("Pet Lift", -0.5f, 3.0f, 0.05f, 0.95f, "m");
        cosmeticPetSpeed = new SliderSetting("Pet Speed", 0.05f, 5.0f, 0.05f, 1.15f, "x");
        cosmeticPetScale = new SliderSetting("Pet Size", 0.15f, 2.0f, 0.05f, 0.55f, "x");
        cosmeticPetFirstPerson = new BooleanSetting("Pet First Person", true);
        cosmeticPetAlpha = new SliderSetting("Pet Alpha", 0f, 100f, 1f, 82f, "%");
        cosmeticPetGlow = new SliderSetting("Pet Glow", 0f, 100f, 1f, 65f, "%");
        cosmeticPetGlowColor = new ColorSetting("Pet Glow Colour", 0xFF141414);
        cosmeticPetUlt = new KeybindSetting("Pet ULT", "NONE");
        cosmetics = new ModuleInfo("Cosmetics", "worn light, yours only", Category.VISUAL,
            List.of(cosmeticCrown, cosmeticBack, cosmeticAura, cosmeticCharacter, cosmeticPet),
            List.of(cosmeticColor, cosmeticSize, cosmeticSpeed, cosmeticPetRadius, cosmeticPetHeight,
                cosmeticPetSpeed, cosmeticPetScale, cosmeticPetFirstPerson, cosmeticPetAlpha,
                cosmeticPetGlow, cosmeticPetGlowColor, cosmeticPetUlt));
        cosmetics.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.PLAYER);
        MODULES.add(cosmetics);

        handOutline = new ModeSetting("Outline", List.of("None", "Line", "Glow", "Aura", "Energy"), 4);
        handMotion = new ModeSetting("Trail", List.of("None", "Soft", "Long", "Smoke"), 3);
        handOutlineColor = new ColorSetting("Colour", Theme.DEFAULT_ACCENT);
        handStrength = new SliderSetting("Outline Strength", 0.2f, 2.5f, 0.05f, 1.2f, "x");
        handTrailStrength = new SliderSetting("Trail Strength", 0.2f, 3.0f, 0.05f, 1.2f, "x");
        betterHands = new ModuleInfo("Better Hands", "lit edges, and what comes off them",
            Category.VISUAL,
            List.of(handOutline, handMotion, handOutlineColor),
            List.of(handStrength, handTrailStrength));
        betterHands.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.PLAYER);
        MODULES.add(betterHands);

        skyColor = new ColorSetting("Sky Colour", Theme.DEFAULT_ACCENT);

        rainMode = new ModeSetting("Rain", List.of("Off", "Fast", "Quality"), 2);
        rainStrength = new SliderSetting("Rain Strength", 0.2f, 2.0f, 0.05f, 1.0f, "x");
        rainColor = new ColorSetting("Rain Colour", 0xBFD4FF);
        targetStyle = new ModeSetting("Style", List.of("Orbit", "Cubes", "Sweep"), 0);
        targetReact = new BooleanSetting("React To Hits", true);
        targetColor = new ColorSetting("Colour", Theme.DEFAULT_ACCENT);
        targetSize = new SliderSetting("Size", 0.5f, 2.5f, 0.05f, 1.0f, "x");
        targetHold = new SliderSetting("Hold", 0.0f, 5.0f, 0.1f, 1.8f, "s");
        targetSpeed = new SliderSetting("Speed", 0.2f, 3.0f, 0.05f, 1.0f, "x");
        hitStyle = new ModeSetting("Style", List.of("Impact", "Slash", "Shock", "Bubble"), 3);
        hitColor = new ColorSetting("Colour", Theme.DEFAULT_ACCENT);
        hitSize = new SliderSetting("Size", 0.3f, 2.5f, 0.05f, 1.0f, "x");
        hitLife = new SliderSetting("Duration", 0.2f, 1.5f, 0.05f, 0.5f, "s");
        hitEffects = new ModuleInfo("Hit Effect", "what a landed blow leaves behind",
            Category.COMBAT, List.of(hitStyle, hitColor), List.of(hitSize, hitLife));
        hitEffects.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.PLAYER);
        MODULES.add(hitEffects);
        killStyle = new ModeSetting("Style", List.of("Soul", "Portal", "Tornado", "Shatter"), 0);
        killColor = new ColorSetting("Colour", Theme.DEFAULT_ACCENT);
        killSize = new SliderSetting("Size", 0.5f, 3.0f, 0.05f, 1.0f, "x");
        killLife = new SliderSetting("Duration", 1.5f, 8.0f, 0.1f, 4.5f, "s");
        killEffects = new ModuleInfo("Kill Effect", "what leaves when they go down",
            Category.COMBAT, List.of(killStyle), List.of(killColor, killSize, killLife));
        killEffects.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.PLAYER);
        MODULES.add(killEffects);
        nameTagScale = new SliderSetting("Scale", 0.6f, 1.6f, 0.05f, 1.0f, "x");
        nameTagItem = new BooleanSetting("Held Item", true);
        nameTags = new ModuleInfo("Name Tags", "who is in front of you, at a glance",
            Category.VISUAL, List.of(), List.of(nameTagScale, nameTagItem));
        nameTags.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.PLAYER);
        MODULES.add(nameTags);
        targetMarks = new ModuleInfo("Target ESP", "a rig round whoever you aim at",
            Category.COMBAT, List.of(targetStyle, targetReact), List.of(targetColor, targetSize, targetHold, targetSpeed));
        targetMarks.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.PLAYER);
        MODULES.add(targetMarks);
        fakeMode = new ModeSetting("Damage", List.of("Killable", "Invincible"), 0);
        fakeHealth = new SliderSetting("Health", 10.0f, 200.0f, 5.0f, 20.0f, null);
        fakeRespawn = new BooleanSetting("Respawn", true);
        fakeNameTag = new BooleanSetting("Name Tag", true);
        fakePlayer = new ModuleInfo("Fake Player", "a dummy only you can see", Category.PLAYER,
            List.of(fakeMode, fakeRespawn),
            List.of(fakeHealth, fakeNameTag));
        fakePlayer.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.UTILITIES);
        MODULES.add(fakePlayer);
        shaderSky = new ModuleInfo("Shaders", "the sky, and the weather on it", Category.VISUAL,
            List.of(skyMode, skySpeed, skyScale, rainMode),
            List.of(skyIntensity, skyStars, skyColor, rainStrength, rainColor));
        shaderSky.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.WORLD);
        MODULES.add(shaderSky);

        ambienceTime = new ModeSetting("Time",
            List.of("Dawn", "Morning", "Noon", "Sunset", "Dusk", "Midnight", "Custom"), 5);
        ambienceCustom = new SliderSetting("Custom Time", 0.0f, 24000.0f, 100.0f, 18000.0f, null);

        gradeLook = new ModeSetting("Look",
            List.of("Natural", "Cinematic", "Cold Night", "Golden Hour", "Dream"), 1);
        gradeExposure = new SliderSetting("Brightness", 50.0f, 180.0f, 1.0f, 100.0f, "%");
        gradeSaturation = new SliderSetting("Colour", 0.0f, 200.0f, 1.0f, 112.0f, "%");
        gradeVignette = new SliderSetting("Edge Darkening", 0.0f, 100.0f, 1.0f, 30.0f, "%");
        bloomStrength = new SliderSetting("Glow", 0.0f, 200.0f, 1.0f, 55.0f, "%");
        ambienceFog = new BooleanSetting("Fog", false);
        fogDistance = new SliderSetting("Fog Range", 8.0f, 320.0f, 4.0f, 120.0f, null);
        fogColor = new ColorSetting("Fog Colour", 0x33406B);
        ambience = new ModuleInfo("Ambience", "world lighting and colour", Category.VISUAL,
            List.of(ambienceTime, ambienceCustom, gradeLook, gradeExposure),
            List.of(gradeSaturation, bloomStrength, gradeVignette, ambienceFog, fogDistance, fogColor));
        ambience.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.WORLD);
        MODULES.add(ambience);

        filterSaturation = new SliderSetting("Saturation", 0.0f, 2.5f, 0.05f, 1.2f, "x");
        filterBrightness = new SliderSetting("Brightness", -0.4f, 0.4f, 0.02f, 0.0f, null);
        filterContrast = new SliderSetting("Contrast", 0.4f, 2.0f, 0.05f, 1.0f, "x");
        filterVignette = new SliderSetting("Vignette", 0.0f, 1.0f, 0.05f, 0.0f, null);
        filterExposure = new SliderSetting("Exposure", 0.3f, 2.3f, 0.05f, 1.0f, "x");
        filterGamma = new SliderSetting("Gamma", 0.4f, 2.2f, 0.05f, 1.0f, null);
        filterGrain = new SliderSetting("Grain", 0.0f, 1.0f, 0.05f, 0.0f, null);
        filterUseTint = new BooleanSetting("Tint", false);
        filterTintColor = new ColorSetting("Tint Colour", 0xFFFFEBD2);
        filterTintStrength = new SliderSetting("Tint Strength", 0.0f, 1.0f, 0.05f, 0.12f, null);

        filters = new ModuleInfo("Filters", "screen color grading and post-processing", Category.VISUAL,
            List.of(filterSaturation, filterBrightness, filterContrast, filterVignette),
            List.of(filterExposure, filterGamma, filterGrain, filterUseTint, filterTintColor, filterTintStrength));
        filters.toggleSilently(true);
        filters.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.WORLD);
        MODULES.add(filters);

        configProfile = new ModeSetting("Profile", List.of("default"), 0, profile -> {
            ConfigManager.loadProfile(profile);
        });
        configAutoSave = new BooleanSetting("Auto Save", true);
        configModule = new ModuleInfo("Config", "save and load your presets", Category.CONFIG,
            List.of(configProfile),
            List.of(configAutoSave));
        configModule.toggleSilently(true);
        configModule.withClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory.OTHER);
        MODULES.add(configModule);
    }

    public static void syncConfigProfiles() {
        if (configProfile != null) {
            configProfile.setOptions(ConfigManager.getProfiles(), ConfigManager.getActiveProfile());
        }
    }

    public static ModeSetting configProfile() {
        return configProfile;
    }

    public static BooleanSetting configAutoSave() {
        return configAutoSave;
    }

    private ModuleRegistry() {
    }

    public static SliderSetting uiRadius() {
        return uiRadius;
    }

    public static ColorSetting uiAccent() {
        return uiAccent;
    }

    public static ColorSetting uiSecondary() {
        return uiSecondary;
    }

    public static boolean themeFade() {
        return themeFade == null || themeFade.value();
    }

    public static BooleanSetting themeFadeSetting() {
        return themeFade;
    }

    public static boolean interfaceBlur() {
        return guiBlur == null || guiBlur.value();
    }

    public static BooleanSetting guiBlurSetting() {
        return guiBlur;
    }

    public static BooleanSetting customColorsSetting() {
        return uiCustomColors;
    }

    public static ColorSetting uiBackground() {
        return uiBackground;
    }

    public static ColorSetting uiText() {
        return uiText;
    }

    public static boolean customColors() {
        return uiCustomColors != null && uiCustomColors.value();
    }

    public static ModeSetting hudStyle() {
        return hudStyle;
    }

    public static ModeSetting hudShape() {
        return hudShape;
    }

    public static BooleanSetting customScoreboard() {
        return customScoreboard;
    }

    public static SliderSetting scoreboardRadius() {
        return scoreboardRadius;
    }

    public static SliderSetting scoreboardOpacity() {
        return scoreboardOpacity;
    }

    public static BooleanSetting customTab() {
        return customTab;
    }

    public static BooleanSetting targetHud() {
        return targetHud;
    }

    public static BooleanSetting coordinatesHud() {
        return coordinatesHud;
    }

    public static BooleanSetting cooldownsHud() {
        return cooldownsHud;
    }

    public static BooleanSetting armorHud() {
        return armorHud;
    }

    public static ModeSetting armorHudMode() {
        return armorHudMode;
    }

    public static BooleanSetting inventoryHud() {
        return inventoryHud;
    }

    public static BooleanSetting inventoryHotbar() {
        return inventoryHotbar;
    }

    public static BooleanSetting lowArmorNotifications() {
        return lowArmorNotifications;
    }

    public static BooleanSetting killStreakHud() {
        return killStreakHud;
    }

    public static ColorSetting tabColor() {
        return tabColor;
    }

    public static SliderSetting tabOpacity() {
        return tabOpacity;
    }

    public static float interfaceScale() {
        return linkUiScale == null ? 1.0f : linkUiScale.value();
    }

    public static float interfaceTransparency() {
        return linkUiTransparency == null ? 1.0f : linkUiTransparency.value() / 100.0f;
    }

    public static boolean cursorTracker() {
        return linkCursorTracker == null || linkCursorTracker.value();
    }

    public static boolean tint(String element) {
        return switch (element) {
            case "watermark" -> tintWatermark.value();
            case "hotbar" -> tintHotbar.value();
            case "potions" -> tintPotions.value();
            case "keybinds" -> tintKeybinds.value();
            case "media" -> tintWatermark.value();
            case "nametags" -> tintWatermark.value();
            default -> true;
        };
    }

    public static ModuleInfo mediaPlayer() {
        return mediaPlayer;
    }

    public static BooleanSetting albumArt() {
        return albumArt;
    }

    public static boolean lyricsOn() {
        return mediaPlayer.enabled() && lyricsEnabled.value();
    }

    public static ColorSetting lyricsColour() {
        return lyricsColour;
    }

    public static SliderSetting lyricsSize() {
        return lyricsSize;
    }

    public static SliderSetting lyricsSpacing() {
        return lyricsSpacing;
    }

    public static ModuleInfo betterAnimations() {
        return betterAnimations;
    }

    /** True when the hand-motion subset of Better Animations is enabled. */
    public static boolean animateHand() {
        return betterAnimations != null && betterAnimations.enabled()
            && (animHand == null || animHand.value());
    }

    public static ModuleInfo aspectRatio() {
        return aspectRatio;
    }

    public static SliderSetting aspectRatioValue() {
        return aspectRatioValue;
    }

    public static boolean animate(String element) {
        return switch (element) {
            case "chat" -> animChat.value();
            case "tab" -> animTab.value();
            case "inventory" -> animInventory.value();
            case "perspective" -> animPerspective.value();
            default -> true;
        };
    }

    public static ModuleInfo cosmetics() {
        return cosmetics;
    }

    public static String cosmeticSlot(String slot) {
        return switch (slot) {
            case "crown" -> cosmeticCrown.current();
            case "back" -> cosmeticBack.current();
            case "aura" -> cosmeticAura.current();
            case "character" -> cosmeticCharacter.current();
            case "pet" -> cosmeticPet.current();
            default -> "None";
        };
    }

    public static ColorSetting cosmeticColor() {
        return cosmeticColor;
    }

    public static SliderSetting cosmeticSize() {
        return cosmeticSize;
    }

    public static SliderSetting cosmeticSpeed() {
        return cosmeticSpeed;
    }

    public static SliderSetting cosmeticPetRadius() {
        return cosmeticPetRadius;
    }

    public static SliderSetting cosmeticPetHeight() {
        return cosmeticPetHeight;
    }

    public static SliderSetting cosmeticPetSpeed() {
        return cosmeticPetSpeed;
    }

    public static SliderSetting cosmeticPetScale() {
        return cosmeticPetScale;
    }

    public static BooleanSetting cosmeticPetFirstPerson() {
        return cosmeticPetFirstPerson;
    }

    public static SliderSetting cosmeticPetAlpha() {
        return cosmeticPetAlpha;
    }

    public static SliderSetting cosmeticPetGlow() {
        return cosmeticPetGlow;
    }

    public static ColorSetting cosmeticPetGlowColor() {
        return cosmeticPetGlowColor;
    }

    public static KeybindSetting cosmeticPetUlt() {
        return cosmeticPetUlt;
    }

    public static ModuleInfo betterHands() {
        return betterHands;
    }

    public static ModeSetting handOutline() {
        return handOutline;
    }

    public static ModeSetting handMotion() {
        return handMotion;
    }

    public static ColorSetting handOutlineColor() {
        return handOutlineColor;
    }

    public static SliderSetting handTrailStrength() {
        return handTrailStrength;
    }

    public static SliderSetting handStrength() {
        return handStrength;
    }

    public static ColorSetting skyColor() {
        return skyColor;
    }

    public static ModeSetting closeAnimation() {
        return closeAnimation;
    }

    public static ModuleInfo targetMarks() {
        return targetMarks;
    }

    public static ModeSetting targetStyle() {
        return targetStyle;
    }

    public static BooleanSetting targetReact() {
        return targetReact;
    }

    public static ModuleInfo hitEffects() {
        return hitEffects;
    }

    public static ModeSetting hitStyle() {
        return hitStyle;
    }

    public static ColorSetting hitColor() {
        return hitColor;
    }

    public static SliderSetting hitSize() {
        return hitSize;
    }

    public static SliderSetting hitLife() {
        return hitLife;
    }

    public static ModuleInfo nameTags() {
        return nameTags;
    }

    public static SliderSetting nameTagScale() {
        return nameTagScale;
    }

    public static BooleanSetting nameTagItem() {
        return nameTagItem;
    }

    public static ModuleInfo killEffects() {
        return killEffects;
    }

    public static ModeSetting killStyle() {
        return killStyle;
    }

    public static ColorSetting killColor() {
        return killColor;
    }

    public static SliderSetting killSize() {
        return killSize;
    }

    public static SliderSetting killLife() {
        return killLife;
    }

    public static SliderSetting targetSpeed() {
        return targetSpeed;
    }

    public static SliderSetting targetHold() {
        return targetHold;
    }

    public static SliderSetting targetSize() {
        return targetSize;
    }

    public static ColorSetting targetColor() {
        return targetColor;
    }

    public static ModuleInfo fakePlayer() {
        return fakePlayer;
    }

    public static ModeSetting fakeMode() {
        return fakeMode;
    }

    public static SliderSetting fakeHealth() {
        return fakeHealth;
    }

    public static BooleanSetting fakeRespawn() {
        return fakeRespawn;
    }

    public static BooleanSetting fakeNameTag() {
        return fakeNameTag;
    }

    public static ModuleInfo shaders() {
        return shaderSky;
    }

    public static ModeSetting rainMode() {
        return rainMode;
    }

    public static SliderSetting rainStrength() {
        return rainStrength;
    }

    public static ColorSetting rainColor() {
        return rainColor;
    }

    public static ModuleInfo shaderSky() {
        return shaderSky;
    }

    public static ModeSetting skyMode() {
        return skyMode;
    }

    public static SliderSetting skySpeed() {
        return skySpeed;
    }

    public static SliderSetting skyIntensity() {
        return skyIntensity;
    }

    public static SliderSetting skyStars() {
        return skyStars;
    }

    public static SliderSetting skyScale() {
        return skyScale;
    }

    public static ModuleInfo ambience() {
        return ambience;
    }

    public static SliderSetting gradeExposure() { return gradeExposure; }

    public static ModeSetting gradeLook() { return gradeLook; }

    public static SliderSetting gradeSaturation() { return gradeSaturation; }

    public static SliderSetting gradeVignette() { return gradeVignette; }

    public static SliderSetting bloomStrength() { return bloomStrength; }

    public static BooleanSetting ambienceFog() { return ambienceFog; }

    public static SliderSetting fogDistance() { return fogDistance; }

    public static ColorSetting fogColor() { return fogColor; }

    public static ModeSetting ambienceTime() {
        return ambienceTime;
    }

    public static SliderSetting ambienceCustom() {
        return ambienceCustom;
    }

    public static ModuleInfo filters() { return filters; }
    public static SliderSetting filterSaturation() { return filterSaturation; }
    public static SliderSetting filterBrightness() { return filterBrightness; }
    public static SliderSetting filterContrast() { return filterContrast; }
    public static SliderSetting filterVignette() { return filterVignette; }
    public static SliderSetting filterExposure() { return filterExposure; }
    public static SliderSetting filterGamma() { return filterGamma; }
    public static SliderSetting filterGrain() { return filterGrain; }
    public static BooleanSetting filterUseTint() { return filterUseTint; }
    public static ColorSetting filterTintColor() { return filterTintColor; }
    public static SliderSetting filterTintStrength() { return filterTintStrength; }

    public static List<ModuleInfo> all() {
        return MODULES;
    }

    /**
     * Registers an object-based module (Phase 4+). Its {@link ModuleInfo} joins
     * the GUI list, so ClickGUI, keybinds and {@code ConfigJson} pick it up
     * with no extra wiring. Legacy static modules are untouched.
     */
    public static void registerModule(Module module) {
        OBJECT_MODULES.put(module.id(), module);
        MODULES.add(module.info());
    }

    /** Object-based module by id, or null. */
    public static Module module(String id) {
        return OBJECT_MODULES.get(id);
    }

    public static ModuleInfo customScreens() {
        return customScreens;
    }

    public static BooleanSetting screenLoading() {
        return screenLoading;
    }

    public static BooleanSetting screenMultiplayer() {
        return screenMultiplayer;
    }

    public static BooleanSetting screenSettings() {
        return screenSettings;
    }

    public static BooleanSetting screenPause() {
        return screenPause;
    }

    public static BooleanSetting screenTitle() {
        return screenTitle;
    }

    public static BooleanSetting screenHotbar() {
        return screenHotbar;
    }

    public static BooleanSetting screenStatusEffects() {
        return screenStatusEffects;
    }

    public static void syncFromMenuConfig() {
        MainMenuConfig cfg = MainMenuConfig.get();
        if (screenLoading != null) screenLoading.setValueSilently(cfg.customLoadingScreen);
        if (screenMultiplayer != null) screenMultiplayer.setValueSilently(cfg.customMultiplayerScreen);
        if (screenSettings != null) screenSettings.setValueSilently(cfg.customSettingsScreen);
        if (screenPause != null) screenPause.setValueSilently(cfg.customPauseScreen);
        if (screenTitle != null) screenTitle.setValueSilently(!cfg.useVanillaTitleScreen);
        if (screenHotbar != null) screenHotbar.setValueSilently(cfg.customHotbar);
        if (screenStatusEffects != null) screenStatusEffects.setValueSilently(cfg.customStatusEffects);
        updateScreensModuleState();
        ConfigManager.save();
    }

    private static void updateScreensModuleState() {
        if (customScreens != null) {
            boolean any = (screenLoading != null && screenLoading.value())
                || (screenMultiplayer != null && screenMultiplayer.value())
                || (screenSettings != null && screenSettings.value())
                || (screenPause != null && screenPause.value())
                || (screenTitle != null && screenTitle.value())
                || (screenHotbar != null && screenHotbar.value())
                || (screenStatusEffects != null && screenStatusEffects.value());
            customScreens.toggleSilently(any);
        }
    }

    public static ModeSetting guiStyle() {
        return guiStyle;
    }

    public static boolean isClassicGui() {
        return guiStyle != null && "Classic".equalsIgnoreCase(guiStyle.value());
    }

    public static boolean isLinkenGui() {
        return guiStyle != null && "Linken".equalsIgnoreCase(guiStyle.value());
    }

    public static boolean isLinkenHud() {
        return true;
    }

    public static boolean isLinkenMenu() {
        return dev.linkenvisuals.ui.style.StyleManager.getStyle() == dev.linkenvisuals.ui.style.MenuStyle.LINKEN
            || !BuildFlavor.STRANGE;
    }

    public static boolean isGuiStyleLocked() {
        return StyleFlavor.isGuiStyleLocked();
    }

    /** Pure rule used by tests and callers that already have the style name. */
    public static boolean isGuiStyleLocked(String style) {
        return style != null && "Linken".equalsIgnoreCase(style.trim());
    }

    /** Ctrl+Left/Right cycles only the styles available in this flavor. */
    public static void cycleGuiStyle(int direction) {
        if (isGuiStyleLocked()) {
            return;
        }
        List<String> styles = StyleFlavor.guiStyleOptions();
        if (styles.size() <= 1) {
            return;
        }
        String current = guiStyle == null ? StyleFlavor.defaultGuiStyle() : guiStyle.value();
        int index = 0;
        for (int i = 0; i < styles.size(); i++) {
            if (styles.get(i).equalsIgnoreCase(current)) {
                index = i;
                break;
            }
        }
        int next = Math.floorMod(index + (direction < 0 ? -1 : 1), styles.size());
        setGuiStyle(styles.get(next));
    }

    public static void setGuiStyle(String style) {
        if (!StyleFlavor.allowsGuiStyle(style) || guiStyle == null) {
            return;
        }
        guiStyle.set(StyleFlavor.sanitizeGuiStyle(style));
        ConfigManager.save();
    }

    /** Re-applies the compile-time family after a config load. */
    public static void enforceFlavorStyles() {
        if (guiStyle != null) {
            guiStyle.set(StyleFlavor.sanitizeGuiStyle(guiStyle.value()));
        }
        if (hudStyle != null) {
            hudStyle.set(StyleFlavor.sanitizeHudStyle(hudStyle.value()));
        }
        if (hudShape != null) {
            hudShape.set(StyleFlavor.sanitizeHudShape(hudShape.value()));
        }
    }

    public static List<ModuleInfo> byCategory(Category category) {
        List<ModuleInfo> result = new ArrayList<>();
        for (ModuleInfo module : MODULES) {
            if (module.category() == category) {
                result.add(module);
            }
        }
        return result;
    }

    public static List<ModuleInfo> byClassicCategory(dev.linkenvisuals.clickgui.classic.ClassicCategory category) {
        List<ModuleInfo> result = new ArrayList<>();
        for (ModuleInfo module : MODULES) {
            if (module.classicCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }

    public static void onKeyPressed(int keyCode) {
        if (keyCode < 0) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.currentScreen != null) {
            return;
        }
        for (ModuleInfo module : MODULES) {
            if (module.keyCode() == keyCode) {
                module.toggle();
            }
        }
    }
}
