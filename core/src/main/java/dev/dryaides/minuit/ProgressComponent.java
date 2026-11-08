package dev.dryaides.minuit;

/**
 * A progress bar. A negative fraction means "working, no measure": a block slides along the track
 * instead of pretending to know how far it got. It runs in from beyond the left end and out past the
 * right end, clipped by the track, one pass every {@link #SLIDE_MS} ms on whole pixels; with
 * {@link MotionStyle#NONE} it rests in the middle.
 */
public class ProgressComponent extends BaseComponent {

    /** One pass of the block, entry to exit. Before 2026-10 it moved 0.02 of the track per drawn frame (about 0.8 s at 60 fps). */
    public static final long SLIDE_MS = 2500;

    public float fraction = -1;
    public int color;

    public ProgressComponent(Box box) {
        super(box);
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        int fill = this.color != 0 ? this.color : p.accent();
        if (this.fraction >= 0) {
            theme.frame().bar(d, p, this.x, this.y, this.w, this.h, this.fraction, fill);
            return;
        }
        theme.frame().bar(d, p, this.x, this.y, this.w, this.h, 0F, fill);
        int block = Math.max(6, this.w / 5);
        int travel = this.w - 2 + block;
        int bx = motion() == MotionStyle.NONE ? this.x + (this.w - block) / 2
                : this.x + 1 - block + (int) (Math.floorMod(clock.getAsLong(), SLIDE_MS) * travel / SLIDE_MS);
        d.scissor(this.x + 1, this.y + 1, this.x + this.w - 1, this.y + this.h - 1);
        Shapes.rounded(d, bx, this.y + 1, block, this.h - 2, Math.max(0, Math.min(2, (this.h - 2) / 2)), fill);
        d.endScissor();
    }
}
