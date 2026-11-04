package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.utils.server.MultiplayerScreenHelper;
import ru.method.linkenvisuals.utils.server.ServerManager;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenButtonMixin {

    @Shadow
    protected MultiplayerServerListWidget serverListWidget;

    @Shadow
    private ButtonWidget buttonEdit;

    @Shadow
    private ButtonWidget buttonDelete;

    @Inject(method = "updateButtonActivationStates", at = @At("TAIL"))
    private void onUpdateButtonStates(CallbackInfo ci) {
        MultiplayerServerListWidget.Entry entry = serverListWidget.getSelectedOrNull();
        ServerInfo server = MultiplayerScreenHelper.getServer(entry);
        if (server != null && ServerManager.isProtectedServer(server)) {
            buttonEdit.active = false;
            buttonDelete.active = false;
        }
    }
}
