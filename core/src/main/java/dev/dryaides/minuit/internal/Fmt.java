package dev.dryaides.minuit.internal;

/**
 * The few number formats core needs, written out so no {@code String.format} (and with it the JDK
 * Formatter, Locale, Currency and TimeZone tables) lands in the web build. Output matches
 * {@code String.format(Locale.ROOT, ...)}: digits come from {@code Double.toString}, rounded half up.
 */
public final class Fmt {
    private Fmt() {
    }

    /** Same as {@code String.format(Locale.ROOT, "%.<decimals>e", value)}. */
    public static String sci(double value, int decimals) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value < 0D ? "-Infinity" : "Infinity";
        }
        boolean negative = value < 0D || (value == 0D && 1D / value < 0D);
        StringBuilder out = new StringBuilder();
        if (negative) {
            out.append('-');
        }
        String digits;
        int exp;
        if (value == 0D) {
            digits = "0";
            exp = 0;
        } else {
            int[] e = new int[1];
            digits = digits(Math.abs(value), e);
            exp = e[0];
            int keep = decimals + 1;
            if (digits.length() > keep) {
                boolean up = digits.charAt(keep) >= '5';
                char[] d = digits.substring(0, keep).toCharArray();
                if (up) {
                    int i = keep - 1;
                    while (i >= 0 && d[i] == '9') {
                        d[i--] = '0';
                    }
                    if (i >= 0) {
                        d[i]++;
                    } else {
                        d[0] = '1';
                        exp++;
                    }
                }
                digits = new String(d);
            }
        }
        out.append(digits.charAt(0));
        if (decimals > 0) {
            out.append('.');
            for (int i = 1; i <= decimals; i++) {
                out.append(i < digits.length() ? digits.charAt(i) : '0');
            }
        }
        out.append('e').append(exp < 0 ? '-' : '+');
        int abs = Math.abs(exp);
        if (abs < 10) {
            out.append('0');
        }
        return out.append(abs).toString();
    }

    /** Same as {@code String.format(Locale.ROOT, "%.<decimals>f", value)} for finite values below 1e15. */
    public static String fixed(double value, int decimals) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value < 0D ? "-Infinity" : "Infinity";
        }
        boolean negative = value < 0D || (value == 0D && 1D / value < 0D);
        String digits = "";
        int exp = 0;
        if (value != 0D) {
            int[] e = new int[1];
            digits = digits(Math.abs(value), e);
            exp = e[0];
        }
        // digit k (0-based) of the decimal expansion sits at power exp - k; keep powers >= -decimals
        int keep = exp + 1 + decimals;
        StringBuilder all = new StringBuilder();
        int lead = 0;
        if (keep < 0) {
            digits = "";
        } else if (digits.length() > keep) {
            boolean up = digits.charAt(keep) >= '5';
            char[] d = digits.substring(0, keep).toCharArray();
            digits = new String(d);
            if (up) {
                int i = keep - 1;
                while (i >= 0 && d[i] == '9') {
                    d[i--] = '0';
                }
                if (i >= 0) {
                    d[i]++;
                    digits = new String(d);
                } else {
                    digits = "1" + new String(d);
                    exp++;
                    keep++;
                }
            }
        }
        // build integer part then fraction from digits at powers exp, exp-1, ...
        int power = Math.max(exp, 0);
        for (int p = power; p >= -decimals; p--) {
            int k = exp - p;
            char c = k >= 0 && k < digits.length() ? digits.charAt(k) : '0';
            if (p == -1) {
                all.append('.');
            }
            all.append(c);
        }
        if (decimals == 0 && all.length() > 0 && all.charAt(all.length() - 1) == '.') {
            all.setLength(all.length() - 1);
        }
        return (negative ? "-" : "") + all;
    }

    /** Upper-case hex of {@code value}, left-padded with zeros to {@code width} ({@code %0<width>X}). */
    public static String hex(int value, int width) {
        StringBuilder out = new StringBuilder(Integer.toHexString(value).toUpperCase(java.util.Locale.ROOT));
        while (out.length() < width) {
            out.insert(0, '0');
        }
        return out.toString();
    }

    /**
     * {@code String.format(pattern, args)} for String arguments: {@code %s}, {@code %S}, {@code %n$s} and {@code %%}.
     * Any other conversion throws, as {@code String.format} does for a String argument ({@code %d} and the like).
     */
    public static String apply(String pattern, Object[] args) {
        StringBuilder out = new StringBuilder();
        int next = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c != '%') {
                out.append(c);
                continue;
            }
            int j = i + 1;
            int index = -1;
            int num = 0;
            int k = j;
            while (k < pattern.length() && Character.isDigit(pattern.charAt(k))) {
                num = num * 10 + (pattern.charAt(k) - '0');
                k++;
            }
            if (k > j && k < pattern.length() && pattern.charAt(k) == '$') {
                index = num - 1;
                j = k + 1;
            }
            if (j >= pattern.length()) {
                throw new IllegalArgumentException("bad format");
            }
            char conv = pattern.charAt(j);
            if (conv == '%' && index < 0) {
                out.append('%');
            } else if (conv == 's' || conv == 'S') {
                int at = index >= 0 ? index : next++;
                if (at < 0 || at >= args.length) {
                    throw new IllegalArgumentException("missing argument");
                }
                String text = String.valueOf(args[at]);
                out.append(conv == 'S' ? text.toUpperCase(java.util.Locale.ROOT) : text);
            } else {
                throw new IllegalArgumentException("unsupported conversion");
            }
            i = j;
        }
        return out.toString();
    }

    /** Significant digits of a positive finite double as printed by {@code Double.toString}; {@code exp[0]} is the power of the first digit. */
    private static String digits(double abs, int[] exp) {
        String s = Double.toString(abs);
        int e = 0;
        int at = s.indexOf('E');
        if (at >= 0) {
            e = Integer.parseInt(s.substring(at + 1));
            s = s.substring(0, at);
        }
        int dot = s.indexOf('.');
        String all = dot < 0 ? s : s.substring(0, dot) + s.substring(dot + 1);
        int intLen = dot < 0 ? s.length() : dot;
        int first = 0;
        while (first < all.length() - 1 && all.charAt(first) == '0') {
            first++;
        }
        int end = all.length();
        while (end > first + 1 && all.charAt(end - 1) == '0') {
            end--;
        }
        exp[0] = intLen - 1 - first + e;
        return all.substring(first, end);
    }
}
