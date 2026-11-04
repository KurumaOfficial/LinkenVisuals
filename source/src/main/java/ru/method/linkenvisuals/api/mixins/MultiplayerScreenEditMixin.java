package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.utils.server.MultiplayerScreenHelper;
import ru.method.linkenvisuals.utils.server.ServerManager;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenEditMixin {

    @Shadow
    protected MultiplayerServerListWidget serverListWidget;

    @Inject(method = "editEntry", at = @At("HEAD"), cancellable = true)
    private void onEdit(CallbackInfo ci) {
        if (isProtectedSelection()) {
            ci.cancel();
        }
    }

    @Inject(method = "removeEntry", at = @At("HEAD"), cancellable = true)
    private void onRemoveEntry(CallbackInfo ci) {
        if (isProtectedSelection()) {
            ci.cancel();
        }
    }

    private boolean isProtectedSelection() {
        MultiplayerServerListWidget.Entry entry = serverListWidget.getSelectedOrNull();
        ServerInfo server = MultiplayerScreenHelper.getServer(entry);
        return server != null && ServerManager.isProtectedServer(server);
    }
}
