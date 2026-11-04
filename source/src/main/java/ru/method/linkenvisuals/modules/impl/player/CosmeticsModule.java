package ru.method.linkenvisuals.modules.impl.player;

import net.minecraft.client.gui.screen.Screen;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.cosmetics.service.CosmeticsService;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEffects;
import ru.method.linkenvisuals.modules.settings.SettingsCollector;
import ru.method.linkenvisuals.modules.settings.impl.ActionSetting;
import ru.method.linkenvisuals.screen.clickgui.ClickGui;
import ru.method.linkenvisuals.screen.cosmetics.CosmeticsScreen;

/**
 * Косметика. Один модуль для всего: гардероб (плащи/шапки/крылья) + косметические эффекты
 * (нимб, сферы, шлейф, питомцы, анимации, Наруто). Эффекты живут в движке {@link CosmeticEffects},
 * чьи настройки показываются прямо в настройках этого модуля. Тумблер = «косметика работает».
 */
public class CosmeticsModule extends Module {

    // Кнопка-действие в настройках: открывает экран-гардероб.
    private final ActionSetting openWardrobe = new ActionSetting(
            "settings.cosmetics.open", "settings.cosmetics.open", this::openWardrobeScreen);

    // Чья косметика видна: все синхронизированные / только друзья / только пати / только своя.
    private final ru.method.linkenvisuals.modules.settings.impl.EnumSetting<ru.method.linkenvisuals.cosmetics.model.CosmeticVisibility> visibility =
            new ru.method.linkenvisuals.modules.settings.impl.EnumSetting<>(
                    "settings.cosmetics.visibility", ru.method.linkenvisuals.cosmetics.model.CosmeticVisibility.EVERYONE) {
                @Override
                public void setValue(ru.method.linkenvisuals.cosmetics.model.CosmeticVisibility value) {
                    super.setValue(value);
                    applyServiceSettings();
                }
            };

    // Движок косметических эффектов — отдельным модулем НЕ регистрируется, его настройки и логика
    // вынесены сюда, в «Косметика».
    private final CosmeticEffects effects = new CosmeticEffects();

    public CosmeticsModule() {
        super("Cosmetics", Category.Player);
        // Порядок настроек: сперва гардероб, затем все настройки эффектов.
        getSettings().add(openWardrobe);
        getSettings().add(visibility);
        SettingsCollector.collectDeclaredSettings(effects, effects.getSettings(), "Cosmetics");
        getSettings().addAll(effects.getSettings());
        applyServiceSettings();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        // Подписываем движок эффектов на события (onRender3D/onKey) пока модуль включён.
        LinkenVisuals.getInstance().getEventHandler().subscribe(effects);
        applyServiceSettings();
    }

    @Override
    public void onDisable() {
        LinkenVisuals.getInstance().getEventHandler().unsubscribe(effects);
        effects.shutdown();
        super.onDisable();
        applyServiceSettings();
    }

    /** Движок косметических эффектов (нимб/сферы/питомец) — для рендера в GUI-превью. */
    public CosmeticEffects getEffects() {
        return effects;
    }

    /** Открыть экран-гардероб (через настройку модуля, а не тумблер). */
    private void openWardrobeScreen() {
        if (mc == null) {
            return;
        }
        // Открыл гардероб — значит хочет пользоваться косметикой: включаем модуль, иначе плащ/шапки
        // виден только в превью (гейт shouldRender по enabled), а В МИРЕ его нет («надет, но не видно»).
        if (!isToggle()) {
            setToggled(true);
        }
        Screen parent = mc.currentScreen instanceof ClickGui ? null : mc.currentScreen;
        if (!(mc.currentScreen instanceof CosmeticsScreen)) {
            mc.setScreen(new CosmeticsScreen(this, parent));
        }
    }

    /** Закрытие гардероба больше не выключает модуль: тумблер = «косметика работает», он независим. */
    public void notifyScreenClosed() {
    }

    private void applyServiceSettings() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) {
            return;
        }
        CosmeticsService service = instance.getCosmeticsService();
        if (service == null) {
            return;
        }
        service.setEnabled(isToggle());
        service.setVisibility(visibility.getValue());
    }
}
