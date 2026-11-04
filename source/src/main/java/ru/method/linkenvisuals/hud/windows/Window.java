package ru.method.linkenvisuals.hud.windows;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import ru.method.linkenvisuals.hud.windows.components.WindowComponent;
import ru.method.linkenvisuals.hud.windows.components.impl.BooleanComponent;
import ru.method.linkenvisuals.hud.windows.components.impl.EnumComponent;
import ru.method.linkenvisuals.hud.windows.components.impl.ListComponent;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.ListSetting;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import lombok.*;

@Getter @Setter
public class Window {
	private static final float WINDOW_CORNER_RADIUS = 12.0f;
	private float x, y, width, height;
	private final List<Setting<?>> settings;
	private final List<WindowComponent> components = new ArrayList<>();
	private final Animation animation = new Animation(300, 1f, true, Easing.BOTH_SINE);

	public Window(float x, float y, float width, float height, List<Setting<?>> settings) {
		this(x, y, width, height, settings, null);
	}

	public Window(float x, float y, float width, float height, List<Setting<?>> settings, List<WindowComponent> extraComponents) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.settings = settings;
		if (extraComponents != null) {
			this.components.addAll(extraComponents);
		}
		for (Setting<?> setting : settings) {
			if (setting instanceof BooleanSetting) components.add(new BooleanComponent(setting.getName(), ((BooleanSetting) setting)));
			else if (setting instanceof EnumSetting<?>) components.add(new EnumComponent(setting.getName(), ((EnumSetting<?>) setting)));
			else if (setting instanceof ListSetting) components.add(new ListComponent(setting.getName(), ((ListSetting) setting)));
		}
		animation.update(true);
		animation.reset();
	}

	public void render(DrawContext context, int mouseX, int mouseY) {
		float finalHeight = getFinalHeight();
		float scale = getRenderScale();
		float centerX = getX() + getWidth() / 2f;
		float centerY = getY() + finalHeight / 2f;
		float renderX = centerX - getWidth() * scale / 2f;
		float renderY = centerY - finalHeight * scale / 2f;
		float[] logicalMouse = toLogicalMouse(mouseX, mouseY);
		int logicalMouseX = Math.round(logicalMouse[0]);
		int logicalMouseY = Math.round(logicalMouse[1]);
		Render2D.startScissor(
				context,
				renderX,
				renderY,
				getWidth() * scale,
				finalHeight * scale
		);

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(centerX, centerY);
		context.getMatrices().scale(scale, scale);
		context.getMatrices().translate(-centerX, -centerY);

		if (ru.method.linkenvisuals.hud.LinkHudStyle.active()) {
			// Контекст-меню в стиле Link (панель в цветах темы + выбранная форма).
			ru.method.linkenvisuals.hud.LinkHudStyle.panel(context.getMatrices(), getX(), getY(), getWidth(), finalHeight,
					ru.method.linkenvisuals.hud.LinkHudStyle.radius(getWidth(), finalHeight), 1f);
		} else {
			Color glassColor = ColorUtils.getGlassColor(255);
			Render2D.drawLiquidRectHud(
					context.getMatrices(),
					getX(),
					getY(),
					getWidth(),
					finalHeight,
					new BorderRadius(WINDOW_CORNER_RADIUS),
					new ColorRGBA(glassColor),
					2.5f,
					22.0f,
					11f,
					175.0f,
					true,
					0f,
					0.19f
			);
		}

		float finalY = y;

		for (WindowComponent component : components) {
			component.setX(x);
			component.setY(finalY);
			component.setWidth(width);
			component.setHeight(height);
			component.setAnimation(animation);
			component.render(context, logicalMouseX, logicalMouseY, 0);
			finalY += component.getHeight() + component.getAddHeight().get() + 4.5f;
		}

		context.getMatrices().popMatrix();
		Render2D.stopScissor(context);
	}

	public void reset() {
		animation.update(false);
	}

	public boolean closed() {
		return animation.finished(false) && animation.getValue() <= 0.01f;
	}

	public void mouseClicked(double mouseX, double mouseY, int button) {
		float[] logicalMouse = toLogicalMouse(mouseX, mouseY);
		for (WindowComponent component : components) component.mouseClicked(logicalMouse[0], logicalMouse[1], button);
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
		float[] logicalMouse = toLogicalMouse(mouseX, mouseY);
		for (WindowComponent component : components) component.mouseReleased(logicalMouse[0], logicalMouse[1], button);
	}

	public boolean isHovered(double mouseX, double mouseY) {
		float[] logicalMouse = toLogicalMouse(mouseX, mouseY);
		return logicalMouse[0] >= x && logicalMouse[0] <= x + width
				&& logicalMouse[1] >= y && logicalMouse[1] <= y + getFinalHeight();
	}

	public float getFinalHeight() {
		float height = 0;
		for (WindowComponent component : components) {
			if (!component.getVisible().get()) continue;
			height += component.getHeight() + component.getAddHeight().get() + 4.5f;
		}

		return height;
	}

	private float getRenderScale() {
		return Math.max(0.01f, (float) animation.getValue() * UI.getInterfaceScale());
	}

	private float[] toLogicalMouse(double mouseX, double mouseY) {
		float centerX = x + width / 2f;
		float centerY = y + getFinalHeight() / 2f;
		float scale = getRenderScale();
		return new float[] {
				(float) (centerX + (mouseX - centerX) / scale),
				(float) (centerY + (mouseY - centerY) / scale)
		};
	}
}
