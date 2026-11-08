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
 */
public record Palette(int frame, int frameDark, int frameLight, int trim,
                      int surface, int surfaceEdge, int section, int well, int wellEdge,
                      int text, int textSoft, int textFaint,
                      int raised, int raisedHover, int raisedText,
                      int primary, int primaryHover, int primaryText,
                      int accent, int accentDeep,
                      int good, int warn, int bad,
                      int destructive, int destructiveHover, int destructiveText,
                      int track, int disabled, int disabledText, int shadow) {

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
}
