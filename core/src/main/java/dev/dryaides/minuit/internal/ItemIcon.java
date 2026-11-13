package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.internal.ImageSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An item's icon off-game, from the textures {@link Draw#texels} reads: the item texture, else a block's
 * cube as the game's GUI shows it, else the block's flat texture. One path for every renderer without the
 * game (the site's live canvas, the bench's still renders), so both draw the same pixels.
 */
public final class ItemIcon {

    private ItemIcon() {
    }

    /** Built cubes by texture base and device size, kept with the faces they were built from so a late face rebuilds them. */
    private static final Map<String, Object[]> CUBES = new ConcurrentHashMap<>();

    /**
     * The icon of {@code itemId} at {@code n} device pixels a side when it is a cube, else its flat texture at
     * the texture's own size (the caller stretches it to the 16 GUI pixels); null when {@code d} has no texture.
     */
    public static ImageSource.Pixels of(Draw d, String itemId, int n) {
        String[] parts = itemId.split(":", 2);
        if (parts.length < 2) {
            parts = new String[]{"minecraft", itemId};
        }
        ImageSource.Pixels flat = d.texels(parts[0] + ":textures/item/" + parts[1] + ".png");
        if (flat != null) {
            return flat;
        }
        if (itemId.equals("minecraft:shield") || itemId.equals("shield")) {
            return shield(d, Math.max(1, n));
        }
        String base = parts[0] + ":textures/block/" + parts[1];
        flat = d.texels(base + ".png");
        if (flat == null) {
            return null;
        }
        ImageSource.Pixels cube = cube(d, base, flat, Math.max(1, n));
        return cube != null ? cube : flat;
    }

    /**
     * A block with no item texture as the game's GUI shows it: the cube under the GUI block transform
     * (30 deg pitch, 225 deg yaw, scale 0.625, orthographic), top face lit, left and right faces shaded, from
     * {@code _top} (else {@code _end}) and {@code _side} when there. Built at {@code n} device pixels for the
     * 16 GUI pixels, so edges and skewed texels land on device pixels as in the game.
     * A texture with any transparent texel (a flower, a sapling) stays flat, as its item model is.
     */
    private static ImageSource.Pixels cube(Draw d, String base, ImageSource.Pixels flat, int n) {
        for (int argb : flat.argb()) {
            if (argb >>> 24 != 255) {
                return null;
            }
        }
        ImageSource.Pixels top = d.texels(base + "_top.png");
        if (top == null) {
            top = d.texels(base + "_end.png");
        }
        ImageSource.Pixels side = d.texels(base + "_side.png");
        top = top != null ? top : flat;
        side = side != null ? side : flat;
        String key = base + "@" + n;
        Object[] kept = CUBES.get(key);
        if (kept != null && kept[0] == top.argb() && kept[1] == side.argb()) {
            return (ImageSource.Pixels) kept[2];
        }
        double k = n / 16.0;
        double edge = 10 * k;
        double hw = edge * Math.sqrt(0.5);
        double hh = hw * 0.5;
        double sh = edge * Math.sqrt(0.75);
        double cx = n / 2.0;
        double yc = n / 2.0 - (2 * hh + sh) / 2 + hh;
        int[] out = new int[n * n];
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                double dx = (px + 0.5 - cx) / hw;
                double y = py + 0.5;
                double dy = (y - yc) / hh;
                double u = (dx + dy + 1) / 2;
                double v = (dy - dx + 1) / 2;
                int argb = 0;
                if (u >= 0 && u < 1 && v >= 0 && v < 1) {
                    argb = texel(top, u, v, 255);
                } else if (dx >= -1 && dx < 0) {
                    double a = dx + 1;
                    double b = (y - yc - hh * a) / sh;
                    argb = b >= 0 && b < 1 ? texel(side, a, b, 204) : 0;
                } else if (dx >= 0 && dx < 1) {
                    double b = (y - yc - hh + hh * dx) / sh;
                    argb = b >= 0 && b < 1 ? texel(side, dx, b, 153) : 0;
                }
                out[py * n + px] = argb;
            }
        }
        ImageSource.Pixels cube = new ImageSource.Pixels(n, n, out);
        CUBES.put(key, new Object[]{top.argb(), side.argb(), cube});
        return cube;
    }

    private static final String SHIELD = "minecraft:textures/entity/shield/shield_base_nopattern.png";

    /**
     * The shield as the game's inventory shows it: its box model (plate 12x22x1, handle 2x6x6 on a 64x64 texture)
     * under the item's flip (scale 1,-1,-1), the -0.5 block offset and the gui display of models/item/shield.json
     * (rotation 15,-25,-5, translation 2,3,0, scale 0.65), orthographic, faces shaded by how far they turn from
     * the viewer. Null while the texture is not loaded.
     */
    private static ImageSource.Pixels shield(Draw d, int n) {
        ImageSource.Pixels tex = d.texels(SHIELD);
        if (tex == null) {
            return null;
        }
        String key = SHIELD + "@" + n;
        Object[] kept = CUBES.get(key);
        if (kept != null && kept[0] == tex.argb()) {
            return (ImageSource.Pixels) kept[2];
        }
        double[] rot = rotationXYZ(Math.toRadians(15), Math.toRadians(-25), Math.toRadians(-5));
        double[] depth = new double[n * n];
        java.util.Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        int[] out = new int[n * n];
        double[][] boxes = {{-6, -11, -2, 12, 22, 1, 0, 0}, {-1, -3, -1, 2, 6, 6, 26, 0}};
        for (double[] b : boxes) {
            double x0 = b[0], y0 = b[1], z0 = b[2], w = b[3], h = b[4], dd = b[5];
            double x1 = x0 + w, y1 = y0 + h, z1 = z0 + dd;
            int u = (int) b[6], v = (int) b[7];
            int iw = (int) w, ih = (int) h, id = (int) dd;
            // Each face: origin corner, the corners its texture u and v run to, its texture rectangle.
            face(out, depth, n, rot, tex, new double[]{x0, y0, z0}, new double[]{x1, y0, z0}, new double[]{x0, y1, z0}, u + id, v + id, iw, ih);
            face(out, depth, n, rot, tex, new double[]{x1, y0, z1}, new double[]{x0, y0, z1}, new double[]{x1, y1, z1}, u + 2 * id + iw, v + id, iw, ih);
            face(out, depth, n, rot, tex, new double[]{x0, y0, z1}, new double[]{x0, y0, z0}, new double[]{x0, y1, z1}, u, v + id, id, ih);
            face(out, depth, n, rot, tex, new double[]{x1, y0, z0}, new double[]{x1, y0, z1}, new double[]{x1, y1, z0}, u + id + iw, v + id, id, ih);
            face(out, depth, n, rot, tex, new double[]{x0, y0, z1}, new double[]{x1, y0, z1}, new double[]{x0, y0, z0}, u + id, v, iw, id);
            face(out, depth, n, rot, tex, new double[]{x0, y1, z0}, new double[]{x1, y1, z0}, new double[]{x0, y1, z1}, u + id + iw, v, iw, id);
        }
        ImageSource.Pixels icon = new ImageSource.Pixels(n, n, out);
        CUBES.put(key, new Object[]{tex.argb(), null, icon});
        return icon;
    }

    /** JOML's rotationXYZ as a row-major 3x3: Rx * Ry * Rz. */
    private static double[] rotationXYZ(double ax, double ay, double az) {
        double cx = Math.cos(ax), sx = Math.sin(ax), cy = Math.cos(ay), sy = Math.sin(ay), cz = Math.cos(az), sz = Math.sin(az);
        return new double[]{
            cy * cz, -cy * sz, sy,
            sx * sy * cz + cx * sz, -sx * sy * sz + cx * cz, -sx * cy,
            -cx * sy * cz + sx * sz, cx * sy * sz + sx * cz, cx * cy};
    }

    /** A model point (entity pixels) to GUI space: x and y in GUI pixels from the slot's top left, z toward the viewer. */
    private static double[] gui(double[] r, double[] p) {
        double x = p[0] / 16 - 0.5, y = -p[1] / 16 - 0.5, z = -p[2] / 16 - 0.5;
        double s = 0.65;
        double rx = s * (r[0] * x + r[1] * y + r[2] * z) + 2 / 16.0;
        double ry = s * (r[3] * x + r[4] * y + r[5] * z) + 3 / 16.0;
        double rz = s * (r[6] * x + r[7] * y + r[8] * z);
        return new double[]{8 + 16 * rx, 8 - 16 * ry, 16 * rz};
    }

    /** Rasterises one textured face, kept where it is nearer the viewer than what is drawn; faces turned away are skipped. */
    private static void face(int[] out, double[] depth, int n, double[] r, ImageSource.Pixels tex,
            double[] o, double[] pu, double[] pv, int tu, int tv, int tw, int th) {
        if (tw == 0 || th == 0) {
            return;
        }
        double[] a = gui(r, o), b = gui(r, pu), c = gui(r, pv);
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double det = ux * vy - uy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (Math.abs(det) < 1e-9 || len < 1e-9) {
            return;
        }
        double toward = Math.abs(nz) / len;
        int light = (int) Math.round(255 * Math.min(1, 0.45 + 0.6 * toward));
        double k = n / 16.0;
        int lo = Math.max(0, (int) Math.floor(Math.min(Math.min(a[0], b[0]), Math.min(c[0], b[0] + vx)) * k));
        int hi = Math.min(n, (int) Math.ceil(Math.max(Math.max(a[0], b[0]), Math.max(c[0], b[0] + vx)) * k));
        int top = Math.max(0, (int) Math.floor(Math.min(Math.min(a[1], b[1]), Math.min(c[1], b[1] + vy)) * k));
        int bot = Math.min(n, (int) Math.ceil(Math.max(Math.max(a[1], b[1]), Math.max(c[1], b[1] + vy)) * k));
        int size = tex.w();
        double ts = size / 64.0;
        for (int py = top; py < bot; py++) {
            for (int px = lo; px < hi; px++) {
                double sx = (px + 0.5) / k - a[0], sy = (py + 0.5) / k - a[1];
                double s = (sx * vy - sy * vx) / det;
                double t = (ux * sy - uy * sx) / det;
                if (s < 0 || s >= 1 || t < 0 || t >= 1) {
                    continue;
                }
                double z = a[2] + s * uz + t * vz;
                int i = py * n + px;
                if (z <= depth[i]) {
                    continue;
                }
                int tx = (int) ((tu + Math.min(tw - 1e-6, s * tw)) * ts);
                int ty = (int) ((tv + Math.min(th - 1e-6, t * th)) * ts);
                int argb = tex.argb()[Math.min(tex.h() - 1, ty) * size + Math.min(size - 1, tx)];
                if (argb >>> 24 < 26) {
                    continue;
                }
                depth[i] = z;
                int rr = ((argb >> 16) & 255) * light / 255;
                int g = ((argb >> 8) & 255) * light / 255;
                int bb = (argb & 255) * light / 255;
                out[i] = 0xFF000000 | rr << 16 | g << 8 | bb;
            }
        }
    }

    /** The texel at (u, v) of the texture's first square frame, its colour scaled by light/255. */
    private static int texel(ImageSource.Pixels src, double u, double v, int light) {
        int size = Math.min(src.w(), src.h());
        int argb = src.argb()[Math.min(size - 1, (int) (v * size)) * src.w() + Math.min(size - 1, (int) (u * size))];
        int r = ((argb >> 16) & 255) * light / 255;
        int g = ((argb >> 8) & 255) * light / 255;
        int b = (argb & 255) * light / 255;
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }
}
