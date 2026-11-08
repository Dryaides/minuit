package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Text written with the game's formatting codes, split into runs of one colour and style. A code is
 * a section sign or an {@code &} followed by {@code 0-9 a-f} (a colour, which also clears the style,
 * as in the game), {@code k l m n o} (obfuscated, bold, strikethrough, underline, italic) or
 * {@code r} (reset); any other letter stays as written. {@code [label](https://...)} is a link.
 * A backslash keeps the next {@code &}, section sign, {@code [} or backslash as a plain character.
 */
public final class RichText {

    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int UNDERLINE = 4;
    public static final int STRIKE = 8;
    public static final int OBFUSCATED = 16;

    /** The game's 16 text colours, codes 0 to f. */
    private static final int[] COLORS = {
            0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF};

    private static final char SECTION = '§';
    private static final String ESCAPED = "&§[\\";

    /**
     * @param color the run's colour, 0 for the caller's default
     * @param link  the URL when the run is a link's label, else null
     */
    public record Run(String text, int color, int style, String link) {
    }

    private final List<Run> runs;

    private RichText(List<Run> runs) {
        this.runs = List.copyOf(runs);
    }

    public static RichText parse(String text) {
        Builder out = new Builder();
        int color = 0;
        int style = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && ESCAPED.indexOf(text.charAt(i + 1)) >= 0) {
                out.add(text.charAt(i + 1), color, style, null);
                i += 2;
                continue;
            }
            if ((c == SECTION || c == '&') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                int hex = "0123456789abcdef".indexOf(code);
                int bit = switch (code) {
                    case 'k' -> OBFUSCATED;
                    case 'l' -> BOLD;
                    case 'm' -> STRIKE;
                    case 'n' -> UNDERLINE;
                    case 'o' -> ITALIC;
                    default -> 0;
                };
                if (hex >= 0) {
                    color = COLORS[hex];
                    style = 0;
                    i += 2;
                    continue;
                }
                if (bit != 0 || code == 'r') {
                    style = bit == 0 ? 0 : style | bit;
                    color = bit == 0 ? 0 : color;
                    i += 2;
                    continue;
                }
            }
            if (c == '[') {
                int close = text.indexOf("](", i + 1);
                int end = close < 0 ? -1 : text.indexOf(')', close + 2);
                if (end > 0) {
                    String label = text.substring(i + 1, close);
                    String url = text.substring(close + 2, end);
                    if (!label.isEmpty() && label.indexOf('[') < 0 && isWeb(url)) {
                        for (int k = 0; k < label.length(); k++) {
                            out.add(label.charAt(k), color, style, url);
                        }
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.add(c, color, style, null);
            i++;
        }
        return new RichText(out.runs());
    }

    /** Only web links open; anything else stays plain text. */
    private static boolean isWeb(String url) {
        return (url.startsWith("https://") || url.startsWith("http://")) && url.indexOf(' ') < 0;
    }

    public List<Run> runs() {
        return this.runs;
    }

    /** The text without its codes, links shown by their label. */
    public String plain() {
        StringBuilder out = new StringBuilder();
        for (Run run : this.runs) {
            out.append(run.text());
        }
        return out.toString();
    }

    public int width(Draw d) {
        int w = 0;
        for (Run run : this.runs) {
            w += d.styledWidth(run.text(), run.style());
        }
        return w;
    }

    /**
     * Lines of at most {@code maxWidth} pixels, broken at spaces across runs; each line keeps the
     * colour and style its characters had. A word longer than the line is broken where it overflows.
     */
    public List<RichText> wrap(Draw d, int maxWidth) {
        List<RichText> lines = new ArrayList<>();
        List<Char> current = new ArrayList<>();
        int width = 0;
        int lastSpace = -1;
        for (Run run : this.runs) {
            for (int i = 0; i < run.text().length(); i++) {
                Char ch = new Char(run.text().charAt(i), run);
                int cw = ch.width(d);
                if (width + cw > maxWidth && !current.isEmpty()) {
                    List<Char> rest = new ArrayList<>();
                    if (lastSpace >= 0 && ch.c() != ' ') {
                        rest.addAll(current.subList(lastSpace + 1, current.size()));
                        current = new ArrayList<>(current.subList(0, lastSpace));
                    }
                    lines.add(build(current));
                    current = rest;
                    width = 0;
                    for (Char kept : current) {
                        width += kept.width(d);
                    }
                    lastSpace = -1;
                    if (ch.c() == ' ' && current.isEmpty()) {
                        continue;
                    }
                }
                if (ch.c() == ' ') {
                    lastSpace = current.size();
                }
                current.add(ch);
                width += cw;
            }
        }
        if (!current.isEmpty() || lines.isEmpty()) {
            lines.add(build(current));
        }
        return lines;
    }

    private static RichText build(List<Char> chars) {
        Builder out = new Builder();
        for (Char ch : chars) {
            out.add(ch.c(), ch.run().color(), ch.run().style(), ch.run().link());
        }
        return new RichText(out.runs());
    }

    private record Char(char c, Run run) {
        int width(Draw d) {
            return d.styledWidth(String.valueOf(this.c), this.run.style());
        }
    }

    /** Gathers characters into runs, a new run each time colour, style or link changes. */
    private static final class Builder {
        private final List<Run> runs = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private int color;
        private int style;
        private String link;

        void add(char c, int color, int style, String link) {
            if (!this.text.isEmpty() && (color != this.color || style != this.style || !Objects.equals(link, this.link))) {
                flush();
            }
            this.color = color;
            this.style = style;
            this.link = link;
            this.text.append(c);
        }

        private void flush() {
            if (!this.text.isEmpty()) {
                this.runs.add(new Run(this.text.toString(), this.color, this.style, this.link));
                this.text.setLength(0);
            }
        }

        List<Run> runs() {
            flush();
            return this.runs;
        }
    }
}
