package dev.dryaides.minuit.form;

import java.util.List;

/**
 * What a player's minuit client said in its hello: protocol, mod version and capability names.
 * The same type on a mod server ({@code MinuitServer.client}) and on Paper ({@code PaperForms.client}).
 */
public record ClientInfo(int protocol, String version, List<String> caps) {

    /** The capability a client announces when it can show forms. */
    public static final String FORMS = "form/1";

    public ClientInfo {
        caps = List.copyOf(caps);
    }

    /** Whether the client announced {@code cap}, such as {@link #FORMS}. */
    public boolean has(String cap) {
        return caps.contains(cap);
    }
}
