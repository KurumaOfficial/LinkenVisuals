package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.api.events.impl.EventMouseScroll;
import ru.method.linkenvisuals.managers.system.HudManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.modules.impl.render.Freelook;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    public void onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        int button = input.button();
        EventMouse event = new EventMouse(button, action);
        LinkenVisuals linkenVisuals = LinkenVisuals.getInstance();
        if (linkenVisuals != null) {
            HudManager hudManager = linkenVisuals.getHudManager();
            if (hudManager != null && hudManager.tryDynamicIslandClick(event)) {
                ci.cancel();
                return;
            }
            linkenVisuals.getEventHandler().post(event);
        }

        Freelook freelook = linkenVisuals != null ? linkenVisuals.getModuleManager().getModule(Freelook.class) : null;
        if (freelook != null) {
            freelook.handleMouseInput(button, action);
        }

        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    public void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        EventMouseScroll event = new EventMouseScroll(horizontal, vertical);
        LinkenVisuals.getInstance().getEventHandler().post(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void linkenvisuals$captureFreelookRotation(CallbackInfo ci) {
        Freelook freelook = LinkenVisuals.getInstance().getModuleManager().getModule(Freelook.class);
        ClientPlayerEntity player = client.player;
        if (freelook == null || player == null) {
            return;
        }

        freelook.captureMouseUpdate(player);
    }

    @Inject(method = "updateMouse", at = @At("RETURN"))
    private void linkenvisuals$restoreFreelookRotation(CallbackInfo ci) {
        Freelook freelook = LinkenVisuals.getInstance().getModuleManager().getModule(Freelook.class);
        ClientPlayerEntity player = client.player;
        if (freelook == null || player == null) {
            return;
        }

        freelook.releaseMouseUpdate(player);
    }
}
