package dev.dryaides.minuit;

/**
 * A loading indicator: eight square dots on a ring, one lit dot travelling clockwise with a fading
 * trail, one step every {@link #STEP_MS} ms. Dots sit on whole pixels and take the palette's accent
 * and well edge. With {@link MotionStyle#NONE} (including {@link BaseComponent#reducedMotion}) the
 * ring stands still with its head at the top.
 */
public class SpinnerComponent extends BaseComponent {

    public static final int DOTS = 8;
    public static final long STEP_MS = 100;
    /** How much of the accent each dot keeps, from the head back along the trail; the rest keep none. */
    private static final float[] TRAIL = {1F, 0.6F, 0.3F, 0.12F};

    /** The colour of the head; 0 keeps the theme's accent. */
    public int color;

    public SpinnerComponent(Box box) {
        super(box);
    }

    /** The lit dot at {@code ms}: 0 at the top, then clockwise, back to 0 after {@code DOTS} steps. */
    public static int head(long ms) {
        return (int) (Math.floorMod(ms, DOTS * STEP_MS) / STEP_MS);
    }

    /** Top-left corner of dot {@code i} in a ring of {@code size} pixels, dots {@code dot} pixels wide. */
    public static int[] dotAt(int i, int size, int dot) {
        double angle = Math.toRadians(-90 + i * (360.0 / DOTS));
        double radius = (size - dot) / 2.0;
        double centre = (size - dot) / 2.0;
        return new int[]{(int) Math.round(centre + radius * Math.cos(angle)), (int) Math.round(centre + radius * Math.sin(angle))};
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        int size = Math.min(this.w, this.h);
        int dot = Math.max(1, size / 8);
        int left = this.x + (this.w - size) / 2;
        int top = this.y + (this.h - size) / 2;
        int head = motion() == MotionStyle.NONE ? 0 : head(clock.getAsLong());
        int lit = this.color != 0 ? this.color : p.accent();
        for (int i = 0; i < DOTS; i++) {
            int behind = Math.floorMod(head - i, DOTS);
            float keep = behind < TRAIL.length ? TRAIL[behind] : 0F;
            int[] at = dotAt(i, size, dot);
            d.fill(left + at[0], top + at[1], left + at[0] + dot, top + at[1] + dot, Shapes.mix(p.wellEdge(), lit, keep));
        }
    }
}
