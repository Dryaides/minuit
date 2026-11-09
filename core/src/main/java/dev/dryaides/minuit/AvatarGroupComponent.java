package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Several {@link AvatarComponent}s overlapping in a row, with a +N for those that do not show.
 *
 * <pre>{@code
 * add(new AvatarGroupComponent(at(new Box("party", 10, 10, 80, 20)),
 *         List.of(new AvatarGroupComponent.Member("Alex Stone", skin), new AvatarGroupComponent.Member("Steve", null)))
 *         .max(4)
 *         .onClick(this::openProfile));
 * }</pre>
 *
 * <p>Heads show as many as fit the box and {@link #max(int)}. The one under the pointer comes to the front; its
 * name is a tooltip, and the +N's tooltip names those it hides. With {@link #onClick(IntConsumer)} the group is one
 * keyboard stop: left and right walk the heads, Enter opens. Without it, the group takes no input.
 */
public class AvatarGroupComponent extends BaseComponent {
    /** One person. The skin may be null. */
    public record Member(String name, AsyncImage skin) {
        public Member {
            name = name == null ? "" : name;
        }
    }

    /** How much of a head the next one covers, as a part of its side. */
    private static final int OVERLAP_DIV = 3;
    private static final int RING = 1;
    private static final int FACE = 8;
    private static final int MAX_NAMES_IN_TIP = 12;

    private final List<Member> members = new ArrayList<>();
    private List<Picture> pictures = List.of();
    private String[] initials = new String[0];
    private int max = Integer.MAX_VALUE;
    private IntConsumer onClick;
    private IntConsumer onMore;
    private int cursor;
    private int moreFor = -1;
    private String moreLabel = "";

    public AvatarGroupComponent(Box box, Collection<Member> members) {
        super(box);
        setMembers(members);
    }

    /** New people. Nulls are dropped. */
    public final void setMembers(Collection<Member> people) {
        members.clear();
        if (people != null) {
            for (Member member : people) {
                if (member != null) {
                    members.add(member);
                }
            }
        }
        initials = new String[members.size()];
        for (int i = 0; i < initials.length; i++) {
            initials[i] = AvatarComponent.initialsOf(members.get(i).name());
        }
        cursor = 0;
    }

    /** The most heads shown. Any others count in the +N. */
    /**
     * A picture for each member, in the same order, in place of the head or initials: null entries keep
     * those. See {@link AvatarComponent#picture(Picture)}.
     */
    public AvatarGroupComponent pictures(List<Picture> pictures) {
        this.pictures = pictures == null ? List.of() : new ArrayList<>(pictures);
        return this;
    }

    public AvatarGroupComponent max(int max) {
        this.max = Math.max(1, max);
        return this;
    }

    /** Called with the person's index when a head is clicked or Enter is pressed on it. */
    public AvatarGroupComponent onClick(IntConsumer onClick) {
        this.onClick = onClick;
        return this;
    }

    /** Called with the number hidden when the +N is clicked. */
    public AvatarGroupComponent onMore(IntConsumer onMore) {
        this.onMore = onMore;
        return this;
    }

    public AvatarGroupComponent active(boolean active) {
        this.active = active;
        return this;
    }

    public int count() {
        return members.size();
    }

    private int side() {
        return Math.max(0, h - 2 * RING);
    }

    /**
     * From one head to the next. What the next head and its ring leave of a head ends on a whole pixel of the 8 px
     * skin face drawn over {@code side}, so no sliver of a face pixel shows past the ring.
     */
    private int step() {
        int side = side();
        int bare = side - side / OVERLAP_DIV - RING;
        int facePixels = 1;
        while (faceEdge(facePixels, side) < bare && facePixels < FACE) {
            facePixels++;
        }
        return Math.max(1, faceEdge(facePixels, side) + RING);
    }

    /** Where face pixel {@code n} starts on a head {@code side} wide, as a nearest-pixel scale samples it. */
    static int faceEdge(int n, int side) {
        return (int) Math.ceil(n * side / (double) FACE - 0.5D);
    }

    /** How many heads are drawn. */
    int shownHeads() {
        int side = side();
        if (side <= 0 || members.isEmpty()) {
            return 0;
        }
        int wanted = Math.min(max, members.size());
        // First with no +N: when all of them fit, a +1 taking the place of the last head would help no one.
        if (wanted == members.size() && widthOf(wanted, false) <= w) {
            return wanted;
        }
        int heads = wanted;
        while (heads > 0 && widthOf(heads, true) > w) {
            heads--;
        }
        return heads;
    }

    /** The number in the +N; 0 for none. */
    int hidden() {
        return members.size() - shownHeads();
    }

    private String moreText(int hidden) {
        if (hidden != moreFor) {
            moreFor = hidden;
            moreLabel = "+" + hidden;
        }
        return moreLabel;
    }

    private int moreWidth(int hidden) {
        return Math.max(side(), TextMeasure.width(moreText(hidden)) - 1 + 2 * 2);
    }

    private int widthOf(int heads, boolean more) {
        int side = side();
        int width = heads <= 0 ? 0 : side + (heads - 1) * step() + 2 * RING;
        if (more) {
            int morePart = moreWidth(members.size() - heads) + 2 * RING;
            width = heads <= 0 ? morePart : heads * step() + morePart;
        }
        return width;
    }

    private int headX(int index) {
        return x + RING + index * step();
    }

    private int headY() {
        return y + RING;
    }

    /** A head's place, for a script or a test. */
    Box headBox(int index) {
        return new Box("head" + index, headX(index), headY(), side(), side());
    }

    /** The +N's place, for a script or a test. Empty when there is none. */
    Box moreBox() {
        int heads = shownHeads();
        int hidden = members.size() - heads;
        if (hidden <= 0) {
            return new Box("more", x, y, 0, 0);
        }
        return new Box("more", headX(heads), headY(), moreWidth(hidden), side());
    }

    /** The person under a point, -1 for none, or -2 on the +N. Later heads lie above earlier ones. */
    int headAt(double mx, double my) {
        if (my < headY() || my >= headY() + side()) {
            return -1;
        }
        int heads = shownHeads();
        if (moreBox().contains(mx, my)) {
            return -2;
        }
        for (int i = heads - 1; i >= 0; i--) {
            if (headBox(i).contains(mx, my)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        int side = side();
        if (side <= 0 || w <= 0) {
            return;
        }
        Palette p = t.palette();
        int heads = shownHeads();
        int hover = hovered ? headAt(mx, my) : -1;
        int ring = FormLook.surface(p);
        int hidden = members.size() - heads;
        for (int i = 0; i < heads; i++) {
            if (i == hover) {
                continue;
            }
            // A covered head ends in a straight cut on the next ring, which then reads whole around the next corner.
            boolean covered = i < heads - 1 || hidden > 0;
            if (covered) {
                d.scissor(headX(i) - RING, y, headX(i + 1) - RING, y + h);
            }
            drawHead(d, t, i, ring, side);
            if (covered) {
                d.endScissor();
            }
        }
        if (hidden > 0) {
            drawMore(d, t, hidden, ring, hover == -2);
        }
        if (hover >= 0) {
            drawHead(d, t, hover, active ? p.text() : ring, side);
        }
    }

    private void drawHead(Draw d, Theme t, int index, int ring, int side) {
        int left = headX(index);
        int top = headY();
        int round = t.frame().plateCorner(side, side) > 0 ? t.frame().plateCorner(side, side) + 1 : 0;
        if (round > 0) {
            Shapes.rounded(d, left - RING, top - RING, side + 2 * RING, side + 2 * RING, round + RING, ring);
        } else {
            t.frame().plate(d, t.palette(), left - RING, top - RING, side + 2 * RING, side + 2 * RING, ring, 0);
        }
        paintTile(d, t, index, ring, side, left, top);
        if (round > 0) {
            PictureLook.corners(d, left, top, side, side, round, ring);
        }
    }

    private void paintTile(Draw d, Theme t, int index, int ring, int side, int left, int top) {
        Member member = members.get(index);
        boolean covered = index < shownHeads() - 1 || hidden() > 0;
        int bare = ring == FormLook.surface(t.palette()) && covered ? step() - RING : side;
        Picture picture = index < pictures.size() ? pictures.get(index) : null;
        if (picture != null && picture.state() == AsyncImage.State.READY) {
            AvatarComponent.paintPicture(d, t, picture, AvatarComponent.faceOf(t.palette(), member.name()), active, left, top, side);
            return;
        }
        AvatarComponent.paint(d, t, initials[index], AvatarComponent.faceOf(t.palette(), member.name()), member.skin(), active, left, top, side, bare);
    }

    private void drawMore(Draw d, Theme t, int hidden, int ring, boolean hover) {
        Palette p = t.palette();
        int bx = headX(shownHeads());
        int by = headY();
        int bw = moreWidth(hidden);
        int bh = side();
        t.frame().plate(d, p, bx - RING, by - RING, bw + 2 * RING, bh + 2 * RING, hover && active ? p.text() : ring, 0);
        int face = active ? FormLook.quietFace(p) : FormLook.disabledFace(p);
        t.frame().raised(d, p, bx, by, bw, bh, face, Shapes.Relief.FLAT);
        if (bh >= FormLook.FONT_H) {
            String text = moreText(hidden);
            int ink = active ? Ink.on(p, face) : p.disabledText();
            d.text(text, bx + (bw - TextMeasure.width(text) + 1) / 2, by + (bh - FormLook.FONT_H + 1) / 2, ink);
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        int at = headAt(mx, my);
        if (at >= 0) {
            String name = members.get(at).name();
            return name.isEmpty() ? tooltip : List.of(name);
        }
        if (at == -2) {
            List<String> names = new ArrayList<>();
            int heads = shownHeads();
            for (int i = heads; i < members.size() && names.size() < MAX_NAMES_IN_TIP; i++) {
                names.add(members.get(i).name());
            }
            int left = members.size() - heads - names.size();
            if (left > 0) {
                names.add("+" + left);
            }
            return names;
        }
        return tooltip;
    }

    /** Under the row, where a name does not cover the heads beside the one it names. */
    @Override
    public int tooltipY(double mx, double my) {
        return y + h + TIP_RISE + 1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !active || button != 0 || !isOver(mx, my)) {
            return false;
        }
        int at = headAt(mx, my);
        if (at >= 0 && onClick != null) {
            BaseComponent.click();
            cursor = at;
            onClick.accept(at);
            return true;
        }
        if (at == -2 && onMore != null) {
            BaseComponent.click();
            onMore.accept(hidden());
            return true;
        }
        return false;
    }

    @Override
    protected boolean navigable() {
        return visible && active && settled() && onClick != null && shownHeads() > 0;
    }

    int cursor() {
        return cursor;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!navigable()) {
            return false;
        }
        int heads = shownHeads();
        switch (key) {
            case LEFT -> cursor = Math.floorMod(cursor - 1, heads);
            case RIGHT -> cursor = Math.floorMod(cursor + 1, heads);
            case HOME -> cursor = 0;
            case END -> cursor = heads - 1;
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean activate() {
        if (!navigable()) {
            return false;
        }
        BaseComponent.click();
        Objects.requireNonNull(onClick).accept(Math.min(cursor, shownHeads() - 1));
        return true;
    }

    @Override
    protected Box focusBox() {
        if (shownHeads() > 0) {
            return headBox(Math.min(cursor, shownHeads() - 1));
        }
        return super.focusBox();
    }
}
