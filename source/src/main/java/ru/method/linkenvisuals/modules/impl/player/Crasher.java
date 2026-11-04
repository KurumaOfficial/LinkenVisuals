package ru.method.linkenvisuals.modules.impl.player;

import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.api.events.impl.EventStartBreakBlock;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.api.mixins.accessors.IClientPlayNetworkHandler;
import ru.method.linkenvisuals.api.mixins.accessors.IEntityRemoval;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.player.crasher.CrasherChatSpoof;
import ru.method.linkenvisuals.modules.impl.player.crasher.CrasherCommands;
import ru.method.linkenvisuals.modules.impl.player.crasher.CrasherMsg;
import ru.method.linkenvisuals.modules.impl.player.crasher.CrasherPhysics;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * «Призрак»-режим: пока модуль включён, на сервер не уходит ни одного пакета движения и
 * взаимодействия — для сервера игрок стоит там, где включил модуль, — а всё, что он ломает,
 * строит, куда идёт и кого «кикает», существует только в его собственном клиенте.
 *
 * <p>Фейковые команды ({@code /op}, {@code /gm}, {@code /kickall}, {@code /broadcast},
 * {@code //wand}, {@code //set}, {@code //sphere}, {@code //hsphere}, {@code //undo}) перехватываются
 * ровно тогда, когда модуль включён, и на сервер не уходят. Отдельной команды включения нет —
 * режим включается и выключается самим модулем (карточка в GUI или бинд).</p>
 *
 * <p>Всё состояние копится при включении и полностью откатывается при выключении: изменённые блоки
 * возвращаются, спрятанные игроки возвращаются в мир и в таб, топорик — обратно в свой слот,
 * гейммод/полёт/позиция восстанавливаются.</p>
 */
public class Crasher extends Module {

    public enum FakeGameMode implements Nameable {
        None("None"),
        Survival("Survival"),
        Creative("Creative"),
        Adventure("Adventure"),
        Spectator("Spectator");

        private final String name;

        FakeGameMode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private final BooleanSetting freezePackets = new BooleanSetting("settings.crasher.freeze_packets", true);
    private final BooleanSetting fakeCommands = new BooleanSetting("settings.crasher.fake_commands", true);
    private final BooleanSetting flight = new BooleanSetting("settings.crasher.flight", true);
    private final EnumSetting<FakeGameMode> gameMode = new EnumSetting<>("settings.crasher.gamemode", FakeGameMode.Creative);
    private final BooleanSetting hidePlayers = new BooleanSetting("settings.crasher.hide_players", false);
    private final BooleanSetting restoreBlocks = new BooleanSetting("settings.crasher.restore_blocks", true);
    private final BooleanSetting fakePhysics = new BooleanSetting("settings.crasher.fake_physics", true);
    private final BooleanSetting restorePosition = new BooleanSetting("settings.crasher.restore_position", true);
    private final BooleanSetting fakeChat = new BooleanSetting("settings.crasher.fake_chat", true);

    /** Точка входа в режим — сюда возвращаемся при выключении (сервер всё это время видел нас тут). */
    private Vec3d anchorPos;
    private float anchorYaw, anchorPitch;
    private ClientWorld anchorWorld;

    /** Оригиналы изменённых локально блоков (первое значение на позицию). */
    private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();

    /** Спрятанные игроки: сущности мира и записи таб-листа. */
    private final List<Entity> hiddenEntities = new ArrayList<>();
    private final Map<UUID, PlayerListEntry> hiddenTabEntries = new LinkedHashMap<>();

    private GameMode previousGameMode;
    private boolean previousAllowFlying;
    private boolean previousFlying;
    private boolean fakeOp;

    /** Слот, куда положили фейковый топорик, и то, что там лежало до этого. */
    private int wandSlot = -1;
    private ItemStack wandOriginalStack = ItemStack.EMPTY;

    private final CrasherPhysics physics = new CrasherPhysics(this);
    private final CrasherCommands commands = new CrasherCommands(this);
    private final CrasherChatSpoof chatSpoof = new CrasherChatSpoof();

    public Crasher() {
        super("Crasher", Category.Player);
        getSettings().add(freezePackets);
        getSettings().add(fakeCommands);
        getSettings().add(flight);
        getSettings().add(gameMode);
        getSettings().add(hidePlayers);
        getSettings().add(restoreBlocks);
        getSettings().add(fakePhysics);
        getSettings().add(restorePosition);
        getSettings().add(fakeChat);

        // Топорик выделения: ЛКМ — pos1, ПКМ — pos2. Клики до сервера не доходят.
        // Fabric-коллбэки регистрируются один раз, вся логика гейтится состоянием модуля.
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (isToggle() && world.isClient() && isHoldingWand()) {
                commands.setPos1(pos);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (isToggle() && world.isClient() && hand == Hand.MAIN_HAND && isHoldingWand()) {
                commands.setPos2(hitResult.getBlockPos());
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    @Override
    public void onEnable() {
        super.onEnable();

        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) {
            return;
        }

        anchorWorld = mc.world;
        anchorPos = player.getEntityPos();
        anchorYaw = player.getYaw();
        anchorPitch = player.getPitch();
        fakeOp = false;

        previousGameMode = mc.interactionManager != null ? mc.interactionManager.getCurrentGameMode() : GameMode.SURVIVAL;
        previousAllowFlying = player.getAbilities().allowFlying;
        previousFlying = player.getAbilities().flying;

        if (flight.getValue()) {
            player.getAbilities().allowFlying = true;
        }

        GameMode fake = toVanilla(gameMode.getValue());
        if (fake != null && mc.interactionManager != null) {
            mc.interactionManager.setGameMode(fake);
            boolean allowFlying = fake == GameMode.CREATIVE || fake == GameMode.SPECTATOR || flight.getValue();
            player.getAbilities().allowFlying = allowFlying;
            if (!allowFlying) {
                player.getAbilities().flying = false;
            }
        }

        if (hidePlayers.getValue()) {
            hideRealPlayers();
        }

        CrasherMsg.send(CrasherMsg.lit("[Crasher] ", net.minecraft.util.Formatting.DARK_GRAY)
                .append(CrasherMsg.lit("Режим ", net.minecraft.util.Formatting.GRAY))
                .append(CrasherMsg.lit("ON", net.minecraft.util.Formatting.GREEN, net.minecraft.util.Formatting.BOLD))
                .append(CrasherMsg.lit(" — сервер видит тебя на месте.", net.minecraft.util.Formatting.GRAY)));
    }

    @Override
    public void onDisable() {
        rollback(true);
        super.onDisable();

        CrasherMsg.send(CrasherMsg.lit("[Crasher] ", net.minecraft.util.Formatting.DARK_GRAY)
                .append(CrasherMsg.lit("Режим ", net.minecraft.util.Formatting.GRAY))
                .append(CrasherMsg.lit("OFF", net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD))
                .append(CrasherMsg.lit(" — всё откачено, ты на исходной точке.", net.minecraft.util.Formatting.GRAY)));
    }

    /** Сброс состояния. {@code restore == false} — мир уже пропал (дисконнект/смена измерения). */
    private void rollback(boolean restore) {
        ClientPlayerEntity player = mc.player;
        boolean sameWorld = mc.world != null && mc.world == anchorWorld;

        if (restore && sameWorld) {
            restoreBlocks();
            restorePlayers();
            restoreWand();
        } else {
            originalBlocks.clear();
            hiddenEntities.clear();
            hiddenTabEntries.clear();
            wandSlot = -1;
            wandOriginalStack = ItemStack.EMPTY;
        }
        physics.clear();
        chatSpoof.clear();
        commands.clearSelection();

        if (restore && player != null) {
            if (mc.interactionManager != null && previousGameMode != null) {
                mc.interactionManager.setGameMode(previousGameMode);
            }
            player.getAbilities().allowFlying = previousAllowFlying;
            player.getAbilities().flying = previousFlying;
            player.sendAbilitiesUpdate();

            if (restorePosition.getValue() && anchorPos != null && sameWorld) {
                player.setVelocity(Vec3d.ZERO);
                player.fallDistance = 0.0F;
                player.refreshPositionAndAngles(anchorPos.x, anchorPos.y, anchorPos.z, anchorYaw, anchorPitch);
                player.setYaw(anchorYaw);
                player.setPitch(anchorPitch);
                player.lastX = anchorPos.x;
                player.lastY = anchorPos.y;
                player.lastZ = anchorPos.z;
                player.lastRenderX = anchorPos.x;
                player.lastRenderY = anchorPos.y;
                player.lastRenderZ = anchorPos.z;
            }
        }

        anchorPos = null;
        anchorWorld = null;
        previousGameMode = null;
        fakeOp = false;
    }

    // ------------------------------------------------------------------ события

    /**
     * Пока модуль включён, сервер не получает ни одного пакета движения / взаимодействия —
     * для него игрок стоит на месте и ничего не делает. Здесь же перехватываются фейковые команды:
     * клиент отправляет их {@link CommandExecutionC2SPacket}-ом, мы его гасим и выполняем локально.
     */
    @EventHandler
    private void onPacketSend(EventPacket.Send event) {
        Packet<?> packet = event.getPacket();

        if (fakeCommands.getValue()) {
            String command = null;
            if (packet instanceof CommandExecutionC2SPacket execution) {
                command = execution.command();
            } else if (packet instanceof ChatMessageC2SPacket chat && chat.chatMessage().startsWith("/")) {
                // На всякий случай: если строка ушла как обычное сообщение, а не как команда.
                command = chat.chatMessage().substring(1);
            }

            if (command != null && commands.handle(command)) {
                event.cancel();
                return;
            }
        }

        if (!freezePackets.getValue()) return;

        if (packet instanceof PlayerMoveC2SPacket
                || packet instanceof VehicleMoveC2SPacket
                || packet instanceof PlayerInputC2SPacket
                || packet instanceof ClientCommandC2SPacket
                || packet instanceof HandSwingC2SPacket
                || packet instanceof PlayerActionC2SPacket
                || packet instanceof PlayerInteractBlockC2SPacket) {
            event.cancel();
        }
    }

    /** Ломаемые блоки исчезают только локально — запоминаем оригинал, чтобы вернуть при выключении. */
    @EventHandler
    private void onStartBreakBlock(EventStartBreakBlock event) {
        if (!restoreBlocks.getValue() || fullNullCheck()) return;
        rememberBlock(event.getPos());
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) {
            rollback(false);
            return;
        }
        if (anchorWorld != null && mc.world != anchorWorld) {
            // Сменился мир — откатывать нечего и негде, просто чистим состояние и переякориваемся.
            rollback(false);
            anchorWorld = mc.world;
            anchorPos = mc.player.getEntityPos();
            anchorYaw = mc.player.getYaw();
            anchorPitch = mc.player.getPitch();
            return;
        }
        chatSpoof.tick();

        if (!fakePhysics.getValue()) {
            physics.clear();
            return;
        }
        for (PlayerEntity p : new ArrayList<>(mc.world.getPlayers())) {
            if (p instanceof OtherClientPlayerEntity other) {
                physics.tick(other);
            }
        }
    }

    // ------------------------------------------------------------------- блоки

    private void rememberBlock(BlockPos pos) {
        ClientWorld world = mc.world;
        if (world == null) return;
        BlockPos immutable = pos.toImmutable();
        originalBlocks.putIfAbsent(immutable, world.getBlockState(immutable));
    }

    /** Ставит блок только на клиенте, запоминая оригинал для отката. */
    public void setFakeBlock(BlockPos pos, BlockState state) {
        ClientWorld world = mc.world;
        if (world == null) return;
        BlockPos immutable = pos.toImmutable();
        rememberBlock(immutable);
        world.setBlockState(immutable, state, 3);
    }

    private void restoreBlocks() {
        ClientWorld world = mc.world;
        if (world != null) {
            for (Map.Entry<BlockPos, BlockState> e : originalBlocks.entrySet()) {
                BlockState state = e.getValue();
                world.setBlockState(e.getKey(), state == null ? Blocks.AIR.getDefaultState() : state, 3);
            }
        }
        originalBlocks.clear();
    }

    /**
     * Вызывается после массовой правки мира ({@code //set}, {@code //sphere}): через 5–7 секунд
     * реальные игроки сервера «отреагируют» в чате на лаги. Только локально.
     */
    public void scheduleFakeChatReaction() {
        if (fakeChat.getValue()) {
            chatSpoof.schedule();
        }
    }

    public int pendingEdits() {
        return originalBlocks.size();
    }

    // ------------------------------------------------------------------ топорик

    /** Кладёт в руку деревянный топорик — только визуально, инвентарь сервера не трогаем. */
    public void giveWand() {
        ClientPlayerEntity player = mc.player;
        if (!isToggle() || player == null) return;

        int slot = player.getInventory().getSelectedSlot();
        if (wandSlot < 0) {
            wandSlot = slot;
            wandOriginalStack = player.getInventory().getStack(slot).copy();
        }
        player.getInventory().setStack(slot, new ItemStack(Items.WOODEN_AXE));
    }

    public boolean isHoldingWand() {
        return isToggle() && mc.player != null && mc.player.getMainHandStack().isOf(Items.WOODEN_AXE);
    }

    private void restoreWand() {
        ClientPlayerEntity player = mc.player;
        if (player != null && wandSlot >= 0) {
            player.getInventory().setStack(wandSlot, wandOriginalStack);
        }
        wandSlot = -1;
        wandOriginalStack = ItemStack.EMPTY;
    }

    public void setFakeOp(boolean value) {
        fakeOp = value;
    }

    public boolean isFakeOp() {
        return isToggle() && fakeOp;
    }

    // ------------------------------------------------------------------ игроки

    /**
     * Прячет с клиента всех реальных игроков (тех, кто есть в таб-листе).
     * NPC-сущности, которых нет в таб-листе, остаются на месте.
     */
    public int hideRealPlayers() {
        ClientWorld world = mc.world;
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (world == null || handler == null || mc.player == null) {
            return 0;
        }

        Map<UUID, PlayerListEntry> tab = ((IClientPlayNetworkHandler) handler).linkenvisuals$getPlayerListEntries();

        for (PlayerEntity p : new ArrayList<>(world.getPlayers())) {
            if (p == mc.player || !tab.containsKey(p.getUuid())) {
                continue;
            }
            hiddenEntities.add(p);
            world.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
        }

        for (UUID uuid : new ArrayList<>(tab.keySet())) {
            if (uuid.equals(mc.player.getUuid())) continue;
            hiddenTabEntries.put(uuid, tab.remove(uuid));
        }

        return hiddenTabEntries.size();
    }

    private void restorePlayers() {
        ClientWorld world = mc.world;
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();

        if (world != null) {
            for (Entity e : hiddenEntities) {
                ((IEntityRemoval) e).linkenvisuals$unsetRemoved();
                world.addEntity(e);
            }
        }
        hiddenEntities.clear();

        if (handler != null) {
            ((IClientPlayNetworkHandler) handler).linkenvisuals$getPlayerListEntries().putAll(hiddenTabEntries);
        }
        hiddenTabEntries.clear();
    }

    private static GameMode toVanilla(FakeGameMode mode) {
        return switch (mode) {
            case None -> null;
            case Survival -> GameMode.SURVIVAL;
            case Creative -> GameMode.CREATIVE;
            case Adventure -> GameMode.ADVENTURE;
            case Spectator -> GameMode.SPECTATOR;
        };
    }

    @Override
    public boolean isUnsafe() {
        return true;
    }
}
