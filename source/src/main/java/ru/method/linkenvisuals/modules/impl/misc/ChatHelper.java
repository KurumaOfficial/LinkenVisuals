package ru.method.linkenvisuals.modules.impl.misc;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.utils.render.ColorUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChatHelper extends Module {
    private static ChatHelper INSTANCE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final BooleanSetting chatHistory = new BooleanSetting("modules.settings.chat_helper.chat_history", true);
    private final BooleanSetting antiSpam = new BooleanSetting("modules.settings.chat_helper.anti_spam", true);
    private final BooleanSetting showTime = new BooleanSetting("modules.settings.chat_helper.show_time", false);

    public ChatHelper() {
        super("ChatHelper", Category.Player);
        INSTANCE = this;
    }

    public static boolean isChatHistoryEnabled() {
        return INSTANCE != null && INSTANCE.isToggled() && INSTANCE.chatHistory.getValue();
    }

    public static boolean isAntiSpamEnabled() {
        return INSTANCE != null && INSTANCE.isToggled() && INSTANCE.antiSpam.getValue();
    }

    public static boolean isShowTimeEnabled() {
        return INSTANCE != null && INSTANCE.isToggled() && INSTANCE.showTime.getValue();
    }

    public static Text applyTimePrefix(Text message) {
        if (!isShowTimeEnabled() || message == null) return message;
        String time = "[" + LocalTime.now().format(TIME_FORMAT) + "] ";
        int color = ColorUtils.getPrimaryColor().getRGB() & 0xFFFFFF;
        MutableText prefix = Text.literal(time).styled(style -> style.withColor(color));
        return Text.empty().append(prefix).append(message.copy());
    }
}
