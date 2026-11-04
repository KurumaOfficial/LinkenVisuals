package ru.method.linkenvisuals.modules.impl.misc;

import com.mojang.authlib.GameProfile;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import ru.method.linkenvisuals.api.events.impl.EventAttackEntity;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.modules.settings.impl.StringSetting;
import ru.method.linkenvisuals.utils.chat.ChatUtils;

import java.util.UUID;

/**
 * Спавнит клиентского клона игрока на месте, где стоишь — чтобы «оставить себя» в ловушке и уйти.
 *
 * <p><b>Важно:</b> это чисто <i>локальная</i> иллюзия. Клон виден только тебе в твоём рендере;
 * другие игроки на сервере его НЕ увидят (сущность не рассылается по сети). Полезно для скринов и
 * личной проверки, но не обманывает реальных врагов на ванильном сервере.</p>
 *
 * <p>Реализация повторяет проверенный в проекте паттерн клиентского спавна из
 * {@code KillEffect}: {@code setId(negativeClientId)} + {@code setUuid(random)} +
 * {@code mc.world.addEntity(...)}. Скин копируется через оверрайд {@link FakeEntity#getSkin()}.</p>
 */
public class FakePlayer extends Module {

    private final BooleanSetting copyEquipment = new BooleanSetting("settings.fakeplayer.copy_equipment", true);
    private final BooleanSetting holdTotem = new BooleanSetting("settings.fakeplayer.totem", true);
    private final StringSetting customName = new StringSetting("settings.fakeplayer.name", "", "");
    private final NumberSetting durationSeconds = new NumberSetting("settings.fakeplayer.duration", 0f, 0f, 600f, 5f);
    private final BindSetting reanchorBind = new BindSetting("settings.fakeplayer.reanchor", new Bind(-1, false));

    private FakeEntity fake;
    private ClientWorld anchorWorld;
    private double anchorX, anchorY, anchorZ;
    private float anchorYaw, anchorBodyYaw, anchorHeadYaw, anchorPitch;
    private int lifeTicks;
    private int nextClientEntityId = Integer.MIN_VALUE;

    public FakePlayer() {
        super("FakePlayer", Category.Player);
        getSettings().add(copyEquipment);
        getSettings().add(holdTotem);
        getSettings().add(customName);
        getSettings().add(durationSeconds);
        getSettings().add(reanchorBind);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        // Клон НЕ спавним при включении модуля — только по нажатию бинда (reanchorBind). Так модуль можно
        // держать включённым без фризов от авто-спавна клона; клон появляется, когда сам нажмёшь бинд.
    }

    @Override
    public void onDisable() {
        despawn();
        super.onDisable();
    }

    @EventHandler
    private void onKey(EventKey event) {
        // mc.currentScreen != null — иначе бинд срабатывает поверх ClickGUI (при назначении бинда/поиске).
        if (fullNullCheck() || mc.currentScreen != null) return;
        if (event.getAction() == 0) return;
        if (reanchorBind.getValue().matchesKey(event.getKey())) reanchor();
    }

    @EventHandler
    private void onMouse(EventMouse event) {
        if (fullNullCheck() || mc.currentScreen != null) return;
        if (event.getAction() == 0) return;
        if (reanchorBind.getValue().matchesMouse(event.getButton())) reanchor();
    }

    @EventHandler
    private void onAttack(EventAttackEntity event) {
        if (fake == null || event.getTarget() != fake) return;
        // Клон — чисто клиентская сущность (её нет на сервере), поэтому ванильный пакет атаки уходит
        // «в пустоту» по несуществующему id и сервер может ругнуться. Гасим его и обрабатываем удар
        // полностью на клиенте: анимация урона + счётчик HP + поп тотема при добивании.
        event.cancel();
        hitFake();
    }

    private void hitFake() {
        if (fake == null || mc.player == null || mc.world == null) return;

        float dmg = (float) mc.player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        if (dmg < 1f) dmg = 1f;

        // Красная вспышка + хёрт-звук + дёрганье конечностей (клиентский обработчик урона).
        fake.onDamaged(fake.getDamageSources().playerAttack(mc.player));

        float hp = fake.getHealth() - dmg;
        if (hp > 0f) {
            fake.setHealth(hp);
            return;
        }

        // Добили: с тотемом в руке — разыгрываем поп и воскрешаем на полном HP (как игрок с тотемом);
        // без тотема — просто восстанавливаем, чтобы дамми оставался на месте для дальнейших ударов.
        if (fake.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            popTotem();
        }
        fake.setHealth(fake.getMaxHealth());
    }

    private void popTotem() {
        if (fake == null || mc.world == null) return;
        fake.playSound(SoundEvents.ITEM_TOTEM_USE, 1.0f, 1.0f);
        for (int i = 0; i < 30; i++) {
            mc.world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING,
                    fake.getParticleX(0.5), fake.getRandomBodyY(), fake.getParticleZ(0.5),
                    mc.world.random.nextGaussian() * 0.15,
                    mc.world.random.nextDouble() * 0.3,
                    mc.world.random.nextGaussian() * 0.15);
        }
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) {
            despawn();
            return;
        }
        if (fake == null) return;

        // Смена мира/измерения — сущность из старого мира больше не валидна.
        if (mc.world != anchorWorld || fake.isRemoved()) {
            despawn();
            return;
        }

        // Держим клона на месте (защита от возможного дрейфа/гравитации).
        fake.refreshPositionAfterTeleport(anchorX, anchorY, anchorZ);
        fake.setYaw(anchorYaw);
        fake.setBodyYaw(anchorBodyYaw);
        fake.setHeadYaw(anchorHeadYaw);
        fake.setPitch(anchorPitch);

        int limit = Math.round(durationSeconds.getValue()) * 20;
        if (limit > 0) {
            lifeTicks++;
            if (lifeTicks >= limit) {
                toggle();
            }
        }
    }

    private void reanchor() {
        if (fullNullCheck()) return;
        despawn();
        spawn();
    }

    private void spawn() {
        PlayerEntity self = mc.player;
        if (self == null || mc.world == null) return;

        despawn();

        GameProfile srcProfile = self.getGameProfile();
        String name = customName.getValue().isBlank() ? srcProfile.name() : customName.getValue().trim();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        profile.properties().putAll(srcProfile.properties());

        SkinTextures skin = self instanceof AbstractClientPlayerEntity acp ? acp.getSkin() : null;

        FakeEntity entity = new FakeEntity(mc.world, profile, skin);
        entity.setId(nextClientEntityId());
        entity.setUuid(profile.id());
        entity.setPose(EntityPose.STANDING);
        entity.setCustomNameVisible(false);

        anchorWorld = mc.world;
        anchorX = self.getX();
        anchorY = self.getY();
        anchorZ = self.getZ();
        anchorYaw = self.getYaw();
        anchorBodyYaw = self.getBodyYaw();
        anchorHeadYaw = self.getHeadYaw();
        anchorPitch = self.getPitch();

        entity.refreshPositionAfterTeleport(anchorX, anchorY, anchorZ);
        entity.setYaw(anchorYaw);
        entity.setBodyYaw(anchorBodyYaw);
        entity.setHeadYaw(anchorHeadYaw);
        entity.setPitch(anchorPitch);

        if (copyEquipment.getValue()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = self.getEquippedStack(slot);
                if (stack != null) {
                    entity.equipStack(slot, stack.copy());
                }
            }
        }

        // Тотем в офф-хенде — чтобы клон был «дамми с тотемом»: при добивании разыгрывает поп тотема
        // и воскресает. Ставим ПОСЛЕ копирования экипировки, чтобы перекрыть пустой офф-хенд оригинала.
        if (holdTotem.getValue()) {
            entity.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
        entity.setHealth(entity.getMaxHealth());

        mc.world.addEntity(entity);
        this.fake = entity;
        this.lifeTicks = 0;

        ChatUtils.sendMessage("FakePlayer: клон установлен (виден только тебе).");
    }

    private void despawn() {
        if (fake != null) {
            fake.discard();
            if (mc.world != null) {
                mc.world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
        fake = null;
        anchorWorld = null;
        lifeTicks = 0;
    }

    private int nextClientEntityId() {
        int id = nextClientEntityId;
        if (nextClientEntityId == Integer.MAX_VALUE) {
            nextClientEntityId = Integer.MIN_VALUE;
        } else {
            nextClientEntityId++;
        }
        return id;
    }

    /** Клиентский клон: копирует скин настоящего игрока и не толкается, чтобы не мешать уходу. */
    private static final class FakeEntity extends OtherClientPlayerEntity {
        private final SkinTextures skin;

        private FakeEntity(ClientWorld world, GameProfile profile, SkinTextures skin) {
            super(world, profile);
            this.skin = skin;
        }

        @Override
        public SkinTextures getSkin() {
            return skin != null ? skin : super.getSkin();
        }

        @Override
        public boolean isPushable() {
            return false;
        }
    }
}
