package ru.method.linkenvisuals.utils.text.streamer;

public final class StreamerModeState {

    public static final int DONATE_COLOR = 0xFF0000;
    public static final int DONATE_BRACKET_COLOR = 0xFFFFFF;
    public static final int DEFAULT_COINS_COLOR = 0xFFFF55;
    public static final int GRIEF_COINS_COLOR = 0xFB8808;
    public static final int CHAT_DONATE_DEBUG_LIMIT = 160;

    private static volatile ScoreboardServerType scoreboardServerType = ScoreboardServerType.UNKNOWN;
    private static volatile boolean tabHasAnarchy;
    private static int chatHudRenderDepth;
    private static int textFieldRenderDepth;
    private static int selfNametagRenderDepth;
    private static int chatDonateDebugLines;

    private StreamerModeState() {
    }

    public static ScoreboardServerType scoreboardServerType() {
        return scoreboardServerType;
    }

    public static void setScoreboardServerType(ScoreboardServerType type) {
        scoreboardServerType = type;
    }

    public static boolean tabHasAnarchy() {
        return tabHasAnarchy;
    }

    public static void setTabHasAnarchy(boolean value) {
        tabHasAnarchy = value;
    }

    public static void incrementChatHudRenderDepth() {
        chatHudRenderDepth++;
    }

    public static void decrementChatHudRenderDepth() {
        if (chatHudRenderDepth > 0) {
            chatHudRenderDepth--;
        }
    }

    public static void incrementTextFieldRenderDepth() {
        textFieldRenderDepth++;
    }

    public static void decrementTextFieldRenderDepth() {
        if (textFieldRenderDepth > 0) {
            textFieldRenderDepth--;
        }
    }

    public static void incrementSelfNametagRenderDepth() {
        selfNametagRenderDepth++;
    }

    public static void decrementSelfNametagRenderDepth() {
        if (selfNametagRenderDepth > 0) {
            selfNametagRenderDepth--;
        }
    }

    public static boolean shouldBypassDrawSanitization() {
        return chatHudRenderDepth > 0 || textFieldRenderDepth > 0;
    }

    public static boolean isSelfNametagRenderActive() {
        return selfNametagRenderDepth > 0;
    }

    public static int chatDonateDebugLines() {
        return chatDonateDebugLines;
    }

    public static void incrementChatDonateDebugLines() {
        chatDonateDebugLines++;
    }

    public enum ScoreboardServerType {
        UNKNOWN,
        ANARCHY,
        GRIEF
    }
}
