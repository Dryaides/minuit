package dev.dryaides.minuit.form;

import java.util.List;

/** A string a player reads: a literal, or a translatable key with a fallback and a style role. */
public sealed interface Text {

    static Text lit(String text) {
        return new Lit(text);
    }

    static Text tr(String key, String fallback, Text... args) {
        return new Tr(key, fallback, List.of(args), Style.PLAIN);
    }

    record Lit(String text) implements Text {
    }

    /** Shown as {@code fallback} when the client has no entry for {@code key}. */
    record Tr(String key, String fallback, List<Text> args, Style style) implements Text {
        public Tr {
            args = List.copyOf(args);
        }
    }

    /** The theme picks the colour of each role; {@link #wire()} is its byte. */
    enum Style {
        PLAIN, MUTED, ACCENT, WARN;

        public int wire() {
            return ordinal();
        }
    }
}
