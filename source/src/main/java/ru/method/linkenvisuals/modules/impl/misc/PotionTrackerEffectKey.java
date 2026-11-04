package ru.method.linkenvisuals.modules.impl.misc;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.Objects;

final class PotionTrackerEffectKey {
    private final RegistryEntry<StatusEffect> effect;
    private final int amplifier;
    private final int duration;

    PotionTrackerEffectKey(RegistryEntry<StatusEffect> effect, int amplifier, int duration) {
        this.effect = effect;
        this.amplifier = amplifier;
        this.duration = duration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PotionTrackerEffectKey that)) return false;
        return amplifier == that.amplifier
                && duration == that.duration
                && Objects.equals(effect, that.effect);
    }

    @Override
    public int hashCode() {
        int result = effect != null ? effect.hashCode() : 0;
        result = 31 * result + amplifier;
        result = 31 * result + duration;
        return result;
    }
}
