package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.CustomHotbar;
import ru.method.linkenvisuals.hud.CustomVitalsBar;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.modules.impl.render.CustomVitals;
import ru.method.linkenvisuals.hud.impl.DynamicIsland;
import ru.method.linkenvisuals.hud.impl.PotionHud;
import ru.method.linkenvisuals.hud.impl.ScoreboardHud;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.modules.impl.misc.ItemHelper;
import ru.method.linkenvisuals.modules.impl.render.NoRender;
import ru.method.linkenvisuals.modules.impl.render.Crosshair;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeManager;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.HudRenderFrame;
import ru.method.linkenvisuals.utils.render.LiquidGlassReflectionCache;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.screen.clickgui.ClickGui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;

import java.awt.*;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void linkenvisuals$beginHudFrame(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        LinkGuiThemeManager.ensureHudAccent();
        HudRenderFrame.begin(context, tickCounter);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ClickGui) {
            LiquidGlassReflectionCache.captureClickGuiBackdrop(true);
        } else if (client.world != null && ColorUtils.isLiquidGlassShaderActive()) {
            LiquidGlassReflectionCache.captureGameplayForHudFrame();
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$hideVanillaStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        PotionHud potionHud = LinkenVisuals.getInstance().getHudManager().getHudElement(PotionHud.class);
        if (potionHud != null && potionHud.isToggled() && !potionHud.closed()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$renderCustomCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Crosshair crosshair = LinkenVisuals.getInstance().getModuleManager().getModule(Crosshair.class);
        if (crosshair == null || !crosshair.isToggled()) {
            return;
        }
        if (crosshair.shouldHideVanillaCrosshair()) {
            ci.cancel();
        }
        ru.method.linkenvisuals.utils.render.Render2D.prepareHudFrame(context);
        crosshair.renderCrosshair(context, tickCounter);
    }

    @Inject(method = "render", at = @At("RETURN"))
    public void linkenvisuals$finishHudFrame(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        try {
            HudRenderFrame.State state = HudRenderFrame.get();
            if (state == null) return;
            if (!state.hudEventPosted()) {
                HudRenderFrame.dispatchHudRender(LinkenVisuals.getInstance().getEventHandler());
            } else if (state.needsDynamicIslandTopPass()) {
                linkenvisuals$renderDynamicIslandTop(context, tickCounter);
            }
            linkenvisuals$renderHudWindowOverlay(context);
        } finally {
            HudRenderFrame.end();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    public void renderScoreboardSidebar(DrawContext drawContext, ScoreboardObjective objective, CallbackInfo ci) {
        StreamerModeUtils.beginScoreboardPass();
        HudRenderFrame.tryDispatchBeforeOverlays(LinkenVisuals.getInstance().getEventHandler());

        NoRender noRender = LinkenVisuals.getInstance().getModuleManager().getModule(NoRender.class);
        if (noRender != null && noRender.isToggled() && noRender.scoreboard.getValue()) {
            ci.cancel();
            return;
        }

        ScoreboardHud scoreboardHud = LinkenVisuals.getInstance().getHudManager().getHudElement(ScoreboardHud.class);
        if (UI.isCustomScoreboardEnabled() && scoreboardHud != null && scoreboardHud.isToggled() && !scoreboardHud.closed()) {
            ci.cancel();
        }
    }

    @Inject(method = "method_65023", at = @At("RETURN"), cancellable = true)
    private static void linkenvisuals$sanitizeScoreboardText(String input, CallbackInfoReturnable<String> cir) {
        StreamerModeUtils.observeScoreboardString(cir.getReturnValue());
        cir.setReturnValue(StreamerModeUtils.sanitizeString(cir.getReturnValue()));
    }

    @ModifyArg(
            method = "method_55439",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud$SidebarEntry;<init>(Lnet/minecraft/text/Text;Lnet/minecraft/text/Text;I)V"),
            index = 0,
            require = 0
    )
    private Text linkenvisuals$sanitizeSidebarName(Text name) {
        StreamerModeUtils.observeScoreboardText(name);
        // Прибавка «наторгованного» модулем SecretDupe к строке монет. Именно здесь, а не в аксессоре
        // SidebarEntry#name(): рендер читает поле записи через getfield и accessor не вызывает.
        return ru.method.linkenvisuals.modules.impl.misc.FakeCosmetic.boostCoins(
                StreamerModeUtils.sanitizeText(name));
    }

    @ModifyArg(
            method = "method_55439",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud$SidebarEntry;<init>(Lnet/minecraft/text/Text;Lnet/minecraft/text/Text;I)V"),
            index = 1,
            require = 0
    )
    private Text linkenvisuals$sanitizeSidebarScore(Text score) {
        StreamerModeUtils.observeScoreboardText(score);
        return StreamerModeUtils.sanitizeText(score);
    }

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"
            ),
            index = 1,
            require = 0
    )
    private Text linkenvisuals$sanitizeSidebarDrawText(Text text) {
        StreamerModeUtils.observeScoreboardText(text);
        return StreamerModeUtils.sanitizeText(text);
    }

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V"
            ),
            index = 1,
            require = 0
    )
    private OrderedText linkenvisuals$sanitizeSidebarDrawOrderedText(OrderedText text) {
        return StreamerModeUtils.sanitizeOrderedText(text);
    }

    // Кастомный хотбар в стиле Link GUI: при активном Link-стиле отменяем ванильный хотбар и рисуем свой.
    // require=0 — если имя метода не совпадёт на другой версии MC, инжект просто не применится (без краша).
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true, require = 0)
    private void linkenvisuals$customHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (MinecraftClient.getInstance().player == null) return;
        // Хотбар и полосы рисуем из ОДНОГО инжекта (renderHotbar HEAD, до context.draw()/flush) —
        // два отдельных @Inject на один HEAD конфликтовали, и полосы не отрисовывались.
        // Кастомный хотбар И полосы теперь гейтятся ОДНИМ тумблером модуля Custom Hotbar (бывш. CustomVitals):
        // включён модуль → и слоты, и полосы в Link-стиле; выключен → полностью ванильный низ экрана.
        boolean vitals = linkenvisuals$customVitalsActive();
        boolean hotbar = vitals; // кастом-хотбар в ЛЮБОМ стиле GUI (Link и Panel), пока модуль включён
        if (hotbar) {
            CustomHotbar.render(context, tickCounter);
        }
        if (vitals) {
            linkenvisuals$drawVitals(context);
        }
        // Отменяем ванильный хотбар только если рисуем свой; иначе оставляем ванильный (полосы всё равно поверх).
        if (hotbar) {
            ci.cancel();
        }
    }

    // Кастомные полосы включены — прячем ванильную полосу опыта (рисуем свою в linkenvisuals$customVitals).
    // Поднимаем полосу опыта на 2px над хотбаром (когда кастом выключен).
    // Прячем ванильные health/food/armor/air, когда включён кастомный вид полос.
    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true, require = 0)
    private void linkenvisuals$cancelVanillaStatus(DrawContext context, CallbackInfo ci) {
        if (linkenvisuals$customVitalsActive()) {
            ci.cancel();
        }
    }

    // Прячем ванильное число уровня опыта — оно рисовалось поверх нашего чипа.
    // Рисует кастомные полосы HP/голода/опыта (вызывается из linkenvisuals$customHotbar, до flush).
    @Unique
    private void linkenvisuals$drawVitals(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (client.options.hudHidden || client.player.isSpectator()) return;
        if (client.player.getAbilities().creativeMode) return;

        CustomVitals module = linkenvisuals$vitalsModule();
        if (module == null) return;

        PlayerEntity p = client.player;
        CustomVitalsBar.State s = new CustomVitalsBar.State();
        s.style = module.getStyle();
        s.segCount = module.getSegmentCount();
        s.showArmorRow = module.showArmorRow();

        float maxHealth = Math.max(1f, p.getMaxHealth());
        s.health = (int) Math.ceil(p.getHealth());
        s.healthFrac = p.getHealth() / maxHealth;
        s.food = p.getHungerManager().getFoodLevel();
        s.foodFrac = s.food / 20f;
        s.expFrac = p.experienceProgress;
        s.level = p.experienceLevel;
        s.armor = p.getArmor();

        int air = p.getAir();
        int maxAir = p.getMaxAir();
        s.airFrac = maxAir <= 0 ? 1f : (float) air / (float) maxAir;
        s.showAir = p.isSubmergedInWater() || air < maxAir;

        s.poison = p.hasStatusEffect(StatusEffects.POISON);
        s.wither = p.hasStatusEffect(StatusEffects.WITHER);
        s.hunger = p.hasStatusEffect(StatusEffects.HUNGER);

        if (p.getVehicle() instanceof LivingEntity mount) {
            s.riding = true;
            s.mountHealthFrac = mount.getHealth() / Math.max(1f, mount.getMaxHealth());
        }
        if (client.player instanceof ClientPlayerEntity cpe) {
            s.jumpFrac = Math.max(0f, Math.min(1f, cpe.getMountJumpStrength()));
        }

        CustomVitalsBar.render(context, s);
    }

    @Unique
    private static CustomVitals linkenvisuals$vitalsModule() {
        return LinkenVisuals.getInstance().getModuleManager().getModule(CustomVitals.class);
    }

    @Unique
    private static boolean linkenvisuals$customVitalsActive() {
        // Кастомный низ экрана (хотбар + полосы HP/голода/опыта и отмена ванильных) действует в ЛЮБОМ
        // стиле GUI, когда включён модуль Custom Hotbar — в том числе в Panel GUI. Так кастом-хотбар
        // перекрывает и хотбар от ресурс-пака (по запросу пользователя). Раньше гейт был привязан к
        // Link-стилю (active()); привязка снята — единственное условие теперь сам тумблер модуля.
        CustomVitals m = linkenvisuals$vitalsModule();
        return m != null && m.isToggled();
    }

    @Inject(method = "renderHotbarItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;III)V", shift = At.Shift.AFTER))
    public void renderHotbarItem(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) return;
        ItemHelper itemHelper = LinkenVisuals.getInstance().getModuleManager().getModule(ItemHelper.class);
        if (itemHelper == null || !itemHelper.isToggled()) return;

        if (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
            Render2D.drawRoundedRect(
                    context.getMatrices(),
                    x,
                    y,
                    16f,
                    16f,
                    0,
                    ColorUtils.pulse(new Color(255, 255, 50, 180), 30)
            );
        } else if (stack.getItem() == Items.GOLDEN_APPLE) {
            Render2D.drawRoundedRect(
                    context.getMatrices(),
                    x,
                    y,
                    16f,
                    16f,
                    0,
                    ColorUtils.pulse(new Color(255, 255, 255, 180), 30)
            );
        } else if (isPotion(stack, StatusEffects.INSTANT_HEALTH.value())) {
            Render2D.drawRoundedRect(
                    context.getMatrices(),
                    x,
                    y,
                    16f,
                    16f,
                    0,
                    ColorUtils.pulse(new Color(255, 50, 50, 180), 30)
            );
        }
    }

    @Unique
    private void linkenvisuals$renderHudWindowOverlay(DrawContext context) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getHudManager() == null) return;
        instance.getHudManager().renderWindowOverlay(context);
    }

    @Unique
    private void linkenvisuals$renderDynamicIslandTop(DrawContext context, RenderTickCounter tickCounter) {
        HudRenderFrame.State state = HudRenderFrame.get();
        if (state == null) return;

        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getHudManager() == null) return;
        DynamicIsland dynamicIsland = instance.getHudManager().getHudElement(DynamicIsland.class);
        if (!instance.getHudManager().isElementVisible(dynamicIsland)) return;

        state.beginDynamicIslandTopPass();
        Render2D.prepareHudFrame(context);
        EventRender2D event = new EventRender2D(context, tickCounter);
        dynamicIsland.onRender2DX2(event);
        dynamicIsland.onRender2D(event);
    }

    @Unique
    private boolean isPotion(ItemStack stack, StatusEffect status) {
        if (!(stack.getItem() instanceof PotionItem)) return false;
        PotionContentsComponent component = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (component == null) return false;
        if (component.potion().isEmpty()) return false;
        for (StatusEffectInstance effect : component.potion().get().value().getEffects()) {
            if (effect.getEffectType().value() == status) return true;
        }
        return false;
    }
}
