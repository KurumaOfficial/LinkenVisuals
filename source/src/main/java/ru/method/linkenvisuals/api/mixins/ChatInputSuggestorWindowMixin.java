package ru.method.linkenvisuals.api.mixins;

import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.util.math.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;

import java.util.List;

@Mixin(ChatInputSuggestor.SuggestionWindow.class)
public abstract class ChatInputSuggestorWindowMixin {

    @Mutable
    @Final
    @Shadow
    private Rect2i area;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void linkenvisuals$resizeForSanitizedSuggestions(ChatInputSuggestor owner, int x, int y, int width, List<Suggestion> suggestions, boolean narrateFirstSuggestion, CallbackInfo ci) {
        if (area == null || suggestions == null || suggestions.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;

        int maxWidth = area.getWidth();
        for (Suggestion suggestion : suggestions) {
            if (suggestion == null) continue;
            String text = suggestion.getText();
            String sanitized = StreamerModeUtils.sanitizeString(text);
            if (sanitized == null || sanitized.equals(text)) continue;
            maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(sanitized));
        }

        if (maxWidth > area.getWidth()) {
            area = new Rect2i(area.getX(), area.getY(), maxWidth, area.getHeight());
        }
    }
}
