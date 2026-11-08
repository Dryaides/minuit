package dev.dryaides.minuit;

/**
 * What a toast says: a title, a body that wraps, an optional 16 px icon. {@link ToastArt} draws it;
 * in game {@code client.Toasts.show} hands it to the game's toast stack.
 *
 * @param body   may be empty
 * @param icon   null for none
 * @param millis how long it stays, before the player's Notification Time option scales it
 * @param key    null, or a key: showing a toast with the key of one still on screen replaces its text
 *               and restarts its time
 */
public record ToastSpec(String title, String body, IconArt icon, int millis, String key) {

    /** 5 seconds, as the game's advancement toast. */
    public static final int DEFAULT_MS = 5000;

    public ToastSpec {
        title = title == null ? "" : title;
        body = body == null ? "" : body;
        millis = Math.max(500, millis);
    }

    public static ToastSpec of(String title, String body) {
        return new ToastSpec(title, body, null, DEFAULT_MS, null);
    }

    public ToastSpec icon(IconArt icon) {
        return new ToastSpec(this.title, this.body, icon, this.millis, this.key);
    }

    public ToastSpec millis(int ms) {
        return new ToastSpec(this.title, this.body, this.icon, ms, this.key);
    }

    public ToastSpec key(String key) {
        return new ToastSpec(this.title, this.body, this.icon, this.millis, key);
    }
}
