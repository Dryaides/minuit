package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Palette;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The dark variant of a light palette's chroma. A text field is a dark face in every family, so an ink drawn
 * inside one (the keybind field's listening text, the tag input's alert) comes from the dark variant, whose
 * colours are made for a dark face.
 */
public final class DarkTwin {
    private static final Map<Palette, Palette> TWINS = new ConcurrentHashMap<>();

    private DarkTwin() {
    }

    public static void pair(Palette light, Palette dark) {
        if (light != null && dark != null && !light.equals(dark)) {
            TWINS.put(light, dark);
        }
    }

    /** The dark variant of {@code p}'s chroma; {@code p} itself when it is dark or has none. */
    public static Palette of(Palette p) {
        return TWINS.getOrDefault(p, p);
    }
}
