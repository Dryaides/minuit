package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ImageSource;
import java.util.HashMap;
import java.util.Map;

/**
 * The game's box models for a renderer without the game (the bench, the site): each texel of the entity's own
 * texture becomes one flat quad of a {@link Mesh3D}, drawn by {@link Renderer3D} into whole GUI pixels. The boxes,
 * pivots and texture offsets are the game's ({@code PlayerModel}, {@code PigModel}); a face's texels run as the
 * game maps them, so a skin reads unmirrored from every side.
 *
 * <p>Model space is the game's: y down, the front toward -z, the entity's right toward -x, 24 at the feet. The
 * mesh turns it upright with the front toward +z.
 */
final class EntitySketch {

    private static final String STEVE = "minecraft:textures/entity/player/wide/steve.png";
    private static final String PIG = "minecraft:textures/entity/pig/pig_temperate.png";
    /** Texels below this alpha are cut, as the game's cutout does. */
    private static final int CUTOUT = 26;
    /** The share of the box the model may fill, a margin as the game's inventory leaves. */
    private static final float FILL = 0.85F;

    private static final Map<String, Mesh3D> MESHES = new HashMap<>();
    private static final ThreadLocal<Renderer3D> RENDERER = ThreadLocal.withInitial(Renderer3D::new);
    private static final ThreadLocal<Camera3D> CAMERA = ThreadLocal.withInitial(Camera3D::new);

    private EntitySketch() {
    }

    /**
     * Draws {@code view} as {@link Draw#entity} asks: the model's height at 80% of the box, its middle at the box's
     * middle, as the game's inventory does.
     *
     * @return false for an entity without a model here, or while its texture is not loaded
     */
    static boolean draw(Draw d, EntityView view, int x0, int y0, int x1, int y1, float yaw, float pitch) {
        String type = view.type().startsWith("minecraft:") ? view.type().substring(10) : view.type();
        Mesh3D mesh;
        if (view.isBlock()) {
            mesh = block(d, type);
        } else if (view.isPlayer() || type.equals("player")) {
            mesh = mesh(d, STEVE, true);
        } else if (type.equals("pig")) {
            mesh = mesh(d, PIG, false);
        } else {
            return false;
        }
        int w = x1 - x0;
        int h = y1 - y0;
        if (mesh == null || w < 4 || h < 4) {
            return false;
        }
        // Fits the model at any yaw and at the pitch shown: its height foreshortened, its widest turn across.
        float rx = (mesh.maxX() - mesh.minX()) / 2F;
        float rz = (mesh.maxZ() - mesh.minZ()) / 2F;
        float across = 2F * (float) Math.sqrt(rx * rx + rz * rz);
        double tilt = Math.toRadians(Math.abs(pitch));
        float tall = (float) ((mesh.maxY() - mesh.minY()) * Math.cos(tilt) + across * Math.sin(tilt));
        float scale = Math.min(h * FILL / Math.max(1F, tall), w * FILL / Math.max(1F, across));
        Camera3D camera = CAMERA.get();
        camera.projection(Camera3D.Projection.ISOMETRIC).fov(20F).limits(1e-3F, 1e6F).target(mesh.centerX(), mesh.centerY(), mesh.centerZ());
        // The game looks from its -z at an entity turned by yaw: a camera at 180 + yaw round the model, so yaw 180
        // shows the face. A positive pitch looks from above.
        camera.orbit(180F + yaw, pitch);
        camera.distance(Math.min(w, h) / 2F / (scale * (float) Math.tan(Math.toRadians(10F))));
        Renderer3D r = RENDERER.get();
        r.smooth(1, 4).lineWidth(1);
        r.begin(camera, null, w, h);
        r.draw(mesh);
        r.end();
        r.emit(d, x0, y0, 1);
        return true;
    }

    private static Mesh3D mesh(Draw d, String texture, boolean player) {
        Mesh3D made = MESHES.get(texture);
        if (made != null) {
            return made;
        }
        ImageSource.Pixels skin = d.texels(texture);
        if (skin == null || skin.w() < 64 || skin.h() < 64) {
            return null;
        }
        Mesh3D mesh = new Mesh3D();
        Builder b = new Builder(mesh, skin);
        if (player) {
            player(b, false);
        } else {
            pig(b);
        }
        MESHES.put(texture, mesh);
        return mesh;
    }

    /**
     * A full block as a cube of its textures: {@code <id>_top} (else {@code _end}, else the plain one) on top,
     * {@code <id>_side} (else plain) round it, {@code <id>_bottom} (else the top) below. Null for a shape that is
     * no cube ({@link #NOT_CUBE}) or for missing textures.
     */
    private static Mesh3D block(Draw d, String id) {
        String key = "block:" + id;
        Mesh3D made = MESHES.get(key);
        if (made != null) {
            return made;
        }
        for (String part : NOT_CUBE) {
            if (id.endsWith(part)) {
                return null;
            }
        }
        ImageSource.Pixels plain = blockTexture(d, id);
        ImageSource.Pixels side = or(blockTexture(d, id + "_side"), plain);
        ImageSource.Pixels top = or(or(blockTexture(d, id + "_top"), blockTexture(d, id + "_end")), plain);
        ImageSource.Pixels bottom = id.equals("grass_block") ? blockTexture(d, "dirt")
                : or(blockTexture(d, id + "_bottom"), top);
        if (side == null || top == null || bottom == null) {
            return null;
        }
        if (id.equals("grass_block")) {
            top = tint(top, GRASS);
        } else if (id.equals("oak_leaves") || id.equals("jungle_leaves") || id.equals("acacia_leaves")
                || id.equals("dark_oak_leaves") || id.equals("mangrove_leaves")) {
            top = tint(top, FOLIAGE);
            side = tint(side, FOLIAGE);
            bottom = tint(bottom, FOLIAGE);
        }
        Mesh3D mesh = new Mesh3D();
        new Builder(mesh, side).part(0, 0, 0, 0).cube(top, bottom, side);
        MESHES.put(key, mesh);
        return mesh;
    }

    /** Block shapes that are no full cube, by the end of their id: the caller keeps its stand-in for them. */
    private static final String[] NOT_CUBE = {
            "_slab", "_stairs", "door", "_fence", "_fence_gate", "_wall", "_pane", "torch", "_button",
            "_pressure_plate", "_sign", "_carpet", "_bed", "_sapling", "rail", "chest", "_banner", "_head",
            "_skull", "lantern", "candle", "ladder", "vine", "lever", "_bars", "chain", "flower_pot", "cake",
            "lily_pad", "_coral", "_fan", "kelp", "grass", "fern", "bush", "_mushroom", "dandelion", "poppy",
            "_orchid", "allium", "azure_bluet", "_tulip", "_daisy", "cornflower", "lily_of_the_valley",
            "wither_rose", "sugar_cane", "cactus", "bamboo", "anvil", "hopper", "cauldron", "brewing_stand",
            "enchanting_table", "lectern", "bell", "campfire", "end_rod", "scaffolding", "shulker_box", "piston",
            "snow"};

    /** Plains grass and oak foliage: the colours the game tints those grey textures with. */
    private static final int GRASS = 0x91BD59;
    private static final int FOLIAGE = 0x77AB2F;

    private static ImageSource.Pixels blockTexture(Draw d, String name) {
        ImageSource.Pixels px = d.texels("minecraft:textures/block/" + name + ".png");
        return px == null || px.w() < 16 || px.h() < 16 ? null : px;
    }

    private static ImageSource.Pixels or(ImageSource.Pixels a, ImageSource.Pixels b) {
        return a != null ? a : b;
    }

    private static ImageSource.Pixels tint(ImageSource.Pixels src, int rgb) {
        int[] in = src.argb();
        int[] out = new int[in.length];
        int tr = rgb >> 16 & 0xFF;
        int tg = rgb >> 8 & 0xFF;
        int tb = rgb & 0xFF;
        for (int i = 0; i < out.length; i++) {
            int c = in[i];
            out[i] = c & 0xFF000000 | ((c >> 16 & 0xFF) * tr / 255) << 16 | ((c >> 8 & 0xFF) * tg / 255) << 8
                    | (c & 0xFF) * tb / 255;
        }
        return new ImageSource.Pixels(src.w(), src.h(), out);
    }

    /** {@code PlayerModel}: each part twice, the second layer inflated over the first. */
    private static void player(Builder b, boolean slim) {
        int arm = slim ? 3 : 4;
        float armY = slim ? 2.5F : 2F;
        b.part(0, 0, 0, 0).box(0, 0, -4, -8, -4, 8, 8, 8, 0F).box(32, 0, -4, -8, -4, 8, 8, 8, 0.5F);
        b.part(0, 0, 0, 0).box(16, 16, -4, 0, -2, 8, 12, 4, 0F).box(16, 32, -4, 0, -2, 8, 12, 4, 0.25F);
        b.part(-5, armY, 0, 0).box(40, 16, slim ? -2 : -3, -2, -2, arm, 12, 4, 0F)
                .box(40, 32, slim ? -2 : -3, -2, -2, arm, 12, 4, 0.25F);
        b.part(5, armY, 0, 0).box(32, 48, -1, -2, -2, arm, 12, 4, 0F).box(48, 48, -1, -2, -2, arm, 12, 4, 0.25F);
        b.part(-1.9F, 12, 0, 0).box(0, 16, -2, 0, -2, 4, 12, 4, 0F).box(0, 32, -2, 0, -2, 4, 12, 4, 0.25F);
        b.part(1.9F, 12, 0, 0).box(16, 48, -2, 0, -2, 4, 12, 4, 0F).box(0, 48, -2, 0, -2, 4, 12, 4, 0.25F);
    }

    /** {@code PigModel} (the temperate pig): the body is modelled standing and turned a quarter round x. */
    private static void pig(Builder b) {
        b.part(0, 12, -6, 0).box(0, 0, -4, -4, -8, 8, 8, 8, 0F).box(16, 16, -2, 0, -9, 4, 3, 1, 0F);
        b.part(0, 11, 2, 90).box(28, 8, -5, -10, -7, 10, 16, 8, 0F);
        b.part(-3, 18, 7, 0).box(0, 16, -2, 0, -2, 4, 6, 4, 0F);
        b.part(3, 18, 7, 0).box(0, 16, -2, 0, -2, 4, 6, 4, 0F);
        b.part(-3, 18, -5, 0).box(0, 16, -2, 0, -2, 4, 6, 4, 0F);
        b.part(3, 18, -5, 0).box(0, 16, -2, 0, -2, 4, 6, 4, 0F);
    }

    /** Adds boxes of one part: its pivot and its turn round x, in degrees. */
    private static final class Builder {
        private final Mesh3D mesh;
        private ImageSource.Pixels tex;
        private float px;
        private float py;
        private float pz;
        private float cos = 1F;
        private float sin;
        private final float[] p = new float[12];

        Builder(Mesh3D mesh, ImageSource.Pixels tex) {
            this.mesh = mesh;
            this.tex = tex;
        }

        Builder part(float px, float py, float pz, float xRotDegrees) {
            this.px = px;
            this.py = py;
            this.pz = pz;
            double a = Math.toRadians(xRotDegrees);
            this.cos = (float) Math.cos(a);
            this.sin = (float) Math.sin(a);
            return this;
        }

        /**
         * One cube as {@code addBox(x, y, z, w, h, d)} at texture offset {@code u, v}, grown by {@code grow} on
         * every side. Faces and texel order follow the game's {@code ModelPart.Cube}.
         */
        Builder box(int u, int v, float x, float y, float z, int w, int h, int d, float grow) {
            float x0 = x - grow;
            float y0 = y - grow;
            float z0 = z - grow;
            float x1 = x + w + grow;
            float y1 = y + h + grow;
            float z1 = z + d + grow;
            float sx = x1 - x0;
            float sy = y1 - y0;
            float sz = z1 - z0;
            float cx = (x0 + x1) / 2F;
            float cy = (y0 + y1) / 2F;
            float cz = (z0 + z1) / 2F;
            // top, bottom, right (-x), front (-z), left (+x), back (+z)
            face(u + d, v, w, d, x0, y0, z1, sx, 0, 0, 0, 0, -sz, cx, cy, cz);
            face(u + d + w, v, w, d, x0, y1, z1, sx, 0, 0, 0, 0, -sz, cx, cy, cz);
            face(u, v + d, d, h, x0, y0, z1, 0, 0, -sz, 0, sy, 0, cx, cy, cz);
            face(u + d, v + d, w, h, x0, y0, z0, sx, 0, 0, 0, sy, 0, cx, cy, cz);
            face(u + d + w, v + d, d, h, x1, y0, z0, 0, 0, sz, 0, sy, 0, cx, cy, cz);
            face(u + d + w + d, v + d, w, h, x1, y0, z1, -sx, 0, 0, 0, sy, 0, cx, cy, cz);
            return this;
        }

        /** A full block, 16 units from the feet up; each face is the top-left 16 by 16 of its texture. */
        Builder cube(ImageSource.Pixels top, ImageSource.Pixels bottom, ImageSource.Pixels side) {
            float c = 16F;
            tex = top;
            face(0, 0, 16, 16, -8, 8, 8, c, 0, 0, 0, 0, -c, 0, 16, 0);
            tex = bottom;
            face(0, 0, 16, 16, -8, 24, 8, c, 0, 0, 0, 0, -c, 0, 16, 0);
            tex = side;
            face(0, 0, 16, 16, -8, 8, 8, 0, 0, -c, 0, c, 0, 0, 16, 0);
            face(0, 0, 16, 16, -8, 8, -8, c, 0, 0, 0, c, 0, 0, 16, 0);
            face(0, 0, 16, 16, 8, 8, -8, 0, 0, c, 0, c, 0, 0, 16, 0);
            face(0, 0, 16, 16, 8, 8, 8, -c, 0, 0, 0, c, 0, 0, 16, 0);
            return this;
        }

        /** A face of {@code tw} by {@code th} texels from {@code tu, tv}: corner o, full edges U (along u) and V (along v). */
        private void face(int tu, int tv, int tw, int th, float ox, float oy, float oz,
                          float ux, float uy, float uz, float vx, float vy, float vz, float cx, float cy, float cz) {
            // outward: from the box's centre to the face's
            float nx = ox + (ux + vx) / 2F - cx;
            float ny = oy + (uy + vy) / 2F - cy;
            float nz = oz + (uz + vz) / 2F - cz;
            for (int j = 0; j < th; j++) {
                for (int i = 0; i < tw; i++) {
                    int argb = texel(tu + i, tv + j);
                    if ((argb >>> 24) < CUTOUT) {
                        continue;
                    }
                    float a0 = i / (float) tw;
                    float a1 = (i + 1) / (float) tw;
                    float b0 = j / (float) th;
                    float b1 = (j + 1) / (float) th;
                    corner(0, ox + ux * a0 + vx * b0, oy + uy * a0 + vy * b0, oz + uz * a0 + vz * b0);
                    corner(3, ox + ux * a1 + vx * b0, oy + uy * a1 + vy * b0, oz + uz * a1 + vz * b0);
                    corner(6, ox + ux * a1 + vx * b1, oy + uy * a1 + vy * b1, oz + uz * a1 + vz * b1);
                    corner(9, ox + ux * a0 + vx * b1, oy + uy * a0 + vy * b1, oz + uz * a0 + vz * b1);
                    quad(argb | 0xFF000000, nx, ny, nz);
                }
            }
        }

        private int texel(int u, int v) {
            return u < 0 || v < 0 || u >= tex.w() || v >= tex.h() ? 0 : tex.argb()[v * tex.w() + u];
        }

        /** Model point to the mesh: turned round the part's pivot, then upright with the front toward +z. */
        private void corner(int at, float x, float y, float z) {
            float ry = y * cos - z * sin;
            float rz = y * sin + z * cos;
            p[at] = px + x;
            p[at + 1] = 24F - (py + ry);
            p[at + 2] = -(pz + rz);
        }

        /** Emits the quad counter-clockwise as seen from outside, which is the side {@link Mesh3D} shows. */
        private void quad(int color, float nx, float ny, float nz) {
            // the outward direction in mesh space: x kept, y and z flipped (the pivot turn is a turn round x)
            float ry = ny * cos - nz * sin;
            float rz = ny * sin + nz * cos;
            float ox = nx;
            float oy = -ry;
            float oz = -rz;
            float ax = p[3] - p[0];
            float ay = p[4] - p[1];
            float az = p[5] - p[2];
            float bx = p[6] - p[0];
            float by = p[7] - p[1];
            float bz = p[8] - p[2];
            float dot = (ay * bz - az * by) * ox + (az * bx - ax * bz) * oy + (ax * by - ay * bx) * oz;
            if (dot >= 0F) {
                mesh.quad(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11], color);
            } else {
                mesh.quad(p[0], p[1], p[2], p[9], p[10], p[11], p[6], p[7], p[8], p[3], p[4], p[5], color);
            }
        }
    }
}
