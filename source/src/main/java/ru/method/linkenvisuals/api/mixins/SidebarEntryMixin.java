package ru.method.linkenvisuals.api.mixins;

import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.utils.text.SyncedUserTextUtils;

@Mixin(targets = "net.minecraft.client.gui.hud.InGameHud$SidebarEntry")
public class SidebarEntryMixin {

    @Inject(method = "name", at = @At("RETURN"), cancellable = true)
    private void linkenvisuals$sanitizeSidebarName(CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(SyncedUserTextUtils.applyBadgeForName(cir.getReturnValue()));
    }

    @Inject(method = "score", at = @At("RETURN"), cancellable = true)
    private void linkenvisuals$sanitizeSidebarScore(CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(SyncedUserTextUtils.applyBadgeForName(cir.getReturnValue()));
    }
}
