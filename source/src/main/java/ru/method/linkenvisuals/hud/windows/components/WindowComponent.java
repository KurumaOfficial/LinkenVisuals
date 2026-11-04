package ru.method.linkenvisuals.hud.windows.components;

import net.minecraft.client.gui.DrawContext;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import lombok.*;

import java.awt.Color;

@Getter @Setter
public abstract class WindowComponent extends Component {
	protected Animation animation;

	public WindowComponent(String name) {
		super(name);
	}

	protected void drawMarqueeLabel(
			DrawContext context,
			String label,
			float fontSize,
			float labelX,
			float labelY,
			float maxWidth,
			float rowHeight,
			Color color,
			int mouseX,
			int mouseY,
			boolean bold
	) {
		var font = bold ? Fonts.ROUNDBOLD : Fonts.REGULAR;
		boolean hovered = MathUtils.isHovered(x, y, width, rowHeight, mouseX, mouseY);
		float labelWidth = font.getWidth(label, fontSize);
		float offset = getMarqueeOffset(labelWidth, maxWidth, hovered);
		Render2D.startScissor(context, labelX, labelY, maxWidth, rowHeight);
		Render2D.drawFont(context.getMatrices(), font.getFont(fontSize), label, labelX - offset, labelY, color);
		if (offset > 0f && labelWidth > maxWidth) {
			Render2D.drawFont(
					context.getMatrices(),
					font.getFont(fontSize),
					label,
					labelX - offset + labelWidth + MARQUEE_GAP,
					labelY,
					color
			);
		}
		Render2D.stopScissor(context);
	}
}
