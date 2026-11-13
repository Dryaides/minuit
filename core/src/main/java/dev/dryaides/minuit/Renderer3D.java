package dev.dryaides.minuit;

import java.util.Arrays;

/**
 * Draws meshes with no graphics API: a software z-buffer on a small grid of cells, then the grid is handed to
 * {@link Draw#fill} as few rectangles as it can (same-coloured runs in a row, then equal runs of rows). One cell is one
 * GUI unit at {@code pixel size} 1, which gives the blocky look of the game's own GUI.
 *
 * <p>Why a z-buffer and not depth sorting: a painter's sort fails on boxes that cross or overlap in a cycle, and
 * no sort key fixes that. Here each cell keeps the nearest face it has seen, so the picture is right for any input.
 * Faces closer together than about 1/3000 of the camera distance count as one plane, and the one added first
 * wins, in every frame: that is what stops z-fighting on coplanar faces.
 *
 * <p>The grid and the rectangles are kept between frames. {@link #stale} says if anything changed; when nothing has,
 * {@link #emit} only replays the rectangles. Neither allocates once the arrays have grown to fit.
 *
 * <p>Faces are opaque. Light is one direction with half-Lambert shading, so every face direction gets its own shade
 * and the edges of a box stay visible from behind. Dark and light themes have a different shadow colour.
 */
final class Renderer3D {
    private static final float COVER_EPS = 1e-3F;
    /** Depth is about 1 at the target, so this is a share of the camera distance. */
    private static final float DEPTH_EPS = 3e-4F;
    /** How far lines are pulled towards the viewer, so that edges on a face show whole. */
    private static final float LINE_BIAS = 4e-3F;
    /** A face seen so nearly edge-on takes no cell: its depth would be all slope. */
    private static final float MIN_AREA = 0.02F;
    private static final int RUN = 5;

    private static final float DARK_AMBIENT = 0.42F;
    private static final float LIGHT_AMBIENT = 0.5F;
    private static final float DARK_THEME_LUMINANCE = 0.3F;

    private int cellsW;
    private int cellsH;
    // Smooth: samples per output cell along each side, edge levels, line width in output cells.
    private int samples = 1;
    private int levels = 4;
    private int lineWidth = 1;
    private int outW;
    private int outH;
    private int[] out = new int[0];
    private int madeSamples = -1;
    private int madeLevels = -1;
    private int madeLineWidth = -1;
    private int[] color = new int[0];
    private float[] depth = new float[0];

    private int[] runs = new int[RUN * 64];
    private int runCount;
    private int[] prevRow = new int[0];
    private int[] curRow = new int[0];

    private float[] viewX = new float[0];
    private float[] viewY = new float[0];
    private float[] viewZ = new float[0];
    private float[] screenX = new float[0];
    private float[] screenY = new float[0];
    private float[] screenD = new float[0];
    private final float[] polyX = new float[6];
    private final float[] polyY = new float[6];
    private final float[] polyZ = new float[6];
    private final double[] outX = new double[6];
    private final double[] outY = new double[6];
    private final double[] outD = new double[6];
    private final int[] tones = new int[Mesh3D.Tone.values().length + 1];
    private final float[] axisOrder = new float[3];

    private final Vec3f light = new Vec3f(0.34F, 0.82F, 0.46F).normalize();
    private long lightRevision;

    // What the kept picture was made from.
    private long madeCamera = -1L;
    private long madeContent = -1L;
    private long madeLight = -1L;
    private Palette madePalette;
    private Camera3D madeCam;
    private int madeCorner = -1;

    // The camera, for the frame being made.
    private Camera3D camera;
    private float cx;
    private float cy;
    private float focal;
    private boolean perspective;
    private float near;
    private float distance;
    private float shadowR;
    private float shadowG;
    private float shadowB;
    private float ambient;

    private int submitted;
    private int culled;
    private int linesDrawn;
    private long cellsWritten;
    private long startedAt;
    private long lastNanos;
    private long rasterCount;

    /** Where the light comes from, in world units. Up and a little to the side by default. */
    public Renderer3D light(float x, float y, float z) {
        light.set(x, y, z).normalize();
        lightRevision++;
        return this;
    }

    /**
     * Smooth edges: each output cell is {@code samples x samples} samples, and an edge cell takes their mean colour with
     * the covered share as alpha, rounded to {@code levels} steps. 1 sample is the blocky picture.
     */
    public Renderer3D smooth(int samples, int levels) {
        this.samples = Math.max(1, samples);
        this.levels = Math.max(1, levels);
        return this;
    }

    /** How wide lines are, in output cells. 1 by default. */
    public Renderer3D lineWidth(int lineWidth) {
        this.lineWidth = Math.max(1, lineWidth);
        return this;
    }

    /**
     * True when {@link #begin} must make a new picture.
     *
     * @param content anything that changes when the meshes change: a sum of their versions will do
     */
    public boolean stale(Camera3D camera, long content, Palette palette, int w, int h, int corner) {
        return camera != madeCam || camera.revision() != madeCamera || content != madeContent || palette != madePalette
                || lightRevision != madeLight || w != outW || h != outH || corner != madeCorner
                || samples != madeSamples || levels != madeLevels || lineWidth != madeLineWidth;
    }

    /** Remembers what this picture is made from, for {@link #stale}. Call it with {@link #begin}. */
    public void made(Camera3D camera, long content, Palette palette, int corner) {
        madeCam = camera;
        madeCamera = camera.revision();
        madeContent = content;
        madePalette = palette;
        madeLight = lightRevision;
        madeCorner = corner;
        madeSamples = samples;
        madeLevels = levels;
        madeLineWidth = lineWidth;
    }

    /** Forgets the picture: the next {@link #stale} says true. */
    public void invalidate() {
        madeCam = null;
    }

    /**
     * Clears the grid and takes up the camera and the theme's colours. {@code w} and {@code h} are in cells. A null
     * palette shades toward black with no theme tones, for a picture that keeps its own colours (an entity's texels).
     */
    public void begin(Camera3D camera, Palette p, int w, int h) {
        startedAt = System.nanoTime();
        outW = Math.max(0, w);
        outH = Math.max(0, h);
        w *= samples;
        h *= samples;
        int cells = Math.max(0, w) * Math.max(0, h);
        if (color.length < cells) {
            color = new int[cells];
            depth = new float[cells];
        }
        if (prevRow.length < w + 1) {
            prevRow = new int[w + 1];
            curRow = new int[w + 1];
        }
        cellsW = Math.max(0, w);
        cellsH = Math.max(0, h);
        Arrays.fill(color, 0, cells, 0);
        Arrays.fill(depth, 0, cells, -Float.MAX_VALUE);
        submitted = 0;
        culled = 0;
        linesDrawn = 0;
        cellsWritten = 0L;
        runCount = 0;
        this.camera = camera;
        camera.update();
        cx = cellsW / 2F;
        cy = cellsH / 2F;
        focal = camera.focal(Math.max(1, cellsW), Math.max(1, cellsH));
        perspective = camera.projection() == Camera3D.Projection.PERSPECTIVE;
        near = camera.near();
        distance = camera.distance();
        resolveTheme(p);
    }

    private void resolveTheme(Palette p) {
        if (p == null) {
            ambient = LIGHT_AMBIENT;
            shadowR = 0;
            shadowG = 0;
            shadowB = 0;
            return;
        }
        int surface = p.surface() | 0xFF000000;
        boolean dark = Shapes.luminance(surface) < DARK_THEME_LUMINANCE;
        ambient = dark ? DARK_AMBIENT : LIGHT_AMBIENT;
        int shadow = dark ? Shapes.mix(0xFF000000, surface, 0.25F) : Shapes.mix(0xFF20242C, surface, 0.15F);
        shadowR = (shadow >> 16) & 0xFF;
        shadowG = (shadow >> 8) & 0xFF;
        shadowB = shadow & 0xFF;
        int back = over(surface, p.well());
        tones[Mesh3D.tone(Mesh3D.Tone.PRIMARY)] = over(surface, p.primary());
        tones[Mesh3D.tone(Mesh3D.Tone.ACCENT)] = over(surface, p.accent());
        tones[Mesh3D.tone(Mesh3D.Tone.GOOD)] = over(surface, p.good());
        tones[Mesh3D.tone(Mesh3D.Tone.WARN)] = over(surface, p.warn());
        tones[Mesh3D.tone(Mesh3D.Tone.BAD)] = over(surface, p.bad());
        tones[Mesh3D.tone(Mesh3D.Tone.RAISED)] = over(surface, p.raised());
        tones[Mesh3D.tone(Mesh3D.Tone.TEXT)] = over(surface, p.text());
        tones[Mesh3D.tone(Mesh3D.Tone.TRACK)] = Shapes.mix(back, over(surface, p.text()), dark ? 0.16F : 0.14F);
        tones[Mesh3D.tone(Mesh3D.Tone.GRID)] = Shapes.mix(back, over(surface, p.text()), 0.3F);
    }

    /** A colour with alpha, as it looks over a solid one. */
    private static int over(int under, int color) {
        return Shapes.mix(under, color | 0xFF000000, (color >>> 24) / 255F);
    }

    /** Draws a mesh into the grid. Any number of meshes may go between {@link #begin} and {@link #end}. */
    public void draw(Mesh3D mesh) {
        int vertices = mesh.vertexCount;
        if (viewX.length < vertices) {
            int size = Math.max(vertices, viewX.length * 2);
            viewX = new float[size];
            viewY = new float[size];
            viewZ = new float[size];
            screenX = new float[size];
            screenY = new float[size];
            screenD = new float[size];
        }
        transform(mesh);
        drawPolygons(mesh);
        drawLines(mesh);
    }

    private void transform(Mesh3D mesh) {
        Vec3f eye = camera.eye();
        Vec3f r = camera.right();
        Vec3f u = camera.up();
        Vec3f f = camera.forward();
        float[] v = mesh.vertices;
        for (int i = 0; i < mesh.vertexCount; i++) {
            float wx = v[i * 3] - eye.x;
            float wy = v[i * 3 + 1] - eye.y;
            float wz = v[i * 3 + 2] - eye.z;
            float x = r.x * wx + r.y * wy + r.z * wz;
            float y = u.x * wx + u.y * wy + u.z * wz;
            float z = f.x * wx + f.y * wy + f.z * wz;
            viewX[i] = x;
            viewY[i] = y;
            viewZ[i] = z;
            if (perspective) {
                if (z > near) {
                    float over = focal / z;
                    screenX[i] = cx + x * over;
                    screenY[i] = cy - y * over;
                    screenD[i] = distance / z;
                }
            } else {
                screenX[i] = cx + x * focal;
                screenY[i] = cy - y * focal;
                screenD[i] = 2F - z / distance;
            }
        }
    }

    private void drawPolygons(Mesh3D mesh) {
        Vec3f eye = camera.eye();
        Vec3f f = camera.forward();
        int[] corners = mesh.corners;
        float[] normals = mesh.normals;
        float[] v = mesh.vertices;
        for (int i = 0; i < mesh.polygonCount; i++) {
            int at = i * 4;
            int count = corners[at + 3] < 0 ? 3 : 4;
            submitted += count - 2;
            float nx = normals[i * 3];
            float ny = normals[i * 3 + 1];
            float nz = normals[i * 3 + 2];
            int a = corners[at];
            float facing = perspective
                    ? nx * (v[a * 3] - eye.x) + ny * (v[a * 3 + 1] - eye.y) + nz * (v[a * 3 + 2] - eye.z)
                    : nx * f.x + ny * f.y + nz * f.z;
            if (facing >= 0F) {
                culled += count - 2;
                continue;
            }
            int shown = gather(corners, at, count);
            if (shown >= 3) {
                fillPolygon(shown, shade(mesh.colors[i], nx, ny, nz));
            }
        }
    }

    /** Copies a polygon's corners to the raster's arrays, cut by the near plane. Returns how many corners remain. */
    private int gather(int[] corners, int at, int count) {
        boolean behind = false;
        if (perspective) {
            for (int c = 0; c < count; c++) {
                behind |= viewZ[corners[at + c]] <= near;
            }
        }
        if (!behind) {
            for (int c = 0; c < count; c++) {
                int index = corners[at + c];
                outX[c] = screenX[index];
                outY[c] = screenY[index];
                outD[c] = screenD[index];
            }
            return count;
        }
        int out = 0;
        for (int c = 0; c < count; c++) {
            int p = corners[at + c];
            int q = corners[at + (c + 1) % count];
            float zp = viewZ[p];
            float zq = viewZ[q];
            if (zp > near) {
                polyX[out] = viewX[p];
                polyY[out] = viewY[p];
                polyZ[out] = zp;
                out++;
            }
            if ((zp > near) != (zq > near)) {
                float t = (near - zp) / (zq - zp);
                polyX[out] = viewX[p] + (viewX[q] - viewX[p]) * t;
                polyY[out] = viewY[p] + (viewY[q] - viewY[p]) * t;
                polyZ[out] = near;
                out++;
            }
        }
        for (int c = 0; c < out; c++) {
            double over = focal / polyZ[c];
            outX[c] = cx + polyX[c] * over;
            outY[c] = cy - polyY[c] * over;
            outD[c] = distance / polyZ[c];
        }
        return out;
    }

    private int shade(int base, float nx, float ny, float nz) {
        int solid = Mesh3D.isTone(base) ? tones[base] : base;
        float half = 0.5F + 0.5F * (nx * light.x + ny * light.y + nz * light.z);
        float s = ambient + (1F - ambient) * half;
        float t = 1F - s;
        int r = (int) (((solid >> 16) & 0xFF) * s + shadowR * t);
        int g = (int) (((solid >> 8) & 0xFF) * s + shadowG * t);
        int b = (int) ((solid & 0xFF) * s + shadowB * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The polygon in outX, outY, outD, which must be convex: a span per row, tested cell by cell against the grid. */
    private void fillPolygon(int n, int ink) {
        double x0 = outX[0];
        double y0 = outY[0];
        double d0 = outD[0];
        double area2 = 0D;
        int second = 1;
        int third = 2;
        for (int i = 2; i < n; i++) {
            double cross = (outX[i - 1] - x0) * (outY[i] - y0) - (outY[i - 1] - y0) * (outX[i] - x0);
            if (Math.abs(cross) > Math.abs(area2)) {
                area2 = cross;
                second = i - 1;
                third = i;
            }
        }
        if (Math.abs(area2) < MIN_AREA) {
            return;
        }
        double ax = outX[second] - x0;
        double ay = outY[second] - y0;
        double bx = outX[third] - x0;
        double by = outY[third] - y0;
        double da = outD[second] - d0;
        double db = outD[third] - d0;
        double slopeX = (da * by - db * ay) / area2;
        double slopeY = (db * ax - da * bx) / area2;
        double base = d0 - slopeX * x0 - slopeY * y0;

        double minY = y0;
        double maxY = y0;
        double minD = d0;
        double maxD = d0;
        for (int i = 1; i < n; i++) {
            minY = Math.min(minY, outY[i]);
            maxY = Math.max(maxY, outY[i]);
            minD = Math.min(minD, outD[i]);
            maxD = Math.max(maxD, outD[i]);
        }
        int firstRow = Math.max(0, (int) Math.ceil(minY - 0.5D - COVER_EPS));
        int lastRow = Math.min(cellsH - 1, (int) Math.floor(maxY - 0.5D + COVER_EPS));
        for (int row = firstRow; row <= lastRow; row++) {
            double ry = row + 0.5D;
            double left = Double.MAX_VALUE;
            double right = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int j = i + 1 == n ? 0 : i + 1;
                double ya = outY[i];
                double yb = outY[j];
                if ((ry < ya - COVER_EPS && ry < yb - COVER_EPS) || (ry > ya + COVER_EPS && ry > yb + COVER_EPS)) {
                    continue;
                }
                double xa = outX[i];
                double xb = outX[j];
                double span = yb - ya;
                if (Math.abs(span) < 1e-9D) {
                    left = Math.min(left, Math.min(xa, xb));
                    right = Math.max(right, Math.max(xa, xb));
                } else {
                    double t = Math.max(0D, Math.min(1D, (ry - ya) / span));
                    double at = xa + (xb - xa) * t;
                    left = Math.min(left, at);
                    right = Math.max(right, at);
                }
            }
            if (left > right) {
                continue;
            }
            int firstCol = Math.max(0, (int) Math.ceil(left - 0.5D - COVER_EPS));
            int lastCol = Math.min(cellsW - 1, (int) Math.floor(right - 0.5D + COVER_EPS));
            int cell = row * cellsW + firstCol;
            double rowDepth = base + slopeY * ry;
            for (int col = firstCol; col <= lastCol; col++, cell++) {
                double value = rowDepth + slopeX * (col + 0.5D);
                value = value < minD ? minD : value > maxD ? maxD : value;
                if (value > depth[cell] + DEPTH_EPS) {
                    depth[cell] = (float) value;
                    color[cell] = ink;
                    cellsWritten++;
                }
            }
        }
    }

    private void drawLines(Mesh3D mesh) {
        for (int i = 0; i < mesh.lineCount; i++) {
            int a = mesh.lineEnds[i * 2];
            int b = mesh.lineEnds[i * 2 + 1];
            int base = mesh.lineColors[i];
            int ink = (Mesh3D.isTone(base) ? tones[base] : base) | 0xFF000000;
            if (perspective) {
                float za = viewZ[a];
                float zb = viewZ[b];
                if (za <= near && zb <= near) {
                    continue;
                }
                if (za <= near || zb <= near) {
                    int in = za > near ? a : b;
                    int out = za > near ? b : a;
                    float t = (near - viewZ[in]) / (viewZ[out] - viewZ[in]);
                    float x = viewX[in] + (viewX[out] - viewX[in]) * t;
                    float y = viewY[in] + (viewY[out] - viewY[in]) * t;
                    float over = focal / near;
                    line(screenX[in], screenY[in], screenD[in], cx + x * over, cy - y * over, distance / near, ink);
                    continue;
                }
            }
            line(screenX[a], screenY[a], screenD[a], screenX[b], screenY[b], screenD[b], ink);
        }
    }

    /** One cell wide, as a straight walk over the longer axis, first cut down to the grid. */
    private void line(float xa, float ya, float da, float xb, float yb, float db, int ink) {
        float dx = xb - xa;
        float dy = yb - ya;
        float enter = 0F;
        float leave = 1F;
        for (int i = 0; i < 4; i++) {
            float p = i == 0 ? -dx : i == 1 ? dx : i == 2 ? -dy : dy;
            float q = i == 0 ? xa : i == 1 ? cellsW - xa : i == 2 ? ya : cellsH - ya;
            if (p == 0F) {
                if (q < 0F) {
                    return;
                }
            } else {
                float r = q / p;
                if (p < 0F) {
                    enter = Math.max(enter, r);
                } else {
                    leave = Math.min(leave, r);
                }
            }
        }
        if (enter > leave) {
            return;
        }
        linesDrawn++;
        float sx = xa + dx * enter;
        float sy = ya + dy * enter;
        float ex = xa + dx * leave;
        float ey = ya + dy * leave;
        float sd = da + (db - da) * enter;
        float ed = da + (db - da) * leave;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(ex - sx), Math.abs(ey - sy))));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int col = (int) Math.floor(sx + (ex - sx) * t);
            int row = (int) Math.floor(sy + (ey - sy) * t);
            if (col < 0 || row < 0 || col >= cellsW || row >= cellsH) {
                continue;
            }
            float value = sd + (ed - sd) * t;
            value += value * LINE_BIAS;
            int wide = lineWidth * samples;
            if (wide <= 1) {
                stamp(col, row, value, ink);
            } else {
                int half = wide / 2;
                for (int r = row - half; r < row - half + wide; r++) {
                    for (int c = col - half; c < col - half + wide; c++) {
                        if (c >= 0 && r >= 0 && c < cellsW && r < cellsH) {
                            stamp(c, r, value, ink);
                        }
                    }
                }
            }
        }
    }

    private void stamp(int col, int row, float value, int ink) {
        int cell = row * cellsW + col;
        if (value > depth[cell]) {
            depth[cell] = value;
            color[cell] = ink;
            cellsWritten++;
        }
    }

    /** A small set of axes over the picture, nearest drawn last, ignoring depth. The centre and the length are in cells. */
    public void axes(int centerX, int centerY, int length, int xInk, int yInk, int zInk) {
        Vec3f f = camera.forward();
        // Far axis first: x, y, z by how far along the view each one points.
        float[] order = axisOrder;
        order[0] = f.x;
        order[1] = f.y;
        order[2] = f.z;
        for (int pass = 0; pass < 3; pass++) {
            int pick = -1;
            for (int i = 0; i < 3; i++) {
                if (order[i] != -Float.MAX_VALUE && (pick < 0 || order[i] > order[pick])) {
                    pick = i;
                }
            }
            float dx = pick == 0 ? camera.right().x : pick == 1 ? camera.right().y : camera.right().z;
            float dy = pick == 0 ? camera.up().x : pick == 1 ? camera.up().y : camera.up().z;
            int ink = (pick == 0 ? xInk : pick == 1 ? yInk : zInk) | 0xFF000000;
            order[pick] = -Float.MAX_VALUE;
            float x0 = (centerX + 0.5F) * samples;
            float y0 = (centerY + 0.5F) * samples;
            overlayLine(x0, y0, x0 + dx * length * samples, y0 - dy * length * samples, ink);
        }
    }

    private void overlayLine(float xa, float ya, float xb, float yb, int ink) {
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(xb - xa), Math.abs(yb - ya))));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int col = (int) Math.floor(xa + (xb - xa) * t);
            int row = (int) Math.floor(ya + (yb - ya) * t);
            int wide = lineWidth * samples;
            int half = wide / 2;
            for (int r = row - half; r < row - half + wide; r++) {
                for (int c = col - half; c < col - half + wide; c++) {
                    if (c >= 0 && r >= 0 && c < cellsW && r < cellsH) {
                        color[r * cellsW + c] = ink;
                    }
                }
            }
        }
    }

    /** Clears the corner cells, so that a rounded frame round the picture keeps its shape. */
    public void cutCorners(int radius) {
        radius *= samples;
        for (int row = 0; row < radius && row < cellsH; row++) {
            int cut = Math.min(cellsW, radius - row);
            for (int col = 0; col < cut; col++) {
                color[row * cellsW + col] = 0;
                color[row * cellsW + cellsW - 1 - col] = 0;
                if (cellsH - 1 - row >= 0) {
                    color[(cellsH - 1 - row) * cellsW + col] = 0;
                    color[(cellsH - 1 - row) * cellsW + cellsW - 1 - col] = 0;
                }
            }
        }
    }

    /** Turns the grid into rectangles. */
    public void end() {
        int[] grid = color;
        int gridW = cellsW;
        int gridH = cellsH;
        if (samples > 1) {
            resolve();
            grid = out;
            gridW = outW;
            gridH = outH;
        }
        runCount = 0;
        int prevCount = 0;
        int[] prev = prevRow;
        int[] cur = curRow;
        for (int row = 0; row < gridH; row++) {
            int curCount = 0;
            int walk = 0;
            int base = row * gridW;
            int col = 0;
            while (col < gridW) {
                int ink = grid[base + col];
                if (ink == 0) {
                    col++;
                    continue;
                }
                int from = col;
                do {
                    col++;
                } while (col < gridW && grid[base + col] == ink);
                while (walk < prevCount && runs[prev[walk] * RUN] < from) {
                    walk++;
                }
                int same = -1;
                if (walk < prevCount) {
                    int at = prev[walk] * RUN;
                    if (runs[at] == from && runs[at + 2] == col && runs[at + 4] == ink) {
                        same = prev[walk];
                    }
                }
                if (same >= 0) {
                    runs[same * RUN + 3] = row + 1;
                    cur[curCount++] = same;
                } else {
                    if ((runCount + 1) * RUN > runs.length) {
                        runs = Arrays.copyOf(runs, runs.length * 2);
                    }
                    int at = runCount * RUN;
                    runs[at] = from;
                    runs[at + 1] = row;
                    runs[at + 2] = col;
                    runs[at + 3] = row + 1;
                    runs[at + 4] = ink;
                    cur[curCount++] = runCount++;
                }
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
            prevCount = curCount;
        }
        prevRow = prev;
        curRow = cur;
        lastNanos = System.nanoTime() - startedAt;
        rasterCount++;
    }

    /** Each output cell from its samples: the mean colour of what covers it, the covered share as alpha. */
    private void resolve() {
        int cells = outW * outH;
        if (out.length < cells) {
            out = new int[cells];
        }
        int n = samples * samples;
        for (int row = 0; row < outH; row++) {
            for (int col = 0; col < outW; col++) {
                int count = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                for (int sy = 0; sy < samples; sy++) {
                    int at = (row * samples + sy) * cellsW + col * samples;
                    for (int sx = 0; sx < samples; sx++) {
                        int c = color[at + sx];
                        if (c != 0) {
                            count++;
                            r += c >> 16 & 0xFF;
                            g += c >> 8 & 0xFF;
                            b += c & 0xFF;
                        }
                    }
                }
                int ink = 0;
                if (count > 0) {
                    int a = count == n ? 255 : Math.round(Math.round(count * levels / (float) n) * 255F / levels);
                    if (a > 0) {
                        ink = a << 24 | (r / count) << 16 | (g / count) << 8 | (b / count);
                    }
                }
                out[row * outW + col] = ink;
            }
        }
    }

    /** Draws the picture, its top left at x and y, each cell a square of {@code cellSize} GUI units. */
    public void emit(Draw d, int x, int y, int cellSize) {
        int[] r = runs;
        for (int i = 0, at = 0; i < runCount; i++, at += RUN) {
            d.fill(x + r[at] * cellSize, y + r[at + 1] * cellSize, x + r[at + 2] * cellSize, y + r[at + 3] * cellSize, r[at + 4]);
        }
    }

    /**
     * Draws the picture see-through, for a disabled look. Rectangles do not overlap, so the model fades evenly, and what is
     * behind it, a frame's bevel or glass, stays untouched.
     */
    public void emit(Draw d, int x, int y, int cellSize, int alpha) {
        int[] r = runs;
        for (int i = 0, at = 0; i < runCount; i++, at += RUN) {
            d.fill(x + r[at] * cellSize, y + r[at + 1] * cellSize, x + r[at + 2] * cellSize, y + r[at + 3] * cellSize, (((r[at + 4] >>> 24) * alpha / 255) << 24) | (r[at + 4] & 0xFFFFFF));
        }
    }

    // ---- what the last picture took ----

    /** A cell's colour; 0 where nothing was drawn. For a test. */
    public int colorAt(int col, int row) {
        return col < 0 || row < 0 || col >= cellsW || row >= cellsH ? 0 : color[row * cellsW + col];
    }

    public int cellsW() { return cellsW; }
    public int cellsH() { return cellsH; }
    /** Rectangles {@link #emit} draws. This is what costs in the game. */
    public int rectCount() { return runCount; }
    /** Triangles given to the last picture: a quad is two. */
    public int triangles() { return submitted; }
    /** Of those, how many faced away. */
    public int culled() { return culled; }
    public int lines() { return linesDrawn; }
    /** Writes to the grid: above the cell count means overdraw. */
    public long cellWrites() { return cellsWritten; }
    /** How long the last picture took to make, in nanoseconds. */
    public long nanos() { return lastNanos; }
    /** How many pictures were made. It stands still on idle frames. */
    public long pictures() { return rasterCount; }
}
