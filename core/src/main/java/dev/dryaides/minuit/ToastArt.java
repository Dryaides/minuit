package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;
import java.util.List;

/**
 * The look of a toast, after the game's advancement toast, shared by the game, the bench and the site:
 * a 160 by 32 card, the icon at (8, 8), the title in the accent over the body. A body too long for one
 * line shows the title alone for {@link #TITLE_MS}, then fades into two body lines.
 */
public final class ToastArt {

    public static final int W = 160;
    public static final int H = 32;
    public static final int TITLE_MS = 1500;
    public static final int FADE_MS = 300;

    private ToastArt() {
    }

    /** @param shownMs time since the toast came in */
    public static void draw(Draw d, Theme theme, ToastSpec toast, int x, int y, long shownMs) {
        Palette p = theme.palette();
        theme.frame().toast(d, p, x, y, W, H);
        int title = Ink.readable(p, p.accent(), Ink.seen(p, p.surface()), Ink.TEXT);
        if (toast.icon() != null) {
            toast.icon().draw(d, x + 8, y + 8, p.text(), 1F);
        }
        int tx = x + (toast.icon() != null ? 30 : 10);
        int room = x + W - 5 - tx;
        List<String> body = toast.body().isEmpty() ? List.of() : d.wrap(toast.body(), room);
        if (body.isEmpty()) {
            d.textFit(toast.title(), tx, y + 12, room, title);
            return;
        }
        if (body.size() == 1) {
            d.textFit(toast.title(), tx, y + 7, room, title);
            d.text(body.get(0), tx, y + 18, p.text());
            return;
        }
        // The title fades out over the first half, the body in over the second: never both at once.
        float fade = Maths.clamp((shownMs - TITLE_MS) / (float) FADE_MS, 0F, 1F);
        float out = Maths.clamp(1F - 2F * fade, 0F, 1F);
        float in = Maths.clamp(2F * fade - 1F, 0F, 1F);
        if (out > 0F) {
            d.textFit(toast.title(), tx, y + 11, room, Shapes.alpha(title, Math.round(255 * out)));
        }
        if (in > 0F) {
            // The card stays 32 high, as the game's: two lines, the second cut with an ellipsis when more follow.
            int top = y + (H - 2 * 9) / 2 + 1;
            int color = Shapes.alpha(p.text(), Math.round(255 * in));
            d.text(body.get(0), tx, top, color);
            String second = body.size() > 2 ? d.ellipsize(body.get(1) + " " + body.get(2), room) : body.get(1);
            d.text(second, tx, top + 9, color);
        }
    }
}
