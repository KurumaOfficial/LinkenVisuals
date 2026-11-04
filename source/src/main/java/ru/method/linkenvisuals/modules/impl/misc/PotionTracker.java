package ru.method.linkenvisuals.modules.impl.misc;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.notify.Notify;
import ru.method.linkenvisuals.utils.notify.NotifyIcons;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import ru.method.linkenvisuals.utils.text.ColorCodes;


public class PotionTracker extends Module {


	private final Map<RegistryEntry<StatusEffect>, Boolean> trackedEffects = new HashMap<>();
	private final Map<PotionTrackerEffectKey, Long> recentEffects = new HashMap<>();
	public PotionTracker() {
		super("PotionTracker", Category.Player);
	}
	@EventHandler
	public void onPacketReceive(EventPacket.Receive e) {
		if (fullNullCheck()) return;

		if (e.getPacket() instanceof EntityStatusEffectS2CPacket packet) {
			if (mc.world.getEntityById(packet.getEntityId()) != mc.player) return;
			if (mc.player.hasStatusEffect(packet.getEffectId())) return;

			long currentTick = mc.world.getTime();
			PotionTrackerEffectKey key = new PotionTrackerEffectKey(packet.getEffectId(), packet.getAmplifier(), packet.getDuration());
			Long lastTick = recentEffects.get(key);
			if (lastTick != null && currentTick - lastTick <= 1) return;
			recentEffects.put(key, currentTick);

			String potion = ColorCodes.strip(I18n.translate(packet.getEffectId().value().getTranslationKey()));
			String[] romans = { "", "I", "II", "III", "IV", "V" }; 
			String amplifier = (packet.getAmplifier() + 1 < romans.length) ? romans[packet.getAmplifier() + 1] : String.valueOf(packet.getAmplifier() + 1);
			String duration = String.format("%02d:%02d", packet.getDuration() / 20 / 60, packet.getDuration() / 20 % 60);

			ChatUtils.sendMessage(
					I18n.translate("modules.potiontracker.message",
							Formatting.RED + "Вы" + Formatting.WHITE,
							Formatting.GOLD + potion + " " + amplifier + Formatting.WHITE,
							Formatting.GRAY + duration + Formatting.WHITE
					)
			);
		}
	}

	
	@EventHandler
	public void onTick(EventTick e) {
		if (fullNullCheck() || mc.player == null) return;

		Map<RegistryEntry<StatusEffect>, StatusEffectInstance> currentEffects = mc.player.getActiveStatusEffects();

		
		for (RegistryEntry<StatusEffect> trackedEffect : new HashMap<>(trackedEffects).keySet()) {
			if (!currentEffects.containsKey(trackedEffect)) {
				
				trackedEffects.remove(trackedEffect);

				
				if (trackedEffect.value().getCategory() == StatusEffectCategory.BENEFICIAL) {
					String potionName = ColorCodes.strip(I18n.translate(trackedEffect.value().getTranslationKey()));
					LinkenVisuals.getInstance().getNotifyManager().add(
							new Notify(NotifyIcons.failIcon, "Бафф " + potionName + " закончился", 2000L)
					);
				}
			}
		}

		
		for (RegistryEntry<StatusEffect> effect : currentEffects.keySet()) {
			trackedEffects.putIfAbsent(effect, true);
		}
	}

	@Override
	public void onEnable() {
		super.onEnable();
		
		recentEffects.clear();

		if (mc.player != null) {
			for (RegistryEntry<StatusEffect> effect : mc.player.getActiveStatusEffects().keySet()) {
				trackedEffects.put(effect, true);
			}
		}
	}

	@Override
	public void onDisable() {
		super.onDisable();
		
		trackedEffects.clear();
		recentEffects.clear();
	}

}
