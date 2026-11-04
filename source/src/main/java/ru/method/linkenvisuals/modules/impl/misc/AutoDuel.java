package ru.method.linkenvisuals.modules.impl.misc;

import com.mojang.authlib.GameProfile;
import lombok.AllArgsConstructor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.modules.settings.impl.StringSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Автоматические запросы на дуэль всем онлайн-игрокам + автоподтверждение набора/поединка через GUI
 * (перенос AutoDuel из Kimiko). Логика специфична для FunTime (заголовки меню/сообщения на русском).
 */
public class AutoDuel extends Module {

    private static final Pattern NICK_PATTERN = Pattern.compile("^\\w{3,16}$");

    private final EnumSetting<Kit> kit = new EnumSetting<>("settings.autoduel.kit", Kit.Spheres);
    private final NumberSetting slowTime = new NumberSetting("settings.autoduel.speed", 500f, 300f, 1000f, 10f);
    private final BooleanSetting moneyMode = new BooleanSetting("settings.autoduel.money_mode", false);
    private final StringSetting money = new StringSetting("settings.autoduel.money", "10000", "10000", () -> moneyMode.getValue());

    private final List<String> sent = new ArrayList<>();
    private long counter, clearCounter, choiceCounter, confirmCounter;
    private double lastPosX, lastPosY, lastPosZ;
    private boolean disableRequested;

    public AutoDuel() {
        super("AutoDuel", Category.Other);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        long now = System.currentTimeMillis();
        counter = clearCounter = choiceCounter = confirmCounter = now;
        sent.clear();
        disableRequested = false;
        if (mc.player != null) {
            lastPosX = mc.player.getX();
            lastPosY = mc.player.getY();
            lastPosZ = mc.player.getZ();
        }
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (disableRequested) { disableRequested = false; setToggled(false); return; }
        if (fullNullCheck() || mc.player.networkHandler == null || mc.interactionManager == null) return;
        handleDuelLogic();
        handleScreenInteraction();
    }

    @EventHandler
    public void onPacket(EventPacket.Receive e) {
        if (!(e.getPacket() instanceof GameMessageS2CPacket msg)) return;
        String text = msg.content().getString().toLowerCase(Locale.ROOT);
        boolean starting = text.contains("начало") && text.contains("через") && text.contains("секунд");
        boolean forbidden = text.contains("дуэли") && text.contains("запрещено") && text.contains("команд");
        if (starting || forbidden) disableRequested = true;
    }

    private void handleDuelLogic() {
        List<String> players = getOnlinePlayers();
        double dist = Math.sqrt(Math.pow(lastPosX - mc.player.getX(), 2) + Math.pow(lastPosY - mc.player.getY(), 2)
                + Math.pow(lastPosZ - mc.player.getZ(), 2));
        if (dist > 500.0) { disableRequested = true; return; }
        lastPosX = mc.player.getX();
        lastPosY = mc.player.getY();
        lastPosZ = mc.player.getZ();

        if (System.currentTimeMillis() - clearCounter >= 800L * Math.max(1, players.size())) {
            sent.clear();
            clearCounter = System.currentTimeMillis();
        }
        String self = mc.player.getGameProfile().name();
        for (String nick : players) {
            if (sent.contains(nick) || nick.equals(self)) continue;
            if (System.currentTimeMillis() - counter < slowTime.getValue()) continue;
            sendDuelRequest(nick);
            sent.add(nick);
            counter = System.currentTimeMillis();
        }
    }

    private List<String> getOnlinePlayers() {
        List<String> out = new ArrayList<>();
        if (mc.player == null || mc.player.networkHandler == null) return out;
        for (PlayerListEntry entry : mc.player.networkHandler.getPlayerList()) {
            GameProfile profile = entry.getProfile();
            String name = profile.name();
            if (name == null || !NICK_PATTERN.matcher(name).matches()) continue;
            out.add(name);
        }
        return out;
    }

    private void sendDuelRequest(String nick) {
        if (moneyMode.getValue()) {
            mc.player.networkHandler.sendChatCommand("duel " + nick + " " + money.getValue());
        } else {
            mc.player.networkHandler.sendChatCommand("duel " + nick);
        }
    }

    private void handleScreenInteraction() {
        if (mc.currentScreen == null || mc.player.currentScreenHandler == null) return;
        ScreenHandler handler = mc.player.currentScreenHandler;
        String title = mc.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (title.contains("выбор набора") && System.currentTimeMillis() - choiceCounter >= 150L) {
            int slot = kit.getValue().slot();
            if (slot >= 0) {
                mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
            }
            choiceCounter = System.currentTimeMillis();
            return;
        }
        if (title.contains("настройка поединка") && System.currentTimeMillis() - confirmCounter >= 150L) {
            mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
            confirmCounter = System.currentTimeMillis();
        }
    }

    @AllArgsConstructor
    public enum Kit implements Nameable {
        Spheres("settings.autoduel.kit.spheres", 5),
        Shield("settings.autoduel.kit.shield", 0),
        Spikes3("settings.autoduel.kit.spikes3", 1),
        Bow("settings.autoduel.kit.bow", 2),
        Totems("settings.autoduel.kit.totems", 3),
        NoDebuff("settings.autoduel.kit.nodebuff", 4),
        Classic("settings.autoduel.kit.classic", 6),
        CheaterParadise("settings.autoduel.kit.cheater", 7),
        Netherite("settings.autoduel.kit.netherite", 8);

        private final String name;
        private final int slot;

        @Override
        public String getName() {
            return name;
        }

        public int slot() {
            return slot;
        }
    }
}
