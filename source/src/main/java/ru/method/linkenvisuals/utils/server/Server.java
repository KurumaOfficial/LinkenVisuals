package ru.method.linkenvisuals.utils.server;

import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.world.GameMode;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.mixins.accessors.IBossBarHud;
import ru.method.linkenvisuals.modules.impl.misc.ScoreboardHealth;
import ru.method.linkenvisuals.utils.Wrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@UtilityClass
public class Server implements Wrapper {
    private static final Pattern PVP_TEXT_PATTERN = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])(pvp|пвп|боя|бой)(?![\\p{L}\\p{N}_])");

    public boolean is(String server) {
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getServerInfo() == null) return false;
        return mc.getNetworkHandler().getServerInfo().address.toLowerCase().contains(server);
    }

    public boolean isFuntime() {
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getServerInfo() == null) return false;
        String address = mc.getNetworkHandler().getServerInfo().address.toLowerCase();
        String brand = mc.getNetworkHandler().getBrand();

        if (address.contains("funtime")) return true;
        if (address.contains("spookytime")) return true;
        if (address.contains("funsky")) return true;
        if (address.contains("space-times")) return true;
        if (address.contains("skytime")) return true;
        if (brand != null && brand.toLowerCase().contains("funtime")) return true;

        return getBossBarText().toLowerCase().contains("funtime");
    }

    public int getPing(PlayerEntity entity) {
        if (entity == null || mc.getNetworkHandler() == null) return 0;
        PlayerListEntry list = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        if (list == null) {
            // Офлайн-серверы за BungeeCord кладут в таб запись с ОФЛАЙНОВЫМ UUID (по нику), который
            // не совпадает с клиентским — поиск по UUID давал null и пинг «0 мс». Ник же стабилен.
            list = mc.getNetworkHandler().getPlayerListEntry(entity.getGameProfile().name());
        }
        return list != null ? list.getLatency() : 0;
    }

    private static final Pattern TIMER_MMSS = Pattern.compile("(\\d+):(\\d{2})");
    private static final Pattern TIMER_SECS = Pattern.compile("(\\d+)\\s*(?:сек|s\\b|sec)");

    public boolean isPvp() {
        // Сканируем ВСЕ боссбары, а не только первый — на сервере часто висит несколько (ивент + КТ).
        return isPvpAny();
    }

    public boolean isPvpAny() {
        for (String t : getBossBarTexts()) {
            if (isPvpText(t)) return true;
        }
        return false;
    }

    public String getPvpBossBar() {
        for (String t : getBossBarTexts()) {
            if (isPvpText(t)) return t;
        }
        return "";
    }

    public String getPvpTimer() {
        String bar = getPvpBossBar();
        return bar.isBlank() ? "" : bar.replaceAll("\\D", "");
    }

    /** Остаток боя в секундах из боевого боссбара (mm:ss или «N сек»); -1 если не распознан. */
    public int getPvpSeconds() {
        String bar = getPvpBossBar();
        if (bar.isBlank()) return -1;
        var mm = TIMER_MMSS.matcher(bar);
        if (mm.find()) {
            try {
                return Integer.parseInt(mm.group(1)) * 60 + Integer.parseInt(mm.group(2));
            } catch (NumberFormatException ignored) {
            }
        }
        var ss = TIMER_SECS.matcher(bar.toLowerCase());
        if (ss.find()) {
            try {
                return Integer.parseInt(ss.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    public boolean isPvpText(String text) {
        return text != null && !text.isBlank() && PVP_TEXT_PATTERN.matcher(text).find();
    }

    /** Тексты всех активных боссбаров. */
    public List<String> getBossBarTexts() {
        List<String> out = new ArrayList<>();
        for (ClientBossBar bossBar : ((IBossBarHud) mc.inGameHud.getBossBarHud()).getBossBars().values()) {
            if (bossBar != null) out.add(bossBar.getName().getString());
        }
        return out;
    }

    public String getBossBarText() {
        for (ClientBossBar bossBar : ((IBossBarHud) mc.inGameHud.getBossBarHud()).getBossBars().values()) {
            if (bossBar == null) continue;
            return bossBar.getName().getString();
        }
        return "";
    }

    public List<String> getSuckers() {
        List<String> suckers = new ArrayList<>();
        for (PlayerListEntry list : mc.getNetworkHandler().getPlayerList()) {
            if (list == null) return Collections.emptyList();
            if (list.getGameMode() == GameMode.SPECTATOR) suckers.add(list.getProfile().name());
        }
        return suckers;
    }

    public float getHealth(LivingEntity entity, boolean gapple) {
        if (LinkenVisuals.getInstance().getModuleManager().getModule(ScoreboardHealth.class).isToggled()) {
            if (entity instanceof PlayerEntity player) {
                if (player.getEntityWorld().getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME) == null) {
                    return entity.getHealth() + (gapple ? entity.getAbsorptionAmount() : 0f);
                }

                ScoreboardObjective objective = player.getEntityWorld().getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME);
                if (objective == null) {
                    return entity.getHealth() + (gapple ? entity.getAbsorptionAmount() : 0f);
                }

                ReadableScoreboardScore score = player.getEntityWorld().getScoreboard().getScore(player, objective);
                MutableText text = ReadableScoreboardScore.getFormattedScore(score, objective.getNumberFormatOr(StyledNumberFormat.EMPTY));
                String healthString = text.getString().replaceAll("\\D", "");

                if (healthString.isEmpty()) {
                    return entity.getHealth() + (gapple ? entity.getAbsorptionAmount() : 0f);
                }

                try {
                    return Float.parseFloat(healthString);
                } catch (NumberFormatException e) {
                    return entity.getHealth() + (gapple ? entity.getAbsorptionAmount() : 0f);
                }
            }
            return entity.getHealth() + (gapple ? entity.getAbsorptionAmount() : 0f);
        }
        return entity.getHealth() + (gapple ? entity.getAbsorptionAmount() : 0f);
    }
}
