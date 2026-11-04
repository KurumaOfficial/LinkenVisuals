package ru.method.linkenvisuals.modules.impl.misc;


import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.EnumMap;
import java.util.Map;


public class UseTracker extends Module {

	private static final int CLEAR_GRACE_TICKS = 4;

	private final Map<Hand, ItemStack> usingItems = new EnumMap<>(Hand.class);
	private final Map<Hand, Integer> useTimers = new EnumMap<>(Hand.class);
	private final Map<Hand, Long> clearAtWorldTick = new EnumMap<>(Hand.class);
	private long lastUseTick = -1;
	private String lastUseSignature = "";

	public UseTracker() {
		super("UseTracker", Category.Player);
	}

	@EventHandler
	public void onTick(EventTick e) {
		if (fullNullCheck()) return;

		checkHandUsage(Hand.MAIN_HAND);
		checkHandUsage(Hand.OFF_HAND);
		processPendingClears();
	}

	private void checkHandUsage(Hand hand) {
		boolean isUsing = mc.player.isUsingItem() && mc.player.getActiveHand() == hand;
		ItemStack currentItem = mc.player.getStackInHand(hand);

		if (isUsing && !currentItem.isEmpty()) {
			clearAtWorldTick.remove(hand);

			if (!usingItems.containsKey(hand)
					|| !ItemStack.areItemsEqual(usingItems.get(hand), currentItem)) {
				usingItems.put(hand, currentItem.copy());
				useTimers.put(hand, mc.player.getItemUseTimeLeft());
			}
			return;
		}

		if (usingItems.containsKey(hand) && !clearAtWorldTick.containsKey(hand)) {
			clearAtWorldTick.put(hand, mc.world.getTime() + CLEAR_GRACE_TICKS);
		}
	}

	private void processPendingClears() {
		if (clearAtWorldTick.isEmpty()) {
			return;
		}

		long now = mc.world.getTime();
		clearAtWorldTick.entrySet().removeIf(entry -> {
			if (now < entry.getValue()) {
				return false;
			}
			Hand hand = entry.getKey();
			usingItems.remove(hand);
			useTimers.remove(hand);
			return true;
		});
	}

	@EventHandler
	public void onPacketReceive(EventPacket.Receive e) {
		if (fullNullCheck()) return;

		if (e.getPacket() instanceof EntityStatusS2CPacket packet) {
			if (packet.getEntity(mc.world) != mc.player || packet.getStatus() != 9) return;

			ItemStack usedItem = null;

			for (Map.Entry<Hand, ItemStack> entry : usingItems.entrySet()) {
				ItemStack savedItem = entry.getValue();
				if (savedItem != null && !savedItem.isEmpty() && canBeConsumed(savedItem)) {
					usedItem = savedItem;
					break;
				}
			}

			if (usedItem == null && mc.player.isUsingItem()) {
				ItemStack activeItem = mc.player.getActiveItem();
				if (!activeItem.isEmpty() && canBeConsumed(activeItem)) {
					usedItem = activeItem;
				}
			}

			if (usedItem == null) {
				ItemStack mainHand = mc.player.getMainHandStack();
				if (!mainHand.isEmpty() && canBeConsumed(mainHand)) {
					usedItem = mainHand;
				}
			}

			if (usedItem != null && !usedItem.isEmpty()) {
				String item = usedItem.getItem().getName().getString();
				boolean potion = usedItem.getItem() instanceof PotionItem;
				long currentTick = mc.world.getTime();
				String signature = item + ":" + potion;
				boolean duplicate = currentTick - lastUseTick <= 1 && signature.equals(lastUseSignature);
				lastUseTick = currentTick;
				lastUseSignature = signature;

				if (!duplicate) {
					ChatUtils.sendMessage(
							potion ? I18n.translate("modules.usetracker.message2",
									Formatting.RED + "Вы" + Formatting.WHITE,
									Formatting.GOLD + item + Formatting.WHITE)
									: I18n.translate("modules.usetracker.message",
									Formatting.RED + "Вы" + Formatting.WHITE,
									Formatting.GOLD + item + Formatting.WHITE)
					);
				}
			}

			usingItems.clear();
			useTimers.clear();
			clearAtWorldTick.clear();
		}
	}

	private boolean canBeConsumed(ItemStack item) {
		UseAction useAction = item.getUseAction();

		return (item.get(DataComponentTypes.FOOD) != null)
				|| (item.getItem() instanceof PotionItem)
				|| useAction == UseAction.EAT
				|| useAction == UseAction.DRINK;
	}

	@Override
	public void onDisable() {
		super.onDisable();
		usingItems.clear();
		useTimers.clear();
		clearAtWorldTick.clear();
		lastUseTick = -1;
		lastUseSignature = "";
	}
}
