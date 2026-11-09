package dev.dryaides.minuit;

/**
 * A mutable affine transform for placing parts of a {@link Mesh3D} while building it: translate, rotate, scale,
 * in the order they are called, as in {@code new Mat4f().translate(0, 2, 0).rotateY(45).scale(2)}. The bottom row
 * is always 0 0 0 1, so it is kept out. The renderer does not use it: the camera works from its own basis.
 */
final class Mat4f {
    /** Rows of the upper three lines: m[0..3] gives x, m[4..7] gives y, m[8..11] gives z. */
    private final float[] m = new float[12];

    public Mat4f() {
        identity();
    }

    public Mat4f identity() {
        for (int i = 0; i < m.length; i++) {
            m[i] = i % 5 == 0 ? 1F : 0F;
        }
        return this;
    }

    public Mat4f set(Mat4f o) {
        System.arraycopy(o.m, 0, m, 0, m.length);
        return this;
    }

    public Mat4f translate(float x, float y, float z) {
        for (int row = 0; row < 3; row++) {
            int r = row * 4;
            m[r + 3] += m[r] * x + m[r + 1] * y + m[r + 2] * z;
        }
        return this;
    }

    public Mat4f scale(float by) {
        return scale(by, by, by);
    }

    public Mat4f scale(float x, float y, float z) {
        for (int row = 0; row < 3; row++) {
            int r = row * 4;
            m[r] *= x;
            m[r + 1] *= y;
            m[r + 2] *= z;
        }
        return this;
    }

    public Mat4f rotateX(float degrees) {
        return rotate(1, 2, degrees);
    }

    public Mat4f rotateY(float degrees) {
        return rotate(2, 0, degrees);
    }

    public Mat4f rotateZ(float degrees) {
        return rotate(0, 1, degrees);
    }

    /** Multiplies by a rotation that turns column {@code a} towards column {@code b}. */
    private Mat4f rotate(int a, int b, float degrees) {
        float rad = (float) Math.toRadians(degrees);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        for (int row = 0; row < 3; row++) {
            int r = row * 4;
            float ma = m[r + a];
            float mb = m[r + b];
            m[r + a] = ma * c + mb * s;
            m[r + b] = mb * c - ma * s;
        }
        return this;
    }

    public float applyX(float x, float y, float z) {
        return m[0] * x + m[1] * y + m[2] * z + m[3];
    }

    public float applyY(float x, float y, float z) {
        return m[4] * x + m[5] * y + m[6] * z + m[7];
    }

    public float applyZ(float x, float y, float z) {
        return m[8] * x + m[9] * y + m[10] * z + m[11];
    }

    /** Writes the transformed point into {@code out}, which may be the same vector as the point. */
    public Vec3f apply(Vec3f point, Vec3f out) {
        return out.set(applyX(point.x, point.y, point.z), applyY(point.x, point.y, point.z), applyZ(point.x, point.y, point.z));
    }
}
