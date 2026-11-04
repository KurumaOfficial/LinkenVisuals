package ru.method.linkenvisuals.modules.impl.misc;

import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.render.Render3D;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.Locale;
import java.util.regex.Pattern;

public class FtHelper extends Module {

    private final BindSetting bindDesor = new BindSetting("settings.funtimehelper.binddesor", new Bind(-1, false));
    private final BindSetting bindTrapka = new BindSetting("settings.funtimehelper.bindtrapka", new Bind(-1, false));
    private final BindSetting bindYavka = new BindSetting("settings.funtimehelper.bindyavka", new Bind(-1, false));
    private final BindSetting bindBozhka = new BindSetting("settings.funtimehelper.bindbozhka", new Bind(-1, false));
    private final BindSetting bindPlast = new BindSetting("settings.funtimehelper.bindplast", new Bind(-1, false));
    private final BindSetting bindSnowball = new BindSetting("settings.funtimehelper.bindsnowball", new Bind(-1, false));
    private static final String[] DESOR_TOKENS = {"дезориентац"};
    private static final String[] TRAPKA_TOKENS = {"трапка"};
    private static final String[] YAVKA_TOKENS = {"явная", "пыль"};
    private static final String[] BOZHKA_TOKENS = {"божья", "аура"};
    private static final String[] PLAST_TOKENS = {"пласт"};
    private static final String[] SNOWBALL_TOKENS = {"снежок", "замороз"};
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("\u00a7x(?:\u00a7[0-9a-fA-F]){6}");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("\u00a7.");

    public FtHelper() {
        super("FTHelper", Category.Player);
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @EventHandler
    public void onKey(EventKey e) {
        if (fullNullCheck() || mc.currentScreen != null) return;

        if (e.getAction() == 1) {
            if (bindDesor.getValue().matchesKey(e.getKey()))
                prekolByName("settings.funtimehelper.binddesor", DESOR_TOKENS);
            if (bindTrapka.getValue().matchesKey(e.getKey()))
                prekolByName("settings.funtimehelper.bindtrapka", TRAPKA_TOKENS);
            if (bindYavka.getValue().matchesKey(e.getKey()))
                prekolByName("settings.funtimehelper.bindyavka", YAVKA_TOKENS);
            if (bindBozhka.getValue().matchesKey(e.getKey()))
                prekolByName("settings.funtimehelper.bindbozhka", BOZHKA_TOKENS);
            if (bindPlast.getValue().matchesKey(e.getKey()))
                prekolByName("settings.funtimehelper.bindplast", PLAST_TOKENS);
            if (bindSnowball.getValue().matchesKey(e.getKey()))
                prekolByName("settings.funtimehelper.bindsnowball", SNOWBALL_TOKENS);
        }
    }

    @EventHandler
    public void onMouse(EventMouse e) {
        if (fullNullCheck() || mc.currentScreen != null) return;

        if (e.getAction() == 1) {
            if (bindDesor.getValue().matchesMouse(e.getButton()))
                prekolByName("settings.funtimehelper.binddesor", DESOR_TOKENS);
            if (bindTrapka.getValue().matchesMouse(e.getButton()))
                prekolByName("settings.funtimehelper.bindtrapka", TRAPKA_TOKENS);
            if (bindYavka.getValue().matchesMouse(e.getButton()))
                prekolByName("settings.funtimehelper.bindyavka", YAVKA_TOKENS);
            if (bindBozhka.getValue().matchesMouse(e.getButton()))
                prekolByName("settings.funtimehelper.bindbozhka", BOZHKA_TOKENS);
            if (bindPlast.getValue().matchesMouse(e.getButton()))
                prekolByName("settings.funtimehelper.bindplast", PLAST_TOKENS);
            if (bindSnowball.getValue().matchesMouse(e.getButton()))
                prekolByName("settings.funtimehelper.bindsnowball", SNOWBALL_TOKENS);
        }
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) return;

        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.getItem() != Items.ENDER_EYE && mainHand.getItem() != Items.SUGAR) return;

        Vec3d playerPos = mc.player.getEntityPos();
        double radius = 10.0;

        boolean playerInRange = false;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.squaredDistanceTo(mc.player) <= radius * radius) {
                playerInRange = true;
                break;
            }
        }

        Color circleColor = playerInRange ? new Color(0, 255, 0, 255) : new Color(255, 0, 0, 255);
        Render3D.renderCircle(event.getMatrixStack(), playerPos, radius, circleColor);
    }

    private void prekolByName(String settingKey, String[] tokens) {
        int slot = findHotbarByName(tokens);
        String settingName = I18n.translate(settingKey);

        if (slot == -1) {
            ChatUtils.sendMessage(
                    Formatting.WHITE + "Предмет " +
                            Formatting.RED + settingName +
                            Formatting.WHITE + " не найден!!"
            );
            return;
        }

        if (mc.player.getInventory().getSelectedSlot() == slot) {
            ChatUtils.sendMessage("Слот уже выбран");
            return;
        }

        mc.player.getInventory().setSelectedSlot(slot);

        ChatUtils.sendMessage(
                Formatting.WHITE + "Предмет " +
                        Formatting.GREEN + settingName +
                        Formatting.WHITE + " взят!"
        );
    }

    private int findHotbarByName(String[] tokens) {
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String name = normalizeName(stack.getName().getString());
            if (name.isEmpty()) continue;
            if (matchesTokens(name, tokens)) return i;
        }
        return -1;
    }

    private String normalizeName(String name) {
        if (StringHelper.isEmpty(name)) return "";
        String cleaned = HEX_COLOR_PATTERN.matcher(name).replaceAll("");
        cleaned = COLOR_CODE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replace("[\u2605]", "").trim();
        return cleaned.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean matchesTokens(String normalizedName, String[] tokens) {
        for (String token : tokens) {
            if (!normalizedName.contains(token)) return false;
        }
        return true;
    }
}
