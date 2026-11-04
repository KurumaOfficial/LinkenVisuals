package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.screen.mainmenu.CustomMainMenuScreen;
import ru.method.linkenvisuals.utils.Wrapper;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen implements Wrapper {

    @Inject(method = "init", at = @At("RETURN"))
    private void linkenvisualss$redirectToCustom(CallbackInfo ci) {
        if (mc == null) {
            return;
        }
        if (mc.currentScreen instanceof CustomMainMenuScreen) {
            return;
        }
        // Defer until client init finishes — setScreen during MinecraftClient.<init>
        // resize can leave Screen.client null and crash setInitialFocus.
        mc.execute(() -> {
            Screen screen = mc.currentScreen;
            if (screen instanceof TitleScreen && !(screen instanceof CustomMainMenuScreen)) {
                mc.setScreen(new CustomMainMenuScreen());
            }
        });
    }
}
