package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Palette;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The palettes minuit ships (its own chroma files and what the kit derives from them: glass, accents, field
 * wells). Their contrast is checked at design time by {@code tools/palette_contrast.py}, so the runtime
 * contrast helpers leave their colours as drawn; a mod's or a pack's palette still gets the safety net.
 */
public final class OfficialPalettes {
    private static final Set<Palette> OFFICIAL = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private OfficialPalettes() {
    }

    public static Palette mark(Palette p) {
        if (p != null) {
            OFFICIAL.add(p);
        }
        return p;
    }

    public static boolean is(Palette p) {
        return p != null && OFFICIAL.contains(p);
    }

    /** {@code derived}, marked official when {@code from} is. */
    public static Palette derive(Palette from, Palette derived) {
        return is(from) ? mark(derived) : derived;
    }
}
