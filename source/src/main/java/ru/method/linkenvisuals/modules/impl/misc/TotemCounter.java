package ru.method.linkenvisuals.modules.impl.misc;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.collection.DefaultedList;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

public class TotemCounter extends Module {
    private int count;

    public TotemCounter() {
        super("TotemCounter", Category.Player);
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) {
            count = 0;
            return;
        }

        int total = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) {
                total += stack.getCount();
            }
        }
        count = total;
    }

    @EventHandler
    private void onRender2D(EventRender2D event) {
        if (fullNullCheck()) return;

        String text = Integer.toString(count);
        float fontSize = 8f;
        float centerX = mc.getWindow().getScaledWidth() / 2f;
        float centerY = mc.getWindow().getScaledHeight() / 2f;
        float iconX = centerX + 10f;
        float iconY = centerY + 8f;
        float textX = iconX + 18f;
        float textY = iconY + 4.5f - Fonts.ROUNDBOLD.getHeight(fontSize) / 2f;

        try {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            event.getContext().drawItem(mc.player, new ItemStack(Items.TOTEM_OF_UNDYING), (int) iconX, (int) iconY, 0);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        } catch (Exception ignored) {}

        Render2D.drawFont(
                event.getContext().getMatrices(),
                Fonts.ROUNDBOLD.getFont(fontSize),
                text,
                textX,
                textY,
                new Color(255, 255, 255, 255)
        );
    }

    public int getCount() {
        return count;
    }

    private int countTotems(DefaultedList<ItemStack> stacks) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
