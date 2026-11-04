package ru.method.linkenvisuals.api.mixins;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.utils.debug.TabProfiler;
import ru.method.linkenvisuals.utils.text.SyncedUserTextUtils;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {

    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void linkenvisuals$sanitizeDisplayNameGet(CallbackInfoReturnable<Text> cir) {
        if (TabProfiler.isActive()) {
            return;
        }
        GameProfile profile = getProfile();
        Text sanitized = ru.method.linkenvisuals.utils.text.StreamerModeUtils.sanitizeTabName(
                cir.getReturnValue(),
                profile == null ? null : profile.name()
        );
        cir.setReturnValue(SyncedUserTextUtils.applyBadgeForPlayerSanitized(
                sanitized,
                profile == null ? null : profile.id(),
                profile == null ? null : profile.name()
        ));
    }
}
