package ru.method.linkenvisuals.modules.impl.misc;


import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventAttackEntity;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;


public class HitSound extends Module {

    private final EnumSetting<SoundMode> sound = new EnumSetting<>("settings.hitsound.sound", SoundMode.BELL);
    private final BooleanSetting onlyCrit = new BooleanSetting("settings.hitsound.only_crit", false);

    public HitSound() {
        super("HitSound", Category.Player);
    }

    @EventHandler
    private void onAttack(EventAttackEntity event) {
        if (fullNullCheck() || event.getPlayer() != mc.player) return;
        if (mc.getSoundManager() == null) return;

        // Труп остаётся кликабельным ещё ~20 тиков (анимация смерти) — по нему звук не нужен.
        // НО здоровье ЧУЖИХ игроков клиенту часто не приходит (сервера/анти-читы его скрывают):
        // там getHealth()==0 и isAlive()==false у совершенно живой цели, и звук молчал по игрокам
        // вообще всегда. Поэтому health-проверку применяем только к НЕ-игрокам (мобам), а игроков
        // отсекаем лишь по удалению сущности из мира.
        if (event.getTarget() instanceof PlayerEntity target) {
            if (target.isRemoved()) return;
        } else if (event.getTarget() instanceof LivingEntity living
                && (!living.isAlive() || living.getHealth() <= 0.0f)) {
            return;
        }

        if (onlyCrit.getValue() && !isCriticalHit(event)) return;

        SoundEvent selected = sound.getValue().getEvent();

        if (selected == null) return;

        mc.getSoundManager().play(PositionedSoundInstance.ui(selected, 1.0f, 1.0f));
    }

    /**
     * Ванильные условия крита в момент удара (зеркало PlayerEntity.attack): цель жива, игрок падает,
     * не на земле/лестнице/в воде, без слепоты, не верхом, не спринтует, замах заряжен (>90%).
     * Событие атаки постится ДО сброса кулдауна, поэтому getAttackCooldownProgress ещё валиден.
     */
    private boolean isCriticalHit(EventAttackEntity event) {
        var p = mc.player;
        if (p == null || !(event.getTarget() instanceof LivingEntity)) return false;
        return p.fallDistance > 0.0f
                && !p.isOnGround()
                && !p.isClimbing()
                && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && !p.hasVehicle()
                && !p.isSprinting()
                && p.getAttackCooldownProgress(0.5f) > 0.9f;
    }

    public enum SoundMode implements Nameable {
        BELL("settings.hitsound.sound.bell", "bell"),
        CRIME("settings.hitsound.sound.crime", "crime"),
        NYA("settings.hitsound.sound.nya", "nya"),
        UWU("settings.hitsound.sound.uwu", "uwu");

        private final String translationKey;
        private final SoundEvent soundEvent;

        SoundMode(String translationKey, String soundId) {
            this.translationKey = translationKey;
            this.soundEvent = resolve(soundId);
        }

        @Override
        public String getName() {
            return translationKey;
        }

        private static SoundEvent resolve(String soundId) {
            Identifier id = LinkenVisuals.id(soundId);
            return Registries.SOUND_EVENT.containsId(id) ? Registries.SOUND_EVENT.get(id) : SoundEvent.of(id);
        }

        public SoundEvent getEvent() {
            return soundEvent;
        }
    }
}
