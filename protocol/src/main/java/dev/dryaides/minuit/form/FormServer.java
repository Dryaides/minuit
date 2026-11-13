package dev.dryaides.minuit.form;

import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.ScreenMessage;
import dev.dryaides.minuit.wire.Limits;
import dev.dryaides.minuit.wire.WireException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The open forms of a server, at most one per player, over a transport for the player type
 * {@code P}. The transport hands every {@code minuit:input} message to {@link #receive} on the
 * server's main thread, and sends what this class gives it on {@code minuit:screen}.
 */
public final class FormServer<P> {

    /** Inputs one player may send per second; the rest of that second is dropped. */
    public static final int PER_SECOND = 30;
    /** Dropped inputs after which a session closes as invalid. */
    public static final int DROPS = 300;

    private final BiConsumer<P, byte[]> send;
    private final LongSupplier clock;
    private final Map<Object, Open<P>> open = new HashMap<>();
    private Function<P, ?> key = p -> p;
    private Fallback.Dialogs<P> dialogs;
    private Predicate<P> required;
    private IntSupplier ids = new IntSupplier() {
        private int next = 1;

        @Override
        public int getAsInt() {
            return this.next++;
        }
    };
    private BiConsumer<P, FormSession> shown = (player, session) -> {
    };

    public FormServer(BiConsumer<P, byte[]> send) {
        this(send, System::currentTimeMillis);
    }

    /** @param clock milliseconds, for the rate limit */
    public FormServer(BiConsumer<P, byte[]> send, LongSupplier clock) {
        this.send = send;
        this.clock = clock;
    }

    /** Shows a vanilla dialog to every player {@code dialogs} reports without minuit forms. */
    public FormServer<P> fallback(Fallback.Dialogs<P> dialogs) {
        this.dialogs = dialogs;
        return this;
    }

    /**
     * For a server with no vanilla dialog: a form opened for a player {@code hasForms} rejects closes
     * at once as {@link CloseReason#UNSUPPORTED} and nothing is sent.
     */
    public FormServer<P> requireForms(Predicate<P> hasForms) {
        this.required = hasForms;
        return this;
    }

    /**
     * Keys the sessions by {@code key} of the player instead of the player object, for a server
     * that builds a new player object on respawn (vanilla): give it the player's UUID. Input from
     * the new object then reaches the session, and messages go to the newest object seen.
     */
    public FormServer<P> keyedBy(Function<P, ?> key) {
        this.key = key;
        return this;
    }

    /**
     * Takes the session ids from {@code ids} instead of counting from 1, for servers where several
     * {@code FormServer}s share the players' screens: give them one counter so no two sessions share an id.
     */
    public FormServer<P> sessionIds(IntSupplier ids) {
        this.ids = ids;
        return this;
    }

    /**
     * Calls {@code shown} after a form went to the player's screen or dialog, so the owner of the
     * players' screens knows which server shows what. A form a nested open replaced before it showed
     * is not reported.
     */
    public FormServer<P> onShown(BiConsumer<P, FormSession> shown) {
        this.shown = shown;
        return this;
    }

    /**
     * Opens {@code form} on the player's screen; a form already open there closes as replaced. That
     * holds for a nested open too: opened from the outer form's own handler, the new form ends the
     * outer session, whose handler gets {@code closed(REPLACED)} before the new form shows.
     */
    public FormSession open(P player, Form form, FormHandler handler) {
        int id = this.ids.getAsInt();
        if (this.required != null && !this.required.test(player)) {
            FormSession refused = new FormSession(id, form, handler, bytes -> {
            });
            refused.end(CloseReason.UNSUPPORTED);
            return refused;
        }
        boolean vanilla = this.dialogs != null && !this.dialogs.hasForms(player);
        byte[] opening = vanilla ? null : encodeOpen(form, id);
        Open<P> mine = new Open<>(player, vanilla);
        FormSession session = new FormSession(id, form, handler, vanilla
                ? bytes -> toDialog(mine.player, bytes)
                : bytes -> this.send.accept(mine.player, bytes));
        mine.session = session;
        Object k = this.key.apply(player);
        Open<P> old = this.open.put(k, mine);
        if (old != null) {
            old.session.end(CloseReason.REPLACED);
        }
        // the old handler may have opened another form in its place: that one is shown, not this
        if (this.open.get(k) != mine) {
            return session;
        }
        if (vanilla) {
            this.dialogs.show(player, Fallback.dialog(session));
        } else {
            this.send.accept(player, opening);
        }
        this.shown.accept(player, session);
        return session;
    }

    /**
     * Encodes the open before any state changes, so a form over {@link Limits#SCREEN_BYTES} fails
     * here and leaves the player's current form alone. The cap sits under every transport's own: the
     * vanilla clientbound custom payload and Bukkit's {@code Messenger.MAX_MESSAGE_SIZE} are both 1 MiB.
     */
    private static byte[] encodeOpen(Form form, int id) {
        try {
            return form.open(id).encode();
        } catch (WireException e) {
            throw new IllegalArgumentException("Form " + form.screenId() + " is too big for minuit:screen ("
                    + e.getMessage() + "); split it into tabs or several forms", e);
        }
    }

    /** The player's open session, or null. */
    public FormSession session(P player) {
        Open<P> o = this.open.get(this.key.apply(player));
        return o == null || !o.session.isOpen() ? null : o.session;
    }

    /** A {@code minuit:input} message from {@code player}. Broken, stale or foreign input is dropped. */
    public void receive(P player, byte[] bytes) {
        Object k = this.key.apply(player);
        Open<P> o = this.open.get(k);
        if (o == null) {
            return;
        }
        o.player = player;
        boolean taken = false;
        if (allowed(o)) {
            try {
                InputMessage input = InputMessage.decode(bytes);
                taken = input.session() == o.session.id() && o.session.accept(input);
            } catch (WireException e) {
                taken = false;
            }
        }
        if (!taken && ++o.drops > DROPS && o.session.isOpen()) {
            if (o.vanilla) {
                this.dialogs.clear(player);
            } else {
                this.send.accept(player, new ScreenMessage.Close(o.session.id(), CloseReason.INVALID).encode());
            }
            o.session.end(CloseReason.INVALID);
        }
        if (!o.session.isOpen() && this.open.get(k) == o) {
            this.open.remove(k);
        }
    }

    /**
     * A {@code minecraft:custom} click with id {@link Fallback#ACTION} from {@code player}, its
     * payload as Java values. The click counts as one input against the rate limit.
     */
    public void receiveDialog(P player, Map<String, ?> payload) {
        Object k = this.key.apply(player);
        Open<P> o = this.open.get(k);
        if (o != null) {
            o.player = player;
        }
        if (o == null || !o.vanilla || !(payload.get(Fallback.SESSION) instanceof Number n)
                || n.intValue() != o.session.id() || !allowed(o)) {
            return;
        }
        for (InputMessage input : Fallback.inputs(o.session, payload)) {
            o.session.accept(input);
        }
        String confirm = o.session.isOpen() && payload.get(Fallback.CHECKOUT) instanceof String cart
                ? Fallback.confirm(o.session, cart) : null;
        if (confirm != null) {
            this.dialogs.show(player, confirm);
        } else if (o.session.isOpen()) {
            this.dialogs.show(player, Fallback.dialog(o.session));
        } else {
            if (this.open.get(k) == o) {
                this.open.remove(k);
                this.dialogs.clear(player);
            }
        }
    }

    /** A patch waits for the next dialog; a close takes the dialog away. */
    private void toDialog(P player, byte[] bytes) {
        if (ScreenMessage.decode(bytes) instanceof ScreenMessage.Close) {
            this.dialogs.clear(player);
        }
    }

    private boolean allowed(Open<P> o) {
        long second = this.clock.getAsLong() / 1000;
        if (second != o.second) {
            o.second = second;
            o.count = 0;
        }
        return ++o.count <= PER_SECOND;
    }

    /** The player left: their session ends without a message. */
    public void left(P player) {
        Open<P> o = this.open.remove(this.key.apply(player));
        if (o != null) {
            o.session.end(CloseReason.PLAYER);
        }
    }

    /**
     * Another server's form took the player's screen: their session here ends as replaced, without a
     * message, since the screen already shows the other form.
     */
    public void replaced(P player) {
        Open<P> o = this.open.remove(this.key.apply(player));
        if (o != null) {
            o.session.end(CloseReason.REPLACED);
        }
    }

    private static final class Open<P> {
        P player;
        FormSession session;
        final boolean vanilla;
        long second = Long.MIN_VALUE;
        int count;
        int drops;

        Open(P player, boolean vanilla) {
            this.player = player;
            this.vanilla = vanilla;
        }
    }
}
