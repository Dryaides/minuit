package dev.dryaides.minuit;

/**
 * A picture that may arrive after the screen opens: a map thumbnail sent by the server, a player
 * skin, a screenshot. It starts {@link State#PENDING}; whoever loads it calls {@link #ready} or
 * {@link #failed}, and every widget that draws it shows a placeholder until then. In game,
 * {@code dev.dryaides.minuit.client.AsyncImages} turns PNG bytes into one.
 */
public final class AsyncImage {

    public enum State { PENDING, READY, FAILED }

    private volatile State state;
    private volatile String texture;
    private volatile int width;
    private volatile int height;

    private AsyncImage(State state, String texture, int width, int height) {
        this.state = state;
        this.texture = texture;
        this.width = width;
        this.height = height;
    }

    /** A picture still on its way. */
    public static AsyncImage pending() {
        return new AsyncImage(State.PENDING, null, 0, 0);
    }

    /** A picture already in a texture, {@code namespace:path/to/file.png}, of {@code width} by {@code height}. */
    public static AsyncImage of(String texture, int width, int height) {
        return new AsyncImage(State.READY, texture, width, height);
    }

    /** The picture is in {@code texture}; widgets draw it from their next frame. */
    public void ready(String texture, int width, int height) {
        this.texture = texture;
        this.width = width;
        this.height = height;
        this.state = State.READY;
    }

    /** The picture will not come: unreadable or absent. The placeholder stays. */
    public void failed() {
        this.state = State.FAILED;
    }

    public State state() {
        return this.state;
    }

    public String texture() {
        return this.texture;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    /**
     * Draws the picture stretched to the box, or the placeholder: a well of the theme with its edge.
     * A null image draws the placeholder.
     */
    public static void draw(Draw d, Palette p, AsyncImage image, int x, int y, int w, int h) {
        if (image != null && image.state == State.READY && image.texture != null) {
            d.image(image.texture, x, y, w, h, image.width, image.height, 1F);
            return;
        }
        d.fill(x, y, x + w, y + h, p.well());
        Shapes.ring(d, x, y, w, h, 0, p.wellEdge());
        if (image != null && image.state == State.PENDING && w >= 12 && h >= 6) {
            // Three dots that light in turn, so a picture on its way does not read as a missing one.
            int lit = BaseComponent.reducedMotion ? -1 : (int) (BaseComponent.clock.getAsLong() / 300 % 3);
            int cx = x + w / 2 - 4;
            int cy = y + h / 2 - 1;
            for (int i = 0; i < 3; i++) {
                d.fill(cx + i * 3, cy, cx + i * 3 + 2, cy + 2, i == lit ? p.textSoft() : p.textFaint());
            }
        }
    }
}
