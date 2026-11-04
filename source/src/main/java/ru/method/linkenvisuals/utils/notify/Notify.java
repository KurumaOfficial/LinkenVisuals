package ru.method.linkenvisuals.utils.notify;

import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.TimerUtils;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.Render2D;
import lombok.Getter;

import java.awt.*;

@Getter
public class Notify implements Wrapper {

    private final NotifyIcon icon;
    private final String notify;
    private final long delay;
    private float y;
    private final Animation animation = new Animation(300, 1f, true, Easing.BOTH_SINE);
    private final TimerUtils timer = new TimerUtils();

    public Notify(NotifyIcon icon, String notify, long delay) {
        this.icon = icon;
        this.notify = notify;
        this.delay = delay;
        // Стартуем у нижней кромки — плашка плавно подъезжает ВВЕРХ на своё место над хотбаром.
        y = mc.getWindow().getScaledHeight();
        timer.reset();
    }

    public void render(EventRender2D e, float picunY) {
        y = animate(y, picunY);
        float width = Fonts.MEDIUM.getWidth(notify, 9f);
        float width2 = Fonts.ICONSNEW.getWidth(icon.icon(), 8f);
        float width3 = width + width2 + 7f;
        float x = mc.getWindow().getScaledWidth() / 2f - (width3 / 2f);
        if (timer.passed(delay)) animation.update(false);
        float animValue = (float) animation.getValue();
        // Плашка гаснет ЦЕЛИКОМ: раньше фон был с фиксированной альфой 255 и «прыгал» в конце,
        // пока текст плавно уходил — теперь фон затухает тем же animValue.
        Color glassColor = ColorUtils.getGlassColor(Math.max(0, Math.round(255f * animValue)));
        Render2D.drawLiquidRect(
                e.getContext().getMatrices(),
                x - 2.5f,
                y - 2.5f,
                width3 + 5f,
                15f,
                new BorderRadius(Render2D.DEFAULT_CORNER_RADIUS),
                new ColorRGBA(glassColor),
                2.5f,
                22.0f,
                11f,
                175.0f,
                true,
                0f,
                0.19f
        );
        Render2D.drawFont(e.getContext().getMatrices(), Fonts.MEDIUM.getFont(9f), notify, x + width2 + 4f, y - 0.5f, new Color(255, 255, 255, (int) (255 * animValue)));
        Render2D.drawFont(e.getContext().getMatrices(), Fonts.ICONSNEW.getFont(8f), icon.icon(), x + 1f, y + 1f, new Color(255, 255, 255, (int) (255 * animValue)));
    }

    public float animate(float value, float target) {
        return value + (target - value) / 8f;
    }

    public boolean expired() {
        return timer.passed(delay) && animation.getValue() < 0.01f;
    }
}
