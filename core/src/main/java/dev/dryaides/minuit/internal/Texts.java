package dev.dryaides.minuit.internal;

/** The text guards the text fields share. */
public final class Texts {

    private Texts() {
    }

    /** At most {@code max} UTF-16 chars of {@code value} (null reads as ""), never ending on half an emoji. */
    public static String clip(String value, int max) {
        String text = value == null ? "" : value;
        int end = Math.max(0, max);
        if (text.length() <= end) {
            return text;
        }
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }
}
