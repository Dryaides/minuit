package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A grid of solid cubes, each with one colour or a colour per face. Only faces with air beside them become
 * polygons, and side by side faces of one colour merge into one quad, so a big model of few colours stays cheap.
 * The mesh is built on first use and kept until the model changes. Cube x, y, z becomes the world box from there
 * to one more, moved so that the model's centre is the origin; y goes up.
 *
 * <pre>{@code
 * VoxelModel crate = new VoxelModel(3, 3, 3).fill(0, 0, 0, 2, 2, 2, 0xA0703C);
 * crate.set(1, 2, 1, Mesh3D.tone(Mesh3D.Tone.PRIMARY));
 * add(new ModelPreviewComponent(at(new Box("crate", 10, 10, 80, 100)), crate, "Crate"));
 * }</pre>
 */
@Incubating
public final class VoxelModel {
    /** The longest side. Past this a software picture would be mostly noise anyway. */
    public static final int MAX_SIDE = 128;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    /** 0 is air, otherwise one more than an index into materials. */
    private final int[] cells;
    private final List<int[]> materials = new ArrayList<>();
    private final Mesh3D mesh = new Mesh3D();
    private boolean dirty = true;
    private int solid;
    private int exposed;

    public VoxelModel(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = clampSide(sizeX);
        this.sizeY = clampSide(sizeY);
        this.sizeZ = clampSide(sizeZ);
        this.cells = new int[this.sizeX * this.sizeY * this.sizeZ];
    }

    /**
     * Builds a model from text: {@code layers[y][z].charAt(x)}, the first layer at the bottom. A character missing
     * from the legend, a space for instance, is air. Rows may differ in length.
     */
    public static VoxelModel ofLayers(Map<Character, Integer> legend, String[]... layers) {
        int depth = 1;
        int width = 1;
        for (String[] layer : layers) {
            depth = Math.max(depth, layer.length);
            for (String row : layer) {
                width = Math.max(width, row.length());
            }
        }
        VoxelModel model = new VoxelModel(width, Math.max(1, layers.length), depth);
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < layers[y].length; z++) {
                String row = layers[y][z];
                for (int x = 0; x < row.length(); x++) {
                    Integer color = legend.get(row.charAt(x));
                    if (color != null) {
                        model.set(x, y, z, color);
                    }
                }
            }
        }
        return model;
    }

    private static int clampSide(int side) {
        return Math.max(1, Math.min(MAX_SIDE, side));
    }

    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    private int index(int x, int y, int z) {
        return x + sizeX * (z + sizeZ * y);
    }

    /** A cube in one colour. Outside the grid does nothing. Colour 0 is air. */
    public VoxelModel set(int x, int y, int z, int color) {
        if (color == 0) {
            return clear(x, y, z);
        }
        return setMaterial(x, y, z, material(color, color, color, color, color, color));
    }

    /** A cube with a colour per face, indexed by {@link Mesh3D#EAST} and the others. The array is copied. */
    public VoxelModel set(int x, int y, int z, int[] faces) {
        return setMaterial(x, y, z, material(faces[Mesh3D.EAST], faces[Mesh3D.WEST], faces[Mesh3D.UP],
                faces[Mesh3D.DOWN], faces[Mesh3D.SOUTH], faces[Mesh3D.NORTH]));
    }

    private VoxelModel setMaterial(int x, int y, int z, int material) {
        if (inside(x, y, z)) {
            int at = index(x, y, z);
            if (cells[at] == 0) {
                solid++;
            }
            cells[at] = material;
            dirty = true;
        }
        return this;
    }

    private int material(int e, int w, int u, int d, int s, int n) {
        for (int i = 0; i < materials.size(); i++) {
            int[] m = materials.get(i);
            if (m[0] == e && m[1] == w && m[2] == u && m[3] == d && m[4] == s && m[5] == n) {
                return i + 1;
            }
        }
        materials.add(new int[] {e, w, u, d, s, n});
        return materials.size();
    }

    /** Takes a cube away. */
    public VoxelModel clear(int x, int y, int z) {
        if (inside(x, y, z) && cells[index(x, y, z)] != 0) {
            cells[index(x, y, z)] = 0;
            solid--;
            dirty = true;
        }
        return this;
    }

    /** Fills a block, corners included. Corners outside the grid are brought in. */
    public VoxelModel fill(int x0, int y0, int z0, int x1, int y1, int z1, int color) {
        for (int y = Math.max(0, Math.min(y0, y1)); y <= Math.min(sizeY - 1, Math.max(y0, y1)); y++) {
            for (int z = Math.max(0, Math.min(z0, z1)); z <= Math.min(sizeZ - 1, Math.max(z0, z1)); z++) {
                for (int x = Math.max(0, Math.min(x0, x1)); x <= Math.min(sizeX - 1, Math.max(x0, x1)); x++) {
                    set(x, y, z, color);
                }
            }
        }
        return this;
    }

    public boolean solid(int x, int y, int z) {
        return inside(x, y, z) && cells[index(x, y, z)] != 0;
    }

    /** A cube's colour on one face; 0 for air. */
    public int color(int x, int y, int z, int face) {
        if (!inside(x, y, z) || cells[index(x, y, z)] == 0) {
            return 0;
        }
        return materials.get(cells[index(x, y, z)] - 1)[face];
    }

    /** How many cubes are solid. */
    public int solidCount() {
        return solid;
    }

    public boolean isEmpty() {
        return solid == 0;
    }

    /** How many cube faces have air beside them: the count without any merging. */
    public int exposedFaces() {
        mesh();
        return exposed;
    }

    /** The polygons, one quad per merged face. Built when the model has changed, and kept: do not add to it. */
    public Mesh3D mesh() {
        if (dirty) {
            dirty = false;
            build();
        }
        return mesh;
    }

    private void build() {
        mesh.clear();
        exposed = 0;
        int[] size = {sizeX, sizeY, sizeZ};
        int longest = Math.max(sizeX, Math.max(sizeY, sizeZ));
        int[] mask = new int[longest * longest];
        int[] at = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            int u = (axis + 1) % 3;
            int v = (axis + 2) % 3;
            for (int sign = 1; sign >= -1; sign -= 2) {
                int face = axis == 0 ? (sign > 0 ? Mesh3D.EAST : Mesh3D.WEST)
                        : axis == 1 ? (sign > 0 ? Mesh3D.UP : Mesh3D.DOWN)
                        : (sign > 0 ? Mesh3D.SOUTH : Mesh3D.NORTH);
                for (int k = 0; k < size[axis]; k++) {
                    at[axis] = k;
                    for (int j = 0; j < size[v]; j++) {
                        at[v] = j;
                        for (int i = 0; i < size[u]; i++) {
                            at[u] = i;
                            mask[i + j * size[u]] = faceAt(at, axis, sign, face);
                        }
                    }
                    merge(mask, size, axis, u, v, sign, k);
                }
            }
        }
    }

    private int faceAt(int[] at, int axis, int sign, int face) {
        int x = at[0];
        int y = at[1];
        int z = at[2];
        int material = cells[index(x, y, z)];
        if (material == 0) {
            return 0;
        }
        int nx = axis == 0 ? x + sign : x;
        int ny = axis == 1 ? y + sign : y;
        int nz = axis == 2 ? z + sign : z;
        if (solid(nx, ny, nz)) {
            return 0;
        }
        int color = materials.get(material - 1)[face];
        if (color != 0) {
            exposed++;
        }
        return color;
    }

    /** Greedy rectangles: as wide as one colour runs, then as tall as that whole width repeats. */
    private void merge(int[] mask, int[] size, int axis, int u, int v, int sign, int k) {
        int su = size[u];
        int sv = size[v];
        float[] origin = {-sizeX / 2F, -sizeY / 2F, -sizeZ / 2F};
        float plane = k + (sign > 0 ? 1 : 0) + origin[axis];
        float[] a = new float[3];
        float[] b = new float[3];
        float[] c = new float[3];
        float[] d = new float[3];
        for (int j = 0; j < sv; j++) {
            for (int i = 0; i < su; ) {
                int color = mask[i + j * su];
                if (color == 0) {
                    i++;
                    continue;
                }
                int wide = 1;
                while (i + wide < su && mask[i + wide + j * su] == color) {
                    wide++;
                }
                int tall = 1;
                boolean same = true;
                while (j + tall < sv && same) {
                    for (int n = 0; n < wide && same; n++) {
                        same = mask[i + n + (j + tall) * su] == color;
                    }
                    if (same) {
                        tall++;
                    }
                }
                for (int r = 0; r < tall; r++) {
                    Arrays.fill(mask, i + (j + r) * su, i + wide + (j + r) * su, 0);
                }
                float u0 = i + origin[u];
                float u1 = i + wide + origin[u];
                float v0 = j + origin[v];
                float v1 = j + tall + origin[v];
                a[axis] = plane; a[u] = u0; a[v] = v0;
                b[axis] = plane; b[u] = u1; b[v] = v0;
                c[axis] = plane; c[u] = u1; c[v] = v1;
                d[axis] = plane; d[u] = u0; d[v] = v1;
                // The u and v axes follow the axis in turn, so this order faces the positive side.
                if (sign > 0) {
                    mesh.quad(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], color);
                } else {
                    mesh.quad(d[0], d[1], d[2], c[0], c[1], c[2], b[0], b[1], b[2], a[0], a[1], a[2], color);
                }
                i += wide;
            }
        }
    }
}
