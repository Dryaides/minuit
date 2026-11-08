package dev.dryaides.minuit;

/** Pixels a family draws outside a widget's box, per side: a band under a button, a rim around it. */
public record Insets(int top, int left, int right, int bottom) {

    public static final Insets NONE = new Insets(0, 0, 0, 0);

    /** {@code box} grown by these insets. */
    public Box around(Box box) {
        return new Box(box.name(), box.x() - this.left, box.y() - this.top, box.w() + this.left + this.right,
                box.h() + this.top + this.bottom);
    }
}
