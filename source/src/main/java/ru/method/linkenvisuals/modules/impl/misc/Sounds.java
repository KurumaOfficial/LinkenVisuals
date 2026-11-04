package ru.method.linkenvisuals.modules.impl.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventAttackEntity;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.api.events.impl.EventPopTotem;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.managers.system.SoundManager;
import ru.method.linkenvisuals.managers.system.SoundManager.AssistCue;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.render.KillStreak;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Звуки на боевые события (убийство, серия, потеря своего тотема, смерть, низкое HP) + Dota-подобное
 * визуальное оформление килстрика («DOUBLE KILL … RAMPAGE … GODLIKE»).
 *
 * <p>Учитываются убийства ЛЮБЫХ живых сущностей (и игроков, и мобов) — удобно для теста серий.</p>
 */
public class Sounds extends Module {

    private static final long DAMAGE_RETENTION_MS = 4_500L;
    private static final long STREAK_WINDOW_MS = 9_000L;

    private final NumberSetting volume = new NumberSetting("settings.sounds.volume", 130f, 0f, 200f, 5f);
    private final BooleanSetting kills = new BooleanSetting("settings.sounds.kills", true);
    private final BooleanSetting killStreak = new BooleanSetting("settings.sounds.streak", true);
    private final BooleanSetting totem = new BooleanSetting("settings.sounds.totem", true);
    private final BooleanSetting death = new BooleanSetting("settings.sounds.death", true);
    private final BooleanSetting lowHealth = new BooleanSetting("settings.sounds.lowhealth", true);
    private final NumberSetting lowHealthAt = new NumberSetting("settings.sounds.lowhealth_at", 6f, 1f, 19f, 1f, lowHealth::getValue);

    /**
     * id жертвы -> метка урона. ConcurrentHashMap обязателен: {@code EventPacket.Receive} постится
     * из netty-треда, а {@code onTick} итерирует мапу в главном — на HashMap это CME-краш.
     * Флаг {@code mob} запоминаем в момент урона: к моменту смерти сущность уже может быть удалена
     * из мира, и тип по id не восстановить.
     */
    private final Map<Integer, Mark> damaged = new ConcurrentHashMap<>();
    private final AssistCue[] killCues = {AssistCue.KILL_MOLODEC, AssistCue.KILL_UNICHTOZJEN, AssistCue.KILL_ZAPEN};
    private final Random rnd = new Random();

    /** Ниже какого HP пропажу сущности считаем убийством (а не выходом из прогрузки). */
    private static final float DESPAWN_KILL_HP = 6.0f;

    private record Mark(long at, boolean mob, boolean player, float lastHealth) {}

    private int streak;
    private long lastKillMs;
    private boolean deadFlag;
    private boolean lowFlag;

    public Sounds() {
        super("Sounds", Category.Player);
    }

    @Override
    public void onDisable() {
        damaged.clear();
        streak = 0;
        deadFlag = false;
        lowFlag = false;
        super.onDisable();
    }

    @EventHandler
    private void onAttack(EventAttackEntity event) {
        if (fullNullCheck() || event.getPlayer() != mc.player) return;
        if (!(event.getTarget() instanceof LivingEntity living) || living == mc.player) return;
        if (living.getId() < 0) return; // клиентские иллюзии (FakePlayer/KillEffect) — не убийства
        damaged.put(living.getId(), new Mark(System.currentTimeMillis(),
                living instanceof MobEntity, living instanceof PlayerEntity, living.getHealth()));
    }

    @EventHandler
    private void onPacket(EventPacket.Receive event) {
        Packet<?> packet = event.getPacket();
        if (!(packet instanceof EntityDamageS2CPacket dmg) || fullNullCheck()) return;
        if (mc.world == null || mc.player == null) return;

        int targetId = dmg.entityId();
        int playerId = mc.player.getId();
        if (targetId == playerId) return;

        boolean byPlayer = dmg.sourceCauseId() == playerId || dmg.sourceDirectId() == playerId;
        if (!byPlayer) {
            Entity direct = mc.world.getEntityById(dmg.sourceDirectId());
            if (!(direct instanceof ProjectileEntity proj) || !isOwnProjectile(proj)) return;
        }
        Entity target = mc.world.getEntityById(targetId);
        if (target instanceof LivingEntity livingTarget && target != mc.player && targetId >= 0) {
            damaged.put(targetId, new Mark(System.currentTimeMillis(),
                    target instanceof MobEntity, target instanceof PlayerEntity, livingTarget.getHealth()));
        }
    }

    @EventHandler
    private void onTotem(EventPopTotem event) {
        if (fullNullCheck() || !totem.getValue()) return;
        if (event.getPlayer() == mc.player) {
            play(AssistCue.TOTEM_POP);
        }
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) {
            damaged.clear();
            return;
        }

        long now = System.currentTimeMillis();
        if (streak > 0 && now - lastKillMs > STREAK_WINDOW_MS) streak = 0;

        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0.0f;
        if (dead) {
            if (!deadFlag) {
                deadFlag = true;
                streak = 0;
                if (death.getValue()) {
                    play(rnd.nextBoolean() ? AssistCue.SELF_RESPAWN : AssistCue.SELF_RESPAWN_ALT);
                }
            }
        } else {
            deadFlag = false;
        }

        float hp = mc.player.getHealth();
        float threshold = lowHealthAt.getValue();
        if (!dead && hp > 0f && hp <= threshold) {
            if (!lowFlag) {
                lowFlag = true;
                if (lowHealth.getValue()) play(AssistCue.LOW_HEALTH);
            }
        } else if (hp > threshold + 2f) {
            lowFlag = false;
        }

        // Убийства помеченных целей (мобы + игроки).
        Iterator<Map.Entry<Integer, Mark>> it = damaged.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Mark> e = it.next();
            if (now - e.getValue().at() > DAMAGE_RETENTION_MS) {
                it.remove();
                continue;
            }
            Entity ent = mc.world.getEntityById(e.getKey());
            if (ent instanceof LivingEntity living) {
                if (living.isAlive() && living.getHealth() > 0.0f) {
                    // Запоминаем последнее здоровье — по нему решим, было ли исчезновение смертью.
                    Mark m = e.getValue();
                    e.setValue(new Mark(m.at(), m.mob(), m.player(), living.getHealth()));
                    continue;
                }
                onKill(now, e.getValue());
                it.remove();
            } else if (ent == null) {
                // Сущность пропала. Считаем убийством ТОЛЬКО если видели её на низком HP: иначе она
                // просто вышла из прогрузки, а раньше это давало ложные звуки килла и серии —
                // из-за чего поведение модуля выглядело случайным.
                if (e.getValue().lastHealth() <= DESPAWN_KILL_HP) {
                    onKill(now, e.getValue());
                }
                it.remove();
            }
        }
    }

    private void onKill(long now, Mark mark) {
        streak++;
        lastKillMs = now;

        // Кого KillStreak показывает на плашке (игроки всегда, мобы по его настройке) — тот тир
        // озвучивает ОН: голос обязан совпадать с заголовком плашки, а счётчики у модулей разные.
        // Остальных (выключенные мобы, стойки) озвучиваем здесь, иначе их серии молчат.
        KillStreak ks = killStreakModule();
        boolean plaqueOwned = ks != null && ks.isToggled()
                && (mark.mob() ? ks.countsMobs() : mark.player());
        boolean ksVoices = plaqueOwned && ks.voicesTiers();
        if (killStreak.getValue() && streak >= 2 && !plaqueOwned) {
            play(streakCue(MathHelper.clamp(streak - 2, 0, 4)));
        } else if (kills.getValue() && !(ksVoices && streak >= 2)) {
            play(killCues[rnd.nextInt(killCues.length)]);
        }

    }

    /** Тир плашки -> звук. fx: 0 ДВОЙНОЕ, 1 ТРОЙНОЕ, 2 МЕГАКИЛЛ, 3 МОНСТРКИЛЛ, 4 БЕЗУМИЕ. */
    public static AssistCue streakCue(int fx) {
        return switch (fx) {
            case 0 -> AssistCue.DOTA_DOUBLE_KILL;
            case 1 -> AssistCue.DOTA_TRIPLE_KILL;
            case 2 -> AssistCue.DOTA_MEGA_KILL;
            case 3 -> AssistCue.DOTA_MONSTER_KILL;
            default -> AssistCue.DOTA_RAMPAGE;
        };
    }

    private KillStreak killStreakModule() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return null;
        return instance.getModuleManager().getModule(KillStreak.class);
    }

    private boolean isOwnProjectile(ProjectileEntity proj) {
        Entity owner = proj.getOwner();
        return owner instanceof PlayerEntity pe && pe.getUuid().equals(mc.player.getUuid());
    }

    private void play(AssistCue cue) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getSoundManager() == null || mc.getSoundManager() == null) return;
        SoundManager sm = instance.getSoundManager();
        SoundEvent event = sm.getAssistSound(cue);
        if (event == null) return;
        float vol = Math.max(0f, volume.getValue() / 100f);
        if (vol <= 0f) return;
        mc.getSoundManager().play(PositionedSoundInstance.ui(event, 1.0f, vol));
    }

}
