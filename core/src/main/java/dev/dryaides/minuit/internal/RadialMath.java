package dev.dryaides.minuit.internal;

/** The radial wheel's geometry and timing, apart from any drawing so it can be tested alone. */
public final class RadialMath {

    private RadialMath() {
    }

    /**
     * The part under a cursor at {@code dx, dy} from the centre (screen y grows downwards), parts
     * laid clockwise from the top. Each part is centred on its angle and spans half a part each side.
     */
    public static int sector(double dx, double dy, int count) {
        if (count <= 0) {
            return -1;
        }
        double angle = (Math.toDegrees(Math.atan2(dy, dx)) + 90 + 360) % 360;
        return (int) Math.round(angle / (360.0 / count)) % count;
    }

    /** True when the cursor sits closer to the centre than {@code fraction} of the radius. */
    public static boolean inDeadZone(double dx, double dy, double radius, double fraction) {
        return Math.hypot(dx, dy) < radius * fraction;
    }

    /** The page after one wheel notch; wheel up ({@code amount > 0}) goes back one page. */
    public static int pageStep(int page, double amount, int count) {
        if (count <= 0 || amount == 0) {
            return page;
        }
        return Math.floorMod(page + (amount > 0 ? -1 : 1), count);
    }

    /** Ease-out quadratic of a slide started at {@code start}: 0 at the start, 1 once settled. */
    public static float slide(long start, long now, long duration) {
        if (duration <= 0 || now - start >= duration) {
            return 1F;
        }
        float t = Math.max(0L, now - start) / (float) duration;
        return 1F - (1F - t) * (1F - t);
    }

    /** {@link #ring} result for a pixel outside every part. */
    public static final int OUTSIDE = -1;
    public static final int FACE = 0;
    public static final int EDGE = 1;
    /** The band a lifted part has left uncovered, under its face. */
    public static final int SHADOW = 2;

    /**
     * Which part of a ring cut in {@code count} sectors covers the pixel centre at {@code dx, dy},
     * packed as {@code sector * 4 + kind} (kind {@link #FACE}, {@link #EDGE} or {@link #SHADOW}), or
     * {@link #OUTSIDE}. The sector is {@link #sector}'s, so the drawn part under the cursor is the
     * aimed one. Sectors are {@code gap} px apart along the arc; sector {@code lifted} stands
     * {@code lift} px further out.
     */
    public static int ring(double dx, double dy, int count, double inner, double outer, double gap,
                           int lifted, double lift) {
        if (count <= 0) {
            return OUTSIDE;
        }
        double dist = Math.hypot(dx, dy);
        if (dist < inner || dist >= outer + lift) {
            return OUTSIDE;
        }
        int s = sector(dx, dy, count);
        double step = 360.0 / count;
        double angle = (Math.toDegrees(Math.atan2(dy, dx)) + 90 + 360) % 360;
        double off = Math.abs(((angle - s * step) % 360 + 540) % 360 - 180);
        double toBorder = count == 1 ? Double.MAX_VALUE : Math.toRadians(step / 2 - off) * dist;
        if (toBorder < gap / 2) {
            return OUTSIDE;
        }
        boolean up = s == lifted;
        double in = up ? inner + lift : inner;
        double out = up ? outer + lift : outer;
        if (dist >= out) {
            return OUTSIDE;
        }
        if (dist < in) {
            return s * 4 + SHADOW;
        }
        boolean edge = dist < in + 1 || dist >= out - 1 || toBorder < gap / 2 + 1;
        return s * 4 + (edge ? EDGE : FACE);
    }
}
