package unkw.visualtest.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Locale;

public final class VisualHud {
    private static final Identifier TARGET_BODY = Identifier.of("visual-test", "textures/gui/target/target_body.png");
    private static final Identifier TARGET_ROOM = Identifier.of("visual-test", "textures/gui/target/target_room.png");
    private static final Identifier TARGET_HEALTH = Identifier.of("visual-test", "textures/gui/target/target_health.png");
    private static final Identifier SURFACE = Identifier.of("visual-test", "textures/gui/sprites/hud/surface.png");
    private static final Identifier SURFACE_STRONG = Identifier.of("visual-test", "textures/gui/sprites/hud/surface_strong.png");
    private static final Identifier WATERMARK_SURFACE = Identifier.of("visual-test", "textures/gui/sprites/hud/watermark.png");
    private static final Identifier RECESS = Identifier.of("visual-test", "textures/gui/sprites/hud/recess.png");
    private static final Identifier SLOT = Identifier.of("visual-test", "textures/gui/sprites/hud/slot.png");
    private static final float WATERMARK_TEXT_SIZE = 7.5F;
    private static final float HUD_TEXT_SIZE = 9.0F;
    private static final float TARGET_TEXT_SIZE = 7.5F;
    private static final int TEXT = 0xFFF2F4F2;
    private static final int MUTED = 0xFF9EA5A2;
    private static final int ACCENT = 0xFFC1CBC7;
    private static final int SEPARATOR = 0x426E7774;
    private static final int CRITICAL = 0xFFDCA19E;
    private static final int HARMFUL = 0xFFE26F6B;

    // Появление/исчезновение таргетхуда — как у HelperItemsHud: центр-скейл 0→1 за 200мс,
    // EASE_OUT_CUBIC, симметрично в обе стороны (InfinityAnimation мода).
    private static final ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation TARGET_APPEAR =
            new ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation(
                    ru.method.linkenvisuals.utils.animations.Easing.EASE_OUT_CUBIC);
    // Та же keybind-анимация для брони и алёрта прочности.
    private static final ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation ARMOR_APPEAR =
            new ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation(
                    ru.method.linkenvisuals.utils.animations.Easing.EASE_OUT_CUBIC);
    private static final ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation ALERT_APPEAR =
            new ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation(
                    ru.method.linkenvisuals.utils.animations.Easing.EASE_OUT_CUBIC);

    private static boolean hasAnyArmor(HudModel.Snapshot data) {
        for (ItemStack piece : data.armor()) {
            if (!piece.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private VisualHud() {
    }

    // ── Мосты для стиля «Карточка» штатного Link HUD ─────────────────────────────────────────────
    // Штатные элементы (Watermark/TargetHud/PotionHud/CooldownsHud/ArmorHud/LowArmorNotifications)
    // при HUD Style = Card рисуют СЕБЯ этими методами — готовыми элементами visual-test. Каждый мост
    // самодостаточен (свой beginFrame/flush шрифта round) и возвращает размер для хит-бокса элемента.

    private static HudModel.Snapshot cardSnapshot(MinecraftClient client) {
        return HudModel.capture(client, unkw.visualtest.client.Visual_testClient.state());
    }

    /** Вотермарка visual-test в точке (x,y). Возвращает ширину (высота всегда 17). */
    public static int cardWatermark(DrawContext context, MinecraftClient client, int x, int y) {
        RoundFontRenderer.beginFrame();
        HudModel.Snapshot data = cardSnapshot(client);
        int w = watermarkWidth(data);
        renderWatermark(context, data, x, y, w, 17);
        RoundFontRenderer.flush(context);
        return w;
    }

    /** Таргет-карточка visual-test (180x56, −20%). Возвращает false, если цели нет (не нарисовано). */
    public static boolean cardTarget(DrawContext context, MinecraftClient client, int x, int y) {
        HudModel.Snapshot data = cardSnapshot(client);
        float show = TARGET_APPEAR.animate(data.target() != null ? 1f : 0f, 200);
        if (data.target() == null || show <= 0.01f) {
            return false;
        }
        RoundFontRenderer.beginFrame();
        // −20% + центр-скейл появления (как айтемхуд); при полном показе левый-верхний угол = (x,y),
        // хит-бокс элемента = 144x45.
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x + 72f, y + 22.4f);
        matrices.scale(0.8f * show, 0.8f * show);
        matrices.translate(-(x + 90f), -(y + 28f));
        renderTarget(context, client, data.target(), x, y, 180, 56);
        RoundFontRenderer.flush(context);
        matrices.popMatrix();
        return true;
    }

    /** Эффекты visual-test (150 x 20+n*21, до 2 строк). Возвращает высоту или 0, если пусто. */
    public static int cardEffects(DrawContext context, MinecraftClient client, int x, int y) {
        HudModel.Snapshot data = cardSnapshot(client);
        // Показываем ВСЕ эффекты. Лимит в 2 строки схлопывал остальные в «Эффекты +4» — сам счётчик
        // в renderEffects остаётся, но при полном списке size() == count и «+N» не рисуется.
        int limit = data.effects().size();
        if (limit == 0) {
            return 0;
        }
        RoundFontRenderer.beginFrame();
        renderEffects(context, client, data, x, y, limit);
        RoundFontRenderer.flush(context);
        return 20 + limit * 21;
    }

    /** Задержки visual-test (150 x 20+n*21, до 3 строк). Возвращает высоту или 0, если пусто. */
    public static int cardCooldowns(DrawContext context, MinecraftClient client, int x, int y) {
        HudModel.Snapshot data = cardSnapshot(client);
        int limit = Math.min(data.cooldowns().size(), 3);
        if (limit == 0) {
            return 0;
        }
        RoundFontRenderer.beginFrame();
        renderCooldowns(context, client, data.cooldowns(), x, y, limit);
        RoundFontRenderer.flush(context);
        return 20 + limit * 21;
    }

    /** Броня visual-test (82x25). false — панель скрыта (брони нет; в чате показываем всегда). */
    public static boolean cardArmor(DrawContext context, MinecraftClient client, int x, int y) {
        HudModel.Snapshot data = cardSnapshot(client);
        // Виден и в редакторе HUD мода: иначе без надетой брони карточку нельзя было перетащить.
        boolean visible = hasAnyArmor(data) || data.preview()
                || ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        // Keybind-анимация: центр-скейл 0→1 по видимости, симметрично в обе стороны.
        float show = ARMOR_APPEAR.animate(visible ? 1f : 0f, 200);
        if (show <= 0.01f) {
            return false;
        }
        RoundFontRenderer.beginFrame();
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x + 41f, y + 12.5f);
        matrices.scale(show, show);
        matrices.translate(-(x + 41f), -(y + 12.5f));
        renderArmor(context, client, data, x, y);
        RoundFontRenderer.flush(context);
        matrices.popMatrix();
        return true;
    }

    /** Плашка «Низкая прочность» visual-test (−30%, 112x27). Возвращает false, если алерта нет. */
    public static boolean cardDurability(DrawContext context, MinecraftClient client, int x, int y) {
        HudModel.Snapshot data = cardSnapshot(client);
        float show = ALERT_APPEAR.animate(data.alert() != null ? 1f : 0f, 200);
        if (data.alert() == null || show <= 0.01f) {
            return false;
        }
        RoundFontRenderer.beginFrame();
        // −30% + keybind-анимация появления; при полном показе левый-верхний угол = (x,y).
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x + 56f, y + 13.3f);
        matrices.scale(0.7f * show, 0.7f * show);
        matrices.translate(-(x + 80f), -(y + 19f));
        renderDurability(context, client, data.alert(), x, y);
        RoundFontRenderer.flush(context);
        matrices.popMatrix();
        return true;
    }

    private static void renderWatermark(DrawContext context, HudModel.Snapshot data, int x, int y, int width, int height) {
        SmoothGuiRenderer.drawNineSlice(context, WATERMARK_SURFACE, x, y, width, height, 80, 80, 28, 7);
        String[] parts = watermarkParts(data);
        int cursor = x + 7;
        for (int index = 0; index < parts.length; index++) {
            int color = index == 0 ? ACCENT : index == 1 ? TEXT : MUTED;
            RoundFontRenderer.queue(parts[index], cursor, y + 4, WATERMARK_TEXT_SIZE, color);
            cursor += watermarkTextWidth(parts[index]);
            if (index + 1 < parts.length) {
                cursor += 3;
                context.fill(cursor, y + 4, cursor + 1, y + 12, SEPARATOR);
                cursor += 4;
            }
        }
    }

    private static int watermarkWidth(HudModel.Snapshot data) {
        String[] parts = watermarkParts(data);
        int width = 14 + (parts.length - 1) * 7;
        for (String part : parts) {
            width += watermarkTextWidth(part);
        }
        return width;
    }

    private static String[] watermarkParts(HudModel.Snapshot data) {
        // Координаты и BPS убраны по запросу — только бренд, ник, FPS и пинг.
        return new String[]{
                "linken.",
                fit(data.playerName(), 64, WATERMARK_TEXT_SIZE),
                data.fps() + " FPS",
                data.ping()
        };
    }

    private static int watermarkTextWidth(String value) {
        return (int) Math.ceil(RoundFontRenderer.width(value, WATERMARK_TEXT_SIZE));
    }

    private static void renderHint(DrawContext context, TextRenderer renderer, HudModel.Snapshot data, int x, int y) {
        String value = data.preview() ? "F8   Тестовый режим" : "F8   Только реальные данные";
        int width = textWidth(renderer, value) + 20;
        surface(context, x, y, width, 18, false);
        text(context, renderer, value, x + 10, y + 5, MUTED);
    }

    private static void renderArmor(DrawContext context, MinecraftClient client, HudModel.Snapshot data, int x, int y) {
        surface(context, x, y, 82, 25, false);
        int armorCount = data.armor().size();
        for (int index = 0; index < armorCount; index++) {
            renderArmorSlot(context, client, data.armor().get(index), x + 5 + index * 18, y + 4, 17);
        }
    }

    private static void renderEffects(DrawContext context, MinecraftClient client, HudModel.Snapshot data, int x, int y, int limit) {
        int count = Math.min(limit, data.effects().size());
        int height = 20 + count * 21;
        surface(context, x, y, 150, height, false);
        String title = data.effects().size() > count
                ? "Эффекты  +" + (data.effects().size() - count)
                : "Эффекты";
        label(context, client.textRenderer, title, x + 9, y + 6);

        for (int index = 0; index < count; index++) {
            HudModel.EffectRow effect = data.effects().get(index);
            int rowY = y + 19 + index * 21;
            inset(context, x + 5, rowY, 18, 18);
            Identifier effectTex = net.minecraft.client.gui.hud.InGameHud.getEffectTexture(effect.type());
            if (effectTex != null) {
                context.drawGuiTexture(
                        net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                        effectTex,
                        x + 8,
                        rowY + 3,
                        12,
                        12
                );
            }
            String name = fit(client.textRenderer, effect.name() + (effect.amplifier() > 1 ? " " + effect.amplifier() : ""), 72);
            text(context, client.textRenderer, name, x + 29, rowY + 5, effect.harmful() ? HARMFUL : TEXT);
            pillText(context, client.textRenderer, effect.duration(), x + 109, rowY + 1, 35, 16, effect.seconds() <= 10 ? CRITICAL : MUTED);
        }
    }

    private static void renderCooldowns(DrawContext context, MinecraftClient client, List<HudModel.CooldownRow> rows, int x, int y, int limit) {
        int count = Math.min(limit, rows.size());
        int height = 20 + count * 21;
        surface(context, x, y, 150, height, false);
        String title = rows.size() > count ? "Задержки  +" + (rows.size() - count) : "Задержки";
        label(context, client.textRenderer, title, x + 9, y + 6);
        for (int index = 0; index < count; index++) {
            HudModel.CooldownRow row = rows.get(index);
            int rowY = y + 19 + index * 21;
            inset(context, x + 5, rowY, 18, 18);
            context.drawItem(row.stack(), x + 6, rowY + 1);
            String name = fit(client.textRenderer, row.name(), 72);
            text(context, client.textRenderer, name, x + 29, rowY + 5, TEXT);
            pillText(context, client.textRenderer, row.value(), x + 109, rowY + 1, 35, 16, MUTED);
        }
    }

    private static void renderDurability(DrawContext context, MinecraftClient client, HudModel.DurabilityAlert alert, int x, int y) {
        surface(context, x, y, 160, 38, true);
        inset(context, x + 6, y + 8, 22, 22);
        context.drawItem(alert.stack(), x + 9, y + 11);
        strongText(context, client.textRenderer, "Низкая прочность", x + 35, y + 7, TEXT);
        strongText(context, client.textRenderer, alert.percent() + "%", x + 35, y + 21, durabilityColor(alert.percent()));
    }

    private static void renderTarget(DrawContext context, MinecraftClient client, HudModel.Target target, int x, int y, int width, int height) {
        drawAsset(context, TARGET_BODY, x, y, width, height, 720, 224);
        int roomSize = 50;
        int roomX = x + 3;
        int roomY = y + 3;
        drawAsset(context, TARGET_ROOM, roomX, roomY, roomSize, roomSize, 400, 400);
        HudEntityPreview.render(context, client, target.entity(), roomX, roomY, roomSize, roomSize);

        int infoX = x + 62;
        int infoRight = x + width - 8;
        int infoWidth = Math.max(1, infoRight - infoX);
        String distance = String.format(Locale.ROOT, "%.1f м", target.distance());

        String name = fitTargetStrong(client.textRenderer, target.name(), infoWidth);
        targetStrongText(context, client.textRenderer, name, infoX, y + 7, TEXT);

        int distanceWidth = targetTextWidth(client.textRenderer, distance);
        String health = String.format(Locale.ROOT, "%.1f / %.1f ХП", target.health(), target.maxHealth());
        int healthWidth = Math.max(12, infoWidth - distanceWidth - 7);
        targetStrongText(context, client.textRenderer, fitTargetStrong(client.textRenderer, health, healthWidth), infoX, y + 19, TEXT);
        targetText(context, client.textRenderer, distance, infoRight - distanceWidth, y + 19, MUTED);

        // Броня цели — ПОД строкой ХП, слева, над полосой здоровья.
        int armorCount = 0;
        for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{
                net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}) {
            ItemStack piece = target.entity().getEquippedStack(slot);
            if (!piece.isEmpty()) {
                context.drawItem(piece, infoX + 17 * armorCount++, y + 29);
            }
        }

        float healthRatio = target.maxHealth() <= 0.0F ? 0.0F : MathHelper.clamp(target.health() / target.maxHealth(), 0.0F, 1.0F);
        int healthWidthFilled = Math.round(infoWidth * healthRatio);
        if (healthWidthFilled > 0) {
            context.drawTexture(
                    net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                    TARGET_HEALTH,
                    infoX,
                    y + 48,
                    0.0F,
                    0.0F,
                    healthWidthFilled,
                    2,
                    healthWidthFilled * 4,
                    8,
                    440,
                    8
            );
        }
    }

    private static void drawAsset(DrawContext context, Identifier texture, int x, int y, int width, int height, int sourceWidth, int sourceHeight) {
        SmoothGuiRenderer.draw(context, texture, x, y, width, height, sourceWidth, sourceHeight);
    }

    private static void renderArmorSlot(DrawContext context, MinecraftClient client, ItemStack stack, int x, int y, int size) {
        SmoothGuiRenderer.draw(context, SLOT, x, y, size, size, 72, 72);
        if (!stack.isEmpty()) {
            int itemOffset = Math.max(0, (size - 15) / 2);
            context.drawItem(stack, x + itemOffset, y + itemOffset);
            context.drawStackOverlay(client.textRenderer, stack, x + itemOffset, y + itemOffset, "");
        }
    }

    private static void surface(DrawContext context, int x, int y, int width, int height, boolean strong) {
        SmoothGuiRenderer.drawNineSlice(context, strong ? SURFACE_STRONG : SURFACE, x, y, width, height, 80, 80, 28, 7);
    }

    private static void inset(DrawContext context, int x, int y, int width, int height) {
        SmoothGuiRenderer.drawNineSlice(context, RECESS, x, y, width, height, 64, 64, 24, 6);
    }

    private static void pillText(DrawContext context, TextRenderer renderer, String value, int x, int y, int width, int height, int color) {
        inset(context, x, y, width, height);
        centeredText(context, renderer, value, x, y, width, height, color);
    }

    private static void label(DrawContext context, TextRenderer renderer, String value, int x, int y) {
        strongText(context, renderer, value, x, y, MUTED);
    }

    private static void text(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        RoundFontRenderer.queue(value, x, y, HUD_TEXT_SIZE, color);
    }

    private static void strongText(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        RoundFontRenderer.queue(value, x, y, HUD_TEXT_SIZE, color);
    }

    private static void targetText(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        RoundFontRenderer.queue(value, x, y, TARGET_TEXT_SIZE, color);
    }

    private static void targetStrongText(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        RoundFontRenderer.queue(value, x, y, TARGET_TEXT_SIZE, color);
    }

    private static void centeredText(DrawContext context, TextRenderer renderer, String value, int x, int y, int width, int height, int color) {
        // Round вместо ceil/целочисленного деления: таймер в пилюле стоял на 1px выше имени в той же
        // строке и уезжал влево до 1px — рассинхрон бейслайнов и центров.
        int cx = x + Math.max(0, Math.round((width - RoundFontRenderer.width(value, HUD_TEXT_SIZE)) / 2f));
        int cy = y + Math.max(0, (height - 9 + 1) / 2);
        text(context, renderer, value, cx, cy, color);
    }

    private static int textWidth(TextRenderer renderer, String value) {
        return (int) Math.ceil(RoundFontRenderer.width(value, HUD_TEXT_SIZE));
    }

    private static int targetTextWidth(TextRenderer renderer, String value) {
        return (int) Math.ceil(RoundFontRenderer.width(value, TARGET_TEXT_SIZE));
    }

    private static int durabilityColor(int percent) {
        float ratio = Math.max(0, Math.min(100, percent)) / 100.0F;
        float phase = ratio * 2.0F;
        int red;
        int green;
        int blue;
        if (phase <= 1.0F) {
            red = blend(224, 222, phase);
            green = blend(92, 198, phase);
            blue = blend(92, 86, phase);
        } else {
            float value = phase - 1.0F;
            red = blend(222, 132, value);
            green = blend(198, 220, value);
            blue = blend(86, 105, value);
        }
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int blend(int start, int end, float ratio) {
        return Math.round(start + (end - start) * ratio);
    }

    private static String fit(TextRenderer renderer, String value, int maxWidth) {
        return fit(value, maxWidth, HUD_TEXT_SIZE);
    }

    private static String fitTargetStrong(TextRenderer renderer, String value, int maxWidth) {
        return fit(value, maxWidth, TARGET_TEXT_SIZE);
    }

    private static String fit(String value, int maxWidth, float size) {
        if (RoundFontRenderer.width(value, size) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        if (RoundFontRenderer.width(suffix, size) > maxWidth) {
            return "";
        }
        int end = value.length();
        while (end > 0 && RoundFontRenderer.width(value.substring(0, end) + suffix, size) > maxWidth) {
            end = value.offsetByCodePoints(end, -1);
        }
        return value.substring(0, end) + suffix;
    }
}
