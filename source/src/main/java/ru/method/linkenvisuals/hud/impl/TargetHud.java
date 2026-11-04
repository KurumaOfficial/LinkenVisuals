package ru.method.linkenvisuals.hud.impl;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.notify.Notify;
import ru.method.linkenvisuals.utils.notify.NotifyIcons;
import ru.method.linkenvisuals.utils.server.Server;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.LiquidPanelPresets;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeManager;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemePreset;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;


public class TargetHud extends HudElement {

    public TargetHud() {
        super("TargetHud");
    }

    private final InfinityAnimation healthAnimation = new InfinityAnimation(Easing.LINEAR);
    private final InfinityAnimation gappleAnimation = new InfinityAnimation(Easing.LINEAR);
    private final InfinityAnimation fadeAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final InfinityAnimation hurtAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);

    private final InfinityAnimation absorptionAnimation = new InfinityAnimation(Easing.LINEAR);
    private float lastHealth = -1f;
    private float hitFlash = 0f;

    private PlayerEntity lastTarget = null;
    private PlayerEntity renderTarget = null;
    private long lastTargetTime = 0;
    private final long TARGET_DELAY = 1000;
    // Только БЛИЗКИЕ игроки (детект + «липкость» цели): и прямой crosshair, и мягкий конус, и удержание
    // цели при отходе ограничены этой дистанцией. Раньше 30 — таргет держался за игроком до 30 блоков.
    private final double MAX_RANGE = 3.5;
    // Мягкий конус «почти навёл»: fallback-захват, когда строго под прицелом игрока нет.
    // ponytail: константа, не настройка — HudElement (в отличие от Module) не даёт settings штатно.
    // Насколько «мимо» игрока можно смотреть и всё равно поймать таргет: хитбокс расширяется на столько
    // блоков во все стороны (лево/право/верх/низ). Больше — прощает больший увод прицела от центра тела.
    private static final double AIM_HITBOX_MARGIN = 0.75;

    private boolean lastNameBoundsValid = false;
    private float lastNameX = 0.0f;
    private float lastNameY = 0.0f;
    private float lastNameW = 0.0f;
    private float lastNameH = 0.0f;
    private String lastNameValue = null;
    private long lastCopyTimeMs = 0L;

    private float lastNameRowX = 0.0f;
    private float lastNameRowY = 0.0f;
    private float lastNameRowW = 0.0f;
    private float lastNameRowH = 0.0f;
    private boolean lastNameRowValid = false;

    // --- Кэш горячего пути onRender2D (переиспользуем результаты между кадрами) ---
    // Кэш обрезки имени: результат fitText валиден, пока не изменились (fullName, nameMaxWidth).
    private String lastFullName = null;
    private float lastNameMaxWidth = -1f;
    private String lastDisplayName = null;

    // Кэш форматирования HP: String-формат пересчитываем только при смене округлённого ключа.
    private long lastHpKey = Long.MIN_VALUE;
    private String lastHpText = null;

    // Кэш списка предметов цели: пересобираем раз в ~100мс или при смене цели.
    private final List<ItemStack> cachedItems = new ArrayList<>();
    private long lastItemsRefreshMs = 0L;
    private PlayerEntity lastItemsTarget = null;
    private static final long ITEMS_REFRESH_MS = 100L;

    private boolean shouldHidePlayer(PlayerEntity player) {
        return player == null
                || player.isInvisible()
                || player.hasStatusEffect(StatusEffects.INVISIBILITY);
    }

    private boolean isFriendTarget(PlayerEntity player) {
        if (player == null) return false;
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getFriendManager() == null) return false;
        var profile = player.getGameProfile();
        if (profile == null || profile.name() == null) return false;
        return instance.getFriendManager().isFriend(profile.name());
    }

    /** Компактный формат HP: до 999.9 — как есть, дальше «12.3K»/«1.2M» — чтобы не вылезать за панель. */
    private static String formatHp(float hp) {
        if (hp < 1000f) return String.format("%.1f", hp);
        if (hp < 1_000_000f) return String.format("%.1fK", hp / 1000f);
        return String.format("%.1fM", hp / 1_000_000f);
    }

    private PlayerEntity getCurrentTargetedPlayer() {
        if (fullNullCheck()) return null;

        HitResult hitResult = mc.crosshairTarget;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHitResult = (EntityHitResult) hitResult;
            Entity entity = entityHitResult.getEntity();

            if (entity instanceof PlayerEntity && entity != mc.player) {
                PlayerEntity playerEntity = (PlayerEntity) entity;
                if (shouldHidePlayer(playerEntity)) return null;
                double distance = mc.player.distanceTo(entity);
                if (distance <= MAX_RANGE) {
                    return playerEntity;
                }
            }
        }
        // Строго под прицелом никого — пробуем мягкий конус «почти навёл».
        return getPlayerInAimCone();
    }

    private PlayerEntity coneCache;
    private long coneCacheMs;

    /**
     * Fallback: ближайший к линии взгляда игрок в мягком конусе (когда строго под прицелом никого).
     * ТРОТТЛИНГ: полный обход игроков + raycast делаем максимум раз в ~120мс, между — отдаём кэш.
     * Раньше при беге по людному серверу (нет строгого crosshair-таргета почти всегда) этот скан +
     * raycast гонялся на КАЖДОМ кадре и заметно ронял FPS. HUD спокойно терпит 120мс на захват цели.
     */
    private PlayerEntity getPlayerInAimCone() {
        long now = System.currentTimeMillis();
        if (now - coneCacheMs < 120L) return coneCache;
        coneCacheMs = now;
        coneCache = computeConePlayer();
        return coneCache;
    }

    private PlayerEntity computeConePlayer() {
        if (mc.world == null || mc.player == null) return null;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d rayEnd = eye.add(look.multiply(MAX_RANGE));
        PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || shouldHidePlayer(p) || isFriendTarget(p)) continue;
            if (mc.player.distanceTo(p) > MAX_RANGE) continue;
            // Хитбокс, расширенный на AIM_HITBOX_MARGIN во все стороны: ловим игрока, даже если смотришь
            // чуть левее/правее/ниже него, а не строго в центр. Луч прицела пересекает эту коробку.
            var hit = p.getBoundingBox().expand(AIM_HITBOX_MARGIN).raycast(eye, rayEnd);
            if (hit.isEmpty()) continue;
            double d = hit.get().squaredDistanceTo(eye); // ближайший по лучу игрок = чей бокс встречаем раньше
            if (d < bestDist) { bestDist = d; best = p; }
        }
        // Один raycast (LOS) — только на победителя, а не на каждого игрока каждый кадр.
        return best != null && hasLineOfSight(eye, best) ? best : null;
    }

    /** Линия обзора до центра хитбокса цели не перекрыта блоком (не цепляем игроков сквозь стены). */
    private boolean hasLineOfSight(Vec3d eye, PlayerEntity target) {
        Vec3d center = target.getBoundingBox().getCenter();
        BlockHitResult bhr = mc.world.raycast(new RaycastContext(
                eye, center, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player));
        return bhr == null || bhr.getType() != HitResult.Type.BLOCK
                || bhr.getPos().squaredDistanceTo(eye) >= center.squaredDistanceTo(eye) - 1.0e-3;
    }

    /** Чат-скрин открыт: TargetHUD показываем всегда (как в превью HUD-редактора), а не только при наведении. */
    private boolean isChatOpen() {
        return mc != null && mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
    }

    private boolean shouldShowTarget() {
        if (ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive() || isChatOpen()) {
            PlayerEntity currentTarget = getCurrentTargetedPlayer();
            if (currentTarget != null && isFriendTarget(currentTarget)) {
                renderTarget = null;
                lastTarget = null;
                return false;
            }
            if (currentTarget != null && !shouldHidePlayer(currentTarget)) {
                renderTarget = currentTarget;
                return true;
            }

            PlayerEntity self = mc.player;
            if (self == null || shouldHidePlayer(self)) {
                renderTarget = null;
                return false;
            }
            renderTarget = self;
            return true;
        }

        PlayerEntity currentTarget = getCurrentTargetedPlayer();
        long currentTime = System.currentTimeMillis();

        if (currentTarget != null) {
            if (isFriendTarget(currentTarget)) {
                renderTarget = null;
                lastTarget = null;
                return false;
            }
            if (shouldHidePlayer(currentTarget)) {
                renderTarget = null;
                return false;
            }
            lastTarget = currentTarget;
            renderTarget = currentTarget;
            lastTargetTime = currentTime;
            return true;
        } else {
            if (lastTarget != null) {
                if ((currentTime - lastTargetTime) < TARGET_DELAY) {
                    if (!lastTarget.isRemoved()
                            && lastTarget.isAlive()
                            && mc.player.distanceTo(lastTarget) <= MAX_RANGE
                            && !shouldHidePlayer(lastTarget)
                            && !isFriendTarget(lastTarget)) {
                        renderTarget = lastTarget;
                        return true;
                    }
                }
                lastTarget = null;
                return false;
            }
        }
        return false;
    }

    // --- Тематические цвета: следуют за выбранным пресетом Link-темы (тёмная тема → тёмный TargetHUD). ---
    // ВАЖНО: берём preset.background()/text() напрямую, а НЕ ColorUtils.getBackgroundColor() —
    // последний хранит светлую «стеклобазу» (220,220,220) для тинта Link GUI, а не цвет тёмной панели.
    private Color themeBgBase() {
        LinkGuiThemePreset preset = LinkGuiThemeManager.getSelected();
        return preset != null ? preset.background() : ColorUtils.getBackgroundColor();
    }

    private Color themeBg(int alpha) {
        // В Panel-режиме — классический ТЁМНЫЙ «стеклянный» фон фикс-цветом. НЕ getGlassColor —
        // он тянет backgroundColor, синхронизированный с (возможно светлой) Link-темой → панель белела.
        if (!LinkHudStyle.active()) {
            return ColorUtils.safeColor(18, 18, 21, alpha);
        }
        Color c = themeBgBase();
        return ColorUtils.safeColor(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private Color themeText(int alpha) {
        // Panel-режим: классический белый текст (фикс), а не цвет текста Link-темы.
        if (!LinkHudStyle.active()) {
            return ColorUtils.safeColor(255, 255, 255, alpha);
        }
        LinkGuiThemePreset preset = LinkGuiThemeManager.getSelected();
        Color c = preset != null ? preset.text() : ColorUtils.getTextColor();
        return ColorUtils.safeColor(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /** Контрастная подложка (голова/ячейки): чуть темнее на светлой теме, чуть светлее на тёмной. */
    private Color themeInset(int alpha) {
        return themeInset(alpha, 0.16f);
    }

    /** Подложка ячейки с регулируемой силой контраста (для брони — светлее: больше mix). */
    private Color themeInset(int alpha, float mix) {
        // В Panel-режиме — подложка от фикс-тёмного классического фона (не от Link-темы).
        Color bg = LinkHudStyle.active() ? themeBgBase() : ColorUtils.safeColor(18, 18, 21, 255);
        boolean light = (0.299f * bg.getRed() + 0.587f * bg.getGreen() + 0.114f * bg.getBlue()) / 255f > 0.6f;
        Color m = ColorUtils.mix(bg, light ? Color.BLACK : Color.WHITE, mix);
        return ColorUtils.safeColor(m.getRed(), m.getGreen(), m.getBlue(), alpha);
    }

    /** Ячейка предмета TargetHUD (оружие/броня) с предметом, обводкой и полоской прочности. */
    private void drawTargetItemCell(org.joml.Matrix3x2fStack ms, DrawContext ctx, ItemStack stack,
                                    float boxX, float itemY, float boxSize, float itemScale,
                                    float animationValue, int fa, boolean bright) {
        // Фон ячейки: для брони — заметно светлее (больше контраста к фону панели).
        Color cellBg = themeInset(Math.round(235 * animationValue), bright ? 0.42f : 0.16f);
        Render2D.drawRoundedRect(ms, boxX, itemY, boxSize, boxSize, 3.5f, cellBg);
        // Обводка ячейки: в Link — акцент темы; в Panel (старый вид) — приглушённая нейтральная (текст×фон), без «Link»-акцента.
        Color cellBorder = LinkHudStyle.active()
                ? ColorUtils.alpha(ColorUtils.getPrimaryColor(), Math.round(170 * animationValue))
                : ColorUtils.safeColor(255, 255, 255, Math.round(55 * animationValue));
        Render2D.drawBorder(ms, boxX, itemY, boxSize, boxSize, 3.5f, 1.0f, 2.0f, cellBorder);

        float itemPx = 16f * itemScale;
        float ix = boxX + (boxSize - itemPx) / 2f;
        float iy = itemY + (boxSize - itemPx) / 2f;
        ms.pushMatrix();
        ms.translate(ix, iy);
        ms.scale(itemScale, itemScale);
        ctx.drawItem(stack, 0, 0);
        ms.popMatrix();

        // Без полоски прочности — только процент под ячейкой. Цвет: зелёный (≥50%) → жёлтый → красный.
        if (stack.isDamageable()) {
            float dur = 1.0f - ((float) stack.getDamage() / (float) stack.getMaxDamage());
            int pct = Math.max(0, Math.min(100, Math.round(dur * 100f)));
            // На светлой теме яркие цвета плохо видны на белом — берём затемнённые варианты.
            boolean lightTheme = ru.method.linkenvisuals.hud.LinkHudStyle.isLightTheme();
            Color durC = pct < 25 ? (lightTheme ? new Color(198, 40, 40) : new Color(255, 108, 108))
                    : pct < 50 ? (lightTheme ? new Color(176, 116, 0) : new Color(255, 211, 82))
                    : (lightTheme ? new Color(34, 138, 60) : new Color(108, 221, 122));
            String pctText = pct + "%";
            float pctSize = 4.6f;
            float pctW = Fonts.MEDIUM.getWidth(pctText, pctSize);
            Render2D.drawFont(ms, Fonts.MEDIUM.getFont(pctSize), pctText,
                    boxX + boxSize / 2f - pctW / 2f, itemY + boxSize + 1.5f,
                    ColorUtils.safeColor(durC.getRed(), durC.getGreen(), durC.getBlue(), fa));
        }
    }

    private Color getHeadTintColor(PlayerEntity player, int globalAlpha) {
        int safeAlpha = Math.max(0, Math.min(255, globalAlpha));
        if (player instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) player;
            if (living.hurtTime > 0) {
                float hurtTimeFloat = Math.max(0.0f, Math.min(10.0f, (float)living.hurtTime));
                float hurtProgress = hurtTimeFloat / 10.0f;
                float hurtIntensity = Math.max(0.0f, Math.min(1.0f, hurtAnimation.animate(hurtProgress, 300)));
                int red = 255;
                float greenFloat = 255.0f - (105.0f * hurtIntensity);
                float blueFloat = 255.0f - (105.0f * hurtIntensity);
                int green = Math.max(0, Math.min(255, Math.round(greenFloat)));
                int blue = Math.max(0, Math.min(255, Math.round(blueFloat)));
                return ColorUtils.safeColor(red, green, blue, safeAlpha);
            }
        }
        hurtAnimation.animate(0.0f, 300);
        return ColorUtils.safeColor(255, 255, 255, safeAlpha);
    }

    private Identifier getSkinTexture(PlayerEntity player) {
        if (player == null) return null;

        // AllSkins подменяет скин всех игроков в мире (через PlayerEntityRendererMixin) — голову цели
        // в TargetHUD тоже рисуем оверрайдом, иначе в мире скин один, а в худе другой.
        Identifier override = ru.method.linkenvisuals.modules.impl.render.AllSkins.skinOverride();
        if (override != null) {
            return override;
        }

        if (mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null && entry.getSkinTextures() != null) {
                return entry.getSkinTextures().body().texturePath();
            }
        }

        if (player instanceof AbstractClientPlayerEntity clientPlayer) {
            return clientPlayer.getSkin().body().texturePath();
        }

        return null;
    }

    private void renderPlayerHead(EventRender2D event, PlayerEntity player, float x, float y, float size, float radius, Color tint) {
        Identifier skinTexture = getSkinTexture(player);
        if (skinTexture == null) {
            Render2D.drawRoundedRect(event.getContext().getMatrices(), x, y, size, size, radius, ColorUtils.alpha(ColorUtils.getPrimaryColor(), tint.getAlpha()));
            return;
        }

        Object matrices = event.getContext().getMatrices();
        Render2D.drawTextureNearest(
                matrices,
                x,
                y,
                size,
                size,
                radius,
                8f / 64f,
                8f / 64f,
                16f / 64f,
                16f / 64f,
                skinTexture,
                tint
        );
        Render2D.drawTextureNearest(
                matrices,
                x,
                y,
                size,
                size,
                radius,
                40f / 64f,
                8f / 64f,
                48f / 64f,
                16f / 64f,
                skinTexture,
                tint
        );
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) return;

        // Стиль «Карточка»: элемент заменён готовой таргет-карточкой из visual-test (комната-превью,
        // полоса ХП, шрифт round). Цели нет — не рисуем (в чате показывается тест-цель).
        if (LinkHudStyle.active() && LinkHudStyle.variant() == LinkHudStyle.Variant.Card) {
            float x = getX(), y = getY();
            var ms = e.getContext().getMatrices();
            float ui = beginUiScale(ms, x, y);
            float t = toggledAnimation.getValue() * getDragScale();
            ms.pushMatrix();
            ms.translate(x, y);
            ms.scale(t, t);
            ms.translate(-x, -y);
            boolean drawn = unkw.visualtest.client.hud.VisualHud.cardTarget(e.getContext(), mc, Math.round(x), Math.round(y));
            ms.popMatrix();
            endUiScale(ms);
            if (drawn) {
                setUiScaledBounds(x, y, 144, 45, ui);
            }
            super.onRender2D(e);
            return;
        }
        boolean hasTarget = shouldShowTarget();
        float fadeValue = fadeAnimation.animate(hasTarget ? 1.0f : 0.0f, 200);

        if (fadeValue <= 0.0f) {
            renderTarget = null;
            lastNameBoundsValid = false;
            lastNameRowValid = false;
            return;
        }

        if (renderTarget == null || shouldHidePlayer(renderTarget) || isFriendTarget(renderTarget)) {
            renderTarget = null;
            lastNameBoundsValid = false;
            lastNameRowValid = false;
            return;
        }

        float posX = getX();
        float posY = getY();

        // Геометрия: компактная панель — голова слева, имя+HP+бар; ряд предметов (оффхенд+броня) ВНУТРИ панели.
        float width = 104f;
        float topHeight = 30f;
        float round = 5f;
        float headSize = 20f;
        float padding = 4f;
        float gap = 4f;
        float boxSize = 15f;
        float boxGap = 4f;

        PlayerEntity t = renderTarget;

        // Предметы цели ВНУТРИ панели: ТОЛЬКО оффхенд (без основной руки) + броня.
        // Пересобираем не каждый кадр: раз в ~100мс, при смене цели, либо каждый кадр в превью HUD-редактора.
        long nowMs = System.currentTimeMillis();
        boolean editorPreview = ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        if (editorPreview || t != lastItemsTarget || (nowMs - lastItemsRefreshMs) >= ITEMS_REFRESH_MS) {
            cachedItems.clear();
            if (!t.getOffHandStack().isEmpty()) cachedItems.add(t.getOffHandStack());
            net.minecraft.entity.EquipmentSlot[] armorSlots = {
                    net.minecraft.entity.EquipmentSlot.HEAD,
                    net.minecraft.entity.EquipmentSlot.CHEST,
                    net.minecraft.entity.EquipmentSlot.LEGS,
                    net.minecraft.entity.EquipmentSlot.FEET
            };
            for (net.minecraft.entity.EquipmentSlot slot : armorSlots) {
                ItemStack s = t.getEquippedStack(slot);
                if (!s.isEmpty() && s.getItem() != Items.AIR) cachedItems.add(s);
            }
            lastItemsRefreshMs = nowMs;
            lastItemsTarget = t;
        }
        List<ItemStack> items = cachedItems;
        float itemRowH = items.isEmpty() ? 0f : (boxSize + 10f);
        float height = topHeight + itemRowH;
        float effectiveHeight = height;
        float currentHp = MathUtils.round(Server.getHealth(t, false));
        float maxHp = Math.max(t.getMaxHealth(), 1.0f);
        float absorptionHp = MathUtils.round(t.getAbsorptionAmount());
        boolean isFuntimeServer = Server.isFuntime();
        if (isFuntimeServer) absorptionHp = 0;

        // Вспышка при получении урона целью.
        if (lastHealth >= 0f && currentHp < lastHealth) hitFlash = 1f;
        lastHealth = currentHp;
        if (hitFlash > 0f) hitFlash = Math.max(0f, hitFlash - 0.05f);

        float healthPercent = Math.min(currentHp, maxHp) / maxHp;
        float absorptionPercent = Math.max(0f, absorptionHp) / maxHp;

        float animationValue = Math.max(0.0f, Math.min(1.0f, fadeValue));
        int globalAlpha = Math.max(0, Math.min(255, Math.round(255 * animationValue)));
        int fa = globalAlpha;

        var ms = e.getContext().getMatrices();
        DrawContext ctx = e.getContext();

        float uiScale = beginUiScale(ms, posX, posY);
        ms.pushMatrix();
        float[] dragOffset = getDragOffset();
        float scaleValue = toggledAnimation.getValue() * animationValue * getDragScale();
        float centerX = posX + width / 2 + dragOffset[0];
        float centerY = posY + effectiveHeight / 2 + dragOffset[1];
        ms.translate(centerX, centerY);
        ms.scale(scaleValue, scaleValue);
        ms.translate(-centerX, -centerY);

        // --- Панель ---
        if (LinkHudStyle.active()) {
            // Link-стиль: панель выбранного варианта оформления в цветах темы (Glass/Solid/Gradient/Outline).
            LinkHudStyle.panel(ms, posX, posY, width, height, round, animationValue);
        } else {
            // Panel GUI: то же «жидкое стекло», что у Задержки/Эффектов (getGlassColor + тот же шейдер) —
            // единый вид. Но пресет задаём РАВНЫМ фактическому размеру (drawLiquidRectPreset preset=visual):
            // тогда scaleX=scaleY=1, шейдер рисует РОВНО по контенту и не «вылезает» (в отличие от
            // drawLiquidRectHud, который снапит к чужому пресету с допуском 35%).
            Render2D.drawLiquidRectPreset(ms, posX, posY, width, height, width, height,
                    new BorderRadius(10f),
                    new ColorRGBA(ColorUtils.getGlassColor(255)),
                    2.5f, 22.0f, 11f, 175.0f, true, 0f, 0.19f);
        }
        // лёгкая вспышка панели при ударе
        if (hitFlash > 0.01f) {
            Render2D.drawRoundedRect(ms, posX, posY, width, height, round, ColorUtils.safeColor(255, 50, 50, Math.round(55 * hitFlash * animationValue)));
        }

        // --- Голова --- (центрируем в ВЕРХНЕЙ части панели, не учитывая ряд предметов снизу)
        float headX = posX + padding;
        float headY = posY + (topHeight - headSize) / 2f;
        Render2D.drawRoundedRect(ms, headX, headY, headSize, headSize, 2.5f, themeInset(Math.round(205 * animationValue)));
        Color headTintColor = getHeadTintColor(t, globalAlpha);
        renderPlayerHead(e, t, headX, headY, headSize, 2.5f, headTintColor);

        // --- Текст: имя + число HP ---
        float textX = headX + headSize + gap;
        float nameSize = 7.6f;
        float hpSize = 6.6f;
        float rightLimit = posX + width - padding;
        float nameMaxWidth = Math.max(0f, rightLimit - textX);

        String fullName = StreamerModeUtils.resolvePlainDisplayName(t.getGameProfile().name());
        // Обрезка ТЕМ ЖЕ шрифтом, которым имя рисуется (ROUNDBOLD) — иначе жирный текст шире и вылезал бы за панель.
        // Кэш: если (fullName, nameMaxWidth) не изменились с прошлого кадра — переиспользуем прошлый результат.
        String displayName;
        if (lastDisplayName != null
                && java.util.Objects.equals(fullName, lastFullName)
                && lastNameMaxWidth == nameMaxWidth) {
            displayName = lastDisplayName;
        } else {
            displayName = fitText(Fonts.ROUNDBOLD, fullName, nameSize, nameMaxWidth);
            lastFullName = fullName;
            lastNameMaxWidth = nameMaxWidth;
            lastDisplayName = displayName;
        }
        Color nameColor = themeText(fa);
        Render2D.drawFont(ms, Fonts.ROUNDBOLD.getFont(nameSize), displayName, textX, posY + padding + 1f, nameColor);

        // Кэш HP-текста: String.format пересчитываем только при смене отображаемого значения (округл. до 0.1).
        float hpValue = Math.max(0f, currentHp + absorptionHp);
        long hpKey = Math.round(hpValue * 10f);
        if (lastHpText == null || hpKey != lastHpKey) {
            lastHpText = formatHp(hpValue) + " HP";
            lastHpKey = hpKey;
        }
        String hpText = lastHpText;
        Render2D.drawFont(ms, Fonts.MEDIUM.getFont(hpSize), hpText, textX, posY + padding + nameSize + 3f, themeText(Math.round(205 * animationValue)));

        // имя для копирования по средней кнопке
        lastNameValue = fullName;
        float scaledNameX = centerX + (textX - centerX) * scaleValue;
        float scaledNameY = centerY + ((posY + padding + 1f) - centerY) * scaleValue;
        lastNameX = scaleUiX(posX, scaledNameX, uiScale);
        lastNameY = scaleUiY(posY, scaledNameY, uiScale);
        lastNameW = Math.max(Fonts.ROUNDBOLD.getWidth(displayName, nameSize), 1f) * scaleValue * uiScale;
        lastNameH = Fonts.ROUNDBOLD.getHeight(nameSize) * scaleValue * uiScale;
        lastNameBoundsValid = scaleValue > 0.001f;
        lastNameRowValid = false;

        // --- Полоса здоровья у нижней кромки головы ---
        float barH = 2f;
        float barX = textX;
        float barY = headY + headSize - barH;
        float barW = rightLimit - textX;
        float barRound = 1f;
        Render2D.drawRoundedRect(ms, barX, barY, barW, barH, barRound, ColorUtils.safeColor(0, 0, 0, Math.round(180 * animationValue)));

        float healthWidth = healthAnimation.animate(barW * healthPercent, 200);
        float absorptionWidth = gappleAnimation.animate(barW * absorptionPercent, 200);

        Color c1 = ColorUtils.alpha(ColorUtils.getPrimaryColor(), fa);
        Color c2 = ColorUtils.alpha(ColorUtils.getSecondaryColor(), fa);
        if (healthWidth > 0.5f) {
            Render2D.drawRoundedHorizontalGradient(ms, barX, barY, Math.max(0.5f, healthWidth), barH, barRound, c1, c2);
        }
        if (!isFuntimeServer && absorptionWidth > 0.5f) {
            Render2D.drawRoundedHorizontalGradient(ms, barX, barY, Math.max(0.5f, absorptionWidth), barH, barRound,
                    ColorUtils.safeColor(255, 215, 0, fa), ColorUtils.safeColor(218, 165, 32, fa));
        }

        // --- Ряд предметов (оффхенд + броня) ВНУТРИ панели, по центру; % без полоски прочности ---
        if (!items.isEmpty()) {
            float itemScale = 0.72f * animationValue;
            float rowW = items.size() * boxSize + Math.max(0, items.size() - 1) * boxGap;
            float startX = posX + (width - rowW) / 2f;
            float itemY = posY + topHeight - 1f;
            for (int i = 0; i < items.size(); i++) {
                drawTargetItemCell(ms, ctx, items.get(i), startX + i * (boxSize + boxGap), itemY, boxSize, itemScale, animationValue, fa, true);
            }
        }

        ms.popMatrix();
        endUiScale(ms);
        setUiScaledBounds(posX, posY, width, effectiveHeight, uiScale);
        super.onRender2D(e);
    }

    @Override
    protected boolean onMiddleClick(int mouseX, int mouseY) {
        if (lastNameValue == null || lastNameValue.isBlank()) return false;

        boolean hoveredName = lastNameBoundsValid
                && MathUtils.isHovered(lastNameX, lastNameY, lastNameW, lastNameH, mouseX, mouseY);
        if (!hoveredName && lastNameRowValid) {
            hoveredName = MathUtils.isHovered(lastNameRowX, lastNameRowY, lastNameRowW, lastNameRowH, mouseX, mouseY);
        }
        if (!hoveredName) return false;

        long now = System.currentTimeMillis();
        if (now - lastCopyTimeMs < 250L) return true;
        lastCopyTimeMs = now;

        mc.keyboard.setClipboard(lastNameValue);
        LinkenVisuals.getInstance().getNotifyManager().add(new Notify(NotifyIcons.successIcon, "\u041d\u0438\u043a \u0443\u0441\u043f\u0435\u0448\u043d\u043e \u0441\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043d", 1000));
        return true;
    }
}
