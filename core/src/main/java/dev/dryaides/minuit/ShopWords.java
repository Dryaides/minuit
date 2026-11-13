package dev.dryaides.minuit;

/** The shop bricks' sentences that carry an amount, from the lang file with an English fallback. */
final class ShopWords {

    private ShopWords() {
    }

    /** "30 Coins missing". */
    static String missing(Words words, Currency currency, long amount) {
        return withArgs(words, "minuit.shop.short", currency.text(amount) + " " + currency.name() + " missing",
                currency.text(amount), currency.name());
    }

    /** "12 left". */
    static String left(Words words, int stock) {
        return withArgs(words, "minuit.shop.left", stock + " left", stock);
    }

    /** "3 x Bread, 36 Coins". */
    static String bought(Words words, int quantity, String name, String total, String currency) {
        return withArgs(words, "minuit.shop.bought", (quantity + " x " + name + ", " + total + " " + currency).trim(),
                quantity, name, total, currency);
    }

    /** "5 items, 120 Coins". */
    static String boughtMany(Words words, int count, String total, String currency) {
        return withArgs(words, "minuit.shop.bought.many", (count + " items, " + total + " " + currency).trim(),
                count, total, currency);
    }

    private static String withArgs(Words words, String key, String english, Object... args) {
        if (words != null) {
            String found = words.get(key, args);
            if (!found.equals(key)) {
                return found;
            }
        }
        return english;
    }
}
