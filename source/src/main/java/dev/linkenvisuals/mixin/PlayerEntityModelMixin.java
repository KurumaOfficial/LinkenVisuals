package dev.linkenvisuals.mixin;

import dev.linkenvisuals.cosmetic.character.JasonCharacter;
import dev.linkenvisuals.cosmetic.character.RobloxBaconCharacter;
import dev.linkenvisuals.cosmetic.character.RudyCharacter;
import dev.linkenvisuals.render.state.RudyRenderState;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Character cosmetics: mirrors the render state selection onto the shared model and applies
 * custom character attachment pivots and animations after the vanilla pose.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Unique
    private boolean linkenvisuals$rudy;

    @Unique
    private String linkenvisuals$character = "Default";

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
        at = @At("HEAD"))
    private void linkenvisuals$characterFlag(PlayerEntityRenderState state, CallbackInfo ci) {
        if (state instanceof RudyRenderState custom) {
            linkenvisuals$character = custom.linkenvisuals$getCustomCharacter();
            linkenvisuals$rudy = "Rudy".equals(linkenvisuals$character) || custom.linkenvisuals$isRudy();
        } else {
            linkenvisuals$character = "Default";
            linkenvisuals$rudy = false;
        }
    }

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
        at = @At("TAIL"))
    private void linkenvisuals$customPose(PlayerEntityRenderState state, CallbackInfo ci) {
        PlayerEntityModel self = (PlayerEntityModel) (Object) this;
        if ("Rudy".equals(linkenvisuals$character) || linkenvisuals$rudy) {
            RudyCharacter.pose(self, state.sneaking);
        } else if ("Roblox Bacon".equals(linkenvisuals$character)) {
            RobloxBaconCharacter.pose(self, state);
        } else if ("Jason GTA 6".equals(linkenvisuals$character)) {
            JasonCharacter.pose(self, state);
        }
    }

    @Inject(method = "setArmAngle(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;"
        + "Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;)V",
        at = @At("TAIL"))
    private void linkenvisuals$customGrip(PlayerEntityRenderState state, Arm arm, MatrixStack matrices, CallbackInfo ci) {
        if ("Rudy".equals(linkenvisuals$character) || linkenvisuals$rudy) {
            RudyCharacter.armGrip(arm, matrices);
        } else if ("Roblox Bacon".equals(linkenvisuals$character)) {
            RobloxBaconCharacter.armGrip(arm, matrices);
        } else if ("Jason GTA 6".equals(linkenvisuals$character)) {
            JasonCharacter.armGrip(arm, matrices);
        }
    }
}
