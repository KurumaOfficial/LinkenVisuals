package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.modules.impl.render.AllSkins;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void linkenvisuals$allSkinsOverride(CallbackInfoReturnable<SkinTextures> cir) {
        Identifier override = AllSkins.skinOverride();
        if (override != null) {
            SkinTextures orig = cir.getReturnValue();
            if (orig != null) {
                cir.setReturnValue(new SkinTextures(
                        linkenvisuals$createAsset(override),
                        orig.cape(),
                        orig.elytra(),
                        orig.model(),
                        orig.secure()
                ));
            }
        }
    }

    private static AssetInfo.TextureAsset linkenvisuals$createAsset(Identifier id) {
        return new AssetInfo.TextureAsset() {
            @Override
            public Identifier id() {
                return id;
            }

            @Override
            public Identifier texturePath() {
                return id;
            }
        };
    }
}
