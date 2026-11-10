package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.TextMeasure;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Labels cut to their room, kept per row (by identity: a row's own hashCode may be costly or cyclic) so a still list measures nothing. An entry is reused only while its
 * text, room and renderer class are unchanged: a component measured once by another renderer (the site lays
 * its scenes out with one and draws them with another) is measured again by the one that draws it.
 */
public final class FitCache {

    /** Past this many rows the cache starts over, so a list that streams items does not grow it forever. */
    private static final int MAX = 4096;

    /** A label cut to {@code room}, with the cut's pixel width. */
    public record Fit(String text, int room, Class<?> by, int font, String cut, int width) {
    }

    private final Map<Object, Fit> fits = new IdentityHashMap<>();

    /** {@code text} as {@link Draw#ellipsize} cuts it to {@code room}, measured, cached under {@code key}. */
    public Fit fit(Draw d, Object key, String text, int room) {
        Fit fit = fits.get(key);
        if (fit != null && fit.room == room && fit.by == d.getClass() && fit.font == TextMeasure.fontGeneration() && fit.text.equals(text)) {
            return fit;
        }
        String cut = d.ellipsize(text, room);
        fit = new Fit(text, room, d.getClass(), TextMeasure.fontGeneration(), cut, d.width(cut));
        if (fits.size() >= MAX) {
            fits.clear();
        }
        fits.put(key, fit);
        return fit;
    }

    public void clear() {
        fits.clear();
    }
}
