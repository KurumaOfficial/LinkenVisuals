package dev.linkenvisuals.mixin;

import dev.linkenvisuals.clickgui.ModuleRegistry;
import dev.linkenvisuals.cosmetic.character.JasonCharacter;
import dev.linkenvisuals.cosmetic.character.RobloxBaconCharacter;
import dev.linkenvisuals.cosmetic.character.RudyCharacter;
import dev.linkenvisuals.render.state.RudyRenderState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Character cosmetics: flags the local player's render state when the Cosmetics module has
 * "Rudy", "Roblox Bacon", or "Jason GTA 6" selected.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;"
        + "Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
        at = @At("TAIL"))
    private void linkenvisuals$customCharacter(PlayerLikeEntity entity, PlayerEntityRenderState state,
                                               float tickDelta, CallbackInfo ci) {
        boolean isLocal = entity == MinecraftClient.getInstance().player && ModuleRegistry.cosmetics().enabled();
        String selected = isLocal ? ModuleRegistry.cosmeticSlot("character") : "Default";

        boolean rudy = false;
        String custom = "Default";
        if ("Rudy".equals(selected) && RudyCharacter.available()) {
            rudy = true;
            custom = "Rudy";
        } else if ("Roblox Bacon".equals(selected) && RobloxBaconCharacter.available()) {
            custom = "Roblox Bacon";
        } else if ("Jason GTA 6".equals(selected) && JasonCharacter.available()) {
            custom = "Jason GTA 6";
        } else if ("Astolfo".equals(selected) && dev.linkenvisuals.cosmetic.character.AstolfoMesh.available()) {
            custom = "Astolfo";
        }

        RudyRenderState customState = (RudyRenderState) state;
        customState.linkenvisuals$setRudy(rudy);
        customState.linkenvisuals$setCustomCharacter(custom);

        boolean airborne = !entity.isOnGround() && !entity.isGliding() && !entity.isTouchingWater()
                && Math.abs(entity.getVelocity().y) > 0.04;
        customState.linkenvisuals$setAirborne(airborne);
    }
}
