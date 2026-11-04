package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ReconfiguringScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import ru.method.linkenvisuals.screen.overlay.LinkenConnectRenderer;

/**
 * Пока экран реконфигурации закрыт кастомным окном подключения, глотаем клики.
 *
 * <p>Отрисовку экрана мы отменяем в {@link ScreenMixin}, но его кнопка отключения остаётся живой —
 * невидимой, но нажимаемой. Случайный клик по её месту рвал бы подключение. {@code mouseClicked} —
 * дефолтный метод интерфейса {@code Element}, в байткоде {@code Screen} его нет, поэтому {@code @Inject}
 * туда падает при загрузке; вместо этого добавляем настоящий {@code @Override} (тем же приёмом, что и
 * {@code ConnectScreenMixin}). Когда наше окно НЕ активно (реконфигурация уже в игре) — поведение
 * ванильное через {@code super}.
 */
@Mixin(ReconfiguringScreen.class)
public abstract class ReconfiguringScreenMixin extends Screen {

    protected ReconfiguringScreenMixin(Text title) {
        super(title);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (LinkenConnectRenderer.covers((Screen) (Object) this)) return true;
        return super.mouseClicked(click, doubleClick);
    }
}
