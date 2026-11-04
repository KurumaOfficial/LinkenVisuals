package ru.method.linkenvisuals.hud.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.inv.InventoryUtils;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HelperItemsHud extends HudElement {

    private static final float TILE_SIZE = 18f;
    private static final float TILE_GAP = 6f;
    // Иконка рендерится 16px * ITEM_SCALE, чтобы не вылезать за скруглённые углы 18px-ячейки (~2px зазор).
    private static final float ITEM_SCALE = 0.875f;
    private static final float KEY_FONT_SIZE = 6.5f;
    private static final float KEY_GAP = 6.5f;
    private static final float KEY_SIDE_PADDING = 1.2f;

    private final InfinityAnimation widthAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final InfinityAnimation heightAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final InfinityAnimation fadeAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    public HelperItemsHud() {
        super("HelperItemsHud");
    }

    private record Mapping(Class<? extends Module> moduleClass, String settingName, ItemStack stack) {}

    private record Tile(ItemStack stack, String keyLabel) {}

    // Стеки используются только как иконки (drawItem) — иммутабельны по использованию,
    // поэтому маппинги статичны: раньше 15 new ItemStack создавались каждый кадр.
    private static final Set<Class<? extends Module>> HELPER_CLASSES = Set.of(
            ru.method.linkenvisuals.modules.impl.misc.FtHelper.class,
            ru.method.linkenvisuals.modules.impl.misc.HwHelper.class,
            ru.method.linkenvisuals.modules.impl.misc.RwHelper.class
    );

    private static final List<Mapping> MAPPINGS = List.of(
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.FtHelper.class, "settings.funtimehelper.bindsnowball", new ItemStack(Items.SNOWBALL)),
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.FtHelper.class, "settings.funtimehelper.binddesor", new ItemStack(Items.ENDER_EYE)),
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.FtHelper.class, "settings.funtimehelper.bindbozhka", new ItemStack(Items.PHANTOM_MEMBRANE)),
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.FtHelper.class, "settings.funtimehelper.bindyavka", new ItemStack(Items.SUGAR)),
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.FtHelper.class, "settings.funtimehelper.bindtrapka", new ItemStack(Items.NETHERITE_SCRAP)),
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.FtHelper.class, "settings.funtimehelper.bindplast", new ItemStack(Items.DRIED_KELP)),

                new Mapping(ru.method.linkenvisuals.modules.impl.misc.HwHelper.class, "settings.hwhelper.bindstan", new ItemStack(Items.NETHER_STAR)),
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.HwHelper.class, "settings.hwhelper.bindtrapka", new ItemStack(Items.POPPED_CHORUS_FRUIT)),
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.HwHelper.class, "settings.hwhelper.bindboomtrapka", new ItemStack(Items.PRISMARINE_SHARD)),
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.HwHelper.class, "settings.hwhelper.bindboomthing", new ItemStack(Items.FIRE_CHARGE)),
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.HwHelper.class, "settings.hwhelper.bindsnowball", new ItemStack(Items.SNOWBALL)),

                new Mapping(ru.method.linkenvisuals.modules.impl.misc.RwHelper.class, "settings.rwhelper.bindantifly", new ItemStack(Items.FIREWORK_STAR)),
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.RwHelper.class, "settings.rwhelper.bindexpscroll", new ItemStack(Items.FLOWER_BANNER_PATTERN)),
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.RwHelper.class, "settings.rwhelper.bindtrap", new ItemStack(Items.HEART_OF_THE_SEA)),
                new Mapping(ru.method.linkenvisuals.modules.impl.misc.RwHelper.class, "settings.rwhelper.binddarkpulse", new ItemStack(Items.FIREWORK_STAR))
    );

    private List<Tile> cachedTiles;
    private long tilesExpireAtMs;
    private boolean cachedPreview;

    private List<Tile> collectTiles(boolean preview) {
        List<Tile> tiles = new ArrayList<>();
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return tiles;

        Set<Class<? extends Module>> enabledHelpers = new HashSet<>();
        for (Class<? extends Module> helperClass : HELPER_CLASSES) {
            Module helper = instance.getModuleManager().getModule(helperClass);
            if (helper != null && helper.isToggled()) {
                enabledHelpers.add(helperClass);
            }
        }

        for (Mapping mapping : MAPPINGS) {
            if (preview && !enabledHelpers.isEmpty() && !enabledHelpers.contains(mapping.moduleClass())) continue;

            Module module = instance.getModuleManager().getModule(mapping.moduleClass());
            if (module == null) continue;
            if (!module.isToggled() && !preview) continue;
            if (!module.isToggled() && preview) continue;

            BindSetting bindSetting = findBindSetting(module, mapping.settingName());
            if (bindSetting == null) continue;

            Bind bind = bindSetting.getValue();
            boolean isEmpty = bind == null || bind.isEmpty();
            if (!preview && isEmpty) continue;
            if (!preview && InventoryUtils.findHotbar(mapping.stack().getItem()) == -1) continue;

            String label;
            if (!isEmpty) {
                label = normalizeBindLabel(bind.toString());
            } else {
                label = "";
            }

            tiles.add(new Tile(mapping.stack(), label));
        }

        if (preview && tiles.isEmpty() && enabledHelpers.isEmpty()) {
            tiles.add(new Tile(new ItemStack(Items.SNOWBALL), "R"));
            tiles.add(new Tile(new ItemStack(Items.CLOCK), "V"));
            tiles.add(new Tile(new ItemStack(Items.NETHER_STAR), "Z"));
            tiles.add(new Tile(new ItemStack(Items.GOLD_NUGGET), "X"));
        }

        return tiles;
    }

    private BindSetting findBindSetting(Module module, String settingName) {
        if (module == null || settingName == null) return null;
        for (Setting<?> setting : module.getSettings()) {
            if (!(setting instanceof BindSetting)) continue;
            if (settingName.equals(setting.getName())) return (BindSetting) setting;
        }
        return null;
    }

    private boolean shouldShow(boolean preview, List<Tile> tiles) {
        if (preview) return true;
        return !tiles.isEmpty();
    }

    private static String normalizeBindLabel(String raw) {
        if (raw == null) return "";
        String normalized = raw.replace("_", " ").trim();

        String upper = normalized.toUpperCase();
        if (upper.contains("LEFT BRACKET")) return "[";
        if (upper.contains("RIGHT BRACKET")) return "]";
        if (upper.contains("SEMICOLON")) return ";";
        if (upper.contains("APOSTROPHE")) return "'";
        if (upper.contains("COMMA")) return ",";
        if (upper.contains("PERIOD")) return ".";
        if (upper.contains("SLASH")) return "/";
        if (upper.contains("BACKSLASH")) return "\\";
        if (upper.contains("GRAVE")) return "`";
        if (upper.contains("MINUS")) return "-";
        if (upper.contains("EQUAL")) return "=";

        return normalized;
    }

    private static String fitLabel(String label, float maxWidth) {
        if (label == null) return "";
        String trimmed = label.trim();
        if (trimmed.isEmpty()) return "";

        if (Fonts.MEDIUM.getWidth(trimmed, KEY_FONT_SIZE) <= maxWidth) return trimmed;

        String upper = trimmed.toUpperCase();
        if (upper.contains("MOUSE") && upper.matches(".*\\d+.*")) {
            String digits = upper.replaceAll("\\D+", "");
            String candidate = digits.isEmpty() ? "M" : "M" + digits;
            if (Fonts.MEDIUM.getWidth(candidate, KEY_FONT_SIZE) <= maxWidth) return candidate;
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length > 1) {
            StringBuilder sb = new StringBuilder(parts.length);
            for (String p : parts) {
                if (p.isEmpty()) continue;
                sb.append(Character.toUpperCase(p.charAt(0)));
            }
            String candidate = sb.toString();
            if (!candidate.isEmpty() && Fonts.MEDIUM.getWidth(candidate, KEY_FONT_SIZE) <= maxWidth) return candidate;
            trimmed = candidate.isEmpty() ? trimmed : candidate;
        }

        // Общий хвост-обрезки заменён на базовый fitText (промер тем же шрифтом + многоточие).
        return fitText(Fonts.MEDIUM, trimmed, KEY_FONT_SIZE, maxWidth);
    }

    private void drawSafeKey(EventRender2D e, String text, float centerX, float y, Color color) {
        if (text == null || text.isBlank()) return;
        float maxWidth = TILE_SIZE - (KEY_SIDE_PADDING * 2f);
        String fitted = fitLabel(text, maxWidth);
        if (fitted.isEmpty()) return;
        float width = Fonts.MEDIUM.getWidth(fitted, KEY_FONT_SIZE);
        drawSafeText(e.getContext(), Fonts.MEDIUM, KEY_FONT_SIZE, fitted, centerX - width / 2f, y, color);
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) return;

        boolean preview = ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        // Сбор тайлов (обход модулей/биндов/хотбара) — раз в ~100 мс, а не каждый кадр.
        long now = System.currentTimeMillis();
        if (cachedTiles == null || preview != cachedPreview || now >= tilesExpireAtMs) {
            cachedTiles = collectTiles(preview);
            cachedPreview = preview;
            tilesExpireAtMs = now + 100L;
        }
        List<Tile> tiles = cachedTiles;

        float fadeValue = fadeAnimation.animate(shouldShow(preview, tiles) ? 1.0f : 0.0f, 200);
        if (fadeValue <= 0.0f) return;

        float posX = getX();
        float posY = getY();
        float padding = HUD_PADDING;

        float contentWidth = tiles.isEmpty()
                ? 0f
                : tiles.size() * TILE_SIZE + (tiles.size() - 1) * TILE_GAP;
        float contentHeight = TILE_SIZE + KEY_GAP;

        float targetWidth = padding * 2f + contentWidth;
        float targetHeight = padding * 2f + contentHeight;

        float renderWidth = preview ? targetWidth : widthAnimation.animate(targetWidth, 200);
        float renderHeight = preview ? targetHeight : heightAnimation.animate(targetHeight, 200);

        int globalAlpha = Math.max(0, Math.min(255, Math.round(255 * fadeValue)));

        float uiScale = beginUiScale(e.getContext().getMatrices(), posX, posY);
        e.getContext().getMatrices().pushMatrix();
        float[] dragOffset = getDragOffset();
        float scaleValue = toggledAnimation.getValue() * fadeValue * getDragScale();
        float centerX = posX + renderWidth / 2f + dragOffset[0];
        float centerY = posY + renderHeight / 2f + dragOffset[1];
        e.getContext().getMatrices().translate(centerX, centerY);
        e.getContext().getMatrices().scale(scaleValue, scaleValue);
        e.getContext().getMatrices().translate(-centerX, -centerY);

        if (LinkHudStyle.active()) {
            LinkHudStyle.panel(e.getContext().getMatrices(), posX, posY, renderWidth, renderHeight,
                    LinkHudStyle.radius(renderWidth, renderHeight), globalAlpha / 255f);
        } else {
            Color bgColor = ColorUtils.getGlassColor(globalAlpha);
            Render2D.drawLiquidRectHud(
                    e.getContext().getMatrices(),
                    posX,
                    posY,
                    renderWidth,
                    renderHeight,
                    new BorderRadius(10f),
                    new ColorRGBA(bgColor),
                    2.5f,
                    22.0f,
                    11f,
                    175.0f,
                    true,
                    0f,
                    0.19f
            );
        }

        Color keyColor = ColorUtils.getTextColor(Math.min(230, globalAlpha));

        float startX = posX + (renderWidth - contentWidth) / 2f;
        float startY = posY + (renderHeight - contentHeight) / 2f;

        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            float tileX = startX + i * (TILE_SIZE + TILE_GAP);
            float tileY = startY;

            // Ячейка под предмет — тот же стиль, что слоты брони в ArmorHud (аккуратный inset-слот).
            if (LinkHudStyle.active()) {
                LinkHudStyle.cell(e.getContext().getMatrices(), tileX, tileY, TILE_SIZE, TILE_SIZE, 5f, 1f);
            } else {
                Render2D.drawRoundedRect(e.getContext().getMatrices(), tileX, tileY, TILE_SIZE, TILE_SIZE, 5f, new Color(255, 255, 255, 26));
            }

            // Предмет рендерим чуть меньше ячейки (масштаб вокруг центра) и строго по центру —
            // иначе 16px-иконка углами вылезала за скруглённую рамку ячейки.
            float cellCenterX = tileX + TILE_SIZE / 2f;
            float cellCenterY = tileY + TILE_SIZE / 2f;
            int itemX = Math.round(cellCenterX - 8f);
            int itemY = Math.round(cellCenterY - 8f);
            var matrices = e.getContext().getMatrices();
            matrices.pushMatrix();
            matrices.translate(cellCenterX, cellCenterY);
            matrices.scale(ITEM_SCALE, ITEM_SCALE);
            matrices.translate(-cellCenterX, -cellCenterY);
            try {
                e.getContext().drawItem(mc.player, tile.stack(), itemX, itemY, 0);
            } catch (Exception ignored) {}
            matrices.popMatrix();

            drawSafeKey(e, tile.keyLabel(), tileX + TILE_SIZE / 2f, tileY + TILE_SIZE + 1.25f, keyColor);
        }

        e.getContext().getMatrices().popMatrix();
        endUiScale(e.getContext().getMatrices());

        setUiScaledBounds(posX, posY, renderWidth, renderHeight, uiScale);
        super.onRender2D(e);
    }
}
