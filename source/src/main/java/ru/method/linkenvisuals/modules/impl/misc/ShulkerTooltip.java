package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ShulkerTooltip extends Module {
    private static final float SLOT = 18f;
    private static final float PADDING = 6f;
    private static final int COLUMNS = 9;
    private static final int ROWS = 3;

    private final BooleanSetting alwaysOn = new BooleanSetting("settings.shulkertooltip.always_on", false);
    private final BindSetting showBind = new BindSetting("settings.shulkertooltip.bind", new Bind(-1, false), () -> !alwaysOn.getValue());
    private boolean keyDown;
    private boolean mouseDown;

    public ShulkerTooltip() {
        super("ShulkerTooltip", Category.Player);
    }

    @EventHandler
    private void onKey(EventKey event) {
        if (showBind.getValue().matchesKey(event.getKey())) {
            keyDown = event.getAction() != 0;
        }
    }

    @EventHandler
    private void onMouse(EventMouse event) {
        if (showBind.getValue().matchesMouse(event.getButton())) {
            mouseDown = event.getAction() != 0;
            event.consume();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        keyDown = false;
        mouseDown = false;
    }

    public void render(DrawContext context, Slot slot, int mouseX, int mouseY) {
        if (!isToggled() || slot == null || !slot.hasStack() || !isPressed()) return;

        ItemStack stack = slot.getStack();
        if (!isShulker(stack)) return;

        List<ItemStack> contents = getContents(stack);
        if (contents.isEmpty()) return;

        float width = PADDING * 2f + COLUMNS * SLOT;
        float height = PADDING * 2f + ROWS * SLOT;
        float screenW = context.getScaledWindowWidth();
        float screenH = context.getScaledWindowHeight();
        float margin = 4f;

        float leftCandidate = mouseX - width - 12f;
        float rightCandidate = mouseX + 12f;
        float x = leftCandidate >= margin ? leftCandidate : rightCandidate;
        if (x + width > screenW - margin) {
            x = Math.max(margin, screenW - width - margin);
        }
        float y = Math.max(margin, Math.min(mouseY + 12f, screenH - height - margin));

        // Defensive: remove leaked scissor from other UI paths, otherwise item icons can be clipped.
        Render2D.clearScissor(context);
        context.getMatrices().pushMatrix();

        if (ru.method.linkenvisuals.hud.LinkHudStyle.active()) {
            ru.method.linkenvisuals.hud.LinkHudStyle.panel(context.getMatrices(), x, y, width, height,
                    ru.method.linkenvisuals.hud.LinkHudStyle.radius(width, height), 1f);
        } else {
            Render2D.drawLiquidRect(
                    context.getMatrices(),
                    x,
                    y,
                    width,
                    height,
                    new BorderRadius(8f),
                    new ColorRGBA(ColorUtils.getGlassColor(235)),
                    2.5f,
                    22f,
                    10f,
                    170f,
                    true,
                    0f,
                    0.2f
            );
        }

        Color slotColor = new Color(255, 255, 255, 30);
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            float sx = x + PADDING + (i % COLUMNS) * SLOT;
            float sy = y + PADDING + (i / COLUMNS) * SLOT;
            Render2D.drawRoundedRect(context.getMatrices(), sx + 1f, sy + 1f, 16f, 16f, 4f, slotColor);
            if (i < contents.size() && !contents.get(i).isEmpty()) {
                int itemX = Math.round(sx + 1f);
                int itemY = Math.round(sy + 1f);
                context.drawItem(contents.get(i), itemX, itemY);
                context.drawStackOverlay(mc.textRenderer, contents.get(i), itemX, itemY);
            }
        }
        context.getMatrices().popMatrix();
    }

    private boolean isPressed() {
        if (alwaysOn.getValue()) {
            return true;
        }
        Bind bind = showBind.getValue();
        if (bind == null || bind.isEmpty()) return false;
        return keyDown || mouseDown;
    }

    private boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private List<ItemStack> getContents(ItemStack stack) {
        List<ItemStack> items = new ArrayList<>();
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) return items;
        DefaultedList<ItemStack> copied = DefaultedList.ofSize(COLUMNS * ROWS, ItemStack.EMPTY);
        container.copyTo(copied);
        items.addAll(copied);
        return items;
    }
}
