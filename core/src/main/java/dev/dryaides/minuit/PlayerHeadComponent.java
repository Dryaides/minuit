package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.FitCache;

import java.util.function.Function;

/**
 * A player's face: the face of the skin with its hat layer over it, scaled to the box. The skin is an
 * {@link AsyncImage} of the 64 by 64 skin texture; in game {@code dev.dryaides.minuit.client.PlayerSkins}
 * hands one out, ready at once with the default Steve or Alex skin of that player and swapped for the
 * real skin when it loads. A pending or failed skin draws the placeholder well.
 */
public class PlayerHeadComponent extends BaseComponent {

    public AsyncImage skin;

    public PlayerHeadComponent(Box box, AsyncImage skin) {
        super(box);
        this.skin = skin;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        int size = Math.min(this.w, this.h);
        draw(d, theme.palette(), this.skin, this.x + (this.w - size) / 2, this.y + (this.h - size) / 2, size);
    }

    /** Draws the face of {@code skin} in a {@code size} square, for a list row or a grid card. */
    public static void draw(Draw d, Palette p, AsyncImage skin, int x, int y, int size) {
        if (skin == null || skin.state() != AsyncImage.State.READY || skin.texture() == null) {
            AsyncImage.draw(d, p, skin, x, y, size, size);
            return;
        }
        int texW = Math.max(64, skin.width());
        int texH = Math.max(64, skin.height());
        d.blitRegion(skin.texture(), x, y, size, size, 8, 8, 8, 8, texW, texH, 1F);
        d.blitRegion(skin.texture(), x, y, size, size, 40, 8, 8, 8, texW, texH, 1F);
    }

    /**
     * A list row of a head and a name: a lobby, a podium, a supporter roll. The head takes the row
     * height less two pixels a side.
     */
    public static <T> ListComponent.RowRenderer<T> row(Function<T, AsyncImage> skin, Function<T, String> name) {
        FitCache names = new FitCache();
        return (d, theme, item, row, hovered, selected) -> {
            Palette p = theme.palette();
            int size = row.h() - 4;
            draw(d, p, skin.apply(item), row.x(), row.y() + 2, size);
            d.text(names.fit(d, item, name.apply(item), row.w() - size - 4).cut(), row.x() + size + 4,
                    row.y() + (row.h() - 8) / 2, selected ? p.text() : p.textSoft());
        };
    }

    /** A grid card of a head over a name, for a podium or a seeker reel. */
    public static <T> GridComponent.CellRenderer<T> cell(Function<T, AsyncImage> skin, Function<T, String> name) {
        FitCache names = new FitCache();
        return (d, theme, item, cell, hovered, selected) -> {
            Palette p = theme.palette();
            FrameStyle f = theme.frame();
            f.section(d, p, cell.x(), cell.y(), cell.w(), cell.h());
            if (selected || hovered) {
                Shapes.ring(d, cell.x() - 1, cell.y() - 1, cell.w() + 2, cell.h() + 2, f.corner() + 1,
                        selected ? p.accent() : p.wellEdge());
            }
            int size = Math.max(8, Math.min(cell.w() - 8, cell.h() - 18));
            draw(d, p, skin.apply(item), cell.x() + (cell.w() - size) / 2, cell.y() + 4, size);
            d.centered(names.fit(d, item, name.apply(item), cell.w() - 6).cut(), cell.x() + cell.w() / 2,
                    cell.y() + cell.h() - 12, selected ? p.text() : p.textSoft());
        };
    }
}
