package ru.method.linkenvisuals.utils.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import org.joml.Matrix3x2fStack;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.api.Category;

import java.awt.Color;

/** Иконки-текстуры (белые маски, красятся цветом) — рендерятся как глифы, но из PNG. */
public final class GuiIcons {
    public static final Identifier PALETTE = LinkenVisuals.id("textures/gui/icons/icon_palette.png");
    public static final Identifier AVATAR = LinkenVisuals.id("textures/gui/icons/icon_avatar.png");
    public static final Identifier SOUND = LinkenVisuals.id("textures/gui/icons/icon_sound.png");
    public static final Identifier MUTE = LinkenVisuals.id("textures/gui/icons/icon_mute.png");
    public static final Identifier DOT = LinkenVisuals.id("textures/gui/icons/icon_dot.png");
    public static final Identifier KEYBOARD = LinkenVisuals.id("textures/gui/icons/icon_keyboard.png");
    public static final Identifier SWORD = LinkenVisuals.id("textures/gui/modules/sword.png");

    private GuiIcons() {
    }

    /** Текстура-иконка категории (или null — тогда рисуется глиф-иконка категории). */
    public static Identifier forCategory(Category category) {
        return category == Category.Player ? AVATAR : null;
    }

    // Фильтр ставится текстуре ОДИН раз: setFilter на каждую иконку каждый кадр — десятки лишних
    // GL-вызовов (glTexParameter) за кадр по всем иконкам GUI.
    private static final java.util.Set<Identifier> FILTERED = new java.util.HashSet<>();

    /** Рисует иконку-текстуру по центру (centerX, centerY) размером size, крашенную цветом. */
    public static void draw(Matrix3x2fStack stack, Identifier texture, float centerX, float centerY, float size, Color color) {
        if (texture == null || color == null || color.getAlpha() <= 0 || size <= 0f) return;
        if (!FILTERED.contains(texture)) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                // Линейная фильтрация — чтобы мелкая иконка была гладкой, а не «лесенкой» (как глиф).
                AbstractTexture tex = mc.getTextureManager().getTexture(texture);
                if (tex != null) {
                    FILTERED.add(texture);
                }
            }
        }
        Render2D.drawTexture(stack, centerX - size / 2f, centerY - size / 2f, size, size, 0f, texture, color);
    }
}
