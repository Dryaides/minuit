package dev.dryaides.minuit;

/**
 * How the kit's widgets move. A mod sets it for its screens ({@link BaseComponent#screenMotion}) or for one
 * widget ({@link BaseComponent#motionStyle}); the player's choice in {@code config/minuit-client.json}
 * ({@code "motion"}) wins over both, and {@link BaseComponent#reducedMotion} wins over everything.
 */
public enum MotionStyle {
    /** Every widget jumps to its end state. */
    NONE,
    /** Short eases with no overshoot. */
    SUBTLE,
    /** Whole-pixel springs: a toggle's knob swings past its end and back, a button squashes on press and pops back. */
    BOUNCY;

    /** The style a widget draws with. */
    public static MotionStyle resolve(boolean reduced, MotionStyle player, MotionStyle widget, MotionStyle screen) {
        if (reduced) {
            return NONE;
        }
        if (player != null) {
            return player;
        }
        if (widget != null) {
            return widget;
        }
        return screen == null ? SUBTLE : screen;
    }

    /** The style named {@code id} ("none", "subtle", "bouncy"), or null for anything else. */
    public static MotionStyle parse(String id) {
        if (id != null) {
            for (MotionStyle s : values()) {
                if (s.name().equalsIgnoreCase(id.trim())) {
                    return s;
                }
            }
        }
        return null;
    }
}
