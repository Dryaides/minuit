package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.OfficialPalettes;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The glass mode of a chroma: the family's fills turned into one dark translucent smoke, its relief kept. Every
 * surface, section and button face becomes the smoke; edges, trim, the frame's lit and shadowed rows and the drop
 * shadow keep the half's own colours, so a family keeps its silhouette and bevels (owner, 2026-11-11). Text is white;
 * the accent roles are the half's own, muted toward grey and darkened until white text reads 4.5:1 over the worst
 * backdrop, a white sky with no dim.
 */
@Internal
public final class GlassPalette {
    /**
     * The smoke, #0B0D10 at 0.6. Over a white backdrop it shows as #6D6E70: #FFFFFF text reads 5.1:1 on it, the
     * #F0F0F0 of the first draft only 4.48:1, so body text is white.
     */
    static final int SMOKE = 0x0B0D10;
    static final float ALPHA = 0.6F;
    /** The backdrop white ink fares worst on. */
    static final int WORST = 0xFFFFFFFF;

    private GlassPalette() {
    }

    /** Each glass palette made by {@link #of} to its state-mark palette. */
    private static final Map<Palette, Palette> MARKS = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * The palette a state mark (a toggle's on groove and knob, a chosen radio) is drawn in: on a glass palette, the
     * half's own primary and raised roles, opaque and saturated, since the muted primary is for faces carrying white
     * text and dulls a mark that carries none, and a smoke knob vanishes on its groove; any other palette as it is.
     */
    public static Palette marks(Palette p) {
        Palette m = MARKS.get(p);
        return m != null ? m : p;
    }

    /** True for a palette {@link #of} made, or its {@link #marks}. */
    public static boolean isGlass(Palette p) {
        return MARKS.containsKey(p) || MARKS.containsValue(p);
    }

    /** The glass mode of {@code half}, the chroma's dark half when it has one. */
    public static Palette of(Palette half) {
        Palette glass = glass(half);
        MARKS.put(glass, new Palette(glass.frame(), glass.frameDark(), glass.frameLight(), glass.trim(),
                glass.surface(), glass.surfaceEdge(), glass.section(), glass.well(), glass.wellEdge(),
                glass.text(), glass.textSoft(), glass.textFaint(),
                half.raised(), half.raisedHover(), half.raisedText(),
                half.primary(), half.primaryHover(), half.primaryText(),
                glass.accent(), glass.accentDeep(),
                glass.good(), glass.warn(), glass.bad(),
                glass.destructive(), glass.destructiveHover(), glass.destructiveText(),
                glass.track(), glass.disabled(), glass.disabledText(), glass.shadow(), glass.link(),
                glass.backdrop(), 0, 0, 0));
        return glass;
    }

    private static Palette glass(Palette half) {
        int surface = smoke(0, ALPHA);
        int seen = over(surface, WORST);
        int raised = smoke(24, ALPHA + 0.1F);
        int raisedHover = smoke(48, ALPHA + 0.15F);
        return OfficialPalettes.derive(half, new Palette(surface, half.frameDark(), half.frameLight(), half.trim(),
                surface, half.surfaceEdge(), surface, alpha(0x000000, 0.35F), half.wellEdge(),
                0xFFFFFFFF, 0xFFD0D0D0, half.textFaint(),
                raised, raisedHover, 0xFFFFFFFF,
                muted(half.primary(), 0, seen), muted(half.primary(), 24, seen), 0xFFFFFFFF,
                muted(half.accent(), 0, seen), muted(half.accent(), -24, seen),
                half.good(), half.warn(), half.bad(),
                muted(half.destructive(), 0, seen), muted(half.destructive(), 24, seen), 0xFFFFFFFF,
                alpha(0x000000, 0.4F), half.disabled(), half.disabledText(), half.shadow(), half.link(),
                half.backdrop(), half.line(), half.mark() == 0 ? 0 : muted(half.mark(), 0, seen), 0));
    }

    /** {@code top} as it shows over the opaque {@code under}. */
    static int over(int top, int under) {
        return Shapes.mix(under | 0xFF000000, top | 0xFF000000, (top >>> 24) / 255F);
    }

    private static int smoke(int lift, float alpha) {
        int r = Math.min(255, (SMOKE >> 16 & 0xFF) + lift);
        int g = Math.min(255, (SMOKE >> 8 & 0xFF) + lift);
        int b = Math.min(255, (SMOKE & 0xFF) + lift);
        return alpha(r << 16 | g << 8 | b, alpha);
    }

    private static int alpha(int rgb, float alpha) {
        return Math.round(Math.min(1F, alpha) * 255) << 24 | rgb & 0xFFFFFF;
    }

    /**
     * {@code color} mixed halfway toward grey at 0.85, then darkened a step at a time until white text reads 4.5:1
     * on it over {@code under}, the smoke over the worst backdrop; the 0.1 to spare absorbs the rounding of a blend.
     */
    private static int muted(int color, int lift, int under) {
        int rgb = 0;
        for (int i = 0; i < 3; i++) {
            int c = color >> (16 - 8 * i) & 0xFF;
            rgb |= Math.max(0, Math.min(255, (c + 128) / 2 + lift)) << (16 - 8 * i);
        }
        int face = alpha(rgb, 0.85F);
        for (int step = 0; step < 20 && Shapes.contrast(0xFFFFFFFF, over(face, under)) < Ink.TEXT + 0.1; step++) {
            face = Shapes.mix(face, face & 0xFF000000, 0.1F);
        }
        return face;
    }
}
