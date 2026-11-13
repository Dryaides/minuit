package dev.dryaides.minuit.internal.showcase;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.CopyFieldComponent;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.FrameStyle;
import dev.dryaides.minuit.ListComponent;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.ScrollComponent;
import dev.dryaides.minuit.Shapes;
import dev.dryaides.minuit.SideRailComponent;
import dev.dryaides.minuit.TextMeasure;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.Words;

import java.util.List;
import java.util.function.Consumer;

/**
 * The game's demo of the kit: a rail of categories on the left, then either the home mosaic of live cards or the
 * category's scene list beside one live scene; under it the component's class, one sentence and its page on the
 * site. Every scene is the site's own ({@link SiteScenes}), played with the real mouse and keyboard in the theme
 * the page was opened in. One panel shows one place; the host builds a new panel for {@code open}, as the theme
 * page does for a pick.
 */
public final class ShowcasePanel extends PanelComponent {

    /** The place of the home mosaic; any other place is a scene id of {@link Showcase#ENTRIES}. */
    public static final String HOME = "home";

    private static final int RAIL = SideRailComponent.SIZE + SideRailComponent.POP;
    /** The scene list never narrower than this, nor wider than half the panel. */
    private static final int LIST_MIN = 84;
    private static final int ROW_H = 14;
    private static final int GAP = 6;
    /** The header strip and the room under it. */
    private static final int HEAD = 28;
    /** The two info lines and the copy field beside them. */
    private static final int FOOT = 32;

    private final int pw;
    private final int ph;
    private final Showcase.Entry entry;
    private final Showcase.Category category;
    private final Box list;
    private final Box live;
    private final Box info;

    /**
     * @param place the scene to show ({@link #HOME} or an id of {@link Showcase#ENTRIES}); an unknown one shows home
     * @param open  a place the player asked for, for the host to build a new panel on
     * @param back  the arrow and Escape on home: back to the page the demo was opened from; in a scene they
     *              go to home first
     */
    public ShowcasePanel(Words words, Theme theme, int screenWidth, int screenHeight, String place,
                         Consumer<String> open, Runnable back) {
        super(theme, words, left(screenWidth), top(screenHeight));
        this.pw = width(screenWidth);
        this.ph = height(screenHeight);
        this.entry = Showcase.entry(place);
        this.category = this.entry == null ? Showcase.Category.HOME : this.entry.category();
        McScenes.words = words;
        addBack(this.entry == null ? back : () -> open.accept(HOME));
        addRail(open);
        Box body = at(new Box("body", 8, HEAD, this.pw - 16, this.ph - HEAD - FOOT));
        int copyW = Math.min(160, this.pw / 3);
        this.info = at(new Box("info", 10, this.ph - FOOT + 4, this.pw - 20 - copyW - 8, 22));
        add(new CopyFieldComponent(at(new Box("page", this.pw - 8 - copyW, this.ph - FOOT + 7, copyW, 16)),
                this.entry == null ? Showcase.SITE : this.entry.url()));
        if (this.entry == null) {
            this.list = null;
            this.live = body;
            addHome(body, open);
        } else {
            int listW = listWidth(this.pw);
            this.list = new Box("scenes", body.x(), body.y(), listW, body.h());
            this.live = new Box("live", body.x() + listW + GAP, body.y(), body.w() - listW - GAP, body.h());
            addScene(open);
        }
    }

    /**
     * The scene list wide enough for the longest scene name of any category, so the column keeps one width
     * from scene to scene and no name ends in an ellipsis: the row's insets of {@link #row} and the list's.
     */
    static int listWidth(int pw) {
        int name = 0;
        for (Showcase.Entry e : Showcase.ENTRIES) {
            name = Math.max(name, TextMeasure.width(e.name()));
        }
        return Math.max(LIST_MIN, Math.min(pw / 2, name + 4 + 4 + ListComponent.BAR + 6));
    }

    /** The panel's width on a screen {@code screenWidth} GUI pixels wide, the rail left out. */
    public static int width(int screenWidth) {
        return Math.max(300, Math.min(480, screenWidth - RAIL - 12));
    }

    public static int height(int screenHeight) {
        return Math.max(200, Math.min(320, screenHeight - 12));
    }

    public static int left(int screenWidth) {
        return (screenWidth - width(screenWidth) + RAIL) / 2;
    }

    public static int top(int screenHeight) {
        return (screenHeight - height(screenHeight)) / 2;
    }

    @Override
    public int width() {
        return this.pw;
    }

    @Override
    public int height() {
        return this.ph;
    }

    private void addRail(Consumer<String> open) {
        SideRailComponent rail = add(new SideRailComponent(bounds(), SideRailComponent.Edge.LEFT));
        for (Showcase.Category c : Showcase.Category.values()) {
            String tip = this.words.get(c.nameKey());
            Runnable go = () -> open.accept(c == Showcase.Category.HOME ? HOME : Showcase.of(c).get(0).id());
            if (c.art() != null) {
                rail.action(c.art(), tip, go);
            } else {
                rail.action(c.icon(), tip, go);
            }
        }
        rail.selected(this.category.ordinal(), true);
    }

    /** The mosaic: every home card live, in as many columns as fit, each in the shortest column so far. */
    private void addHome(Box body, Consumer<String> open) {
        ScrollComponent scroll = add(new ScrollComponent(body));
        scroll.fit = false;
        scroll.step = 20;
        scroll.bottomRoom = 4;
        int usable = body.w() - ScrollComponent.BAR - 2;
        int cardW = SiteScenes.HW + 2 * SiteScenes.PAD;
        int cols = Math.max(1, (usable + GAP) / (cardW + GAP));
        int x0 = body.x() + Math.max(0, (usable - cols * cardW - (cols - 1) * GAP) / 2);
        int[] heights = new int[cols];
        for (String[] card : Showcase.HOME) {
            SiteScenes.Stage stage = SiteScenes.home(card[0], this.theme);
            if (stage == null) {
                continue;
            }
            int col = 0;
            for (int i = 1; i < cols; i++) {
                if (heights[i] < heights[col]) {
                    col = i;
                }
            }
            Box box = new Box(card[0], x0 + col * (cardW + GAP), body.y() + 2 + heights[col], Math.min(stage.w, usable),
                    stage.h);
            String target = card[1];
            scroll.add(new StageView(box, stage, target == null ? null : () -> open.accept(target)));
            heights[col] += stage.h + GAP;
        }
    }

    private void addScene(Consumer<String> open) {
        ListComponent<Showcase.Entry> rows = add(new ListComponent<Showcase.Entry>(new Box("scenes", this.list.x() + 2,
                this.list.y() + 3, this.list.w() - 4, this.list.h() - 6), ROW_H, ShowcasePanel::row,
                (Showcase.Entry picked) -> open.accept(picked.id())));
        rows.setItems(Showcase.of(this.category));
        rows.selected = this.entry;
        SiteScenes.Stage stage = SiteScenes.stage(this.entry.id(), this.theme);
        if (stage != null) {
            add(new StageView(new Box("stage", this.live.x() + 4, this.live.y() + 4, this.live.w() - 8,
                    this.live.h() - 8), stage, null));
        }
    }

    private static void row(Draw d, Theme theme, Showcase.Entry e, Box row, boolean hovered, boolean selected) {
        Palette p = theme.palette();
        // The plate (primary when selected, the fading raised wash on hover) is ListComponent's; this draws the label only.
        int ink = selected ? Shapes.textFor(p, p.primaryText(), p.primary()) : hovered ? Shapes.textOn(p, p.raised()) : p.text();
        d.textFit(e.name(), row.x() + 4, row.y() + 3, row.w() - ListComponent.BAR - 6, ink);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        List<String> tip = super.tooltipAt(mx, my);
        if (tip == null && this.entry != null && this.info.contains(mx, my)) {
            return List.of(this.entry.className(), this.entry.sentence());
        }
        return tip;
    }

    @Override
    protected void drawBackground(Draw d, int mx, int my) {
        Palette p = this.theme.palette();
        FrameStyle frame = this.theme.frame();
        frame.panel(d, p, this.left, this.top, this.pw, this.ph);
        Box header = at(new Box("header", 6, 6, this.pw - 12, 18));
        frame.header(d, p, header.x(), header.y(), header.w(), header.h());
        String title = this.words.get("minuit.demo.title");
        int tx = this.left + titleX();
        int ty = this.top + 10;
        d.text(title, tx, ty, p.text());
        d.textFit(this.words.get(this.category.nameKey()), tx + d.width(title) + 8, ty,
                this.pw - 14 - titleX() - d.width(title) - 8, p.textSoft());
        if (this.list != null) {
            frame.section(d, p, this.list.x(), this.list.y(), this.list.w(), this.list.h());
            frame.section(d, p, this.live.x(), this.live.y(), this.live.w(), this.live.h());
        }
        String name = this.entry == null ? "minuit" : this.entry.className();
        String sentence = this.entry == null ? this.words.get("minuit.demo.home_hint") : this.entry.sentence();
        d.textFit(name, this.info.x(), this.info.y(), this.info.w(), p.text());
        d.textFit(sentence, this.info.x(), this.info.y() + 11, this.info.w(), p.textSoft());
    }
}
