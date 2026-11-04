package ru.method.linkenvisuals.api.mixins;

import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;

@Mixin(ScoreboardScore.class)
public class ScoreboardScoreMixin {

    @Inject(method = "getDisplayText", at = @At("RETURN"), cancellable = true)
    private void linkenvisuals$sanitizeDisplayText(CallbackInfoReturnable<Text> cir) {
        StreamerModeUtils.observeScoreboardText(cir.getReturnValue());
        cir.setReturnValue(StreamerModeUtils.sanitizeText(cir.getReturnValue()));
    }
}
