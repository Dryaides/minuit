package dev.dryaides.minuit.paper;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads the payload of a fallback dialog click: one SNBT compound of numbers, strings and booleans,
 * as {@code BinaryTagHolder#string()} gives it. A nested compound or list throws.
 */
final class Snbt {

    private final String s;
    private int at;

    private Snbt(String s) {
        this.s = s;
    }

    /** The compound as Java values: Integer or Long, Float or Double, String, Boolean. */
    static Map<String, Object> flat(String snbt) {
        Snbt in = new Snbt(snbt);
        Map<String, Object> out = new HashMap<>();
        in.expect('{');
        if (in.peek() == '}') {
            in.at++;
        } else {
            do {
                String key = in.peek() == '"' || in.peek() == '\'' ? in.quoted() : in.bare();
                in.expect(':');
                out.put(key, in.value());
            } while (in.next(',', '}'));
        }
        in.skipSpace();
        if (in.at != in.s.length()) {
            throw in.fail("trailing text");
        }
        return out;
    }

    private Object value() {
        char c = peek();
        if (c == '"' || c == '\'') {
            return quoted();
        }
        String word = bare();
        if (word.equals("true") || word.equals("false")) {
            return Boolean.parseBoolean(word);
        }
        char last = Character.toLowerCase(word.charAt(word.length() - 1));
        String digits = Character.isLetter(last) ? word.substring(0, word.length() - 1) : word;
        try {
            return switch (last) {
                case 'b', 's' -> Integer.valueOf(digits);
                case 'l' -> Long.valueOf(digits);
                case 'f' -> Float.valueOf(digits);
                case 'd' -> Double.valueOf(digits);
                default -> digits.contains(".") || digits.contains("e") || digits.contains("E")
                        ? (Object) Double.valueOf(digits) : (Object) Integer.valueOf(digits);
            };
        } catch (NumberFormatException e) {
            throw fail("not a number: " + word);
        }
    }

    private String bare() {
        skipSpace();
        int start = this.at;
        while (this.at < this.s.length()) {
            char c = this.s.charAt(this.at);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '+')) {
                break;
            }
            this.at++;
        }
        if (start == this.at) {
            throw fail("expected a word");
        }
        return this.s.substring(start, this.at);
    }

    private String quoted() {
        skipSpace();
        char quote = this.s.charAt(this.at++);
        StringBuilder out = new StringBuilder();
        while (true) {
            if (this.at >= this.s.length()) {
                throw fail("unclosed string");
            }
            char c = this.s.charAt(this.at++);
            if (c == quote) {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (this.at >= this.s.length()) {
                throw fail("unclosed string");
            }
            char e = this.s.charAt(this.at++);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 's' -> out.append(' ');
                case 'u' -> {
                    if (this.at + 4 > this.s.length()) {
                        throw fail("short \\u escape");
                    }
                    out.append((char) Integer.parseInt(this.s.substring(this.at, this.at + 4), 16));
                    this.at += 4;
                }
                default -> out.append(e);
            }
        }
    }

    /** True after {@code more}, false after {@code end}. */
    private boolean next(char more, char end) {
        char c = peek();
        this.at++;
        if (c == more) {
            return true;
        }
        if (c == end) {
            return false;
        }
        throw fail("expected '" + more + "' or '" + end + "'");
    }

    private void expect(char c) {
        if (peek() != c) {
            throw fail("expected '" + c + "'");
        }
        this.at++;
    }

    private char peek() {
        skipSpace();
        if (this.at >= this.s.length()) {
            throw fail("unexpected end");
        }
        return this.s.charAt(this.at);
    }

    private void skipSpace() {
        while (this.at < this.s.length() && Character.isWhitespace(this.s.charAt(this.at))) {
            this.at++;
        }
    }

    private IllegalArgumentException fail(String why) {
        return new IllegalArgumentException(why + " at " + this.at);
    }
}
