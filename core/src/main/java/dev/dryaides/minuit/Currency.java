package dev.dryaides.minuit;

import java.util.function.LongFunction;

/**
 * The money of a mod's shop: a name, an icon and how an amount is written. Amounts are whole units of a
 * virtual currency, never real money.
 *
 * <pre>{@code
 * Currency gems = Currency.of("Gems", ItemView.of("minecraft:emerald"));
 * Currency coins = Currency.of("Coins", null).format(n -> n + " c"); // the theme's coin glyph
 * }</pre>
 *
 * @param name   the currency's name, for tooltips and sentences: "Not enough Gems"
 * @param icon   drawn beside every amount at its own size: a {@link Glyph} takes the text's colour, an item or
 *               sprite keeps its own; null for the theme's {@link Icon#COIN}
 * @param format writes an amount; null for {@link #grouped(long)}
 */
@Incubating
public record Currency(String name, IconArt icon, LongFunction<String> format) {

    public Currency {
        name = name == null ? "" : name;
        format = format == null ? Currency::grouped : format;
    }

    public static Currency of(String name, IconArt icon) {
        return new Currency(name, icon, null);
    }

    public Currency format(LongFunction<String> format) {
        return new Currency(name, icon, format);
    }

    /** The amount as this currency writes it, without the icon. */
    public String text(long amount) {
        return format.apply(amount);
    }

    /** The icon to draw in a theme: this currency's own, else the theme's coin, else the kit's coin with no theme. */
    public IconArt icon(Theme theme) {
        if (icon != null) {
            return icon;
        }
        return theme != null ? theme.icon(Icon.COIN) : Icon.COIN.glyph();
    }

    /** Digits in groups of three with commas: 1,250. */
    public static String grouped(long amount) {
        String digits = Long.toString(amount);
        int start = amount < 0 ? 1 : 0;
        StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3);
        out.append(digits, 0, start);
        int len = digits.length() - start;
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                out.append(',');
            }
            out.append(digits.charAt(start + i));
        }
        return out.toString();
    }
}
