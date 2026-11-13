package dev.dryaides.minuit;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * A grid of colour swatches to pick one from: a dye colour, a team colour, a wool block. The arrows move the choice
 * as in a radio group; past the grid's edge they go on to the next stop.
 *
 * <pre>{@code
 * add(new SwatchPickerComponent(at(new Box("dye", 10, 10, 0, 0)), SwatchPickerComponent.DYES, 14, i -> setDye(i))
 *         .names(dyeNames));
 * }</pre>
 *
 * <p>A box of width 0 lays the swatches in {@link #columns(int)} columns, 8 by default; a wider box fits as many as it
 * holds. Height 0 takes the rows the swatches need. The swatch colours are the mod's data, not theme roles; the
 * edges and rings around them take the theme's.
 */
public class SwatchPickerComponent extends BaseComponent {

    /** The sixteen dye colours in the game's order, white to black. */
    public static final int[] DYES = {
            0xFFF9FFFE, 0xFFF9801D, 0xFFC74EBD, 0xFF3AB3DA, 0xFFFED83D, 0xFF80C71F, 0xFFF38BAA, 0xFF474F52,
            0xFF9D9D97, 0xFF169C9C, 0xFF8932B8, 0xFF3C44AA, 0xFF835432, 0xFF5E7C16, 0xFFB02E26, 0xFF1D1D21};

    public static final int SIDE = 12;
    private static final int GAP = 3;
    private static final int DEFAULT_COLUMNS = 8;

    private final int givenW;
    private final int givenH;
    private final int[] colours;
    private final IntConsumer onPick;
    private int selected;
    private int columns = DEFAULT_COLUMNS;
    private String[] names;

    /** @param selected the index picked at first, or -1 for none */
    public SwatchPickerComponent(Box box, int[] colours, int selected, IntConsumer onPick) {
        super(box);
        this.givenW = box.w();
        this.givenH = box.h();
        this.colours = colours == null ? new int[0] : colours.clone();
        this.onPick = onPick == null ? i -> { } : onPick;
        this.selected = selected >= 0 && selected < this.colours.length ? selected : -1;
        fit();
    }

    /** The columns of a box of width 0. */
    public SwatchPickerComponent columns(int columns) {
        this.columns = Math.max(1, columns);
        fit();
        return this;
    }

    /** A tooltip name per swatch, in the same order as the colours. */
    public SwatchPickerComponent names(String... names) {
        this.names = names;
        return this;
    }

    public SwatchPickerComponent active(boolean active) {
        this.active = active;
        return this;
    }

    /** The index picked, or -1. */
    public int selected() {
        return this.selected;
    }

    /** The colour picked, or 0 when none is. */
    public int colour() {
        return this.selected < 0 ? 0 : this.colours[this.selected];
    }

    /** Picks {@code index} from outside, with no call to the listener; -1 picks none. */
    public SwatchPickerComponent select(int index) {
        this.selected = index >= 0 && index < this.colours.length ? index : -1;
        return this;
    }

    /** The columns laid out now. */
    private int cols() {
        if (this.givenW <= 0) {
            return Math.min(this.columns, Math.max(1, this.colours.length));
        }
        return Math.max(1, (this.w - 2 + GAP) / (SIDE + GAP));
    }

    private int rows() {
        return (this.colours.length + cols() - 1) / cols();
    }

    private void fit() {
        if (this.givenW <= 0) {
            this.w = cols() * (SIDE + GAP) - GAP + 2;
        }
        if (this.givenH <= 0) {
            this.h = rows() * (SIDE + GAP) - GAP + 2;
        }
    }

    /** The swatch at a point, or -1. The ring's pixel around each swatch counts as part of it. */
    int indexAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return -1;
        }
        int col = (int) Math.floor((mx - this.x) / (SIDE + GAP));
        int row = (int) Math.floor((my - this.y) / (SIDE + GAP));
        int index = row * cols() + col;
        return col < cols() && index >= 0 && index < this.colours.length ? index : -1;
    }

    private int left(int index) {
        return this.x + 1 + index % cols() * (SIDE + GAP);
    }

    private int top(int index) {
        return this.y + 1 + index / cols() * (SIDE + GAP);
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        fit();
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        int hover = this.active && hovered ? indexAt(mx, my) : -1;
        int edge = this.active ? FormLook.edge(p) : FormLook.disabledFace(p);
        for (int i = 0; i < this.colours.length; i++) {
            int colour = this.colours[i] | 0xFF000000;
            if (!this.active) {
                colour = Shapes.mix(FormLook.surface(p), colour, 0.35F);
            }
            frame.plate(d, p, left(i), top(i), SIDE, SIDE, colour, edge);
            if (i == this.selected) {
                frame.ringAround(d, p, left(i), top(i), SIDE, SIDE, 1, this.active ? FormLook.solid(p.text()) : p.disabledText());
            } else if (i == hover) {
                frame.ringAround(d, p, left(i), top(i), SIDE, SIDE, 1, Shapes.mix(FormLook.surface(p), FormLook.solid(p.text()), 0.5F * this.hoverAnim));
            }
        }
    }

    /** The ring goes round the picked swatch, or round the first when none is picked. */
    @Override
    protected Box focusBox() {
        int i = Math.max(0, this.selected);
        if (this.colours.length == 0) {
            return box();
        }
        return new Box("swatch", left(i) - 1, top(i) - 1, SIDE + 2, SIDE + 2);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        int i = indexAt(mx, my);
        if (i >= 0 && this.names != null && i < this.names.length && this.names[i] != null) {
            return List.of(this.names[i]);
        }
        return super.tooltipAt(mx, my);
    }

    private void pick(int index) {
        if (index == this.selected) {
            return;
        }
        this.selected = index;
        click();
        this.onPick.accept(index);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || button != 0) {
            return false;
        }
        int i = indexAt(mx, my);
        if (i < 0) {
            return false;
        }
        pick(i);
        return true;
    }

    @Override
    protected boolean navigable() {
        return this.active && this.colours.length > 0;
    }

    /** The arrows move the choice through the grid; Home and End go to the first and last swatch. */
    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!this.active || this.colours.length == 0) {
            return false;
        }
        boolean arrow = key == InputKey.LEFT || key == InputKey.RIGHT || key == InputKey.UP || key == InputKey.DOWN;
        if (this.selected < 0 && arrow) {
            pick(0);
            return true;
        }
        int n = this.colours.length;
        int cols = cols();
        int at = this.selected;
        int next = switch (key) {
            case LEFT -> at % cols == 0 ? -1 : at - 1;
            case RIGHT -> at % cols == cols - 1 || at + 1 >= n ? -1 : at + 1;
            case UP -> at - cols;
            case DOWN -> at + cols >= n ? -1 : at + cols;
            case HOME -> 0;
            case END -> n - 1;
            default -> -1;
        };
        if (next < 0) {
            return false;
        }
        pick(next);
        return true;
    }

    @Override
    protected boolean activate() {
        if (!this.active || this.colours.length == 0 || this.selected >= 0) {
            return false;
        }
        pick(0);
        return true;
    }
}
