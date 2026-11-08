package dev.dryaides.minuit.form;

/**
 * What a server does with a player's screen. Every call comes after {@link FormSession} has checked
 * the input against the form as sent and stored it, so {@link FormSession#value} already reads the
 * new value.
 */
public interface FormHandler {

    default void changed(FormSession session, String id, Value value) {
    }

    default void pressed(FormSession session, String id) {
    }

    default void closed(FormSession session, CloseReason why) {
    }
}
