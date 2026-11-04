package ru.method.linkenvisuals.hud.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.item.ItemStack;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;

public class InventoryHud extends HudElement {
    private static final float SLOT = 18f;
    private static final float ICON = 16f;
    private static final float ITEM_SCALE = 0.9f;
    private static final float GAP = 2f;
    private static final float PADDING = 7f;
    private static final float HOTBAR_SECTION_GAP = 7f;
    private static final float HOTBAR_SEPARATOR_HEIGHT = 1f;
    private static final int COLUMNS = 9;

    private final BooleanSetting hotbar = new BooleanSetting("settings.inventoryhud.hotbar", false);

    public InventoryHud() {
        super("InventoryHud");
    }

    @Override
    public void onRender2D(EventRender2D event) {
        if (fullNullCheck() || closed()) return;

        boolean preview = ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        boolean showHotbar = hotbar.getValue();
        int rows = showHotbar ? 4 : 3;
        float width = PADDING * 2f + COLUMNS * SLOT + (COLUMNS - 1) * GAP;
        float height = PADDING * 2f + rows * SLOT + (rows - 1) * GAP + (showHotbar ? HOTBAR_SECTION_GAP : 0f);
        float x = getX();
        float y = getY();

        float uiScale = beginUiScale(event.getContext().getMatrices(), x, y);
        event.getContext().getMatrices().pushMatrix();
        float scale = toggledAnimation.getValue() * getDragScale();
        float cx = x + width / 2f;
        float cy = y + height / 2f;
        event.getContext().getMatrices().translate(cx, cy);
        event.getContext().getMatrices().scale(scale, scale);
        event.getContext().getMatrices().translate(-cx, -cy);

        if (LinkHudStyle.active()) {
            LinkHudStyle.panel(event.getContext().getMatrices(), x, y, width, height,
                    LinkHudStyle.radius(width, height), preview ? 1f : 0.92f);
        } else {
            Render2D.drawLiquidRectHud(
                    event.getContext().getMatrices(),
                    x,
                    y,
                    width,
                    height,
                    new BorderRadius(10f),
                    new ColorRGBA(ColorUtils.getGlassColor(preview ? 235 : 210)),
                    2.8f,
                    24f,
                    12f,
                    180f,
                    true,
                    0f,
                    0.22f
            );
        }

        if (showHotbar) {
            float separatorY = y + PADDING + 3 * SLOT + 2 * GAP + HOTBAR_SECTION_GAP / 2f;
            Render2D.drawRoundedRect(
                    event.getContext().getMatrices(),
                    x + PADDING + 3f,
                    separatorY,
                    width - PADDING * 2f - 6f,
                    HOTBAR_SEPARATOR_HEIGHT,
                    HOTBAR_SEPARATOR_HEIGHT,
                    new Color(255, 255, 255, preview ? 85 : 60)
            );
        }

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int inventoryIndex = row < 3 ? 9 + row * COLUMNS + col : col;
                float slotX = pixel(x + PADDING + col * (SLOT + GAP));
                float slotY = pixel(y + PADDING + row * (SLOT + GAP) + (showHotbar && row == 3 ? HOTBAR_SECTION_GAP : 0f));
                float iconX = slotX + (SLOT - ICON) / 2f;
                float iconY = slotY + (SLOT - ICON) / 2f;
                // Как в ArmorHud: врезанная ячейка темы (с контрастной кромкой на светлых темах),
                // иначе на белой панели слоты сливались. Вне Link-стиля — прежний бледный фон на стекле.
                if (LinkHudStyle.active()) {
                    LinkHudStyle.cell(event.getContext().getMatrices(), slotX, slotY, SLOT, SLOT, 5f, 1f);
                } else {
                    Render2D.drawRoundedRect(event.getContext().getMatrices(), iconX, iconY, ICON, ICON, 4f, new Color(255, 255, 255, 24));
                }
                ItemStack stack = mc.player.getInventory().getStack(inventoryIndex);
                if (!stack.isEmpty()) {
                    drawItem(event, stack, iconX, iconY);
                }
            }
        }

        event.getContext().getMatrices().popMatrix();
        endUiScale(event.getContext().getMatrices());
        setUiScaledBounds(x, y, width, height, uiScale);
        super.onRender2D(event);
    }

    private void drawItem(EventRender2D event, ItemStack stack, float x, float y) {
        try {
            int itemX = Math.round(x);
            int itemY = Math.round(y);
            event.getContext().getMatrices().pushMatrix();
            float inset = (ICON - ICON * ITEM_SCALE) / 2f;
            event.getContext().getMatrices().translate(itemX + inset, itemY + inset);
            event.getContext().getMatrices().scale(ITEM_SCALE, ITEM_SCALE);
            event.getContext().drawItem(mc.player, stack, 0, 0, 0);
            event.getContext().drawStackOverlay(mc.textRenderer, stack, 0, 0);
            event.getContext().getMatrices().popMatrix();
        } catch (Exception ignored) {}
    }

    private float pixel(float value) {
        return Math.round(value);
    }
}
