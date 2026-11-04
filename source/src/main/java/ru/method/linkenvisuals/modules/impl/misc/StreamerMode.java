package ru.method.linkenvisuals.modules.impl.misc;

import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.StringSetting;

public class StreamerMode extends Module {

    private final BooleanSetting hideAnarchy = new BooleanSetting("settings.streamermode.hide_anarchy", true);
    private final StringSetting customName = new StringSetting("settings.streamermode.name", "", "settings.streamermode.name.placeholder", "", 20, null);
    private final StringSetting customDonate = new StringSetting("settings.streamermode.donate", "", "settings.streamermode.donate.placeholder", "", 20, null) {
        @Override
        public void setValue(String newValue) {
            super.setValue(stripObfuscatedFormatting(newValue));
        }
    };
    private final StringSetting customCoins = new StringSetting("settings.streamermode.coins", "", "settings.streamermode.coins.placeholder", "", 16, null);
    private final StringSetting friendNicknames = new StringSetting(
            "settings.streamermode.friend_nicks",
            "",
            "settings.streamermode.friend_nicks.placeholder",
            "",
            256,
            null
    );

    public StreamerMode() {
        super("StreamerMode", Category.Other);
        getSettings().add(hideAnarchy);
        getSettings().add(customName);
        getSettings().add(customDonate);
        getSettings().add(customCoins);
        getSettings().add(friendNicknames);
    }

    public boolean shouldHideAnarchy() {
        return hideAnarchy.getValue();
    }

    public String getCustomName() {
        return customName.getValue();
    }

    public String getCustomDonate() {
        return customDonate.getValue();
    }

    public String getCustomCoins() {
        return customCoins.getValue();
    }

    public String getFriendNicknames() {
        return friendNicknames.getValue();
    }

    private static String stripObfuscatedFormatting(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < input.length()
                    && Character.toLowerCase(input.charAt(i + 1)) == 'k') {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
