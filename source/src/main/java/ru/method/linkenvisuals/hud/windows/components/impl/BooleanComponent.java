package ru.method.linkenvisuals.hud.windows.components.impl;

import java.awt.Color;

import ru.method.linkenvisuals.hud.windows.components.WindowComponent;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;

public class BooleanComponent extends WindowComponent {

	private final BooleanSetting setting;
	private final Animation toggleAnimation = new Animation(300, 1, false, Easing.linkenvisuals);

	public BooleanComponent(String name, BooleanSetting setting) {
		super(name);
		this.setting = setting;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		toggleAnimation.update(setting.getValue());
		String label = I18n.translate(getName());
		drawMarqueeLabel(
				context,
				label,
				8f,
				x + 5f,
				y + 4f,
				Math.max(0f, width - 26f),
				height,
				ColorUtils.getTextColor((int) (255 * animation.getValue())),
				mouseX,
				mouseY,
				true
		);
		Render2D.drawRoundedRect(context.getMatrices(), x + width - 20f, y + 4.5f, 16f * toggleAnimation.getValue(), 8f, 2.5f, ColorUtils.getGlobalColor((int) (255 * toggleAnimation.getLinear())));
		Render2D.drawRoundedRect(context.getMatrices(), x + width - 4f - (16 * toggleAnimation.getReversedValue()), y + 4.5f, 16f * toggleAnimation.getReversedValue(), 8f, 2.5f, new Color(23, 23, 23, 100));
		Render2D.drawRoundedRect(context.getMatrices(), x + width - 19.5f + (8f * toggleAnimation.getValue()), y + 5f, 7f, 7f, 2.5f, ColorUtils.getTextColor(255));
	}

	@Override
	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (MathUtils.isHovered(x + width - 20f, y + 3.5f, 16f, 8f, (float) mouseX, (float) mouseY) && button == 0) setting.setValue(!setting.getValue());
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {

	}

	@Override
	public void keyPressed(int keyCode, int scanCode, int modifiers) {

	}

	@Override
	public void keyReleased(int keyCode, int scanCode, int modifiers) {

	}

	@Override
	public void charTyped(char chr, int modifiers) {

	}
}
