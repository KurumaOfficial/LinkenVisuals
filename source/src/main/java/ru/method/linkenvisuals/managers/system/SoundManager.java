package ru.method.linkenvisuals.managers.system;


import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.Wrapper;

import java.util.HashMap;
import java.util.Map;


public class SoundManager implements Wrapper {
    private static final long CRITICAL_SHAKE_DURATION_MS = 650L;

    private final SoundEvent enable;
    private final SoundEvent disable;
    private final SoundEvent critical;
    private final SoundEvent clickGuiOpen;
    private final Map<AssistCue, SoundEvent> assistSounds = new HashMap<>();
    private long criticalStartMs = -1L;
    private Module criticalSource;

    public SoundManager() {
        this.enable = register("enable");
        this.disable = register("disable");
        this.critical = register("critical");
        this.clickGuiOpen = register("clickgui_open");
        for (AssistCue cue : AssistCue.values()) {
            assistSounds.put(cue, register(cue.getId()));
        }
    }

    private SoundEvent register(String path) {
        Identifier id = LinkenVisuals.id(path);
        return Registries.SOUND_EVENT.containsId(id) ? Registries.SOUND_EVENT.get(id) : SoundEvent.of(id);
    }

    private void play(SoundEvent sound) {
        if (sound == null || mc.getSoundManager() == null) return;
        mc.getSoundManager().play(PositionedSoundInstance.ui(sound, 1.0f));
    }

    public void playEnable() {
        play(enable);
    }

    public void playDisable() {
        play(disable);
    }

    public void playCritical(Module source) {
        criticalStartMs = System.currentTimeMillis();
        criticalSource = source;
        play(critical);
    }

    public void playClickGuiOpen() {
        play(clickGuiOpen);
    }

    public void playAssist(AssistCue cue) {
        if (cue == null) return;
        play(assistSounds.get(cue));
    }

    public SoundEvent getAssistSound(AssistCue cue) {
        return assistSounds.get(cue);
    }

    public float getCriticalShakeOffset(Module module) {
        if (module == null) return 0f;
        if (criticalSource != module) return 0f;
        if (criticalStartMs < 0) return 0f;
        long elapsed = System.currentTimeMillis() - criticalStartMs;
        if (elapsed >= CRITICAL_SHAKE_DURATION_MS) {
            criticalStartMs = -1L;
            criticalSource = null;
            return 0f;
        }

        float progress = elapsed / (float) CRITICAL_SHAKE_DURATION_MS;
        float damping = 1f - progress;
        return (float) (Math.sin(progress * Math.PI * 6f) * damping * 4f);
    }

    public enum AssistCue {
        HELLO("hellodragons"),
        LOW_HEALTH("krithealth_lechisy"),
        KILL_MOLODEC("molodec_ubil"),
        KILL_UNICHTOZJEN("unichtozjen"),
        KILL_ZAPEN("zapen"),
        ARROW_HIT("vcheli"),
        SELF_RESPAWN("respawn"),
        SELF_RESPAWN_ALT("next_raz_povezyo"),
        TOTEM_POP("snes_totema"),
        MULTI_KILL("multikill"),
        UNSTOPPABLE("neostonovim"),
        DOUBLE_KILL("dabkill"),

        // Тиры килстрика в стиле Dota. Файлы .ogg кладутся в assets/linkenvisuals/sounds/ под этими
        // именами; пока файла нет, тир просто молчит (клиент не падает, в лог идёт предупреждение).
        DOTA_DOUBLE_KILL("dota_double_kill"),
        DOTA_TRIPLE_KILL("dota_triple_kill"),
        DOTA_MEGA_KILL("dota_mega_kill"),
        DOTA_MONSTER_KILL("dota_monster_kill"),
        DOTA_RAMPAGE("dota_rampage");

        private final String id;

        AssistCue(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}
