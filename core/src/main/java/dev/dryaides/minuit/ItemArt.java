package dev.dryaides.minuit;

import java.util.List;

/**
 * How the components draw a picture that may be a Minecraft item ({@link ItemView}) or any other {@link IconArt}.
 *
 * <p>The library's {@code ItemView.draw} ignores colour and alpha, so a disabled tint never reaches an item: a veil
 * is laid over it instead. Art shrinks only by n/gs (whole screen pixels, crisp at any GUI scale) and is never
 * enlarged here.
 */
final class ItemArt {
    /** Height of a row that holds a 16 px item: one pixel above and below. */
    static final int ITEM_ROW = 18;
    /** Veil laid over an item when its place is disabled, as {@code CardComponent} and {@code AvatarComponent} do. */
    private static final int VEIL_ALPHA = 0xA0;

    private ItemArt() {
    }

    static boolean isItem(IconArt art) {
        return art instanceof ItemView;
    }

    /** The scale for art {@code artH} tall in {@code room} GUI pixels: 1, or n/gs that keeps it in whole screen pixels. */
    static float scale(Draw d, int artH, int room) {
        if (artH <= 0 || artH <= room) {
            return 1F;
        }
        int gs = Math.max(1, d.guiScale());
        int n = Math.max(1, Math.min(gs, (int) Math.floor(gs * (float) room / artH)));
        return n / (float) gs;
    }

    /** The side the art takes once fitted to {@code room}. */
    static int side(Draw d, IconArt art, int room) {
        if (art == null) {
            return 0;
        }
        return Math.round(Math.max(art.width(), art.height()) * scale(d, art.height(), room));
    }

    /** Fits for a {@link Draw} not yet known (layout): the size at scale 1 or shrunk to the room, never past it. */
    static int sideFor(IconArt art, int room) {
        if (art == null) {
            return 0;
        }
        return Math.min(room, Math.max(art.width(), art.height()));
    }

    /**
     * Draws {@code art} centred in the square {@code room x room} at {@code (x, y)}, shrunk crisply if taller. A
     * disabled item gets a dark veil; other art is drawn in white. Prefer the overload with a palette.
     */
    static int fit(Draw d, IconArt art, int x, int y, int room, boolean enabled) {
        return fit(d, null, art, x, y, room, 0xFFFFFFFF, enabled);
    }

    /**
     * Draws {@code art} centred in the square {@code room x room} at {@code (x, y)} in {@code color}, shrunk crisply
     * if taller than {@code room}. When {@code enabled} is false and the art is an item, the palette's surface veils
     * it. Returns the side drawn.
     */
    static int fit(Draw d, Palette p, IconArt art, int x, int y, int room, int color, boolean enabled) {
        if (art == null || room <= 0) {
            return 0;
        }
        float s = scale(d, art.height(), room);
        int w = Math.round(art.width() * s);
        int h = Math.round(art.height() * s);
        int left = x + (room - w) / 2;
        int top = y + (room - h) / 2;
        if (s == 1F) {
            art.draw(d, left, top, color, 1F);
        } else {
            d.push(left, top, s);
            art.draw(d, 0, 0, color, 1F);
            d.pop();
        }
        if (!enabled && isItem(art)) {
            int veil = p == null ? 0x000000 : p.surface();
            d.fill(left, top, left + w, top + h, ((VEIL_ALPHA & 0xFF) << 24) | (veil & 0xFFFFFF));
        }
        return Math.max(w, h);
    }

    /**
     * Draws {@code art} in a row: from {@code left}, centred down a band {@code bandH} tall at {@code top}, shrunk
     * crisply (n/gs) if taller than {@code room}, an item veiled when not {@code enabled}. Returns the width drawn.
     */
    static int inRow(Draw d, Palette p, IconArt art, int left, int top, int bandH, int room, int color, boolean enabled) {
        if (art == null || room <= 0) {
            return 0;
        }
        float s = scale(d, art.height(), room);
        int w = Math.round(art.width() * s);
        int h = Math.round(art.height() * s);
        int y = top + (bandH - h) / 2;
        if (s == 1F) {
            art.draw(d, left, y, color, 1F);
        } else {
            d.push(left, y, s);
            art.draw(d, 0, 0, color, 1F);
            d.pop();
        }
        if (!enabled && isItem(art)) {
            int veil = p == null ? 0x000000 : p.surface();
            d.fill(left, y, left + w, y + h, ((VEIL_ALPHA & 0xFF) << 24) | (veil & 0xFFFFFF));
        }
        return w;
    }

    /** The width {@link #inRow} will give art in {@code room}, without a {@link Draw}: at most the room (a bound). */
    static int rowWidth(IconArt art, int room) {
        if (art == null) {
            return 0;
        }
        return art.height() <= room ? art.width() : Math.min(art.width(), Math.max(1, room * art.width() / Math.max(1, art.height())));
    }

    /** Count (over 1, short as {@code 1.2k}) and durability bar for a 16 px item drawn at {@code (x, y)}. */
    static void decorations(Draw d, ItemView item, int x, int y) {
        if (item == null) {
            return;
        }
        d.itemDecorations(item, x, y, item.count() > 1 ? SlotComponent.shortCount(item.count()) : null);
    }

    /** The game's own tooltip for an item under the mouse, during render. False for other art. */
    static boolean hoverTooltip(Draw d, IconArt art, int mx, int my) {
        return art instanceof ItemView item && d.itemTooltip(item, mx, my);
    }

    /** A tooltip that works where the game's cannot: the item's name. */
    static List<String> nameTip(ItemView item) {
        String n = name(item);
        return n.isEmpty() ? List.of() : List.of(n);
    }

    /** The item's display name through the game's {@link ItemLookup}, or a name made from its id. */
    static String name(ItemView item) {
        if (item == null || item.id() == null) {
            return "";
        }
        String n = ItemLookup.game().name(item.id());
        if (n != null && !n.isBlank()) {
            return n;
        }
        String id = item.id();
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
