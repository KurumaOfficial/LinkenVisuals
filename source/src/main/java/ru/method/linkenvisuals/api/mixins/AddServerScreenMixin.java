package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.utils.server.ServerManager;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AddServerScreen.class)
public class AddServerScreenMixin {

    @Shadow @Final
    private ServerInfo server;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (ServerManager.isProtectedServer(server)) {
            AddServerScreen screen = (AddServerScreen) (Object) this;
            screen.close();
        }
    }

    @Inject(method = "addAndClose", at = @At("HEAD"), cancellable = true)
    private void onAddAndClose(CallbackInfo ci) {
        if (ServerManager.isProtectedServer(server)) {
            ci.cancel();
        }
    }
}
