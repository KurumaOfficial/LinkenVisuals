package ru.method.linkenvisuals.utils.chat;

import java.awt.Color;

import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import lombok.experimental.UtilityClass;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

@UtilityClass
public class ChatUtils implements Wrapper {
    private static final String PREFIX_PLAIN = "LinkenVisuals > ";

    public void sendMessage(String message) {
        if (message == null) return;
        mc.execute(() -> {
            if (mc.player == null) return;
            MutableText text = buildPrefix();

            text.append(Text.literal(message)
                    .setStyle(Style.EMPTY
                            .withBold(false)
                            .withColor(TextColor.fromRgb(new Color(200, 200, 200).getRGB()))
                    )
            );

            mc.player.sendMessage(text, false);
        });
    }

    public void sendMessage(MutableText message) {
        if (message == null) return;
        mc.execute(() -> {
            if (mc.player == null) return;
            MutableText text = buildPrefix();
            text.append(message.copy());
            mc.player.sendMessage(text, false);
        });
    }

    public boolean isClientMessage(Text message) {
        return message != null && message.getString().startsWith(PREFIX_PLAIN);
    }

    private MutableText buildPrefix() {
        MutableText text = Text.literal("");
        String prefix = "LinkenVisuals";
        for (int i = 0; i < prefix.length(); i++) {
            text.append(Text.literal(prefix.charAt(i) + "")
                    .setStyle(Style.EMPTY
                            .withBold(true)
                            .withColor(TextColor.fromRgb(ColorUtils.gradient((float) i / prefix.length()).getRGB()))
                    )
            );
        }

        text.append(Text.literal(" > ")
                .setStyle(Style.EMPTY
                        .withBold(false)
                        .withColor(TextColor.fromRgb(new Color(200, 200, 200).getRGB()))
                )
        );
        return text;
    }
}
