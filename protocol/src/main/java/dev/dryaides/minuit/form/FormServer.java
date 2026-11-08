package dev.dryaides.minuit.form;

import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.ScreenMessage;
import dev.dryaides.minuit.wire.WireException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
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
    private int nextSession = 1;

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
     * Opens {@code form} on the player's screen; a form already open there closes as replaced. That
     * holds for a nested open too: opened from the outer form's own handler, the new form ends the
     * outer session, whose handler gets {@code closed(REPLACED)} before the new form shows.
     */
    public FormSession open(P player, Form form, FormHandler handler) {
        int id = this.nextSession++;
        if (this.required != null && !this.required.test(player)) {
            FormSession refused = new FormSession(id, form, handler, bytes -> {
            });
            refused.end(CloseReason.UNSUPPORTED);
            return refused;
        }
        boolean vanilla = this.dialogs != null && !this.dialogs.hasForms(player);
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
            this.send.accept(player, form.open(id).encode());
        }
        return session;
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
        if (o.session.isOpen()) {
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
