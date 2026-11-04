package ru.method.linkenvisuals.screen.clickgui.components.impl;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.combat.AutoSwap;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiSettingStyle;
import ru.method.linkenvisuals.utils.render.LinkGuiRenderContext;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class BindComponent extends Component {

	private final BindSetting setting;
	private boolean binding;
	private final Animation hoverAnimation = new Animation(140, 1f, false, Easing.EASE_OUT_EXPO);

	public BindComponent(BindSetting setting) {
		super(setting.getName());
		this.setting = setting;
		this.visible = setting::isVisible;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		float bindWidth = 40f;
		float bindX = x + width - 44f;
		float bindY = y + 2f;
		float bindHeight = height - 4f;

		boolean bindHovered = MathUtils.isHovered(bindX, bindY, bindWidth, bindHeight, mouseX, mouseY);
		hoverAnimation.update(bindHovered || binding);

		if (LinkGuiRenderContext.isActive()) {
			String label = I18n.translate(setting.getName());
			LinkGuiSettingStyle.drawRowLabel(context.getMatrices(), label, x + 2f, y, width - 52f);
			String text = getBindText();
			text = fitText(text, bindWidth - 8f);
			LinkGuiSettingStyle.drawValueChip(context.getMatrices(), text, bindX, y + 3f, bindWidth, LinkGuiSettingStyle.ROW_HEIGHT - 6f,
					Math.max(hoverAnimation.getValue(), binding ? 1f : 0f));
			return;
		}

		Color textColor = ColorUtils.getTextColor(255);
		String label = I18n.translate(setting.getName());
		float labelWidth = Fonts.REGULAR.getWidth(label, 7.5f);
		float labelMaxWidth = Math.max(0f, width - 52f);
		boolean hovered = MathUtils.isHovered(x, y, width, height, mouseX, mouseY);
		float offset = getMarqueeOffset(labelWidth, labelMaxWidth, hovered);
		float nameY = y + (height - Fonts.REGULAR.getHeight(7.5f)) / 2f;
		Render2D.startScissor(context, x + 4f, y, labelMaxWidth, height);
		Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(7.5f), label, x + 4f - offset, nameY, textColor);
		if (offset > 0f && labelWidth > labelMaxWidth) {
			Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(7.5f), label, x + 4f - offset + labelWidth + MARQUEE_GAP, nameY, textColor);
		}
		Render2D.stopScissor(context);

		float radius = 5f;
		Color outline = (bindHovered || binding)
				? ColorUtils.getGlobalColor(235)
				: ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), 150);
		Render2D.drawRoundedRect(context.getMatrices(), bindX, bindY, bindWidth, bindHeight, radius, outline);

		float inset = 1f;
		float innerX = bindX + inset;
		float innerY = bindY + inset;
		float innerW = bindWidth - (inset * 2f);
		float innerH = bindHeight - (inset * 2f);
		float innerRadius = Math.max(0f, radius - inset);

		Color glass = ColorUtils.getGlassColor(255);
		Render2D.drawLiquidRectClickGuiChip(
				context.getMatrices(),
				innerX,
				innerY,
				innerW,
				innerH,
				new BorderRadius(innerRadius),
				new ColorRGBA(glass),
				2.5f,
				18.0f,
				11f,
				220.0f,
				true,
				0f,
				0.19f
		);

		int accentAlpha = (int) MathUtils.lerp(90f, 165f, (float) hoverAnimation.getValue());
		if (binding) {
			accentAlpha = 185;
		}
		Render2D.drawRoundedRect(
				context.getMatrices(),
				innerX,
				innerY,
				innerW,
				innerH,
				innerRadius,
				ColorUtils.getGlobalColor(accentAlpha)
		);

		Color bindTextColor;
		if (binding) {
			bindTextColor = ColorUtils.pulse(ColorUtils.getTextColor(255), 18);
		} else if (bindHovered) {
			bindTextColor = ColorUtils.getTextColor(255);
		} else {
			bindTextColor = ColorUtils.mix(ColorUtils.getTextColor(255), ColorUtils.getGlassBase(), 0.25f);
		}

		String text = getBindText();
		text = fitText(text, bindWidth - 8f);
		float textWidth = Fonts.MEDIUM.getWidth(text, 7f);
		float textHeight = Fonts.MEDIUM.getHeight(7f);

		float textX = bindX + (bindWidth - textWidth) / 2f;
		float textY = bindY + (bindHeight - textHeight) / 2f;

		Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(7f), text, textX, textY, bindTextColor);
	}

	private String getBindText() {
		if (binding) return "...";

		if (!setting.getValue().isMouse()) {
			int legacyKey = setting.getValue().getKey();
			if (legacyKey <= -95 && legacyKey >= -120) {
				int button = legacyKey + 100;
				if (button >= 0) {
					setting.getValue().setKey(button);
					setting.getValue().setMouse(true);
				}
			}
		}

		int key = setting.getValue().getKey();
		if (key == -1) return "None";

		if (setting.getValue().isMouse()) {
			return switch (key) {
				case Bind.MOUSE_SCROLL_UP -> "Wheel+";
				case Bind.MOUSE_SCROLL_DOWN -> "Wheel-";
				case 0 -> "LMB";
				case 1 -> "RMB";
				case 2 -> "MMB";
				default -> "MB" + (key + 1);
			};
		}

		return setting.getValue().toString();
	}

	private String fitText(String text, float maxWidth) {
		if (Fonts.MEDIUM.getWidth(text, 7f) <= maxWidth) return text;

		String ellipsis = "...";
		float ellipsisWidth = Fonts.MEDIUM.getWidth(ellipsis, 7f);
		String trimmed = text;
		while (trimmed.length() > 1 && Fonts.MEDIUM.getWidth(trimmed, 7f) + ellipsisWidth > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + ellipsis;
	}

	@Override
	public void mouseClicked(double mouseX, double mouseY, int button) {
		float bindWidth = 40f;
		float bindX = x + width - 44f;
		float bindY = LinkGuiRenderContext.isActive() ? y + 3f : y + 2f;
		float bindHeight = LinkGuiRenderContext.isActive() ? LinkGuiSettingStyle.ROW_HEIGHT - 6f : height - 4f;

		if (MathUtils.isHovered(bindX, bindY, bindWidth, bindHeight, (float) mouseX, (float) mouseY) && button == 0) {
			binding = !binding;
		} else if (binding && button != 0) {
			if (isAutoSwapConflict(button, true)) {
				ChatUtils.sendMessage(Formatting.RED + "Занято колесом автосвапа");
				binding = false;
				return;
			}
			setting.getValue().setKey(button);
			setting.getValue().setMouse(true);
			binding = false;
		} else if (binding) {
			binding = false;
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (!binding || verticalAmount == 0.0) return false;
		int key = verticalAmount > 0.0 ? Bind.MOUSE_SCROLL_UP : Bind.MOUSE_SCROLL_DOWN;
		if (isAutoSwapConflict(key, true)) {
			ChatUtils.sendMessage(Formatting.RED + "Р—Р°РЅСЏС‚Рѕ РєРѕР»РµСЃРѕРј Р°РІС‚РѕСЃРІР°РїР°");
			binding = false;
			return true;
		}
		setting.getValue().setKey(key);
		setting.getValue().setMouse(true);
		binding = false;
		return true;
	}

	@Override
	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		if (binding) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
				setting.getValue().setKey(-1);
				setting.getValue().setMouse(false);
			} else {
				if (isAutoSwapConflict(keyCode, false)) {
					ChatUtils.sendMessage(Formatting.RED + "Занято колесом автосвапа");
					binding = false;
					return;
				}
				setting.getValue().setKey(keyCode);
				setting.getValue().setMouse(false);
			}
			binding = false;
		}
	}

	@Override
	public void keyReleased(int keyCode, int scanCode, int modifiers) {}

	@Override
	public void charTyped(char chr, int modifiers) {}

	@Override
	public void onClose() {
		binding = false;
	}

	@Override
	public boolean isBinding() {
		return binding;
	}

	private boolean isAutoSwapConflict(int key, boolean isMouse) {
		if (!isHelperBindSetting(setting.getName())) return false;
		Bind autoSwapBind = getAutoSwapBind();
		if (autoSwapBind == null || autoSwapBind.isEmpty()) return false;
		if (autoSwapBind.isMouse() != isMouse) return false;
		return autoSwapBind.getKey() == key;
	}

	private boolean isHelperBindSetting(String settingName) {
		return settingName.startsWith("settings.funtimehelper.")
				|| settingName.startsWith("settings.hwhelper.")
				|| settingName.startsWith("settings.rwhelper.");
	}

	private Bind getAutoSwapBind() {
		LinkenVisuals instance = LinkenVisuals.getInstance();
		if (instance == null) return null;
		AutoSwap autoSwap = instance.getModuleManager().getModule(AutoSwap.class);
		if (autoSwap == null) return null;
		for (Setting<?> setting : autoSwap.getSettings()) {
			if (setting instanceof BindSetting bindSetting && "settings.autoswap.key".equals(bindSetting.getName())) {
				return bindSetting.getValue();
			}
		}
		return null;
	}
}
