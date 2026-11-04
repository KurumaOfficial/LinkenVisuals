package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventAttackEntity;
import ru.method.linkenvisuals.api.events.impl.EventStartBreakBlock;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    /**
     * Клик по слоту с призрачным предметом SecretDupe. Ванильный вызов уходит пакетом на сервер, тот видит
     * в слоте своё содержимое и отвечает ресинком всего инвентаря — призрак стирался прямо под курсором.
     * Модуль применяет клик локально, поэтому здесь просто отменяем отправку.
     */
    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    public void linkenvisuals$fakeCosmeticClickSlot(int syncId, int slotId, int button,
                                                    net.minecraft.screen.slot.SlotActionType actionType,
                                                    PlayerEntity player, CallbackInfo ci) {
        if (ru.method.linkenvisuals.modules.impl.misc.FakeCosmetic.handleSlotClick(slotId, button, actionType, player)) {
            ci.cancel();
        }
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    public void attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        EventAttackEntity event = new EventAttackEntity(player, target);
        LinkenVisuals.getInstance().getEventHandler().post(event);
        if (event.isCancelled()) {
            ci.cancel();
            return;
        }
        // The Strange hit/kill systems are static and event-driven; the legacy
        // attack mixin is the one registered in production, so feed them here.
        dev.linkenvisuals.player.KillEffects.watch(target);
        dev.linkenvisuals.player.TargetMarks.noteHit(target);
        dev.linkenvisuals.player.HitEffects.add(target, player.getAttackCooldownProgress(0.5f), null);
        dev.linkenvisuals.render.DamageParticlesRenderer.onAttack(target);
        dev.linkenvisuals.render.FortniteDamageRenderer.onAttack(target, false);
    }

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    public void attackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        EventStartBreakBlock event = new EventStartBreakBlock(pos, direction);
        LinkenVisuals.getInstance().getEventHandler().post(event);
        if (event.isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Продолжение ломания при ЗАЖАТОЙ кнопке идёт здесь, каждый тик (attackBlock — только первый удар).
     * Без перехвата MineHelper отменял лишь старт, а зажатое ломание всё равно доламывало блок и тратило
     * последнюю прочность инструмента. Постим то же событие — модуль так же может отменить.
     */
    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    public void updateBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        EventStartBreakBlock event = new EventStartBreakBlock(pos, direction);
        LinkenVisuals.getInstance().getEventHandler().post(event);
        if (event.isCancelled()) {
            cir.setReturnValue(false);
        }
    }
}
