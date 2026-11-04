package ru.method.linkenvisuals.modules.api;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandNotificationManager;
import ru.method.linkenvisuals.managers.system.SoundManager;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.utils.Wrapper;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.resource.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Getter

public abstract class Module implements Wrapper {
    private final String name, description;
    private final Category category;
    protected boolean toggled;
    @Setter private Bind bind = new Bind(-1, false);
    private final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, Category category) {
        this.name = name;
        this.category = category;
        this.description = "descriptions" + "." + category.name().toLowerCase() + "." + name.toLowerCase();
    }

    public String getDisplayName() {
        String key = "modules." + name.toLowerCase(Locale.ROOT);
        String translated = I18n.translate(key);
        return key.equals(translated) ? name : translated;
    }

    public void onEnable() {
        toggled = true;
        LinkenVisuals.getInstance().getEventHandler().subscribe(this);
        boolean suppressFeedback = LinkenVisuals.getInstance().isSuppressToggleFeedback();
        if (!suppressFeedback && !fullNullCheck()) {
            DynamicIslandNotificationManager.getInstance().addNotification(this.getDisplayName(), true);
        }
        SoundManager soundManager = LinkenVisuals.getInstance().getSoundManager();
        if (!suppressFeedback && soundManager != null) {
            soundManager.playEnable();
        }
    }

    public void onDisable() {
        toggled = false;
        LinkenVisuals.getInstance().getEventHandler().unsubscribe(this);
        boolean suppressFeedback = LinkenVisuals.getInstance().isSuppressToggleFeedback();
        if (!suppressFeedback && !fullNullCheck()) {
            DynamicIslandNotificationManager.getInstance().addNotification(this.getDisplayName(), false);
        }
        SoundManager soundManager = LinkenVisuals.getInstance().getSoundManager();
        if (!suppressFeedback && soundManager != null) {
            soundManager.playDisable();
        }
    }

    public void setToggled(boolean toggled) {
        if (toggled) onEnable();
        else onDisable();
    }

    public void toggle() {
        setToggled(!toggled);
    }

    public boolean isToggle() {
        return toggled;
    }

    public boolean isUnsafe() {
        return false;
    }

    /**
     * Action-модуль: без состояния «вкл/выкл» и без бинда. Клик по карточке в GUI вызывает {@link #activate()}
     * (например {@code Elements} — открытие редактора HUD), а не тогл.
     */
    public boolean isActionModule() {
        return false;
    }

    /** Действие при клике по карточке action-модуля (по умолчанию — обычный тогл). */
    public void activate() {
        toggle();
    }

    /** Скрывать ли тумблер на карточке модуля (для action-модулей — да). */
    public boolean hidesToggle() {
        return isActionModule();
    }

    // Свежедобавленные модули — рядом с их кейбиндом в GUI рисуется бейдж «NEW».
    // Убирать имена из списка по мере того, как модули перестают быть новыми.
    private static final java.util.Set<String> NEW_MODULES = java.util.Set.of(
            "AutoReply", "FakePlayer", "FastExp", "CustomVitals", "Drochka",
            "KillStreak", "Sounds"
    );

    /** Помечен ли модуль как новый (бейдж «NEW» у кейбинда в карточке). */
    public boolean isNew() {
        return NEW_MODULES.contains(name);
    }

    public String getUnsafeDescriptionKey() {
        return "descriptions.module.unsafe";
    }

    public static boolean fullNullCheck() {
        return mc.player == null || mc.world == null;
    }

    protected void playCriticalSound() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) return;
        SoundManager soundManager = instance.getSoundManager();
        if (soundManager != null) {
            soundManager.playCritical(this);
        }
    }
}
