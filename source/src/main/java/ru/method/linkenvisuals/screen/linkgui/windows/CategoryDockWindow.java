package ru.method.linkenvisuals.screen.linkgui.windows;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiChrome;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiContext;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiDockTab;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope;
import ru.method.linkenvisuals.utils.render.GuiIcons;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiColors;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;

import java.awt.Color;

import net.minecraft.util.math.MathHelper;

public final class CategoryDockWindow {
    private static final long TAB_SLIDE_MS = 520L;

    private final LinkGuiContext context;
    // values() клонирует массив enum на каждый вызов — держим статическую копию.
    private static final LinkGuiDockTab[] TABS = LinkGuiDockTab.values();
    private final Animation[] activeAnims = new Animation[TABS.length];
    private final Animation[] hoverAnims = new Animation[TABS.length];
    private final InfinityAnimation tabSlideAnim = new InfinityAnimation(Easing.BOTH_SINE);
    private float x, y, width, height;
    private boolean tabSlideInitialized;

    public CategoryDockWindow(LinkGuiContext context) {
        this.context = context;
        for (int i = 0; i < activeAnims.length; i++) {
            activeAnims[i] = new Animation(420, 1f, false, Easing.BOTH_SINE);
            hoverAnims[i] = new Animation(360, 1f, false, Easing.BOTH_SINE);
        }
    }

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void tick() {
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        render(context.getMatrices(), mouseX, mouseY);
    }

    private void render(Matrix3x2fStack stack, int mouseX, int mouseY) {
        LinkGuiChrome.drawGlassPanel(stack, x, y, width, height, 12f);

        LinkGuiDockTab[] tabs = TABS;
        float pad = 6f;
        float tabW = (width - pad * 2f) / tabs.length;
        float tabH = height - pad * 2f;
        float tabY = y + pad;

        int selectedIndex = 0;
        for (int i = 0; i < tabs.length; i++) {
            if (this.context.getSelectedCategory() == tabs[i].category()) {
                selectedIndex = i;
                break;
            }
        }

        float indicatorTargetX = x + pad + selectedIndex * tabW + 2f;
        float indicatorW = tabW - 4f;
        if (!tabSlideInitialized) {
            tabSlideAnim.animate(indicatorTargetX, 1L);
            tabSlideInitialized = true;
        }
        float indicatorX = tabSlideAnim.animate(indicatorTargetX, TAB_SLIDE_MS);
        LinkGuiChrome.drawPillButton(stack, indicatorX, tabY, indicatorW, tabH, 9f, 0.92f);

        float tx = x + pad;
        for (int i = 0; i < tabs.length; i++) {
            LinkGuiDockTab tab = tabs[i];
            boolean active = this.context.getSelectedCategory() == tab.category();
            boolean hover = MathUtils.isHovered(tx + 2f, tabY, tabW - 4f, tabH, mouseX, mouseY);
            activeAnims[i].update(active);
            hoverAnims[i].update(hover && !active);

            float activeT = activeAnims[i].getValue();
            float hoverT = hoverAnims[i].getValue();
            // Подъём только на hover: активный таб раньше приподнимался на 1.5px, а его пилюля-индикатор
            // оставалась на месте — контент «плавал» над подложкой.
            float lift = hoverT * 4.5f;

            stack.pushMatrix();
            stack.translate(0f, -lift);

            if (!active) {
                LinkGuiChrome.drawPillButton(stack, tx + 2f, tabY, tabW - 4f, tabH, 9f, hoverT * 0.45f);
            }

            Color iconColor = WindowGuiColors.lerp(
                    LinkGuiThemeScope.text(150),
                    LinkGuiThemeScope.accent(),
                    active ? 1f : Math.max(hoverT * 0.8f, activeT * 0.35f)
            );

            float iconRestY = tabY + tabH / 2f - 6.5f;
            float iconActiveY = tabY + 9f;
            float iconY = MathHelper.lerp(Math.max(activeT, hoverT * 0.65f), iconRestY, iconActiveY);
            // Иконка категории: для Player — текстура-аватар, иначе глиф.
            net.minecraft.util.Identifier catTex = GuiIcons.forCategory(tab.category());
            if (catTex != null) {
                GuiIcons.draw(stack, catTex, tx + tabW / 2f, iconY + 6.5f, 15f, iconColor);
            } else {
                WindowGuiRender.drawCenteredText(stack, Fonts.ICONSNEW, tab.icon(),
                        tx + tabW / 2f, iconY, iconColor, 12.5f);
            }

            float labelAlpha = Math.max(activeT, hoverT * 0.75f);
            if (labelAlpha > 0.06f) {
                Color labelColor = WindowGuiColors.lerp(
                        LinkGuiThemeScope.text(145),
                        LinkGuiThemeScope.text(245),
                        active ? 1f : labelAlpha
                );
                float labelSize = 6.8f;
                float labelH = WindowGuiRender.textHeight(Fonts.MEDIUM, labelSize);
                float labelCenterX = tx + tabW / 2f;
                float labelCenterY = tabY + tabH - labelH / 2f - 5f;
                WindowGuiRender.drawCenteredText(stack, Fonts.MEDIUM, tab.label(),
                        labelCenterX, labelCenterY - labelH / 2f + 0.5f, labelColor, labelSize);
            }

            stack.popMatrix();
            tx += tabW;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY)) {
            return false;
        }

        if (button == 0) {
            LinkGuiDockTab[] tabs = TABS;
            float pad = 6f;
            float tabW = (width - pad * 2f) / tabs.length;
            float tabH = height - pad * 2f;
            float tabY = y + pad;
            float tx = x + pad;
            for (LinkGuiDockTab tab : tabs) {
                if (MathUtils.isHovered(tx + 2f, tabY, tabW - 4f, tabH, (float) mouseX, (float) mouseY)) {
                    context.selectDock(tab);
                    return true;
                }
                tx += tabW;
            }
        }

        return true;
    }
}
