package dev.dryaides.minuit;

/**
 * A named rectangle of a layout. Layouts are plain numbers with no Minecraft in them, so a bench
 * checks that no two boxes overlap and that every string drawn stays inside one.
 */
public record Box(String name, int x, int y, int w, int h) {

    public int right() {
        return this.x + this.w;
    }

    public int bottom() {
        return this.y + this.h;
    }

    public boolean intersects(Box other) {
        return this.x < other.right() && other.x < right() && this.y < other.bottom() && other.y < bottom();
    }

    public boolean contains(Box other) {
        return other.x >= this.x && other.y >= this.y && other.right() <= right() && other.bottom() <= bottom();
    }

    public boolean contains(double px, double py) {
        return px >= this.x && px < right() && py >= this.y && py < bottom();
    }

    public Box offset(int dx, int dy) {
        return new Box(this.name, this.x + dx, this.y + dy, this.w, this.h);
    }

    public Box named(String newName) {
        return new Box(newName, this.x, this.y, this.w, this.h);
    }

    /** {@code count} boxes side by side across this one, {@code gap} pixels apart. */
    public Box[] columns(int count, int gap) {
        Box[] out = new Box[count];
        int width = (this.w - gap * (count - 1)) / Math.max(1, count);
        for (int i = 0; i < count; i++) {
            int left = this.x + i * (width + gap);
            int cell = i == count - 1 ? right() - left : width;
            out[i] = new Box(this.name + "[" + i + "]", left, this.y, cell, this.h);
        }
        return out;
    }
}
