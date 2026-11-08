package dev.dryaides.minuit;

/**
 * Translation, as the kit sees it. In game it is {@code I18n::get}; on the bench it is a lang file
 * read from disk. Either way a panel gets finished strings and never a {@code Component}.
 */
@FunctionalInterface
public interface Words {

    String get(String key, Object... args);

    /**
     * The {@code key + ".none"} entry for 0 and {@code key + ".one"} for 1 when they exist, else
     * {@code key}, so a player never reads "(s)".
     */
    default String plural(String key, long count, Object... args) {
        String specific = count == 0 ? key + ".none" : count == 1 ? key + ".one" : null;
        if (specific != null) {
            String text = get(specific, args);
            if (!text.equals(specific)) {
                return text;
            }
        }
        return get(key, args);
    }
}
