package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * Text with the game's formatting codes ({@link RichText}), wrapped to the box. A link is underlined in
 * the accent, shows its URL as tooltip and opens through {@link #links} on a click.
 */
public class RichTextComponent extends BaseComponent {

    /** Opens a web link; the host asks the player first, as the game's chat links do. */
    @FunctionalInterface
    public interface LinkOpener {
        void open(String url);
    }

    /** Plugged by the host; links do nothing until then. */
    public static volatile LinkOpener links = url -> { };

    private static final int LINE = 10;

    private final RichText text;
    private int color;
    private int maxLines = Integer.MAX_VALUE;
    private boolean centered;
    /** The link areas of the last frame, screen coordinates, with their URL. */
    private final List<Area> areas = new ArrayList<>();

    private record Area(int x0, int y0, int x1, int y1, String url) {
        boolean contains(double mx, double my) {
            return mx >= this.x0 && mx < this.x1 && my >= this.y0 && my < this.y1;
        }
    }

    public RichTextComponent(Box box, String formatted) {
        super(box);
        this.text = RichText.parse(formatted);
    }

    /** The colour of text without a colour code; the theme's text colour by default. */
    public RichTextComponent color(int argb) {
        this.color = argb;
        return this;
    }

    /** At most {@code max} lines; the rest is left out. */
    public RichTextComponent lines(int max) {
        this.maxLines = Math.max(1, max);
        return this;
    }

    public RichTextComponent centered() {
        this.centered = true;
        return this;
    }

    public RichText text() {
        return this.text;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        int base = this.color != 0 ? this.color : p.text();
        this.areas.clear();
        List<RichText> lines = this.text.wrap(d, this.w);
        int y = this.y;
        for (int i = 0; i < lines.size() && i < this.maxLines; i++) {
            RichText line = lines.get(i);
            int x = this.centered ? this.x + (this.w - line.width(d)) / 2 : this.x;
            d.rich(line, x, y, base, p.accent());
            int pen = x;
            for (RichText.Run run : line.runs()) {
                int rw = d.styledWidth(run.text(), run.style());
                if (run.link() != null) {
                    this.areas.add(new Area(pen, y - 1, pen + rw, y + LINE, run.link()));
                }
                pen += rw;
            }
            y += LINE;
        }
    }

    private String linkAt(double mx, double my) {
        for (Area area : this.areas) {
            if (area.contains(mx, my)) {
                return area.url();
            }
        }
        return null;
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        String url = isOver(mx, my) ? linkAt(mx, my) : null;
        return url != null ? List.of(url) : super.tooltipAt(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        String url = this.active && button == 0 && isOver(mx, my) ? linkAt(mx, my) : null;
        if (url == null) {
            return false;
        }
        click();
        links.open(url);
        return true;
    }
}
