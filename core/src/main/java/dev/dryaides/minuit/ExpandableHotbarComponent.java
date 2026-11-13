package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A hotbar fixed at an edge of a menu screen that unfolds the player's inventory rows while the pointer
 * is on it and folds them back behind the bar when it leaves: Multi Hotbar's and Dual Hotbar's extra
 * rows, or a bar at the top of the screen. Over a menu ({@link #menuSlots}) the open menu's own player
 * slots show in its wells wherever the bar sits, the game drawing the items and taking the clicks; items
 * dragged out land on the scene's drop targets ({@link #targets}), with a cue where the carried item fits.
 *
 * <pre>{@code
 * hotbar = add(ExpandableHotbarComponent.bottom(width, height, 3, false)
 *         .menuSlots(SlotLayout.playerInventory("inv", leftPos + 8, topPos + 84))
 *         .targets(() -> drops).carried(() -> MinuitItems.view(menu.getCarried())));
 * }</pre>
 */
@Incubating
public class ExpandableHotbarComponent extends BaseComponent implements ItemSource {
    /** The side the rows unfold to: up from a bar at the bottom, down from a bar at the top. */
    public enum Unfold { UP, DOWN }

    /** How far the held slot's frame reaches past an 18 px well, so an edge bar keeps it on screen. */
    public static final int EDGE_ROOM = 3;

    /** A full unfold or fold, in ms. */
    public static final int UNFOLD_MS = 150;

    public final HotbarComponent bar;
    public final SlotGridComponent rows;
    /** False unfolds only through {@link #hold}. */
    public boolean unfoldsOnHover = true;

    private final Unfold direction;
    private final int rowCount;
    /** The first main-inventory row shown, 0 to 2 (vanilla index 9 + 9 * firstRow). */
    private final int firstRow;
    private final int distance;
    private final Glide fold;
    private Box menuInventory;
    private boolean held;
    private Supplier<List<ItemDropTarget.Drop>> targets = List::of;
    private Supplier<ItemView> carried = () -> null;
    private int pointerX = Integer.MIN_VALUE;
    private int pointerY = Integer.MIN_VALUE;

    /**
     * @param barX      the bar's left edge, screen coordinates
     * @param barY      the bar's top edge
     * @param direction where the rows unfold
     * @param rows      main-inventory rows shown, 1 to 3: those nearest the bar, in vanilla order
     * @param hud       the game's HUD bar (182 by 22, cells 20 px apart) instead of a row of 18 px wells
     */
    public ExpandableHotbarComponent(int barX, int barY, Unfold direction, int rows, boolean hud) {
        super(extent(barX, barY, direction, rows, hud));
        if (rows < 1 || rows > 3) {
            throw new IllegalArgumentException("rows " + rows + " is not 1 to 3");
        }
        this.direction = direction;
        this.rowCount = rows;
        this.firstRow = direction == Unfold.UP ? 3 - rows : 0;
        this.bar = hud ? HotbarComponent.hud("hotbar", barX, barY)
                : new HotbarComponent(new Box("hotbar", barX, barY, 9 * SlotLayout.PITCH, SlotLayout.PITCH));
        this.bar.selected = -1;
        int barW = hud ? SlotLayout.HUD_HOTBAR_W : 9 * SlotLayout.PITCH;
        int barH = hud ? SlotLayout.HUD_HOTBAR_H : SlotLayout.PITCH;
        int rowsX = barX + (barW - 9 * SlotLayout.PITCH) / 2;
        int rowsY = direction == Unfold.UP ? barY - SlotLayout.HOTBAR_GAP - rows * SlotLayout.PITCH
                : barY + barH + SlotLayout.HOTBAR_GAP;
        this.rows = new SlotGridComponent(new Box("rows", rowsX, rowsY, 9 * SlotLayout.PITCH, rows * SlotLayout.PITCH), rows, 9);
        this.distance = rows * SlotLayout.PITCH + SlotLayout.HOTBAR_GAP;
        this.fold = new Glide(direction == Unfold.UP ? Slide.DOWN : Slide.UP, UNFOLD_MS);
        this.fold.snap(false);
    }

    /**
     * A bar at the screen's bottom edge, centred, unfolding up: the HUD bar flush as the game's, a row of
     * wells {@link #EDGE_ROOM} px up so the held slot's frame stays on screen.
     */
    public static ExpandableHotbarComponent bottom(int screenW, int screenH, int rows, boolean hud) {
        int w = hud ? SlotLayout.HUD_HOTBAR_W : 9 * SlotLayout.PITCH;
        int h = hud ? SlotLayout.HUD_HOTBAR_H : SlotLayout.PITCH + EDGE_ROOM;
        return new ExpandableHotbarComponent((screenW - w) / 2, screenH - h, Unfold.UP, rows, hud);
    }

    /** A bar at the screen's top edge, centred, unfolding down, with room for the held slot's frame above it. */
    public static ExpandableHotbarComponent top(int screenW, int rows, boolean hud) {
        int w = hud ? SlotLayout.HUD_HOTBAR_W : 9 * SlotLayout.PITCH;
        return new ExpandableHotbarComponent((screenW - w) / 2, hud ? 1 : EDGE_ROOM, Unfold.DOWN, rows, hud);
    }

    private static Box extent(int barX, int barY, Unfold direction, int rows, boolean hud) {
        int barW = hud ? SlotLayout.HUD_HOTBAR_W : 9 * SlotLayout.PITCH;
        int barH = hud ? SlotLayout.HUD_HOTBAR_H : SlotLayout.PITCH;
        int rowsH = rows * SlotLayout.PITCH + SlotLayout.HOTBAR_GAP;
        return new Box("hotbar", barX, direction == Unfold.UP ? barY - rowsH : barY, barW, barH + rowsH);
    }

    /**
     * Shows the open menu's player slots: {@code inventory} is {@link SlotLayout#playerInventory} at the
     * menu's own slot positions, screen coordinates (a vanilla menu: 8, 84 in from the container's corner).
     * The menu's slots then show in this bar and its rows, wherever they are drawn.
     */
    public ExpandableHotbarComponent menuSlots(Box inventory) {
        this.menuInventory = inventory;
        return this;
    }

    /** The scene's drop places for a stack dragged out of the bar, read on every frame. */
    public ExpandableHotbarComponent targets(Supplier<List<ItemDropTarget.Drop>> targets) {
        this.targets = targets;
        return this;
    }

    /** The stack on the cursor now, null for none: the cue shows where it fits. */
    public ExpandableHotbarComponent carried(Supplier<ItemView> carried) {
        this.carried = carried;
        return this;
    }

    /** Display mode: the 36 items in vanilla's index order, 0 to 8 the bar, null for an empty slot. */
    public ExpandableHotbarComponent items(List<ItemView> all36) {
        List<ItemView> padded = new ArrayList<>(all36);
        while (padded.size() < 36) {
            padded.add(null);
        }
        this.bar.items(padded.subList(0, 9));
        int from = 9 + 9 * this.firstRow;
        this.rows.items(padded.subList(from, from + 9 * this.rowCount));
        return this;
    }

    /** Frames the held slot, 0 to 8, as the game's hotbar does; -1 for none. */
    public ExpandableHotbarComponent selected(int slot) {
        this.bar.selected = slot;
        return this;
    }

    /** True keeps the rows unfolded whatever the pointer does: a key held, a pin. */
    public void hold(boolean open) {
        this.held = open;
        follow();
    }

    /** Unfolded or folded at once, with no motion. */
    public void snap(boolean open) {
        this.fold.snap(open);
    }

    /** True while the rows slide; a host that redraws on its clock keeps drawing. */
    public boolean animating() {
        return this.fold.moving();
    }

    /** True while any part of the rows shows. */
    public boolean unfolded() {
        return !this.fold.away();
    }

    /** How far the rows sit from their unfolded place now, whole pixels: toward the bar. */
    public int offset() {
        return Math.round(this.fold.offsetY(this.distance));
    }

    /** What the bar covers now: the bar folded, the bar and its rows while they show. */
    public Box coveredArea() {
        return unfolded() ? box() : this.bar.box();
    }

    @Override
    public boolean isOver(double mx, double my) {
        return this.visible && coveredArea().contains(mx, my);
    }

    /** The first drop target under the point, or null. */
    public ItemDropTarget.Drop targetAt(int x, int y) {
        for (ItemDropTarget.Drop drop : this.targets.get()) {
            if (drop.contains(x, y)) {
                return drop;
            }
        }
        return null;
    }

    /** The first drop target under the point that takes the carried stack: the one to light; null for none. */
    public ItemDropTarget.Drop cued(int x, int y) {
        ItemView item = this.carried.get();
        if (item == null || isOver(x, y)) {
            return null;
        }
        for (ItemDropTarget.Drop drop : this.targets.get()) {
            if (drop.canDrop(x, y, item)) {
                return drop;
            }
        }
        return null;
    }

    /**
     * Per vanilla index, the 18 px cell round the menu slot at the menu's place, shifted to where this bar
     * draws it. A row cell counts only while it lies wholly clear of the bar, so a sliding row is neither
     * drawn nor found under the bar.
     */
    @Override
    protected List<SlotLayout.Well> menuWells() {
        if (this.menuInventory == null || !this.rows.holdsMenu() || !this.bar.holdsMenu()) {
            return List.of();
        }
        List<SlotLayout.Cell> menu = SlotLayout.playerCells(this.menuInventory);
        List<SlotLayout.Well> out = new ArrayList<>();
        int off = offset();
        for (SlotLayout.Cell cell : menu) {
            int index = cell.index();
            int drawnX;
            int drawnY;
            if (index < 9) {
                drawnX = this.bar.itemX(index);
                drawnY = this.bar.itemY(index);
            } else {
                int r = (index - 9) / 9 - this.firstRow;
                if (!unfolded() || r < 0 || r >= this.rowCount) {
                    continue;
                }
                int i = r * 9 + (index - 9) % 9;
                Box drawn = this.rows.cellBox(i).offset(0, off);
                if (!clearOfBar(drawn)) {
                    continue;
                }
                drawnX = drawn.x() + 1;
                drawnY = drawn.y() + 1;
            }
            Box at = new Box("slot" + index, cell.x() - 1, cell.y() - 1, SlotLayout.PITCH, SlotLayout.PITCH);
            out.add(new SlotLayout.Well(at, drawnX - cell.x(), drawnY - cell.y()));
        }
        return out;
    }

    private boolean clearOfBar(Box cell) {
        Box b = this.bar.box();
        return this.direction == Unfold.UP ? cell.bottom() <= b.y() : cell.y() >= b.bottom();
    }

    private void follow() {
        boolean onBar = this.bar.box().contains(this.pointerX, this.pointerY);
        boolean onRows = this.fold.progress() > 0F && box().contains(this.pointerX, this.pointerY);
        if (this.held || this.unfoldsOnHover && (onBar || onRows)) {
            this.fold.show();
        } else {
            this.fold.hide();
        }
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        this.pointerX = mx;
        this.pointerY = my;
        follow();
        if (unfolded()) {
            int off = offset();
            Box b = this.bar.box();
            if (this.direction == Unfold.UP) {
                d.scissor(this.x, this.y, this.x + this.w, b.y());
            } else {
                d.scissor(this.x, b.bottom(), this.x + this.w, this.y + this.h);
            }
            d.push(0, off, 1F);
            // The pointer reaches the rows only at rest: a sliding cell shows no hover.
            boolean still = !this.fold.moving();
            this.rows.draw(d, theme, still ? mx : Integer.MIN_VALUE, still ? my - off : Integer.MIN_VALUE, this.delta);
            d.pop();
            d.endScissor();
        }
        this.bar.draw(d, theme, mx, my, this.delta);
        if (cued(mx, my) != null) {
            d.fill(mx - 8, my - 8, mx + 8, my + 8, Shapes.SLOT_HOVER_FRONT);
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        List<String> tip = this.bar.tooltipAt(mx, my);
        if (tip == null && settledOpen()) {
            tip = this.rows.tooltipAt(mx, my);
        }
        return tip;
    }

    @Override
    public ItemView itemAt(double mx, double my) {
        ItemView item = this.bar.itemAt(mx, my);
        return item != null || !settledOpen() ? item : this.rows.itemAt(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return this.bar.mouseClicked(mx, my, button) || settledOpen() && this.rows.mouseClicked(mx, my, button);
    }

    private boolean settledOpen() {
        return this.fold.shown() && !this.fold.moving();
    }

    @Override
    public void tick() {
        this.rows.tick();
        this.bar.tick();
    }
}
