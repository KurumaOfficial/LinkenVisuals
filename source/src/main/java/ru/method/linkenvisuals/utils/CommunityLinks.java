package ru.method.linkenvisuals.utils;

/**
 * Централизованные ссылки сообщества LinkenVisuals.
 * Единый источник правды: раньше эти URL дублировались в CustomMainMenuScreen,
 * LinkenVisuals и DiscordManager с расходящимися slug'ами.
 * Эталон — актуальные рабочие адреса из главного меню.
 */
public final class CommunityLinks {

    /** Discord-сервер сообщества. */
    public static final String DISCORD_URL = "https://dsc.gg/linkenvisuals";
    /** Основной Telegram-канал. */
    public static final String TELEGRAM_URL = "https://t.me/linkenvisuals";
    /** Официальный сайт. */
    public static final String SITE_URL = "https://linkenvisuals.ru";
    /** Дев-канал (тестовые сборки/новости разработки). */
    public static final String DEV_URL = "https://t.me/linkenvisualsdev";

    private CommunityLinks() {
        // Утилитарный класс констант — инстанцирование запрещено.
    }
}
