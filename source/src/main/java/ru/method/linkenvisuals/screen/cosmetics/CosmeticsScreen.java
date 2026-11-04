package ru.method.linkenvisuals.screen.cosmetics;

import net.minecraft.client.input.CharInput;

import net.minecraft.client.input.KeyInput;

import net.minecraft.client.gui.Click;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.cosmetics.catalog.CosmeticCatalog;
import ru.method.linkenvisuals.cosmetics.model.CosmeticDefinition;
import ru.method.linkenvisuals.cosmetics.model.CosmeticProfile;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;
import ru.method.linkenvisuals.cosmetics.laby.LabyCapeManager;
import ru.method.linkenvisuals.cosmetics.linken.LinkenCape;
import ru.method.linkenvisuals.cosmetics.render.CosmeticEntityPreviewRenderer;
import ru.method.linkenvisuals.cosmetics.service.CosmeticsService;
import ru.method.linkenvisuals.modules.impl.player.CosmeticsModule;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.CapePreviewRenderer;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;
import java.util.List;

public class CosmeticsScreen extends Screen implements Wrapper {
    // Вкладки меню косметики: гардероб (плащ/шапка/крылья). Слот EFFECT убран из меню — косметические
    // эффекты (нимб/сферы/питомец) настраиваются в настройках модуля «Косметика» и показываются в превью.
    private static final CosmeticSlot[] TABS = {
            CosmeticSlot.CHARACTER,
            CosmeticSlot.CAPE,
            CosmeticSlot.HAT,
            CosmeticSlot.HEAD,
            CosmeticSlot.BODY,
            CosmeticSlot.SHIELD,
            CosmeticSlot.WINGS
    };
    private static final int GRID_COLUMNS = 2;

    private final CosmeticsModule module;
    private final Screen parent;
    private final Animation openAnimation = new Animation(320, 1f, true, Easing.EASE_OUT_CUBIC);
    private final CosmeticPlayerPreview playerPreview = new CosmeticPlayerPreview();
    private final CosmeticsWingsColorPicker wingsColorPicker = new CosmeticsWingsColorPicker();
    private CosmeticSlot activeTab = CosmeticSlot.CAPE;
    private float scroll;
    private String hoveredCosmeticId;
    private boolean closing;
    // Область 3D-превью (рисуется отдельно, с масштабом анимации открытия — для синхронности).
    private float pvX, pvY, pvW, pvH;
    private boolean pvHasEntity;
    // Драг-вращение персонажа в превью. Обф-safe: старт в mouseClicked, шаг в render (mouseDragged в
    // обф-сборке не долетает), стоп в mouseReleased + подстраховка опросом GLFW-кнопки.
    private boolean previewDragging;
    private double previewLastMx, previewLastMy;
    // Ввод плаща с laby.net (вкладка «Плащи»).
    private String labyInput = "";
    private boolean labyFocused;
    private long labyMsgUntil;
    private String labyMsg = "";
    private final float[] rLabyField = new float[4], rLabyApply = new float[4];

    public CosmeticsScreen(CosmeticsModule module, Screen parent) {
        super(Text.translatable("cosmetics.title"));
        this.module = module;
        this.parent = parent;
    }

    @Override
    protected void init() {
        scroll = 0f;
        closing = false;
        playerPreview.reset();
        resetPreviewRotation(activeTab);
        // Проигрываем анимацию появления заново при каждом открытии (иначе она «съедалась» из-за
        // задержки загрузки между конструктором и первым кадром).
        openAnimation.reset();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ru.method.linkenvisuals.utils.render.Render2D.prepareHudFrame(context);
        Layout layout = getLayout();
        // Анимация: при закрытии плавно гаснем (update(false)), и лишь когда погасли — реально закрываем.
        openAnimation.update(!closing);
        float open = MathHelper.clamp(openAnimation.getValue(), 0f, 1f);
        if (closing && open <= 0.02f) {
            finishClose();
            return;
        }
        int openAlpha = Math.round(255f * open);
        int contentAlpha = Math.round(255f * open);

        // Затемнение фона — на всю область, без масштаба.
        Render2D.drawRoundedRect(context.getMatrices(), 0, 0, layout.screenW, layout.screenH, 0f, new Color(0, 0, 0, Math.round(58f * open)));

        // Анимация открытия/закрытия: масштаб из центра панели (0.93→1) + затухание, как у окон Link.
        float scale = 0.93f + 0.07f * open;
        float pcx = layout.x + layout.w / 2f;
        float pcy = layout.y + layout.h / 2f;
        pvHasEntity = false;
        updatePreviewRotation(mouseX, mouseY);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(pcx, pcy);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-pcx, -pcy);

        drawGlassPanel(context, layout, openAlpha);
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(17f), I18n.translate("cosmetics.title"), layout.x + 20f, layout.y + 14f, whiteText(contentAlpha));
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(7.6f), I18n.translate("cosmetics.subtitle"), layout.x + 20f, layout.y + 35f, whiteText(Math.round(165f * contentAlpha)));

        drawTabs(context, layout, mouseX, mouseY, contentAlpha);
        updateHoveredItem(layout, mouseX, mouseY);
        drawPreviewPanel(context, layout, mouseX, mouseY, contentAlpha);
        drawGrid(context, layout, mouseX, mouseY, contentAlpha);
        if (activeTab == CosmeticSlot.CAPE) {
            drawLabyCapeUI(context, layout, mouseX, mouseY, contentAlpha);
        }
        context.getMatrices().popMatrix();

        // 3D-превью игрока рисуем ОТДЕЛЬНО, с тем же масштабом из центра панели: ножницы entity
        // не учитывают матрицу-масштаб, поэтому внутри push() оно бы рассинхронилось с анимацией.
        renderPreviewEntity(context, layout, scale, open);

        if (usesColorPicker(activeTab) && wingsColorPicker.consumeDirty()) {
            LinkenVisuals.getInstance().getCosmeticsService().applyLocalTint(activeTab, wingsColorPicker.colorArgb());
        }
    }

    /** Рендер 3D-модели игрока в превью с тем же центр-масштабом, что и панель (для синхронной анимации). */
    private void renderPreviewEntity(DrawContext context, Layout layout, float scale, float open) {
        if (!pvHasEntity || open <= 0.02f) {
            return;
        }
        float pcx = layout.x + layout.w / 2f;
        float pcy = layout.y + layout.h / 2f;
        int ex = Math.round(pcx + (pvX - pcx) * scale);
        int ey = Math.round(pcy + (pvY - pcy) * scale);
        int ew = Math.round(pvW * scale);
        int eh = Math.round(pvH * scale);
        context.createNewRootLayer();
        playerPreview.renderMain(context, ex, ey, ew, eh, 1f);
    }

    /** Шаг драг-вращения превью (вызывается из render — mouseDragged в обф-сборке не долетает). */
    private void updatePreviewRotation(double mouseX, double mouseY) {
        if (!previewDragging) {
            return;
        }
        // Подстраховка: если ЛКМ уже отпущена, а mouseReleased не пришёл (обф) — прекращаем драг.
        if (mc.getWindow() == null
                || GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            previewDragging = false;
            return;
        }
        float dx = (float) (mouseX - previewLastMx);
        float dy = (float) (mouseY - previewLastMy);
        previewLastMx = mouseX;
        previewLastMy = mouseY;
        CosmeticEntityPreviewRenderer.yawDeg += dx * 0.6f;
        CosmeticEntityPreviewRenderer.pitchDeg =
                Math.max(-35f, Math.min(35f, CosmeticEntityPreviewRenderer.pitchDeg + dy * 0.5f));
    }

    /** Сброс ракурса превью под слот: у крыльев/плаща удобнее видеть спину (yaw=180), у остальных — лицом. */
    private void resetPreviewRotation(CosmeticSlot slot) {
        previewDragging = false;
        CosmeticEntityPreviewRenderer.yawDeg = 0f;
        CosmeticEntityPreviewRenderer.pitchDeg = 0f;
    }

    /** Test-only hook used by the visual capture driver to inspect every wardrobe tab. */
    public void linkenvisuals$setPreviewTab(CosmeticSlot slot) {
        if (slot == null) return;
        activeTab = slot;
        scroll = 0f;
        resetPreviewRotation(slot);
    }

    /** Слоты с настройкой цвета (палитра): крылья, головные уборы и эффекты красятся тинтом профиля. */
    private static boolean usesColorPicker(CosmeticSlot slot) {
        return slot == CosmeticSlot.WINGS || slot == CosmeticSlot.EFFECT || slot == CosmeticSlot.HAT;
    }

    /** i18n-ключ подписи цвета по слоту (крылья/шапка/эффект). */
    private static String colorLabelKey(CosmeticSlot slot) {
        return switch (slot) {
            case HAT -> "cosmetics.hat.color";
            case EFFECT -> "cosmetics.effect.color";
            default -> "cosmetics.wings.color";
        };
    }

    private PreviewBounds previewBounds(Layout layout) {
        int colorPickerHeight = usesColorPicker(activeTab) ? Math.round(wingsColorPicker.expandedHeight()) + 6 : 0;
        return new PreviewBounds(
                layout.previewX + 8f,
                layout.contentY + 28f,
                layout.previewW - 16f,
                layout.contentH - 52f - colorPickerHeight
        );
    }

    private void drawGlassPanel(DrawContext context, Layout layout, int openAlpha) {
        Render2D.drawBlurredRect(context.getMatrices(), layout.x, layout.y, layout.w, layout.h, 18f, 22f, new Color(15, 18, 24, Math.round(150f * (openAlpha / 255f))));
        Color webAccent = ColorUtils.getPrimaryColor();
        Render2D.drawPanelBackground(context.getMatrices(), layout.x, layout.y, layout.w, layout.h, 18f,
                new Color(webAccent.getRed(), webAccent.getGreen(), webAccent.getBlue(), openAlpha));
        Render2D.drawRoundedRect(context.getMatrices(), layout.x, layout.y, layout.w, layout.h, 18f,
                new Color(12, 14, 20, Math.round(166f * (openAlpha / 255f))));
        Render2D.drawBorder(context.getMatrices(), layout.x, layout.y, layout.w, layout.h, 18f, 0.75f, new ColorRGBA(ColorUtils.alpha(Color.WHITE, Math.round(38f * (openAlpha / 255f)))));
    }

    private void drawTabs(DrawContext context, Layout layout, int mouseX, int mouseY, int alpha) {
        int x = layout.sidebarX;
        int y = layout.contentY;
        int w = layout.sidebarW;
        int h = layout.contentH;

        Render2D.drawRoundedRect(context.getMatrices(), x, y, w, h, 12f, new Color(255, 255, 255, 18));
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(8.7f), I18n.translate("cosmetics.categories"), x + 10f, y + 9f, whiteText(alpha));

        int itemY = y + 30;
        for (CosmeticSlot tab : TABS) {
            boolean active = tab == activeTab;
            boolean hovered = MathUtils.isHovered(x + 6, itemY, w - 12, 28, mouseX, mouseY);
            Color bg = active
                    ? ColorUtils.alpha(ColorUtils.getPrimaryColor(), 96)
                    : hovered ? new Color(255, 255, 255, 32) : new Color(255, 255, 255, 12);
            Render2D.drawRoundedRect(context.getMatrices(), x + 6, itemY, w - 12, 28, 8f, bg);
            Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(7.6f), I18n.translate(slotTitleKey(tab)), x + 12f, itemY + 8f, whiteText(alpha));
            itemY += 32;
        }
    }

    private void drawPreviewPanel(DrawContext context, Layout layout, int mouseX, int mouseY, int alpha) {
        int x = layout.previewX;
        int y = layout.contentY;
        int w = layout.previewW;
        int h = layout.contentH;

        Render2D.drawRoundedRect(context.getMatrices(), x, y, w, h, 12f, new Color(255, 255, 255, 16));
        Render2D.drawBorder(context.getMatrices(), x, y, w, h, 12f, 0.55f, new ColorRGBA(ColorUtils.alpha(ColorUtils.getPrimaryColor(), Math.round(55f * (alpha / 255f)))));

        CosmeticsService service = LinkenVisuals.getInstance().getCosmeticsService();
        CosmeticProfile profile = service.getLocalProfile();
        String previewCosmeticId = hoveredCosmeticId != null ? hoveredCosmeticId : profile.get(activeTab);
        CosmeticDefinition selected = CosmeticCatalog.resolve(previewCosmeticId, activeTab);
        String selectedLabel;
        if (previewCosmeticId != null && previewCosmeticId.startsWith("laby:")) {
            int ci = customCapes().indexOf(previewCosmeticId.substring(5));
            selectedLabel = ci >= 0 ? "Custom Cape #" + (ci + 1) : "Custom Cape";
        } else {
            selectedLabel = I18n.translate(selected.nameKey());
        }

        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(8.8f), I18n.translate("cosmetics.preview"), x + 10f, y + 10f, whiteText(alpha));

        PreviewBounds bounds = previewBounds(layout);
        int previewInnerX = Math.round(bounds.x);
        int previewInnerY = Math.round(bounds.y);
        int previewInnerW = Math.round(bounds.w);
        int previewInnerH = Math.max(48, Math.round(bounds.h));
        float pickerY = previewInnerY + previewInnerH + 4f;

        if (usesColorPicker(activeTab)) {
            wingsColorPicker.updateDrag(mouseX, mouseY, previewInnerX, pickerY, previewInnerW);
        }

        Render2D.drawRoundedRect(context.getMatrices(), previewInnerX, previewInnerY, previewInnerW, previewInnerH, 10f, new Color(8, 10, 16, Math.round(140f * (alpha / 255f))));

        playerPreview.setSlot(activeTab);
        CosmeticProfile previewProfile = profile.with(activeTab, previewCosmeticId);
        if (usesColorPicker(activeTab)) {
            int tint = wingsColorPicker.isAdjusting()
                    ? wingsColorPicker.colorArgb()
                    : profile.getTint(activeTab);
            if (!wingsColorPicker.isAdjusting()) {
                wingsColorPicker.setColor(tint);
            }
            previewProfile = previewProfile.withTint(activeTab, tint);
        }
        playerPreview.setPreviewProfile(previewProfile);

        // Саму 3D-модель рисуем отдельно (в render → renderPreviewEntity) с масштабом анимации.
        // Здесь только запоминаем область превью.
        pvX = previewInnerX;
        pvY = previewInnerY;
        pvW = previewInnerW;
        pvH = previewInnerH;
        pvHasEntity = true;

        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(6.2f), I18n.translate("cosmetics.preview.hint"), previewInnerX + 6f, previewInnerY + previewInnerH - 11f, whiteText(Math.round(120f * (alpha / 255f))));

        if (usesColorPicker(activeTab)) {
            // Колорпикер — отдельным окном в стиле окна настроек Link: стекло (тёмный градиент)
            // + акцентный аутлайн. Непрозрачная подложка не даёт анимированному шейдеру фона просвечивать.
            float pickerH = wingsColorPicker.expandedHeight();
            float pad = alpha / 255f;
            float px = previewInnerX;
            float py = pickerY - 6f;
            float pw = previewInnerW;
            float ph = pickerH + 12f;
            // ПОЛНОСТЬЮ непрозрачная подложка — анимированный шейдер фона не просвечивает.
            Render2D.drawRoundedRect(context.getMatrices(), px, py, pw, ph, 9f,
                    new Color(22, 24, 31, Math.round(255f * pad)));
            // Аутлайн как у окон Link — акцент темы, мягко гаснущий вниз.
            Color accent = ColorUtils.getPrimaryColor();
            Render2D.drawBorder(context.getMatrices(), px, py, pw, ph, 9f, 1f,
                    new ColorRGBA(ColorUtils.alpha(accent, Math.round(160f * pad))));
            wingsColorPicker.render(context, previewInnerX, pickerY, previewInnerW, mouseX, mouseY, alpha, colorLabelKey(activeTab));
            if (wingsColorPicker.consumeDirty()) {
                service.applyLocalTint(activeTab, wingsColorPicker.colorArgb());
            }
        }

        float labelW = Fonts.MEDIUM.getWidth(selectedLabel, 6.8f);
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(6.8f), selectedLabel, x + (w - labelW) / 2f, y + h - 14f, whiteText(Math.round(190f * (alpha / 255f))));
    }

    /** Высота видимой области грида. На вкладке «Плащи» снизу закреплено поле ввода laby.net (~90px),
     *  поэтому грид укорачиваем — иначе последний ряд карточек налазит на строку ввода (и не долистывается). */
    private int gridViewportH(Layout layout) {
        int reserve = activeTab == CosmeticSlot.CAPE ? 128 : 42;
        return Math.max(60, layout.contentH - reserve);
    }

    private void drawGrid(DrawContext context, Layout layout, int mouseX, int mouseY, int alpha) {
        int x = layout.gridX;
        int y = layout.contentY;
        int w = layout.gridW;
        int h = layout.contentH;

        Render2D.drawRoundedRect(context.getMatrices(), x, y, w, h, 12f, new Color(255, 255, 255, 16));
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(9.4f), I18n.translate(slotTitleKey(activeTab)), x + 10f, y + 10f, whiteText(alpha));

        CosmeticProfile profile = LinkenVisuals.getInstance().getCosmeticsService().getLocalProfile();
        String selectedId = profile.get(activeTab);
        List<CosmeticDefinition> items = CosmeticCatalog.forSlot(activeTab);
        List<String> custom = customCapes(); // применённые laby-плащи (только вкладка «Плащи»)
        int total = items.size() + custom.size();

        int cellW = Math.max(88, (w - 28) / GRID_COLUMNS);
        int cellH = 86;
        int top = y + 34;
        int viewportH = gridViewportH(layout);
        int rows = (total + GRID_COLUMNS - 1) / GRID_COLUMNS;
        int maxScroll = Math.max(0, rows * (cellH + 8) - viewportH);
        scroll = MathHelper.clamp(scroll, 0, maxScroll);

        Render2D.startScissor(context, x, top, w, viewportH);
        int startY = top - Math.round(scroll);
        for (int i = 0; i < total; i++) {
            int col = i % GRID_COLUMNS;
            int row = i / GRID_COLUMNS;
            float cellX = x + 10f + col * (cellW + 8f);
            float cellY = startY + row * (cellH + 8f);

            boolean isCustom = i >= items.size();
            String cellId = isCustom ? "laby:" + custom.get(i - items.size()) : items.get(i).id();
            boolean selected = cellId.equals(selectedId);
            boolean hovered = MathUtils.isHovered(cellX, cellY, cellW, cellH, mouseX, mouseY);
            Color bg = selected
                    ? ColorUtils.alpha(ColorUtils.getPrimaryColor(), 110)
                    : hovered ? new Color(255, 255, 255, 34) : new Color(255, 255, 255, 14);
            Render2D.drawRoundedRect(context.getMatrices(), cellX, cellY, cellW, cellH, 10f, bg);
            if (selected) {
                Render2D.drawBorder(context.getMatrices(), cellX, cellY, cellW, cellH, 10f, 0.65f, new ColorRGBA(ColorUtils.alpha(ColorUtils.getPrimaryColor(), 180)));
            }

            String label;
            if (isCustom) {
                String customId = custom.get(i - items.size());
                drawCustomCapeIcon(context, customId, cellX, cellY, cellW, alpha);
                label = "Custom Cape #" + (i - items.size() + 1);
                // Кнопка удаления кастома — красный трэш в правом-верхнем углу ячейки.
                float delS = 15f, delX = cellX + cellW - delS - 3f, delY = cellY + 3f;
                boolean delHov = MathUtils.isHovered(delX, delY, delS, delS, mouseX, mouseY);
                Render2D.drawRoundedRect(context.getMatrices(), delX, delY, delS, delS, delS / 2f,
                        new Color(delHov ? 235 : 60, delHov ? 70 : 66, delHov ? 76 : 72, Math.round((delHov ? 255 : 170) * (alpha / 255f))));
                float gW = Fonts.ICONSNEW.getWidth("W", 7.5f);
                Render2D.drawFont(context.getMatrices(), Fonts.ICONSNEW.getFont(7.5f), "W",
                        delX + (delS - gW) / 2f, delY + (delS - Fonts.ICONSNEW.getHeight(7.5f)) / 2f,
                        new Color(255, 235, 236, alpha));
            } else {
                CosmeticDefinition item = items.get(i);
                if (LinkenCape.ID.equals(item.id())) {
                    // Статичный снимок готового кадра плаща (box-уменьшение): выглядит как плащ на игроке,
                    // но не «обновляется» и не искрит. Волну не добавляем (waveAmount=0).
                    // Снимок — уже готовая грань → сэмплим целиком (fullUv); до снимка — живой лист с UV грани.
                    drawCapeCard(context, LinkenCape.iconTexture(), LabyCapeManager.State.READY,
                            cellX, cellY, cellW, alpha, 0f, LinkenCape.iconReady());
                } else if (!item.isNone()) {
                    int iconSize = 46;
                    int iconX = Math.round(cellX + (cellW - iconSize) * 0.5f);
                    int iconY = Math.round(cellY + 8f);
                    Render2D.drawRoundedRect(context.getMatrices(), iconX - 2f, iconY - 2f, iconSize + 4f, iconSize + 4f, 8f, new Color(8, 10, 16, Math.round(120f * (alpha / 255f))));
                    playerPreview.renderIcon(context, iconX, iconY, iconSize, item, activeTab, alpha / 255f);
                }
                label = I18n.translate(item.nameKey());
            }
            float labelW = Fonts.MEDIUM.getWidth(label, 6.8f);
            Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(6.8f), label, cellX + (cellW - labelW) / 2f, cellY + cellH - 16f, whiteText(alpha));
        }
        Render2D.stopScissor(context);
    }

    /** Применённые laby-плащи для вкладки «Плащи» (иначе пусто). */
    private List<String> customCapes() {
        return activeTab == CosmeticSlot.CAPE
                ? LabyCapeManager.knownIds()
                : java.util.Collections.emptyList();
    }

    /** Карточка кастом-плаща в ячейке: живая ткань + состояния «грузится» / «не смогли». */
    private void drawCustomCapeIcon(DrawContext context, String id, float cellX, float cellY, float cellW, int alpha) {
        LabyCapeManager.State state = LabyCapeManager.state(id);
        drawCapeCard(context, LabyCapeManager.textureFor(id), state, cellX, cellY, cellW, alpha, 1f, false); // статичная текстура — оживляем волной
    }

    /**
     * Карточка плаща: тёмная «сцена» с акцентным свечением, под ней тень, сверху — сама грань плаща,
     * натянутая шейдером живой ткани ({@link CapePreviewRenderer}) и обведённая тонкой рамкой.
     * Пока текстура едет с laby.net — скелетон с бегущим бликом, если не доехала — понятная заглушка.
     */
    private void drawCapeCard(DrawContext context, net.minecraft.util.Identifier tex, LabyCapeManager.State state,
                              float cellX, float cellY, float cellW, int alpha, float waveAmount, boolean fullUv) {
        var ms = context.getMatrices();
        float a = alpha / 255f;
        int iconH = 50;
        int iconW = Math.round(iconH * 10f / 16f);
        float iconX = Math.round(cellX + (cellW - iconW) / 2f);
        float iconY = cellY + 7f;
        Color accent = ColorUtils.getPrimaryColor();

        // Сцена: подложка чуть шире плаща + мягкое акцентное свечение за ним.
        Render2D.drawRoundedRect(ms, iconX - 6f, iconY - 4f, iconW + 12f, iconH + 8f, 8f,
                new Color(9, 11, 17, Math.round(165f * a)));
        Render2D.drawRoundedRect(ms, iconX - 3f, iconY + iconH * 0.18f, iconW + 6f, iconH * 0.7f, 7f,
                ColorUtils.alpha(accent, Math.round(26f * a)));
        // Тень под тканью — карточка перестаёт быть «наклейкой».
        Render2D.drawRoundedRect(ms, iconX + 1f, iconY + 3f, iconW, iconH, 4f,
                new Color(0, 0, 0, Math.round(110f * a)));

        if (state == LabyCapeManager.State.READY && tex != null) {
            if (fullUv) {
                // Текстура уже является готовой гранью (снимок linken) — сэмплим её целиком, а не вырезаем
                // «грань плаща» повторно (иначе в иконку попадает растянутый верхне-левый уголок снимка).
                CapePreviewRenderer.render(ms, iconX, iconY, iconW, iconH, 4f, tex, a, waveAmount, 0f, 0f, 1f, 1f);
            } else {
                CapePreviewRenderer.render(ms, iconX, iconY, iconW, iconH, 4f, tex, a, waveAmount);
            }
        } else {
            drawCapePlaceholder(ms, state, iconX, iconY, iconW, iconH, a, accent);
        }

        Render2D.drawBorder(ms, iconX, iconY, iconW, iconH, 4f, 0.7f,
                new ColorRGBA(ColorUtils.alpha(accent, Math.round(90f * a))));
    }

    /** Заглушка вместо плаща: скелетон с бегущим бликом (грузится) или перечёркнутое поле (ошибка). */
    private void drawCapePlaceholder(org.joml.Matrix3x2fStack ms, LabyCapeManager.State state,
                                     float x, float y, float w, float h, float a, Color accent) {
        Render2D.drawRoundedRect(ms, x, y, w, h, 4f, new Color(18, 21, 28, Math.round(230f * a)));
        if (state == LabyCapeManager.State.FAILED) {
            Color bad = new Color(232, 96, 96, Math.round(190f * a));
            var font = Fonts.ROUNDBOLD.getFont(9f);
            Render2D.drawFont(ms, font, "!", x + (w - font.getWidth("!")) / 2f, y + h / 2f - font.getHeight() / 2f, bad);
            Render2D.drawRoundedRect(ms, x + 4f, y + h * 0.62f, w - 8f, 1f, 0.5f,
                    ColorUtils.alpha(bad, Math.round(90f * a)));
            return;
        }
        // Бегущий сверху вниз блик — понятный признак «ещё едет», без спиннера и лишних draw call'ов.
        float t = (System.currentTimeMillis() % 1200L) / 1200f;
        float bandH = h * 0.28f;
        float bandY = y - bandH + t * (h + bandH);
        float top = Math.max(y, bandY);
        float bottom = Math.min(y + h, bandY + bandH);
        if (bottom > top) {
            Render2D.drawRoundedRect(ms, x + 2f, top, w - 4f, bottom - top, 3f,
                    ColorUtils.alpha(accent, Math.round(55f * a)));
        }
    }

    private void updateHoveredItem(Layout layout, int mouseX, int mouseY) {
        hoveredCosmeticId = null;
        List<CosmeticDefinition> items = CosmeticCatalog.forSlot(activeTab);
        int cellW = Math.max(88, (layout.gridW - 28) / GRID_COLUMNS);
        int cellH = 86;
        int top = layout.contentY + 34;
        int viewportH = gridViewportH(layout);
        if (!MathUtils.isHovered(layout.gridX, top, layout.gridW, viewportH, (float) mouseX, (float) mouseY)) {
            return;
        }

        List<String> custom = customCapes();
        int total = items.size() + custom.size();
        int startY = top - Math.round(scroll);
        for (int i = 0; i < total; i++) {
            int col = i % GRID_COLUMNS;
            int row = i / GRID_COLUMNS;
            float cellX = layout.gridX + 10f + col * (cellW + 8f);
            float cellY = startY + row * (cellH + 8f);
            if (MathUtils.isHovered(cellX, cellY, cellW, cellH, (float) mouseX, (float) mouseY)) {
                hoveredCosmeticId = i < items.size() ? items.get(i).id() : "laby:" + custom.get(i - items.size());
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button != 0) {
            return super.mouseClicked(click, doubleClick);
        }

        Layout layout = getLayout();

        int tabY = layout.contentY + 30;
        for (CosmeticSlot tab : TABS) {
            if (MathUtils.isHovered(layout.sidebarX + 6, tabY, layout.sidebarW - 12, 28, (float) mouseX, (float) mouseY)) {
                activeTab = tab;
                resetPreviewRotation(tab);
                scroll = 0f;
                hoveredCosmeticId = null;
                labyFocused = false;
                playerPreview.setSlot(tab);
                return true;
            }
            tabY += 32;
        }

        // Плащи: поле/кнопка laby.net.
        if (activeTab == CosmeticSlot.CAPE) {
            if (MathUtils.isHovered(rLabyApply[0], rLabyApply[1], rLabyApply[2], rLabyApply[3], (float) mouseX, (float) mouseY)) {
                applyLabyCape();
                return true;
            }
            if (MathUtils.isHovered(rLabyField[0], rLabyField[1], rLabyField[2], rLabyField[3], (float) mouseX, (float) mouseY)) {
                labyFocused = true;
                return true;
            }
            labyFocused = false;
        }

        if (usesColorPicker(activeTab)) {
            PreviewBounds bounds = previewBounds(layout);
            // Координаты ДОЛЖНЫ совпадать с рендером (округление identical), иначе мелкие пресеты-образцы
            // (11px) промахиваются: рендер рисует по Math.round(bounds.*), клик раньше брал сырые bounds.*.
            int previewInnerX = Math.round(bounds.x);
            int previewInnerY = Math.round(bounds.y);
            int previewInnerW = Math.round(bounds.w);
            int previewInnerH = Math.max(48, Math.round(bounds.h));
            float pickerY = previewInnerY + previewInnerH + 4f;
            if (wingsColorPicker.mouseClicked(previewInnerX, pickerY, previewInnerW, mouseX, mouseY, button)) {
                return true;
            }
        }

        PreviewBounds bounds = previewBounds(layout);
        if (MathUtils.isHovered(bounds.x, bounds.y, bounds.w, bounds.h, (float) mouseX, (float) mouseY)) {
            previewDragging = true;
            previewLastMx = mouseX;
            previewLastMy = mouseY;
            return true;
        }

        CosmeticsService service = LinkenVisuals.getInstance().getCosmeticsService();
        List<CosmeticDefinition> items = CosmeticCatalog.forSlot(activeTab);
        List<String> custom = customCapes();
        int total = items.size() + custom.size();
        int cellW = Math.max(88, (layout.gridW - 28) / GRID_COLUMNS);
        int cellH = 86;
        int top = layout.contentY + 34 - Math.round(scroll);
        for (int i = 0; i < total; i++) {
            int col = i % GRID_COLUMNS;
            int row = i / GRID_COLUMNS;
            float cellX = layout.gridX + 10f + col * (cellW + 8f);
            float cellY = top + row * (cellH + 8f);
            if (MathUtils.isHovered(cellX, cellY, cellW, cellH, (float) mouseX, (float) mouseY)) {
                if (i < items.size()) {
                    service.applyLocalSelection(activeTab, items.get(i).id());
                } else {
                    String cid = custom.get(i - items.size());
                    float delS = 15f, delX = cellX + cellW - delS - 3f, delY = cellY + 3f;
                    if (MathUtils.isHovered(delX, delY, delS, delS, (float) mouseX, (float) mouseY)) {
                        // Клик по красному трэшу — удалить кастом; если он сейчас надет, сбросить на первый пункт.
                        LabyCapeManager.removeKnown(cid);
                        if (("laby:" + cid).equals(service.getLocalProfile().get(activeTab)) && !items.isEmpty()) {
                            service.applyLocalSelection(activeTab, items.get(0).id());
                        }
                    } else {
                        service.applyLabyCapeId(cid); // переключить на сохранённый custom cape (id уже разобран)
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button == 0) {
            previewDragging = false;
        }
        if (button == 0 && wingsColorPicker.mouseReleased(button)) {
            LinkenVisuals.getInstance().getCosmeticsService().applyLocalTint(activeTab, wingsColorPicker.colorArgb());
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = getLayout();
        int top = layout.contentY + 34;
        int viewportH = gridViewportH(layout);
        if (MathUtils.isHovered(layout.gridX, top, layout.gridW, viewportH, (float) mouseX, (float) mouseY)) {
            scroll -= (float) verticalAmount * 16f;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (labyFocused) {
            boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { labyFocused = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { applyLabyCape(); return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!labyInput.isEmpty()) labyInput = labyInput.substring(0, labyInput.length() - 1);
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                String clip = mc.keyboard.getClipboard();
                if (clip != null) labyInput = (labyInput + clip.replace("\n", " ").replace("\r", "")).trim();
                if (labyInput.length() > 300) labyInput = labyInput.substring(0, 300);
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_A) { labyInput = ""; return true; }
            return true;
        }
        if (wingsColorPicker.keyPressed(keyCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        if (labyFocused && chr >= ' ' && chr != 127) {
            if (labyInput.length() < 300) labyInput += chr;
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        // Не закрываем сразу — запускаем анимацию затухания; реальное закрытие в render() по её окончании.
        if (!closing) {
            closing = true;
        }
    }

    private void finishClose() {
        module.notifyScreenClosed();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    public void closeSilently() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    /** Текст в косметике — белее (на тёмном фоне читается лучше). */
    private static Color whiteText(int alpha) {
        return new Color(236, 239, 246, Math.max(0, Math.min(255, alpha)));
    }

    /** Поле ввода плаща с laby.net + кнопка «Применить» (низ грид-панели, только вкладка «Плащи»). */
    private void drawLabyCapeUI(DrawContext context, Layout layout, int mouseX, int mouseY, int alpha) {
        var ms = context.getMatrices();
        float pad = alpha / 255f;
        float fieldH = 26f;
        float fieldY = layout.contentY + layout.contentH - 74f;
        float applyW = 66f;
        float fieldX = layout.gridX + 10f;
        float fieldW = layout.gridW - 20f - applyW - 8f;

        Render2D.drawFont(ms, Fonts.ROUNDBOLD.getFont(8.2f), "Плащ с laby.net", fieldX, fieldY - 15f, whiteText(alpha));

        // Поле.
        Render2D.drawRoundedRect(ms, fieldX, fieldY, fieldW, fieldH, 7f, new Color(255, 255, 255, labyFocused ? 26 : 15));
        Render2D.drawBorder(ms, fieldX, fieldY, fieldW, fieldH, 7f, 0.7f,
                new ColorRGBA(ColorUtils.alpha(ColorUtils.getPrimaryColor(), Math.round((labyFocused ? 155 : 55) * pad))));
        var vf = Fonts.MEDIUM.getFont(7.2f);
        boolean placeholder = labyInput.isEmpty() && !labyFocused;
        String shown = placeholder ? "laby.net/capes/…" : fitLeft(vf, labyInput, fieldW - 14f);
        Render2D.drawFont(ms, vf, shown, fieldX + 7f, fieldY + fieldH / 2f - vf.getHeight() / 2f,
                whiteText(placeholder ? Math.round(110f * pad) : alpha));
        if (labyFocused) {
            float cx = fieldX + 7f + vf.getWidth(shown) + 1f;
            Render2D.drawRoundedRect(ms, cx, fieldY + 6f, 1f, fieldH - 12f, 0.5f, whiteText(alpha));
        }
        rLabyField[0] = fieldX; rLabyField[1] = fieldY; rLabyField[2] = fieldW; rLabyField[3] = fieldH;

        // Кнопка «Применить».
        float ax = fieldX + fieldW + 8f;
        boolean aHov = MathUtils.isHovered(ax, fieldY, applyW, fieldH, (float) mouseX, (float) mouseY);
        Render2D.drawRoundedRect(ms, ax, fieldY, applyW, fieldH, 7f, ColorUtils.alpha(ColorUtils.getPrimaryColor(), aHov ? 210 : 150));
        var bf = Fonts.ROUNDBOLD.getFont(7.4f);
        Render2D.drawFont(ms, bf, "Применить", ax + (applyW - bf.getWidth("Применить")) / 2f, fieldY + fieldH / 2f - bf.getHeight() / 2f, new Color(10, 17, 13));
        rLabyApply[0] = ax; rLabyApply[1] = fieldY; rLabyApply[2] = applyW; rLabyApply[3] = fieldH;

        String note = System.currentTimeMillis() < labyMsgUntil ? labyMsg : "Вставь ссылку или ID плаща с laby.net";
        Render2D.drawFont(ms, Fonts.MEDIUM.getFont(6.4f), note, fieldX, fieldY + fieldH + 6f, whiteText(Math.round(130f * pad)));
    }

    /** Применить введённый плащ; даёт короткий фидбек. */
    private void applyLabyCape() {
        String id = ru.method.linkenvisuals.cosmetics.laby.LabyCapeManager.parseId(labyInput);
        if (id == null) {
            labyMsg = "Не похоже на ссылку/ID laby.net";
            labyMsgUntil = System.currentTimeMillis() + 4000L;
            return;
        }
        LinkenVisuals.getInstance().getCosmeticsService().applyLabyCape(labyInput);
        labyMsg = "Плащ применён, грузится…";
        labyMsgUntil = System.currentTimeMillis() + 4000L;
        labyFocused = false;
    }

    /** Обрезка слева с «…» — показываем ХВОСТ строки (id важен в конце ссылки). */
    private static String fitLeft(ru.method.linkenvisuals.utils.render.fonts.Instance font, String text, float maxW) {
        if (font.getWidth(text) <= maxW) return text;
        String ell = "…";
        String s = text;
        while (s.length() > 1 && font.getWidth(ell + s) > maxW) {
            s = s.substring(1);
        }
        return ell + s;
    }


    private static String slotTitleKey(CosmeticSlot slot) {
        return switch (slot) {
            case CHARACTER -> "cosmetics.slot.character";
            case CAPE -> "cosmetics.slot.cape";
            case HAT -> "cosmetics.slot.hat";
            case HEAD -> "cosmetics.slot.head";
            case BODY -> "cosmetics.slot.body";
            case SHIELD -> "cosmetics.slot.shield";
            case WEAPON -> "cosmetics.slot.weapon";
            case PET -> "cosmetics.slot.pet";
            case WINGS -> "cosmetics.slot.wings";
            case EFFECT -> "cosmetics.slot.effect";
        };
    }


    private Layout getLayout() {
        int screenW = Math.max(1, this.width);
        int screenH = Math.max(1, this.height);
        if ((screenW <= 1 || screenH <= 1) && mc != null && mc.getWindow() != null) {
            screenW = mc.getWindow().getScaledWidth();
            screenH = mc.getWindow().getScaledHeight();
        }
        // Панель не шире экрана; при высоком GUI-scale (маленький scaledWidth) допускаем и узкую панель.
        // Размер окна: масштаб от экрана, но с ПОТОЛКОМ (иначе на больших/мелкогуишных экранах и на
        // телефонах окно раздувалось) И не больше самого экрана (минус поля) — вылезти не может.
        int panelW = Math.min(Math.min(760, screenW - 16), Math.round(screenW * 0.9f));
        int panelH = Math.min(Math.min(460, screenH - 16), Math.round(screenH * 0.86f));
        int x = Math.round((screenW - panelW) * 0.5f);
        int y = Math.round((screenH - panelH) * 0.5f);
        int headerH = 56;
        int contentY = y + headerH;
        int contentH = Math.max(140, panelH - headerH - 12);
        // Три колонки ВСЕГДА укладываются в panelW: sidebar/preview — пропорция с мягкими границами,
        // а grid берёт РОВНО остаток. Раньше были жёсткие минимумы (112+230+140) + gridW=max(140,…) —
        // на узкой панели их сумма превышала ширину, и правая колонка «Плащ» вылезала за экран.
        int margin = 14, gap = 10;
        int avail = Math.max(60, panelW - margin * 2 - gap * 2);
        int sidebarW = Math.min(138, Math.max(84, Math.round(avail * 0.20f)));
        int previewW = Math.min(320, Math.max(120, Math.round(avail * 0.42f)));
        if (sidebarW + previewW > avail - 90) {                 // не оставили гриду минимум ~90 — ужимаем превью
            previewW = Math.max(96, avail - sidebarW - 90);
        }
        int gridW = Math.max(70, avail - sidebarW - previewW);  // остаток; не вылезает за панель
        int sidebarX = x + margin;
        int previewX = sidebarX + sidebarW + gap;
        int gridX = previewX + previewW + gap;
        return new Layout(screenW, screenH, x, y, panelW, panelH, sidebarX, contentY, sidebarW, contentH, previewX, previewW, gridX, gridW);
    }

    private record Layout(int screenW, int screenH, int x, int y, int w, int h, int sidebarX, int contentY, int sidebarW, int contentH, int previewX, int previewW, int gridX, int gridW) {}

    private record PreviewBounds(float x, float y, float w, float h) {}
}
