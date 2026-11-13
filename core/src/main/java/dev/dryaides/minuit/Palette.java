package dev.dryaides.minuit;

/**
 * The colours of a theme, by role. Widgets only ever ask for a role, so a mod changes its look by
 * changing this record and its {@link FrameStyle}, never a widget.
 *
 * @param frame        the outer frame: leather, slate
 * @param frameDark    its shadowed edge
 * @param frameLight   its lit edge, top left
 * @param trim         the detail on the frame: stitches, a shine line
 * @param surface      the panel's inside: paper, dark amethyst
 * @param surfaceEdge  the line around the inside
 * @param section      a card on the surface
 * @param well         a sunken area: a field, a list, a meter
 * @param wellEdge     the frame of a well
 * @param text         body text
 * @param textSoft     labels and secondary text
 * @param textFaint    hints, timestamps
 * @param raised       a button at rest
 * @param raisedHover  a button under the mouse
 * @param raisedText   text on a button
 * @param primary      the one action a screen exists for
 * @param primaryHover the same under the mouse
 * @param primaryText  text on it
 * @param accent       toggles, sliders, the level meter
 * @param accentDeep   the same, darker
 * @param good         done, heard, ready
 * @param warn         something to look at, nothing broken
 * @param bad          broken, refused
 * @param destructive      a button that deletes or resets, with its hover and text colours
 * @param destructiveHover the same under the mouse
 * @param destructiveText  text on it
 * @param track        the empty part of a bar
 * @param disabled     a control that cannot be used
 * @param disabledText its text
 * @param shadow       drop shadows
 * @param link         a link's label in text; 0 takes the accent ({@link #linkInk()})
 * @param backdrop     the page behind panels where the host draws one (the site, the bench); 0 for
 *                     none, the game's own background
 * @param line         a 1 px outline the family draws around fields, tracks and secondary buttons in place of
 *                     their relief, with flat faces in the chroma's own steps (a shadcn-like neutral chroma);
 *                     0 keeps the family's relief
 * @param mark         a mark drawn in the primary's place on the panel (bare primary badge, heatmap top,
 *                     current and done steps, carousel dot); 0 takes the accent ({@link #markInk()})
 * @param slot         the face of an inventory slot where it differs from the well (bedrock light keeps the
 *                     game's dark slot under a lighter well); 0 takes the well ({@link #slotFace()})
 */
public record Palette(int frame, int frameDark, int frameLight, int trim,
                      int surface, int surfaceEdge, int section, int well, int wellEdge,
                      int text, int textSoft, int textFaint,
                      int raised, int raisedHover, int raisedText,
                      int primary, int primaryHover, int primaryText,
                      int accent, int accentDeep,
                      int good, int warn, int bad,
                      int destructive, int destructiveHover, int destructiveText,
                      int track, int disabled, int disabledText, int shadow, int link, int backdrop, int line, int mark, int slot) {

    /** The 1.x form, before the link, backdrop, line, mark and slot roles: those take their fallbacks. */
    public Palette(int frame, int frameDark, int frameLight, int trim,
                   int surface, int surfaceEdge, int section, int well, int wellEdge,
                   int text, int textSoft, int textFaint,
                   int raised, int raisedHover, int raisedText,
                   int primary, int primaryHover, int primaryText,
                   int accent, int accentDeep,
                   int good, int warn, int bad,
                   int destructive, int destructiveHover, int destructiveText,
                   int track, int disabled, int disabledText, int shadow) {
        this(frame, frameDark, frameLight, trim, surface, surfaceEdge, section, well, wellEdge,
                text, textSoft, textFaint, raised, raisedHover, raisedText,
                primary, primaryHover, primaryText, accent, accentDeep, good, warn, bad,
                destructive, destructiveHover, destructiveText, track, disabled, disabledText, shadow, 0, 0, 0, 0, 0);
    }

    /** The colour of a link's label: {@link #link} when the chroma sets one, else the accent. */
    public int linkInk() {
        return this.link == 0 ? this.accent : this.link;
    }

    /** The face of an inventory slot: {@link #slot} when the chroma sets one, else the well. */
    public int slotFace() {
        return this.slot == 0 ? this.well : this.slot;
    }

    /** The colour of a primary mark on the panel: {@link #mark} when the chroma sets one, else the accent. */
    public int markInk() {
        return this.mark == 0 ? this.accent : this.mark;
    }

    /**
     * The form from before the destructive roles. {@code destructive} and {@code destructiveHover}
     * take {@code bad}, {@code destructiveText} takes {@code raisedText}.
     */
    public Palette(int frame, int frameDark, int frameLight, int trim,
                   int surface, int surfaceEdge, int section, int well, int wellEdge,
                   int text, int textSoft, int textFaint,
                   int raised, int raisedHover, int raisedText,
                   int primary, int primaryHover, int primaryText,
                   int accent, int accentDeep,
                   int good, int warn, int bad,
                   int track, int disabled, int disabledText, int shadow) {
        this(frame, frameDark, frameLight, trim, surface, surfaceEdge, section, well, wellEdge,
                text, textSoft, textFaint, raised, raisedHover, raisedText,
                primary, primaryHover, primaryText, accent, accentDeep, good, warn, bad,
                bad, bad, raisedText, track, disabled, disabledText, shadow);
    }

    /**
     * A palette role, one per component in record order. Roles added in 2.x arrive here and in {@link Builder};
     * the canonical constructor gains one overload per new role, so the older forms keep compiling.
     */
    public enum Role {
        FRAME,
        FRAME_DARK,
        FRAME_LIGHT,
        TRIM,
        SURFACE,
        SURFACE_EDGE,
        SECTION,
        WELL,
        WELL_EDGE,
        TEXT,
        TEXT_SOFT,
        TEXT_FAINT,
        RAISED,
        RAISED_HOVER,
        RAISED_TEXT,
        PRIMARY,
        PRIMARY_HOVER,
        PRIMARY_TEXT,
        ACCENT,
        ACCENT_DEEP,
        GOOD,
        WARN,
        BAD,
        DESTRUCTIVE,
        DESTRUCTIVE_HOVER,
        DESTRUCTIVE_TEXT,
        TRACK,
        DISABLED,
        DISABLED_TEXT,
        SHADOW,
        LINK,
        BACKDROP,
        LINE,
        MARK,
        SLOT
    }

    /** The colour this palette gives a role. */
    public int get(Role role) {
        return switch (role) {
            case FRAME -> this.frame;
            case FRAME_DARK -> this.frameDark;
            case FRAME_LIGHT -> this.frameLight;
            case TRIM -> this.trim;
            case SURFACE -> this.surface;
            case SURFACE_EDGE -> this.surfaceEdge;
            case SECTION -> this.section;
            case WELL -> this.well;
            case WELL_EDGE -> this.wellEdge;
            case TEXT -> this.text;
            case TEXT_SOFT -> this.textSoft;
            case TEXT_FAINT -> this.textFaint;
            case RAISED -> this.raised;
            case RAISED_HOVER -> this.raisedHover;
            case RAISED_TEXT -> this.raisedText;
            case PRIMARY -> this.primary;
            case PRIMARY_HOVER -> this.primaryHover;
            case PRIMARY_TEXT -> this.primaryText;
            case ACCENT -> this.accent;
            case ACCENT_DEEP -> this.accentDeep;
            case GOOD -> this.good;
            case WARN -> this.warn;
            case BAD -> this.bad;
            case DESTRUCTIVE -> this.destructive;
            case DESTRUCTIVE_HOVER -> this.destructiveHover;
            case DESTRUCTIVE_TEXT -> this.destructiveText;
            case TRACK -> this.track;
            case DISABLED -> this.disabled;
            case DISABLED_TEXT -> this.disabledText;
            case SHADOW -> this.shadow;
            case LINK -> this.link;
            case BACKDROP -> this.backdrop;
            case LINE -> this.line;
            case MARK -> this.mark;
            case SLOT -> this.slot;
        };
    }

    /** This palette with one role changed. */
    public Palette with(Role role, int argb) {
        return builder(this).set(role, argb).build();
    }

    /**
     * A builder seeded from a base palette: {@code Palette.builder(Themes.VANILLA.palette()).set(Role.PRIMARY,
     * 0xFF6236C4).build()}. A role set to 0 keeps its fallback ({@link #linkInk()}, {@link #markInk()},
     * {@link #slotFace()}).
     */
    public static Builder builder(Palette base) {
        return new Builder(base);
    }

    /** Builds a palette role by role; see {@link #builder(Palette)}. */
    public static final class Builder {

        private final int[] values = new int[Role.values().length];

        private Builder(Palette base) {
            for (Role role : Role.values()) {
                this.values[role.ordinal()] = base.get(role);
            }
        }

        public Builder set(Role role, int argb) {
            this.values[role.ordinal()] = argb;
            return this;
        }

        public Palette build() {
            int[] v = this.values;
            return new Palette(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17], v[18], v[19], v[20], v[21], v[22], v[23], v[24], v[25], v[26], v[27], v[28], v[29], v[30], v[31], v[32], v[33], v[34]);
        }
    }
}
