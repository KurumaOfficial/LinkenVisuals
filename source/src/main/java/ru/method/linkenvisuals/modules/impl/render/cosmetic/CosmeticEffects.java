package ru.method.linkenvisuals.modules.impl.render.cosmetic;

import lombok.experimental.NonFinal;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.api.events.impl.EventRenderWorld;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandNotificationManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEnums.HaloQuality;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEnums.PetSkin;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEnums.SphereEditor;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEnums.SphereEffect;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEnums.SphereMode;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;

import java.awt.Color;

public class CosmeticEffects extends Module {

    final Identifier defaultTexture = LinkenVisuals.id("client/xuynya/ghost_bloom.png");

    final BooleanSetting spheresEnabled = new BooleanSetting("cosmetic.spheres", false);
    final BooleanSetting haloEnabled = new BooleanSetting("cosmetic.halo", false);
    final BooleanSetting petEnabled = new BooleanSetting("cosmetic.pet", false);
    final BooleanSetting narutoEnabled = new BooleanSetting("cosmetic.naruto", false);

    final EnumSetting<PetSkin> petSkin = new EnumSetting<>(
            "cosmetic.pet.skin",
            PetSkin.PARROT,
            this::petOn
    );
    final BooleanSetting petFirstPerson = new BooleanSetting(
            "cosmetic.pet.first_person",
            true,
            this::petOn
    );
    final BindSetting ravenSceneBind = new BindSetting(
            "cosmetic.raven.scene_bind",
            new Bind(-1, false),
            this::ravenUiOn
    );

    @NonFinal
    boolean ravenSceneRequested = false;

    final BooleanSetting haloOthers = new BooleanSetting("cosmetic.halo.others", false, this::haloOn);
    final BooleanSetting haloFirstPerson = new BooleanSetting("cosmetic.halo.first_person", true, this::haloOn);
    final NumberSetting haloDistance = new NumberSetting("cosmetic.halo.distance", 72f, 8f, 192f, 1f, this::haloOn);
    final EnumSetting<HaloQuality> haloQuality = new EnumSetting<>("cosmetic.halo.quality", HaloQuality.MEDIUM, this::haloOn);
    final ColorSetting haloColor = new ColorSetting("cosmetic.halo.color", new Color(255, 255, 255, 255), this::haloOn);
    final BooleanSetting haloAnimate = new BooleanSetting("cosmetic.halo.animate", true, this::haloOn);
    final NumberSetting haloRadius = new NumberSetting("cosmetic.halo.radius", 0.36f, 0.18f, 0.90f, 0.01f, this::haloOn);
    final NumberSetting haloThickness = new NumberSetting("cosmetic.halo.thickness", 0.06f, 0.02f, 0.16f, 0.01f, this::haloOn);
    final NumberSetting haloYOffset = new NumberSetting("cosmetic.halo.y_offset", 0.10f, -0.60f, 0.60f, 0.01f, this::haloOn);

    final EnumSetting<SphereMode> mode = new EnumSetting<>("cosmetic.sphere.mode", SphereMode.STATIC, this::spheresOn);
    final EnumSetting<SphereEffect> effect = new EnumSetting<>("cosmetic.sphere.effect", SphereEffect.TRAIL, this::spheresOn);
    final ColorSetting exort = new ColorSetting("cosmetic.sphere.exort", new Color(255, 200, 100, 200), this::spheresOn);
    final ColorSetting wex = new ColorSetting("cosmetic.sphere.wex", new Color(255, 150, 255, 200), this::spheresOn);
    final ColorSetting quas = new ColorSetting("cosmetic.sphere.quas", new Color(150, 150, 255, 200), this::spheresOn);
    final NumberSetting size = new NumberSetting("cosmetic.sphere.size", 0.12f, 0.05f, 0.22f, 0.01f, this::spheresOn);
    final NumberSetting radius = new NumberSetting("cosmetic.sphere.radius", 0.42f, 0.18f, 1.25f, 0.01f, this::spheresOn);
    final NumberSetting height = new NumberSetting("cosmetic.sphere.height", 0.05f, -1.20f, 3.50f, 0.01f, this::spheresOn);
    final NumberSetting speed = new NumberSetting(
            "cosmetic.sphere.speed",
            1.85f,
            0.2f,
            4.0f,
            0.01f,
            () -> spheresOn() && mode.getValue() != SphereMode.STATIC
    );
    final NumberSetting breathe = new NumberSetting("cosmetic.sphere.breathe", 0.08f, 0.0f, 0.30f, 0.01f, this::spheresOn);
    final NumberSetting spiralHeight = new NumberSetting(
            "cosmetic.sphere.spiral_height",
            1.25f,
            0.2f,
            6.0f,
            0.01f,
            () -> spheresOn() && (mode.getValue() == SphereMode.SPIRAL || mode.getValue() == SphereMode.SPIRAL_V2)
    );
    final NumberSetting waveAmp = new NumberSetting(
            "cosmetic.sphere.wave_amp",
            0.22f,
            0.0f,
            1.2f,
            0.01f,
            () -> spheresOn() && mode.getValue() == SphereMode.WAVES
    );
    final NumberSetting waveFreq = new NumberSetting(
            "cosmetic.sphere.wave_freq",
            2.4f,
            0.6f,
            8.0f,
            0.01f,
            () -> spheresOn() && mode.getValue() == SphereMode.WAVES
    );
    final EnumSetting<SphereEditor> sphereEditor = new EnumSetting<>("cosmetic.sphere.editor", SphereEditor.ALL, this::spheresOn);
    final NumberSetting editLR = new NumberSetting("cosmetic.sphere.edit_lr", 0.0f, -1.50f, 1.50f, 0.01f, this::spheresOn);
    final NumberSetting editFB = new NumberSetting("cosmetic.sphere.edit_fb", 0.0f, -1.50f, 1.50f, 0.01f, this::spheresOn);
    final NumberSetting trailLen = new NumberSetting(
            "cosmetic.sphere.trail_len",
            18f,
            6f,
            48f,
            1f,
            () -> spheresOn() && effect.getValue() != SphereEffect.NONE
    );
    final NumberSetting trailPower = new NumberSetting(
            "cosmetic.sphere.trail_power",
            1.10f,
            0.2f,
            2.2f,
            0.01f,
            () -> spheresOn() && effect.getValue() != SphereEffect.NONE
    );
    final BooleanSetting self = new BooleanSetting("cosmetic.sphere.self", true, this::spheresOn);
    final BooleanSetting others = new BooleanSetting("cosmetic.sphere.others", false, this::spheresOn);
    final BooleanSetting firstPerson = new BooleanSetting("cosmetic.sphere.first_person", true, this::spheresOn);

    final NumberSetting petRadius = new NumberSetting("cosmetic.pet.radius", 0.65f, 0.15f, 2.5f, 0.01f, this::petOn);
    final NumberSetting petHeight = new NumberSetting("cosmetic.pet.height", 0.95f, -0.5f, 3.0f, 0.01f, this::petOn);
    final NumberSetting petSpeed = new NumberSetting("cosmetic.pet.speed", 1.15f, 0.05f, 5.0f, 0.01f, this::petOn);
    final NumberSetting petScale = new NumberSetting("cosmetic.pet.scale", 0.55f, 0.15f, 2.0f, 0.01f, this::petOn);

    final NumberSetting ravenAlpha = new NumberSetting("cosmetic.raven.alpha", 210f, 0f, 255f, 1f, this::ravenUiOn);
    final ColorSetting ravenColor = new ColorSetting("cosmetic.raven.color", new Color(20, 20, 20, 255), this::ravenUiOn);
    final NumberSetting ravenGlow = new NumberSetting("cosmetic.raven.glow", 65f, 0f, 100f, 1f, this::ravenUiOn);

    final Nimb nimb;
    final Spheres spheres;
    final Raven raven;
    final Naruto naruto;

    public CosmeticEffects() {
        super("CosmeticEffects", Category.Visuals);
        this.nimb = new Nimb(this);
        this.spheres = new Spheres(this);
        this.raven = new Raven(this);
        this.naruto = new Naruto(this);
    }

    @Override
    public void onDisable() {
        shutdown();
        super.onDisable();
    }

    /** Останавливает все эффекты. Вызывается из CosmeticsModule при выключении модуля «Косметика». */
    public void shutdown() {
        ravenSceneRequested = false;
        if (spheres != null) {
            spheres.deactivate();
        }
        if (nimb != null) {
            nimb.deactivate();
        }
        if (raven != null) {
            raven.deactivate();
        }
        if (naruto != null) {
            naruto.onDisable();
        }
    }

    // ПИТОМЕЦ / СФЕРЫ / анимации — рисуем в обычном проходе мира (pre-fog, EventRender3D.Game), как в
    // оригинале (master): здесь чистое состояние рендера сущностей, питомец выходит непрозрачным.
    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) {
            return;
        }

        boolean petActive = petOn();
        if (!petActive) {
            return;
        }

        MatrixStack matrices = event.getMatrixStack();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        try {
            boolean camFirst = mc.options.getPerspective().isFirstPerson();
            if (!(camFirst && !bool(petFirstPerson))) {
                raven.onWorldRender(event);
            }
        } finally {
            matrices.pop();
        }
    }

    // НИМБ — аддитивное свечение. Рисуем его в EventRenderWorld (ПОСЛЕ прохода FogBlur), иначе блюр
    // размывает нимб (особенно от первого лица). Только нимб — остальное на pre-fog (см. onRender3D).
    @EventHandler
    public void onRenderWorld(EventRenderWorld worldEvent) {
        if (fullNullCheck()) {
            return;
        }
        boolean spheresActive = spheresOn();
        boolean haloActive = haloOn();
        if (!spheresActive && !haloActive) {
            return;
        }

        EventRender3D.Game event = new EventRender3D.Game(worldEvent.getTickCounter(), worldEvent.getMatrixStack());

        // Spheres/trails are camera-relative world-space buffers drawn with an
        // explicit encoder pass (additive bloom, no GL state mutation).
        if (spheresActive) {
            spheres.onWorldRender(event);
        }

        if (!haloActive) {
            return;
        }
        MatrixStack matrices = event.getMatrixStack();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        try {
            nimb.onWorldRender(event);
        } finally {
            matrices.pop();
        }
    }

    @EventHandler
    public void onKey(EventKey event) {
        // Движок подписан на события только пока включён модуль «Косметика», поэтому собственный
        // тумблер тут не проверяем (его нет — это не отдельный модуль).
        if (fullNullCheck()) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        if (ravenUiOn() && ravenSceneBind.getValue().matchesKey(event.getKey())) {
            ravenSceneRequested = true;
            DynamicIslandNotificationManager.getInstance().addInfoNotification("Raven Scene");
            LinkenVisuals.getInstance().getSoundManager().playEnable();
        }
    }

    boolean pollRavenScene() {
        if (!ravenSceneRequested) {
            return false;
        }
        ravenSceneRequested = false;
        return true;
    }

    /** Wardrobe effect slot -> local effects module. */
    public void applyEffectSelection(String id) {
        spheresEnabled.setValue("effect_spheres".equals(id));
    }

    boolean spheresOn() {
        return bool(spheresEnabled);
    }

    boolean haloOn() {
        return bool(haloEnabled);
    }

    boolean petOn() {
        return bool(petEnabled);
    }

    /** Сущность текущего питомца для GUI-превью, или null если питомец выключен. */
    public net.minecraft.entity.LivingEntity getPreviewPetEntity() {
        if (!petOn() || raven == null) return null;
        return raven.getPreviewPetEntity();
    }

    /** Сущность скина для иконок в сетке (не зависит от toggle питомца). */
    public net.minecraft.entity.LivingEntity getPreviewPetEntity(PetSkin skin) {
        if (raven == null) return null;
        return raven.getPreviewPetEntity(skin);
    }

    public boolean isPetOn() {
        return petOn();
    }

    /**
     * Данные питомца для синка на сервер (блок cosmetics.pet). null — модуль выключен
     * или питомец не включён: другие игроки его не увидят.
     */
    public com.google.gson.JsonObject petSyncPayload() {
        if (!isToggled() || !petOn()) {
            return null;
        }
        com.google.gson.JsonObject pet = new com.google.gson.JsonObject();
        pet.addProperty("skin", petSkin.getValue().name());
        pet.addProperty("radius", petRadius.getValue());
        pet.addProperty("height", petHeight.getValue());
        pet.addProperty("speed", petSpeed.getValue());
        pet.addProperty("scale", petScale.getValue());
        return pet;
    }

    /**
     * Данные нимба для синка (блок cosmetics.halo): другие игроки увидят нимб
     * с ЭТИМИ цветом/размерами. null — модуль или нимб выключены.
     */
    public com.google.gson.JsonObject haloSyncPayload() {
        if (!isToggled() || !haloOn()) {
            return null;
        }
        com.google.gson.JsonObject halo = new com.google.gson.JsonObject();
        halo.addProperty("color", haloColor.getValue().getRGB());
        halo.addProperty("radius", haloRadius.getValue());
        halo.addProperty("thickness", haloThickness.getValue());
        halo.addProperty("y_offset", haloYOffset.getValue());
        halo.addProperty("animate", CosmeticEffects.bool(haloAnimate));
        return halo;
    }

    /** Нимб-рендерер для отрисовки чужих (синхронизированных) нимбов из {@link RemotePets}. */
    public Nimb nimbRenderer() {
        return nimb;
    }

    boolean narutoOn() {
        return bool(narutoEnabled);
    }

    boolean petIsBat() {
        return petSkin.getValue() == PetSkin.BAT;
    }

    boolean petIsParrot() {
        return petSkin.getValue() == PetSkin.PARROT;
    }

    boolean petIsRaven() {
        return petSkin.getValue() == PetSkin.RAVEN;
    }

    boolean petIsFairy() {
        return petSkin.getValue() == PetSkin.FAIRY;
    }

    boolean petIsBee() {
        return petSkin.getValue() == PetSkin.BEE;
    }

    boolean petIsVex() {
        return petSkin.getValue() == PetSkin.VEX;
    }

    boolean petIsFox() {
        return petSkin.getValue() == PetSkin.FOX;
    }

    boolean petIsPig() {
        return petSkin.getValue() == PetSkin.PIG;
    }

    boolean petIsFrog() {
        return petSkin.getValue() == PetSkin.FROG;
    }

    boolean petIsPufferfish() {
        return petSkin.getValue() == PetSkin.PUFFERFISH;
    }

    boolean petIsSlime() {
        return petSkin.getValue() == PetSkin.SLIME;
    }

    boolean ravenUiOn() {
        return petOn() && petIsRaven();
    }

    public static boolean bool(BooleanSetting setting) {
        return setting != null && Boolean.TRUE.equals(setting.getValue());
    }

    public BooleanSetting spheresEnabledSetting() {
        return spheresEnabled;
    }

    public BooleanSetting haloEnabledSetting() {
        return haloEnabled;
    }

    public BooleanSetting petEnabledSetting() {
        return petEnabled;
    }

    public BooleanSetting narutoEnabledSetting() {
        return narutoEnabled;
    }

    public EnumSetting<PetSkin> petSkinSetting() {
        return petSkin;
    }
}
