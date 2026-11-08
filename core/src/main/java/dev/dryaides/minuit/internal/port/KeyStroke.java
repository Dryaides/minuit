package dev.dryaides.minuit.internal.port;

/** A key press as the game hands it: the key code of the running version, Ctrl (Command on macOS) and Shift. Not API. */
public record KeyStroke(int code, int scancode, boolean control, boolean shift) {
}
