package dev.dryaides.minuit.form;

/**
 * A value a player set on a screen: a toggle's {@link Bool}, a slider's, a dropdown's or a tab
 * row's {@link Num}, a text field's {@link Str}.
 */
public sealed interface Value {

    record Bool(boolean value) implements Value {
    }

    record Num(int value) implements Value {
    }

    record Str(String value) implements Value {
    }
}
