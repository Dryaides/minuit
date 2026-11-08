package dev.dryaides.minuit;

/**
 * The keys a widget reacts to, named by meaning so the kit needs no key codes. The host maps the
 * game's keys onto them, the shortcuts with Ctrl included; any other key arrives as {@link #OTHER}
 * and its character, if it types one, through {@link PanelComponent#charTyped}.
 */
public enum InputKey {
    LEFT, RIGHT, HOME, END, BACKSPACE, DELETE, ENTER, ESCAPE, TAB,
    SELECT_ALL, COPY, CUT, PASTE,
    UP, DOWN, SPACE,
    OTHER
}
