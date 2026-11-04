package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import ru.method.linkenvisuals.LinkenVisuals;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.command.CommandSource;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;

import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {

    @Final @Shadow TextFieldWidget textField;
    @Shadow boolean completingSuggestions;
    @Shadow private ParseResults<CommandSource> parse;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private ChatInputSuggestor.SuggestionWindow window;
    @Shadow public abstract void show(boolean narrateFirstSuggestion);

    @Inject(method = "refresh", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;canRead()Z", remap = false), cancellable = true)
    public void refresh(CallbackInfo ci, @Local StringReader reader) {
        String prefix = LinkenVisuals.getInstance().getCommandManager().getPrefix();

        if (reader.canRead(prefix.length()) && reader.getString().startsWith(prefix, reader.getCursor()) && !LinkenVisuals.getInstance().isPanic()) {
            String fullInput = this.textField.getText();
            int commandStart = reader.getCursor() + prefix.length();
            String normalizedInput = LinkenVisuals.getInstance().getCommandManager()
                    .normalizeInput(fullInput.substring(commandStart));
            StringReader normalizedReader = new StringReader(prefix + normalizedInput);
            int cursor;
            CommandDispatcher<CommandSource> dispatcher = LinkenVisuals.getInstance().getCommandManager().getDispatcher();
            normalizedReader.setCursor(commandStart);
            this.parse = dispatcher.parse(normalizedReader, LinkenVisuals.getInstance().getCommandManager().getSource());
            if (!((cursor = this.textField.getCursor()) < commandStart || this.window != null && this.completingSuggestions)) {
                this.pendingSuggestions = dispatcher.getCompletionSuggestions(this.parse, cursor);
                this.pendingSuggestions.thenRun(() -> {
                    if (this.pendingSuggestions.isDone()) this.show(false);
                });
            }

            ci.cancel();
        }
    }

    @Inject(method = "provideRenderText", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$maskSensitiveRenderText(String text, int firstCharacterIndex, CallbackInfoReturnable<OrderedText> cir) {
        String maskedSegment = StreamerModeUtils.maskChatInputSegment(
                this.textField.getText(),
                firstCharacterIndex,
                text == null ? 0 : text.length()
        );
        if (maskedSegment != null) {
            cir.setReturnValue(OrderedText.styledForwardsVisitedString(maskedSegment, Style.EMPTY));
        }
    }

    @Inject(method = "show", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$hideSensitiveSuggestions(boolean narrateFirstSuggestion, CallbackInfo ci) {
        if (StreamerModeUtils.isSensitiveCommandStart(this.textField.getText())) {
            ci.cancel();
        }
    }
}
