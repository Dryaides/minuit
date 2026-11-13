package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.OfficialPalettes;

/**
 * The colours of a text field, whatever the family: a dark face and light text, so typed text
 * reads on a light palette too, and a mod's vanilla edit box, which always adds the game's
 * drop shadow, gets light text that the shadow sharpens instead of smearing.
 */
final class FieldInk {

    /** A face at or under this luminance is dark enough for the light text. */
    static final double DARK_FACE = 0.06;

    private FieldInk() {
    }

    /** {@code p} with its well darkened toward its text colour when the well is light. */
    static Palette palette(Palette p) {
        int well = face(p);
        if (well == p.well()) {
            return p;
        }
        return OfficialPalettes.derive(p, new Palette(p.frame(), p.frameDark(), p.frameLight(), p.trim(), p.surface(), p.surfaceEdge(),
                p.section(), well, p.wellEdge(), p.text(), p.textSoft(), p.textFaint(), p.raised(),
                p.raisedHover(), p.raisedText(), p.primary(), p.primaryHover(), p.primaryText(), p.accent(),
                p.accentDeep(), p.good(), p.warn(), p.bad(), p.destructive(), p.destructiveHover(),
                p.destructiveText(), p.track(), p.disabled(), p.disabledText(), p.shadow(), p.link(), p.backdrop(), p.line(), p.mark(), p.slot()));
    }

    /** The field's face: the well when it is already dark and opaque, else the darker of text and surface, deepened. */
    static int face(Palette p) {
        int well = p.well();
        if ((well >>> 24) == 0xFF && Shapes.luminance(well) <= DARK_FACE) {
            return well;
        }
        int ink = Shapes.luminance(p.text()) < Shapes.luminance(p.surface()) ? p.text() : p.surface();
        int face = ink | 0xFF000000;
        while (Shapes.luminance(face) > DARK_FACE) {
            face = Shapes.mix(face, 0xFF000000, 0.25F);
        }
        return face;
    }

    /** Light text: the palette's lighter of text and surface, near-white when that is not light enough. */
    static int text(Palette p) {
        int light = Shapes.luminance(p.text()) > Shapes.luminance(p.surface()) ? p.text() : p.surface();
        light |= 0xFF000000;
        return Shapes.contrast(light, face(p)) >= 7 ? light : Shapes.NEAR_WHITE;
    }

    static int hint(Palette p) {
        return Shapes.mix(text(p), face(p), 0.45F);
    }
}
