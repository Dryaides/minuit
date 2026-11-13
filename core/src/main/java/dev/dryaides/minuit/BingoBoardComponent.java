package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * A bingo card: a grid of item wells, each cell open, done, or claimed by one or more teams. A done cell
 * gets a ring of the palette's good colour inside its well; each team that claimed it gets an equal share of
 * a stripe along the well's bottom edge in the team's colour, split on whole pixels. The tooltip gives the
 * item, then the teams that claimed it. Keyboard and controller move a cell cursor as on any slot grid.
 */
@Incubating
public class BingoBoardComponent extends SlotGridComponent {

    /** A team, named and coloured by the mod. */
    public record Team(String name, int color) {
    }

    /** Height of the claim stripe, inside the well. */
    public static final int STRIPE = 3;

    private final boolean[] done;
    private final List<List<Team>> claims;

    /** A {@code size} by {@code size} card at {@code x, y}, one 18 px well per cell. */
    public BingoBoardComponent(int x, int y, int size) {
        this(x, y, size, size);
    }

    public BingoBoardComponent(int x, int y, int rows, int cols) {
        super(new Box("bingo", x, y, cols * SlotLayout.PITCH, rows * SlotLayout.PITCH), rows, cols);
        this.done = new boolean[rows * cols];
        this.claims = new ArrayList<>(rows * cols);
        for (int i = 0; i < rows * cols; i++) {
            this.claims.add(new ArrayList<>());
        }
    }

    public BingoBoardComponent done(int index, boolean on) {
        this.done[index] = on;
        return this;
    }

    public boolean done(int index) {
        return this.done[index];
    }

    /** Adds {@code team} to the cell's claims, once; claims show in the order they came. */
    public BingoBoardComponent claim(int index, Team team) {
        if (!this.claims.get(index).contains(team)) {
            this.claims.get(index).add(team);
        }
        return this;
    }

    public BingoBoardComponent unclaim(int index, Team team) {
        this.claims.get(index).remove(team);
        return this;
    }

    public List<Team> claims(int index) {
        return List.copyOf(this.claims.get(index));
    }

    @Override
    protected void renderOver(Draw d, Theme theme) {
        Palette p = theme.palette();
        for (int i = 0; i < this.done.length; i++) {
            Box cell = cellBox(i);
            int l = cell.x() + 1;
            int t = cell.y() + 1;
            int r = cell.x() + cell.w() - 1;
            int b = cell.y() + cell.h() - 1;
            if (this.done[i]) {
                d.fill(l, t, r, t + 1, p.good());
                d.fill(l, b - 1, r, b, p.good());
                d.fill(l, t + 1, l + 1, b - 1, p.good());
                d.fill(r - 1, t + 1, r, b - 1, p.good());
            }
            List<Team> teams = this.claims.get(i);
            int n = teams.size();
            if (n > 0) {
                int width = r - l;
                for (int k = 0; k < n; k++) {
                    int a = l + width * k / n;
                    int e = l + width * (k + 1) / n;
                    d.fill(a, b - STRIPE, e, b, teams.get(k).color());
                }
            }
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        List<String> base = super.tooltipAt(mx, my);
        int cell = cellAt(mx, my);
        if (cell < 0 || this.claims.get(cell).isEmpty()) {
            return base;
        }
        List<String> out = base == null ? new ArrayList<>() : new ArrayList<>(base);
        for (Team team : this.claims.get(cell)) {
            out.add(team.name());
        }
        return out;
    }
}
