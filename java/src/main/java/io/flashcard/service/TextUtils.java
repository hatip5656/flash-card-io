package io.flashcard.service;

public final class TextUtils {

    private TextUtils() {}

    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static String streakEmoji(int streak) {
        return streak >= 7 ? "\uD83D\uDD25" : streak >= 3 ? "\u26A1" : "\uD83D\uDCC5";
    }
}
