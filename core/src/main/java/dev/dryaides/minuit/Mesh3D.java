package dev.dryaides.minuit;

import java.util.Arrays;

/**
 * Flat-coloured triangles, quads and lines in world units, kept in plain arrays: a mesh is built once, and drawing
 * it allocates nothing. A face shows from the side where its corners turn counter-clockwise; the other side is
 * skipped. A colour is {@code 0xRRGGBB} or {@code 0xAARRGGBB} with its alpha ignored, as faces are opaque, or
 * {@link #tone(Tone)} for a colour that follows the theme.
 *
 * <pre>{@code
 * Mesh3D mesh = new Mesh3D()
 *         .box(-1, 0, -1, 1, 2, 1, 0xC08040)
 *         .box(-2, 0, -2, 2, 0.25F, 2, Mesh3D.tone(Mesh3D.Tone.PRIMARY))
 *         .grid(0, 4, 8, Mesh3D.tone(Mesh3D.Tone.GRID));
 * }</pre>
 */
@Incubating
public final class Mesh3D {
    /** A colour from the player's theme, read when the picture is drawn. */
    public enum Tone { PRIMARY, ACCENT, GOOD, WARN, BAD, RAISED, TEXT, TRACK, GRID }

    /** Which way a box's or voxel's face looks: used as an index into per-face colours. */
    public static final int EAST = 0;
    public static final int WEST = 1;
    public static final int UP = 2;
    public static final int DOWN = 3;
    public static final int SOUTH = 4;
    public static final int NORTH = 5;
    public static final int FACES = 6;

    private static final float PLANAR = 1e-4F;

    float[] vertices = new float[48];
    int vertexCount;
    /** Four vertex indices per polygon; the last is -1 for a triangle. */
    int[] corners = new int[32];
    float[] normals = new float[24];
    int[] colors = new int[8];
    int polygonCount;
    int triangleCount;
    int[] lineEnds = new int[16];
    int[] lineColors = new int[8];
    int lineCount;

    private long version;
    private Mat4f transform;
    private float minX = Float.MAX_VALUE;
    private float minY = Float.MAX_VALUE;
    private float minZ = Float.MAX_VALUE;
    private float maxX = -Float.MAX_VALUE;
    private float maxY = -Float.MAX_VALUE;
    private float maxZ = -Float.MAX_VALUE;

    /** The colour value that stands for a tone of the theme. */
    public static int tone(Tone tone) {
        return tone.ordinal() + 1;
    }

    static boolean isTone(int color) {
        return color >>> 24 == 0 && (color & 0xFFFFFF) <= Tone.values().length && (color & 0xFFFFFF) != 0;
    }

    /** Applied to what is added from now on. Null for none. Later changes to the transform also apply to later additions. */
    Mesh3D transform(Mat4f transform) {
        this.transform = transform;
        return this;
    }

    /** Moves what is added from now on, after the earlier moves, turns and scales. */
    public Mesh3D move(float x, float y, float z) {
        placement().translate(x, y, z);
        return this;
    }

    /** Turns what is added from now on around the x axis, in degrees. */
    public Mesh3D turnX(float degrees) {
        placement().rotateX(degrees);
        return this;
    }

    /** Turns what is added from now on around the vertical axis, in degrees. */
    public Mesh3D turnY(float degrees) {
        placement().rotateY(degrees);
        return this;
    }

    /** Turns what is added from now on around the z axis, in degrees. */
    public Mesh3D turnZ(float degrees) {
        placement().rotateZ(degrees);
        return this;
    }

    public Mesh3D scale(float by) {
        placement().scale(by);
        return this;
    }

    /** Drops every move, turn and scale: what is added next goes where its coordinates say. */
    public Mesh3D unmoved() {
        transform = null;
        return this;
    }

    private Mat4f placement() {
        if (transform == null) {
            transform = new Mat4f();
        }
        return transform;
    }

    public Mesh3D clear() {
        vertexCount = 0;
        polygonCount = 0;
        triangleCount = 0;
        lineCount = 0;
        minX = Float.MAX_VALUE;
        minY = Float.MAX_VALUE;
        minZ = Float.MAX_VALUE;
        maxX = -Float.MAX_VALUE;
        maxY = -Float.MAX_VALUE;
        maxZ = -Float.MAX_VALUE;
        version++;
        return this;
    }

    public boolean isEmpty() {
        return polygonCount == 0 && lineCount == 0;
    }

    public int vertexCount() { return vertexCount; }
    public int polygonCount() { return polygonCount; }
    /** Polygons counted as triangles: a quad is two. */
    public int triangleCount() { return triangleCount; }
    public int lineCount() { return lineCount; }
    /** Goes up with every change. */
    public long version() { return version; }

    public float minX() { return vertexCount == 0 ? 0F : minX; }
    public float minY() { return vertexCount == 0 ? 0F : minY; }
    public float minZ() { return vertexCount == 0 ? 0F : minZ; }
    public float maxX() { return vertexCount == 0 ? 0F : maxX; }
    public float maxY() { return vertexCount == 0 ? 0F : maxY; }
    public float maxZ() { return vertexCount == 0 ? 0F : maxZ; }
    public float centerX() { return (minX() + maxX()) / 2F; }
    public float centerY() { return (minY() + maxY()) / 2F; }
    public float centerZ() { return (minZ() + maxZ()) / 2F; }

    /** The radius of the sphere round the bounds' centre that holds them. */
    public float radius() {
        float dx = maxX() - minX();
        float dy = maxY() - minY();
        float dz = maxZ() - minZ();
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 2F;
    }

    private int vertex(float x, float y, float z) {
        float wx = x;
        float wy = y;
        float wz = z;
        if (transform != null) {
            wx = transform.applyX(x, y, z);
            wy = transform.applyY(x, y, z);
            wz = transform.applyZ(x, y, z);
        }
        if (vertexCount * 3 == vertices.length) {
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
        }
        int at = vertexCount * 3;
        vertices[at] = wx;
        vertices[at + 1] = wy;
        vertices[at + 2] = wz;
        minX = Math.min(minX, wx);
        minY = Math.min(minY, wy);
        minZ = Math.min(minZ, wz);
        maxX = Math.max(maxX, wx);
        maxY = Math.max(maxY, wy);
        maxZ = Math.max(maxZ, wz);
        return vertexCount++;
    }

    private void polygon(int a, int b, int c, int d, int color) {
        if (polygonCount == colors.length) {
            colors = Arrays.copyOf(colors, polygonCount * 2);
            corners = Arrays.copyOf(corners, polygonCount * 8);
            normals = Arrays.copyOf(normals, polygonCount * 6);
        }
        int at = polygonCount * 4;
        corners[at] = a;
        corners[at + 1] = b;
        corners[at + 2] = c;
        corners[at + 3] = d;
        float ux = vertices[b * 3] - vertices[a * 3];
        float uy = vertices[b * 3 + 1] - vertices[a * 3 + 1];
        float uz = vertices[b * 3 + 2] - vertices[a * 3 + 2];
        float vx = vertices[c * 3] - vertices[a * 3];
        float vy = vertices[c * 3 + 1] - vertices[a * 3 + 1];
        float vz = vertices[c * 3 + 2] - vertices[a * 3 + 2];
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-12F) {
            return; // no area
        }
        normals[polygonCount * 3] = nx / length;
        normals[polygonCount * 3 + 1] = ny / length;
        normals[polygonCount * 3 + 2] = nz / length;
        colors[polygonCount] = color;
        polygonCount++;
        triangleCount += d < 0 ? 1 : 2;
        version++;
    }

    public Mesh3D triangle(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, int color) {
        polygon(vertex(ax, ay, az), vertex(bx, by, bz), vertex(cx, cy, cz), -1, color);
        return this;
    }

    /** Four corners in order round the face. Corners off one plane make two triangles, split between the first and third. */
    public Mesh3D quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, int color) {
        int a = vertex(ax, ay, az);
        int b = vertex(bx, by, bz);
        int c = vertex(cx, cy, cz);
        int d = vertex(dx, dy, dz);
        if (planar(a, b, c, d)) {
            polygon(a, b, c, d, color);
        } else {
            polygon(a, b, c, -1, color);
            polygon(a, c, d, -1, color);
        }
        return this;
    }

    private boolean planar(int a, int b, int c, int d) {
        float[] v = vertices;
        float ux = v[b * 3] - v[a * 3];
        float uy = v[b * 3 + 1] - v[a * 3 + 1];
        float uz = v[b * 3 + 2] - v[a * 3 + 2];
        float wx = v[c * 3] - v[a * 3];
        float wy = v[c * 3 + 1] - v[a * 3 + 1];
        float wz = v[c * 3 + 2] - v[a * 3 + 2];
        float nx = uy * wz - uz * wy;
        float ny = uz * wx - ux * wz;
        float nz = ux * wy - uy * wx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-12F) {
            return false;
        }
        float off = nx * (v[d * 3] - v[a * 3]) + ny * (v[d * 3 + 1] - v[a * 3 + 1]) + nz * (v[d * 3 + 2] - v[a * 3 + 2]);
        return Math.abs(off) / length <= PLANAR * (1F + (float) Math.sqrt(length));
    }

    public Mesh3D line(float ax, float ay, float az, float bx, float by, float bz, int color) {
        int a = vertex(ax, ay, az);
        int b = vertex(bx, by, bz);
        if (lineCount == lineColors.length) {
            lineColors = Arrays.copyOf(lineColors, lineCount * 2);
            lineEnds = Arrays.copyOf(lineEnds, lineCount * 4);
        }
        lineEnds[lineCount * 2] = a;
        lineEnds[lineCount * 2 + 1] = b;
        lineColors[lineCount] = color;
        lineCount++;
        version++;
        return this;
    }

    /** A box with one colour. The corners may come in any order. */
    public Mesh3D box(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        boxFaces(x0, y0, z0, x1, y1, z1, color, color, color, color, color, color);
        return this;
    }

    /** A box with a colour per face, indexed by {@link #EAST} and the others; a face with colour 0 is left out. */
    public Mesh3D box(float x0, float y0, float z0, float x1, float y1, float z1, int[] faces) {
        boxFaces(x0, y0, z0, x1, y1, z1, faces[EAST], faces[WEST], faces[UP], faces[DOWN], faces[SOUTH], faces[NORTH]);
        return this;
    }

    private void boxFaces(float ax, float ay, float az, float bx, float by, float bz, int e, int w, int u, int d, int s, int n) {
        float x0 = Math.min(ax, bx);
        float y0 = Math.min(ay, by);
        float z0 = Math.min(az, bz);
        float x1 = Math.max(ax, bx);
        float y1 = Math.max(ay, by);
        float z1 = Math.max(az, bz);
        if (e != 0) quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, e);
        if (w != 0) quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, w);
        if (u != 0) quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u);
        if (d != 0) quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, d);
        if (s != 0) quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, s);
        if (n != 0) quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, n);
    }

    /** The twelve edges of a box, as lines. */
    public Mesh3D boxEdges(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        line(x0, y0, z0, x1, y0, z0, color);
        line(x0, y1, z0, x1, y1, z0, color);
        line(x0, y0, z1, x1, y0, z1, color);
        line(x0, y1, z1, x1, y1, z1, color);
        line(x0, y0, z0, x0, y1, z0, color);
        line(x1, y0, z0, x1, y1, z0, color);
        line(x0, y0, z1, x0, y1, z1, color);
        line(x1, y0, z1, x1, y1, z1, color);
        line(x0, y0, z0, x0, y0, z1, color);
        line(x1, y0, z0, x1, y0, z1, color);
        line(x0, y1, z0, x0, y1, z1, color);
        line(x1, y1, z0, x1, y1, z1, color);
        return this;
    }

    /** A square of lines on the plane y, reaching {@code extent} from the origin, cut into {@code cells} on each side. */
    public Mesh3D grid(float y, float extent, int cells, int color) {
        return gridAt(0F, y, 0F, extent, cells, color);
    }

    /** {@link #grid} round another centre. */
    public Mesh3D gridAt(float cx, float y, float cz, float extent, int cells, int color) {
        int n = Math.max(1, cells);
        for (int i = 0; i <= n; i++) {
            float at = -extent + 2F * extent * i / n;
            line(cx + at, y, cz - extent, cx + at, y, cz + extent, color);
            line(cx - extent, y, cz + at, cx + extent, y, cz + at, color);
        }
        return this;
    }

    /** A flat round disc, facing up: what a model stands on. */
    public Mesh3D disc(float cx, float y, float cz, float radius, int sides, int color) {
        int n = Math.max(3, sides);
        for (int i = 0; i < n; i++) {
            double a0 = 2D * Math.PI * i / n;
            double a1 = 2D * Math.PI * (i + 1) / n;
            triangle(cx, y, cz,
                    cx + radius * (float) Math.sin(a0), y, cz + radius * (float) Math.cos(a0),
                    cx + radius * (float) Math.sin(a1), y, cz + radius * (float) Math.cos(a1), color);
        }
        return this;
    }

    /** Copies another mesh's faces and lines into this one, through the current transform. */
    public Mesh3D add(Mesh3D other) {
        for (int i = 0; i < other.polygonCount; i++) {
            int at = i * 4;
            int[] c = other.corners;
            float[] v = other.vertices;
            int a = vertex(v[c[at] * 3], v[c[at] * 3 + 1], v[c[at] * 3 + 2]);
            int b = vertex(v[c[at + 1] * 3], v[c[at + 1] * 3 + 1], v[c[at + 1] * 3 + 2]);
            int t = vertex(v[c[at + 2] * 3], v[c[at + 2] * 3 + 1], v[c[at + 2] * 3 + 2]);
            int d = c[at + 3] < 0 ? -1 : vertex(v[c[at + 3] * 3], v[c[at + 3] * 3 + 1], v[c[at + 3] * 3 + 2]);
            polygon(a, b, t, d, other.colors[i]);
        }
        for (int i = 0; i < other.lineCount; i++) {
            float[] v = other.vertices;
            int a = other.lineEnds[i * 2] * 3;
            int b = other.lineEnds[i * 2 + 1] * 3;
            line(v[a], v[a + 1], v[a + 2], v[b], v[b + 1], v[b + 2], other.lineColors[i]);
        }
        return this;
    }
}
