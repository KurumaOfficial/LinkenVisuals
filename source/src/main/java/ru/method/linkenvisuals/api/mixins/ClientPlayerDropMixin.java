package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.misc.LockSlot;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerDropMixin {

    // Q / Ctrl+Q выбрасывают предмет из ВЫБРАННОГО слота хотбара. Если этот слот залочен в LockSlot —
    // отменяем выброс (остальные действия с предметом не затрагиваются).
    // Выброс призрачного талисмана из SecretDupe: ванильный дроп отменяем, чтобы на сервер не ушёл пакет
    // о слоте, в котором у него лежит что-то своё. Предмет кладётся на землю клиентской сущностью.
    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$fakeCosmeticDrop(boolean dropEntireStack, CallbackInfoReturnable<Boolean> cir) {
        if (ru.method.linkenvisuals.modules.impl.misc.FakeCosmetic.handleDrop(dropEntireStack)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$lockSlotDrop(boolean dropEntireStack, CallbackInfoReturnable<Boolean> cir) {
        LockSlot lockSlot = LinkenVisuals.getInstance().getModuleManager().getModule(LockSlot.class);
        if (lockSlot == null || !lockSlot.isToggled()) {
            return;
        }
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (self.getInventory() != null && lockSlot.isDropLocked(self.getInventory().getSelectedSlot())) {
            cir.setReturnValue(false);
        }
    }
}
