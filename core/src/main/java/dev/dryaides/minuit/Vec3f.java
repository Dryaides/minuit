package dev.dryaides.minuit;

/**
 * A mutable 3D vector for the small software 3D library ({@link Camera3D}, {@link Mesh3D}, {@link Renderer3D}).
 * Every operation writes into this vector and returns it, so a frame needs no new object.
 */
final class Vec3f {
    public float x;
    public float y;
    public float z;

    public Vec3f() {
    }

    public Vec3f(float x, float y, float z) {
        set(x, y, z);
    }

    public Vec3f set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vec3f set(Vec3f o) {
        return set(o.x, o.y, o.z);
    }

    public Vec3f add(float dx, float dy, float dz) {
        return set(x + dx, y + dy, z + dz);
    }

    public Vec3f scale(float by) {
        return set(x * by, y * by, z * by);
    }

    public float dot(float ox, float oy, float oz) {
        return x * ox + y * oy + z * oz;
    }

    public float dot(Vec3f o) {
        return dot(o.x, o.y, o.z);
    }

    public float length() {
        return (float) Math.sqrt(dot(this));
    }

    /** Unit length. A zero vector stays zero. */
    public Vec3f normalize() {
        float length = length();
        return length < 1e-9F ? this : scale(1F / length);
    }

    /** Sets this vector to {@code a} cross {@code b}. Either may be this vector. */
    public Vec3f cross(Vec3f a, Vec3f b) {
        return set(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
