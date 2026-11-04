package ru.method.linkenvisuals.hud.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandNotificationManager;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;
import java.util.Arrays;

public class ArmorHud extends HudElement {
    private static final float V1_SLOT = 19f;
    private static final float V1_GAP = 4f;
    private static final float V1_PADDING = 6f;
    private static final float V1_TEXT_SIZE = 5.6f;

    private static final float V2_PADDING = 6f;
    private static final float V2_ROW_HEIGHT = 17f;
    private static final float V2_GROUP_GAP = 3f;
    private static final float V2_SIDE_GAP = 6f;
    // Доп. сдвиг ЛЕВОЙ группы влево на ширину слота левой руки (оффхенд ~22px + зазор), чтобы панель
    // брони не перекрывала предмет в левой руке. Правую группу не трогаем — справа оффхенда нет.
    private static final float V2_OFFHAND_CLEARANCE = 24f;
    private static final float V2_TEXT_SIZE = 6.8f;
    private static final float V2_GROUP_WIDTH = 49f;
    private static final float V2_HOTBAR_UP_OFFSET = 12f;
    private static final long ALERT_DURATION_MS = 1900L;

    private static final Color DURABILITY_GOOD = new Color(108, 221, 122);
    private static final Color DURABILITY_WARN = new Color(255, 211, 82);
    private static final Color DURABILITY_CRITICAL = new Color(255, 108, 108);
    private static final Color DURABILITY_EMPTY = new Color(170, 170, 170);

    private final EnumSetting<VisualMode> mode = new EnumSetting<>("settings.armorhud.mode", VisualMode.V1);
    private final InfinityAnimation emptyFadeAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private float emptyFade = 1f;
    private final int[] lastAlertStages = new int[4];
    private final String[] lastAlertKeys = new String[4];

    public ArmorHud() {
        super("ArmorHud");
    }

    @Override
    public void onEnable() {
        resetAlerts();
        super.onEnable();
    }

    private static final net.minecraft.entity.EquipmentSlot[] ARMOR_SLOTS = {
            net.minecraft.entity.EquipmentSlot.FEET,
            net.minecraft.entity.EquipmentSlot.LEGS,
            net.minecraft.entity.EquipmentSlot.CHEST,
            net.minecraft.entity.EquipmentSlot.HEAD
    };

    private ItemStack getArmorPiece(int slotIndex) {
        if (mc.player == null || slotIndex < 0 || slotIndex >= ARMOR_SLOTS.length) {
            return ItemStack.EMPTY;
        }
        return mc.player.getEquippedStack(ARMOR_SLOTS[slotIndex]);
    }

    @Override
    public void onDisable() {
        resetAlerts();
        super.onDisable();
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (fullNullCheck() || mode.getValue() != VisualMode.V2) {
            resetAlerts();
            return;
        }

        for (int armorSlot = 0; armorSlot < 4; armorSlot++) {
            ItemStack stack = getArmorPiece(armorSlot);
            String itemKey = buildAlertKey(stack);
            if (!itemKey.equals(lastAlertKeys[armorSlot])) {
                lastAlertKeys[armorSlot] = itemKey;
                lastAlertStages[armorSlot] = 0;
            }

            int stage = getAlertStage(stack);
            if (stage <= 0) {
                lastAlertStages[armorSlot] = 0;
                continue;
            }

            if (stage > lastAlertStages[armorSlot]) {
                sendDurabilityAlert(stack, getRemainingPercent(stack), stage);
            }

            lastAlertStages[armorSlot] = stage;
        }
    }

    @Override
    public void onRender2D(EventRender2D event) {
        if (fullNullCheck() || closed()) return;

        // Стиль «Карточка»: панель брони из visual-test (слоты 82x25) — только в режиме V1.
        // V2 всегда остаётся классическим видом У ХОТБАРА (renderV2), карточка его не подменяет.
        // Раньше ветка перехватывала рендер до проверки режима, и настройка V1/V2 не делала ничего.
        if (LinkHudStyle.active() && LinkHudStyle.variant() == LinkHudStyle.Variant.Card
                && mode.getValue() == VisualMode.V1) {
            float x = getX(), y = getY();
            var ms = event.getContext().getMatrices();
            float ui = beginUiScale(ms, x, y);
            float t = toggledAnimation.getValue() * getDragScale();
            ms.pushMatrix();
            ms.translate(x, y);
            ms.scale(t, t);
            ms.translate(-x, -y);
            boolean drawn = unkw.visualtest.client.hud.VisualHud.cardArmor(event.getContext(), mc, Math.round(x), Math.round(y));
            ms.popMatrix();
            endUiScale(ms);
            // Границы ставим и в редакторе HUD: без брони cardArmor не рисуется и не ставил bounds —
            // элемент было невозможно схватить и перетащить.
            if (drawn || ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive()) {
                setUiScaledBounds(x, y, 82, 25, ui);
            }
            super.onRender2D(event);
            return;
        }
        // Пустая броня видна только в режиме редактирования (чат / редактор Elements) и плавно
        // появляется/исчезает (fade-scale), а не «попом».
        boolean empty = isArmorEmpty();
        boolean show = !empty || ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        emptyFade = emptyFadeAnimation.animate(show ? 1f : 0f, 220);
        if (empty && emptyFade <= 0.02f) return;

        if (mode.getValue() == VisualMode.V2) {
            renderV2(event);
        } else {
            renderV1(event);
        }

        super.onRender2D(event);
    }

    @Override
    protected boolean onLeftClick(int mouseX, int mouseY) {
        return mode.getValue() == VisualMode.V2;
    }

    /** Надета ли хоть одна часть брони. */
    private boolean isArmorEmpty() {
        if (mc.player == null) return true;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = getArmorPiece(i);
            if (stack != null && !stack.isEmpty()) return false;
        }
        return true;
    }

    private void renderV1(EventRender2D event) {
        boolean preview = ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        float width = V1_PADDING * 2f + V1_SLOT * 4f + V1_GAP * 3f;
        float height = V1_PADDING * 2f + V1_SLOT + 8f;
        float x = getX();
        float y = getY();

        float uiScale = beginUiScale(event.getContext().getMatrices(), x, y);
        event.getContext().getMatrices().pushMatrix();
        float scale = toggledAnimation.getValue() * getDragScale() * emptyFade;
        float cx = x + width / 2f;
        float cy = y + height / 2f;
        event.getContext().getMatrices().translate(cx, cy);
        event.getContext().getMatrices().scale(scale, scale);
        event.getContext().getMatrices().translate(-cx, -cy);

        boolean link = LinkHudStyle.active();
        if (link) {
            LinkHudStyle.panel(event.getContext().getMatrices(), x, y, width, height, LinkHudStyle.radius(width, height), preview ? 1f : 0.96f);
        } else {
            Render2D.drawLiquidRectHud(
                    event.getContext().getMatrices(),
                    x,
                    y,
                    width,
                    height,
                    new BorderRadius(10f),
                    new ColorRGBA(ColorUtils.getGlassColor(preview ? 235 : 210)),
                    2.5f,
                    22f,
                    10f,
                    175f,
                    true,
                    0f,
                    0.2f
            );
        }

        for (int i = 0; i < 4; i++) {
            float slotX = x + V1_PADDING + i * (V1_SLOT + V1_GAP);
            float slotY = y + V1_PADDING;
            if (link) {
                LinkHudStyle.cell(event.getContext().getMatrices(), slotX + 1f, slotY + 1f, 17f, 17f, 5f, 1f);
            } else {
                Render2D.drawRoundedRect(event.getContext().getMatrices(), slotX + 1f, slotY + 1f, 17f, 17f, 5f, new Color(255, 255, 255, 26));
            }
            ItemStack stack = getArmorPiece(3 - i);
            if (!stack.isEmpty()) {
                drawItem(event, stack, slotX + 1.5f, slotY + 1.5f);
                drawV1Percent(event, stack, slotX, slotY + V1_SLOT + 0.6f);
            }
        }

        event.getContext().getMatrices().popMatrix();
        endUiScale(event.getContext().getMatrices());
        setUiScaledBounds(x, y, width, height, uiScale);
    }

    private void drawV1Percent(EventRender2D event, ItemStack stack, float slotX, float textY) {
        int percent = getRemainingPercent(stack);
        if (percent < 0) return;

        String text = percent + "%";
        float textWidth = Fonts.ROUNDBOLD.getWidth(text, V1_TEXT_SIZE);
        Render2D.drawFont(
                event.getContext().getMatrices(),
                Fonts.ROUNDBOLD.getFont(V1_TEXT_SIZE),
                text,
                slotX + (V1_SLOT - textWidth) / 2f,
                textY,
                getDurabilityColor(percent)
        );
    }

    private void renderV2(EventRender2D event) {
        boolean preview = ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        float panelHeight = V2_PADDING * 2f + V2_ROW_HEIGHT * 2f + V2_GROUP_GAP;
        float screenWidth = mc.getWindow().getScaledWidth();
        float screenHeight = mc.getWindow().getScaledHeight();
        float hotbarX = screenWidth / 2f - 91f;
        float hotbarWidth = 182f;
        float hotbarRight = hotbarX + hotbarWidth;
        float hotbarCenterY = screenHeight - 11f - V2_HOTBAR_UP_OFFSET;
        float baseY = hotbarCenterY - panelHeight / 2f;

        float leftAnchorX = hotbarX - V2_SIDE_GAP - V2_OFFHAND_CLEARANCE; // левую группу — за слот левой руки
        float rightAnchorX = hotbarRight + V2_SIDE_GAP;
        float scale = Math.max(0.0f, toggledAnimation.getValue() * getUiScale() * emptyFade);

        renderAnchoredV2Group(event, leftAnchorX - V2_GROUP_WIDTH, baseY, leftAnchorX, hotbarCenterY, scale, preview, new int[]{3, 2});
        renderAnchoredV2Group(event, rightAnchorX, baseY, rightAnchorX, hotbarCenterY, scale, preview, new int[]{1, 0});

        float scaledWidth = V2_GROUP_WIDTH * scale;
        float scaledHeight = panelHeight * scale;
        float unionX = leftAnchorX - scaledWidth;
        float unionY = hotbarCenterY - scaledHeight / 2f;
        float unionWidth = (rightAnchorX + scaledWidth) - unionX;
        setBounds(unionX, unionY, unionWidth, scaledHeight);
    }

    private void renderAnchoredV2Group(EventRender2D event, float x, float y, float anchorX, float anchorY, float scale, boolean preview, int[] armorSlots) {
        if (scale <= 0.0f) return;
        event.getContext().getMatrices().pushMatrix();
        event.getContext().getMatrices().translate(anchorX, anchorY);
        event.getContext().getMatrices().scale(scale, scale);
        event.getContext().getMatrices().translate(-anchorX, -anchorY);
        drawV2Group(event, x, y, preview, armorSlots);
        event.getContext().getMatrices().popMatrix();
    }

    private void drawV2Group(EventRender2D event, float x, float y, boolean preview, int[] armorSlots) {
        float height = V2_PADDING * 2f + V2_ROW_HEIGHT * 2f + V2_GROUP_GAP;
        if (LinkHudStyle.active()) {
            LinkHudStyle.panel(event.getContext().getMatrices(), x, y, V2_GROUP_WIDTH, height, LinkHudStyle.radius(V2_GROUP_WIDTH, height), preview ? 1f : 0.96f);
        } else {
            Render2D.drawLiquidRectHud(
                    event.getContext().getMatrices(),
                    x,
                    y,
                    V2_GROUP_WIDTH,
                    height,
                    new BorderRadius(10f),
                    new ColorRGBA(ColorUtils.getGlassColor(preview ? 235 : 215)),
                    2.5f,
                    22f,
                    10f,
                    175f,
                    true,
                    0f,
                    0.2f
            );
        }

        for (int row = 0; row < armorSlots.length; row++) {
            int armorSlot = armorSlots[row];
            float rowY = y + V2_PADDING + row * (V2_ROW_HEIGHT + V2_GROUP_GAP);
            drawV2Entry(event, x + V2_PADDING, rowY, armorSlot);
        }
    }

    private void drawV2Entry(EventRender2D event, float x, float y, int armorSlot) {
        ItemStack equipped = getArmorPiece(armorSlot);
        int percent = getRemainingPercent(equipped);

        if (LinkHudStyle.active()) {
            LinkHudStyle.cell(event.getContext().getMatrices(), x, y, 17f, 17f, 5f, 1f);
        } else {
            Render2D.drawRoundedRect(event.getContext().getMatrices(), x, y, 17f, 17f, 5f, new Color(255, 255, 255, 24));
        }
        if (equipped.isEmpty()) {
            return;
        }

        drawItem(event, equipped, x + 0.5f, y + 0.5f);

        String text = percent < 0 ? "--" : percent + "%";
        Color textColor = percent < 0 ? DURABILITY_EMPTY : getDurabilityColor(percent);
        float textWidth = Fonts.ROUNDBOLD.getWidth(text, V2_TEXT_SIZE);
        float textRight = x + V2_GROUP_WIDTH - V2_PADDING * 2f;
        float textX = textRight - textWidth;
        float textY = y + (17f - Fonts.ROUNDBOLD.getHeight(V2_TEXT_SIZE)) / 2f + 0.5f;

        Render2D.drawFont(
                event.getContext().getMatrices(),
                Fonts.ROUNDBOLD.getFont(V2_TEXT_SIZE),
                text,
                textX,
                textY,
                textColor
        );
    }

    private void drawItem(EventRender2D event, ItemStack stack, float x, float y) {
        try {
            var matrices = event.getContext().getMatrices();
            int ix = (int) Math.floor(x);
            int iy = (int) Math.floor(y);
            matrices.pushMatrix();
            matrices.translate(x - ix, y - iy);
            event.getContext().drawItem(mc.player, stack, ix, iy, 0);
            matrices.popMatrix();
        } catch (Exception ignored) {
        }
    }

    private int getRemainingPercent(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        if (!stack.isDamageable()) return 100;
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) return 100;
        int remaining = Math.max(0, maxDamage - stack.getDamage());
        return Math.max(0, Math.min(100, Math.round(remaining * 100f / maxDamage)));
    }

    private Color getDurabilityColor(int percent) {
        // На светлой теме яркие зелёный/жёлтый плохо видны на белом фоне — берём затемнённые варианты.
        boolean light = ru.method.linkenvisuals.hud.LinkHudStyle.isLightTheme();
        if (percent < 25) return light ? new Color(198, 40, 40) : DURABILITY_CRITICAL;
        if (percent < 50) return light ? new Color(176, 116, 0) : DURABILITY_WARN;
        return light ? new Color(34, 138, 60) : DURABILITY_GOOD;
    }

    private int getAlertStage(ItemStack stack) {
        int percent = getRemainingPercent(stack);
        if (percent < 0) return 0;
        if (percent < 25) return 2;
        if (percent < 50) return 1;
        return 0;
    }

    private void sendDurabilityAlert(ItemStack stack, int percent, int stage) {
        DynamicIslandNotificationManager manager = DynamicIslandNotificationManager.getInstance();
        Color statusColor = stage >= 2 ? DURABILITY_CRITICAL : DURABILITY_WARN;
        String message = I18n.translate(
                stage >= 2 ? "notifications.armorhud.critical" : "notifications.armorhud.low",
                stack.getName().getString(),
                percent
        );
        manager.addStatusNotification(message, statusColor, ALERT_DURATION_MS);
    }

    private void resetAlerts() {
        Arrays.fill(lastAlertStages, 0);
        Arrays.fill(lastAlertKeys, "");
    }

    private String buildAlertKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return Registries.ITEM.getId(stack.getItem()) + "|" + stack.getMaxDamage();
    }

    private enum VisualMode implements Nameable {
        V1("settings.armorhud.mode.v1"),
        V2("settings.armorhud.mode.v2");

        private final String name;

        VisualMode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
