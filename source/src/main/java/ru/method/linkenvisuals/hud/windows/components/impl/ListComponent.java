package ru.method.linkenvisuals.hud.windows.components.impl;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.managers.system.HudManager;
import ru.method.linkenvisuals.hud.windows.components.WindowComponent;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ListSetting;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;

public class ListComponent extends WindowComponent {

	private final ListSetting setting;
	private static final float ITEM_HEIGHT = 14f;
	private final Map<BooleanSetting, Animation> toggleAnimations = new HashMap<>();

	public ListComponent(String name, ListSetting setting) {
		super(name);
		this.setting = setting;
		for (BooleanSetting setting1 : setting.getValue()) {
			Animation toggleAnimation = new Animation(300, 1f, false, Easing.BOTH_SINE);
			toggleAnimation.snapTo(setting1.getValue());
			toggleAnimations.put(setting1, toggleAnimation);
		}
		this.addHeight = () -> {
			float open = animation != null ? (float) animation.getValue() : 1f;
			if (open <= 0f) return 0f;
			return setting.getValue().size() * ITEM_HEIGHT * open;
		};
		this.visible = setting::isVisible;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		float open = animation != null ? (float) animation.getValue() : 1f;
		int openAlpha = Math.max(0, Math.min(255, (int) (255 * open)));
		String countText = "(" + setting.getToggled().size() + "/" + setting.getValue().size() + ")";
		float countWidth = Fonts.REGULAR.getWidth(countText, 7.5f);
		drawMarqueeLabel(
				context,
				I18n.translate(setting.getName()),
				7.5f,
				x + 5f,
				y + 3.5f,
				Math.max(0f, width - countWidth - 10f),
				height,
				ColorUtils.getTextColor(openAlpha),
				mouseX,
				mouseY,
				false
		);

		Render2D.drawFont(context.getMatrices(),
				Fonts.REGULAR.getFont(7.5f),
				countText,
				x + width - countWidth - 5f,
				y + 3.5f,
				ColorUtils.getTextColor(openAlpha)
		);

		if (open <= 0.01f) return;

		float yOffset = height;
		for (BooleanSetting elementSetting : setting.getValue()) {
			Animation anim = toggleAnimations.get(elementSetting);
			if (anim == null) continue;
			anim.update(elementSetting.getValue());

			Render2D.drawFont(
					context.getMatrices(),
					Fonts.ICONSNEW.getFont(10f),
					"H",
					x + width - 14f,
					y + yOffset + 3.5f,
					ColorUtils.getTextColor((int) (openAlpha * anim.getValue()))
			);
			drawMarqueeLabel(
					context,
					I18n.translate(elementSetting.getName()),
					7.5f,
					x + 6f,
					y + yOffset + 3.5f,
					Math.max(0f, width - 20f),
					ITEM_HEIGHT,
					ColorUtils.getTextColor(openAlpha),
					mouseX,
					mouseY,
					false
			);
			yOffset += ITEM_HEIGHT;
		}
	}

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
			float open = animation != null ? (float) animation.getValue() : 1f;
			if (open <= 0.99f) return;

            float yOffset = height;
            for (BooleanSetting elementSetting : setting.getValue()) {
				if (MathUtils.isHovered(x, y + yOffset, width, ITEM_HEIGHT, (float) mouseX, (float) mouseY)) {
                    boolean newValue = !elementSetting.getValue();
                    elementSetting.setValue(newValue);

                    HudManager hudManager = LinkenVisuals.getInstance().getHudManager();
                    if (hudManager != null) {
                        float spawnX = (float) mouseX + 10f;
                        float spawnY = (float) mouseY - 6f;
                        hudManager.handleElementToggle(elementSetting, newValue, spawnX, spawnY);
                    }
                    break;
                }

                yOffset += ITEM_HEIGHT;
            }
		}
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
