package ru.method.linkenvisuals.screen.linkgui.windows;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiChrome;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiContext;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiLayout;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiColors;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiStyle;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

public final class InterfaceWindow {
    private final LinkGuiContext context;
    private final Animation cursorToggleAnim = new Animation(220, 1f, false, Easing.BOTH_SINE);
    private float x, y, width;
    private boolean draggingSlider;

    // Кэш Setting-объектов модуля UI: резолвим по имени один раз, дальше читаем getValue() без stream-поиска.
    private NumberSetting scaleSetting;
    private EnumSetting<?> guiStyleSetting;

    public InterfaceWindow(LinkGuiContext context) {
        this.context = context;
    }

    public void setBounds(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public float getHeight() {
        return LinkGuiLayout.INTERFACE_HEIGHT;
    }

    public void render(DrawContext drawContext, int mouseX, int mouseY) {
        render(drawContext.getMatrices(), mouseX, mouseY);
    }

    public void renderOverlay(Matrix3x2fStack stack, int mouseX, int mouseY) {
        render(stack, mouseX, mouseY);
    }

    private void render(Matrix3x2fStack stack, int mouseX, int mouseY) {
        float h = getHeight();
        LinkGuiChrome.drawGlassPanel(stack, x, y, width, h, LinkGuiLayout.RADIUS);
        LinkGuiChrome.drawSectionTitle(stack, "INTERFACE", "T", x + 14f, y + 12f);

        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        float scaleVal = readScale(ui);
        Color text = ColorUtils.getTextColor(210);

        WindowGuiRender.drawText(stack, Fonts.MEDIUM, "Interface Scale", x + 14f, y + 38f, text, 7.5f);
        String pct = Math.round(scaleVal * 100f) + "%";
        WindowGuiRender.drawText(stack, Fonts.ROUNDBOLD, pct,
                x + width - 14f - Fonts.ROUNDBOLD.getWidth(pct, 7.5f), y + 38f, LinkGuiThemeScope.accent(), 7.5f);

        float barX = x + 14f;
        float barY = y + 54f;
        float barW = width - 28f;
        WindowGuiRender.drawRoundedRect(stack, barX, barY, barW, 3f, 1.5f, WindowGuiColors.whiteAlpha(0.08f));
        float t = (scaleVal - 0.75f) / 0.75f;
        WindowGuiRender.drawRoundedRect(stack, barX, barY, barW * t, 3f, 1.5f, LinkGuiThemeScope.accent());
        WindowGuiRender.drawRoundedRect(stack, barX + barW * t - 4f, barY - 3f, 8f, 8f, 4f, Color.WHITE);

        if (draggingSlider && ui != null) {
            float clicked = Math.max(0f, Math.min(1f, (mouseX - barX) / barW));
            float next = 0.75f + clicked * 0.75f;
            setScale(ui, next);
            UI.previewInterfaceScale(next);
        }

        WindowGuiRender.drawText(stack, Fonts.MEDIUM, "GUI Style", x + 14f, y + 76f, text, 7.5f);
        float styleX = x + 14f;
        float styleY = y + 90f;
        float styleW = width - 28f;
        float styleH = 22f;
        boolean styleHover = MathUtils.isHovered(styleX, styleY, styleW, styleH, mouseX, mouseY);
        LinkGuiChrome.drawInset(stack, styleX, styleY, styleW, styleH, 7f);
        if (styleHover) {
            LinkGuiChrome.drawPillButton(stack, styleX + 1f, styleY + 1f, styleW - 2f, styleH - 2f, 6f, true);
        }
        WindowGuiRender.drawText(stack, Fonts.MEDIUM, readGuiStyle(ui),
                styleX + 8f, styleY + 6f, ColorUtils.getTextColor(230), 7.5f);

        WindowGuiRender.drawText(stack, Fonts.MEDIUM, "Cursor Tracker", x + 14f, y + 124f, text, 7.5f);
        cursorToggleAnim.update(context.isCursorTracker());
        LinkGuiChrome.drawToggle(stack, x + width - 48f, y + 120f, 34f, 16f, cursorToggleAnim.getValue());

        // Apply / Default buttons for the interface scale.
        float gap = 8f;
        float btnY = y + 150f;
        float btnH = 22f;
        float btnW = (width - 28f - gap) / 2f;
        drawActionButton(stack, x + 14f, btnY, btnW, btnH, "Apply", mouseX, mouseY, true);
        drawActionButton(stack, x + 14f + btnW + gap, btnY, btnW, btnH, "Default", mouseX, mouseY, false);
    }

    private void drawActionButton(Matrix3x2fStack stack, float bx, float by, float bw, float bh,
                                  String label, int mouseX, int mouseY, boolean primary) {
        boolean hover = MathUtils.isHovered(bx, by, bw, bh, mouseX, mouseY);
        LinkGuiChrome.drawInset(stack, bx, by, bw, bh, 7f);
        LinkGuiChrome.drawPillButton(stack, bx + 1f, by + 1f, bw - 2f, bh - 2f, 6f, hover ? 1f : 0.18f);
        Color labelColor = primary ? LinkGuiThemeScope.accent() : ColorUtils.getTextColor(hover ? 245 : 210);
        float size = 7.5f;
        float labelH = WindowGuiRender.textHeight(Fonts.ROUNDBOLD, size);
        WindowGuiRender.drawCenteredText(stack, Fonts.ROUNDBOLD, label,
                bx + bw / 2f, by + (bh - labelH) / 2f, labelColor, size);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        float barX = x + 14f;
        float barY = y + 54f;
        float barW = width - 28f;
        if (MathUtils.isHovered(barX, barY - 5f, barW, 12f, (float) mouseX, (float) mouseY)) {
            draggingSlider = true;
            return true;
        }
        float styleX = x + 14f;
        float styleY = y + 90f;
        if (MathUtils.isHovered(styleX, styleY, width - 28f, 22f, (float) mouseX, (float) mouseY)) {
            cycleGuiStyle();
            return true;
        }
        if (MathUtils.isHovered(x + width - 48f, y + 120f, 34f, 16f, (float) mouseX, (float) mouseY)) {
            context.setCursorTracker(!context.isCursorTracker());
            return true;
        }
        float gap = 8f;
        float btnY = y + 150f;
        float btnH = 22f;
        float btnW = (width - 28f - gap) / 2f;
        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        if (MathUtils.isHovered(x + 14f, btnY, btnW, btnH, (float) mouseX, (float) mouseY)) {
            if (ui != null) ui.applyScaleSetting();
            return true;
        }
        if (MathUtils.isHovered(x + 14f + btnW + gap, btnY, btnW, btnH, (float) mouseX, (float) mouseY)) {
            if (ui != null) {
                ui.resetScaleSetting();
                UI.previewInterfaceScale(readScale(ui));
            }
            return true;
        }
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSlider) {
            UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
            if (ui != null) ui.applyScaleSetting();
        }
        draggingSlider = false;
    }

    // Лениво резолвит Setting по имени и типу; кэширует ссылку. Если ui == null или не найдено — вернёт null (попробуем в следующем кадре).
    @SuppressWarnings("unchecked")
    private static <T extends Setting<?>> T resolve(UI ui, T cached, String name, Class<T> type) {
        if (cached != null) return cached;
        if (ui == null) return null;
        for (Setting<?> s : ui.getSettings()) {
            if (type.isInstance(s) && name.equals(s.getName())) return (T) s;
        }
        return null;
    }

    private NumberSetting scaleSetting(UI ui) {
        return scaleSetting = resolve(ui, scaleSetting, "settings.ui.scale", NumberSetting.class);
    }

    private float readScale(UI ui) {
        NumberSetting s = scaleSetting(ui);
        return s != null ? s.getValue() : 1f;
    }

    private void setScale(UI ui, float value) {
        NumberSetting s = scaleSetting(ui);
        if (s != null) s.setValue(value);
    }

    private EnumSetting<?> guiStyleSetting(UI ui) {
        if (guiStyleSetting != null) return guiStyleSetting;
        if (ui == null) return null;
        for (Setting<?> s : ui.getSettings()) {
            if (s instanceof EnumSetting<?> es && "settings.ui.gui".equals(s.getName())) return guiStyleSetting = es;
        }
        return null;
    }

    private void cycleGuiStyle() {
        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        EnumSetting<?> s = guiStyleSetting(ui);
        if (s != null) s.increaseEnum();
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance != null && !instance.isConfigLoading()) {
            instance.getConfigManager().forceSave();
        }
    }

    private String readGuiStyle(UI ui) {
        EnumSetting<?> s = guiStyleSetting(ui);
        if (s == null) return "Panel";
        Object v = s.getValue();
        return v instanceof WindowGuiStyle ws ? ws.getName() : String.valueOf(v);
    }
}
