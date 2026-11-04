package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.modules.impl.client.UiKeyHandler;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.modules.impl.render.Freelook;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    public void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        int key = input.key();
        int scancode = input.scancode();
        int modifiers = input.modifiers();
        UiKeyHandler.tryHandleKey(key, scancode, action);

        // Бинды функций (EventKey) НЕ шлём, пока открыт чат — иначе набор текста в чате срабатывает как
        // action-бинды модулей (тумблер-бинды и так гейтятся по currentScreen в ModuleManager).
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc != null && mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) {
            return;
        }

        EventKey event = new EventKey(key, action, modifiers);
        LinkenVisuals.getInstance().getEventHandler().post(event);

        Freelook freelook = LinkenVisuals.getInstance().getModuleManager().getModule(Freelook.class);
        if (freelook == null) {
            return;
        }

        freelook.handleKeyboardInput(key, action);
        if (action != GLFW.GLFW_RELEASE && freelook.shouldSuppressVanillaPerspectiveKey(key, scancode)) {
            ci.cancel();
        }
    }
}
