package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.chat.ChatUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DonatePotionsNotify extends Module {
    private final NumberSetting notifyBefore = new NumberSetting("modules.settings.donate_potions_notify.notify_before", 10f, 1f, 60f, 1f);
    private final BooleanSetting assassin = new BooleanSetting("modules.settings.donate_potions_notify.assassin", true);
    private final BooleanSetting paladin = new BooleanSetting("modules.settings.donate_potions_notify.paladin", true);
    private final BooleanSetting wrath = new BooleanSetting("modules.settings.donate_potions_notify.wrath", true);
    private final BooleanSetting sleeping = new BooleanSetting("modules.settings.donate_potions_notify.sleeping", true);
    private final BooleanSetting radiation = new BooleanSetting("modules.settings.donate_potions_notify.radiation", true);
    private final BooleanSetting firecracker = new BooleanSetting("modules.settings.donate_potions_notify.firecracker", true);
    private final BooleanSetting holyWater = new BooleanSetting("modules.settings.donate_potions_notify.holy_water", true);

    private final Set<String> notifiedPotions = new HashSet<>();
    private int tickCounter;

    public DonatePotionsNotify() {
        super("DonatePotionsNotify", Category.Player);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        notifiedPotions.clear();
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck() || ++tickCounter % 10 != 0) return;

        Map<RegistryEntry<StatusEffect>, StatusEffectInstance> effects = mc.player.getActiveStatusEffects();
        checkPotion(new PotionRule("Зелье Ассасина", assassin.getValue(),
                requirement(StatusEffects.STRENGTH, 4),
                requirement(StatusEffects.SPEED, 3),
                requirement(StatusEffects.HASTE, 1)
        ), effects);
        checkPotion(new PotionRule("Зелье Паладина", paladin.getValue(),
                requirement(StatusEffects.RESISTANCE, 1),
                requirement(StatusEffects.FIRE_RESISTANCE, 1),
                requirement(StatusEffects.INVISIBILITY, 1),
                requirement(StatusEffects.HEALTH_BOOST, 3)
        ), effects);
        checkPotion(new PotionRule("Зелье Гнева", wrath.getValue(),
                requirement(StatusEffects.STRENGTH, 5),
                requirement(StatusEffects.SLOWNESS, 4)
        ), effects);
        checkPotion(new PotionRule("Снотворное", sleeping.getValue(),
                requirement(StatusEffects.WEAKNESS, 2),
                requirement(StatusEffects.MINING_FATIGUE, 2),
                requirement(StatusEffects.WITHER, 3),
                requirement(StatusEffects.BLINDNESS, 1)
        ), effects);
        checkPotion(new PotionRule("Зелье Радиации", radiation.getValue(),
                requirement(StatusEffects.POISON, 2),
                requirement(StatusEffects.WITHER, 2),
                requirement(StatusEffects.SLOWNESS, 3),
                requirement(StatusEffects.HUNGER, 5),
                requirement(StatusEffects.GLOWING, 1)
        ), effects);
        checkPotion(new PotionRule("Хлопушка", firecracker.getValue(),
                requirement(StatusEffects.SLOWNESS, 10),
                requirement(StatusEffects.SPEED, 5),
                requirement(StatusEffects.BLINDNESS, 10),
                requirement(StatusEffects.GLOWING, 1)
        ), effects);
        checkPotion(new PotionRule("Святая вода", holyWater.getValue(),
                requirement(StatusEffects.REGENERATION, 3),
                requirement(StatusEffects.INVISIBILITY, 2)
        ), effects);
    }

    private void checkPotion(PotionRule potion, Map<RegistryEntry<StatusEffect>, StatusEffectInstance> effects) {
        if (!potion.enabled()) {
            notifiedPotions.remove(potion.name());
            return;
        }

        int minDurationTicks = Integer.MAX_VALUE;
        for (EffectRequirement requirement : potion.requirements()) {
            StatusEffectInstance instance = effects.get(requirement.effect());
            if (instance == null || instance.getAmplifier() + 1 < requirement.minLevel()) {
                notifiedPotions.remove(potion.name());
                return;
            }
            minDurationTicks = Math.min(minDurationTicks, instance.getDuration());
        }

        int notifyBeforeTicks = Math.round(notifyBefore.getValue() * 20f);
        if (minDurationTicks > notifyBeforeTicks) {
            notifiedPotions.remove(potion.name());
            return;
        }

        if (!notifiedPotions.add(potion.name())) return;
        int seconds = Math.max(0, minDurationTicks / 20);
        ChatUtils.sendMessage("Донатное зелье скоро закончится: " + potion.name() + " (" + seconds + "с)");
    }

    private EffectRequirement requirement(RegistryEntry<StatusEffect> effect, int minLevel) {
        return new EffectRequirement(effect, minLevel);
    }

    private record PotionRule(String name, boolean enabled, EffectRequirement... requirements) {}
    private record EffectRequirement(RegistryEntry<StatusEffect> effect, int minLevel) {}
}
