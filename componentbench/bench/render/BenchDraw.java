package bench.render;

import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.Shapes;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** The library's Draw over a BufferedImage: one GUI unit is guiScale pixels, as in game. */
final class BenchDraw extends Draw {
    private record Tip(List<String> lines, int x, int y) { }

    private final Graphics2D g;
    private final int guiScale;
    private final int guiW;
    private final int guiH;
    private final BenchFont font;
    private final Textures textures;
    private final Deque<AffineTransform> poses = new ArrayDeque<>();
    private final Deque<Rectangle> clips = new ArrayDeque<>();
    private final List<Tip> tips = new ArrayList<>();

    BenchDraw(BufferedImage image, int guiScale, BenchFont font, Textures textures) {
        this.g = image.createGraphics();
        this.guiScale = guiScale;
        this.guiW = image.getWidth() / guiScale;
        this.guiH = image.getHeight() / guiScale;
        this.font = font;
        this.textures = textures;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(guiScale, guiScale);
    }

    /** Draws the queued tooltips on top and closes the picture. */
    void finish() {
        List<Tip> queued = new ArrayList<>(tips);
        tips.clear();
        for (Tip t : queued) {
            drawTip(t);
        }
        g.dispose();
    }

    @Override protected void rect(int x1, int y1, int x2, int y2, int color) {
        g.setColor(new Color(color, true));
        g.fillRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
    }

    @Override protected void string(String text, int x, int y, int color) {
        font.draw(g, text, x, y, new Color(color, true));
    }

    @Override public int width(String text) { return font.width(text); }
    @Override protected String cut(String text, int width) { return font.cut(text, width); }
    @Override public int guiScale() { return guiScale; }

    @Override public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) {
        BufferedImage img = textures.get(texture);
        if (img == null) {
            missing(x, y, w, h);
            return;
        }
        float sx = img.getWidth() / (float) Math.max(1, texW);
        float sy = img.getHeight() / (float) Math.max(1, texH);
        paint(img, x, y, w, h, Math.round(u * sx), Math.round(v * sy), Math.round((u + w) * sx), Math.round((v + h) * sy), alpha);
    }

    @Override public void image(String texture, int x, int y, int w, int h, int texW, int texH, float alpha) {
        BufferedImage img = textures.get(texture);
        if (img == null) {
            missing(x, y, w, h);
            return;
        }
        paint(img, x, y, w, h, 0, 0, img.getWidth(), img.getHeight(), alpha);
    }

    /** As the game does: the region (u, v, rw, rh) of a texW x texH texture stretched over w x h. */
    @Override public void blitRegion(String texture, int x, int y, int w, int h, int u, int v, int rw, int rh, int texW, int texH, float alpha) {
        BufferedImage img = textures.get(texture);
        if (img == null) {
            missing(x, y, w, h);
            return;
        }
        float sx = img.getWidth() / (float) Math.max(1, texW);
        float sy = img.getHeight() / (float) Math.max(1, texH);
        paint(img, x, y, w, h, Math.round(u * sx), Math.round(v * sy), Math.round((u + rw) * sx), Math.round((v + rh) * sy), alpha);
    }

    private void paint(BufferedImage img, int x, int y, int w, int h, int u1, int v1, int u2, int v2, float alpha) {
        Composite was = g.getComposite();
        if (alpha < 1F) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0F, alpha)));
        }
        g.drawImage(img, x, y, x + w, y + h, u1, v1, u2, v2, null);
        g.setComposite(was);
    }

    /**
     * The game's own flat item picture from the cached game jar; a block item (no item picture) is drawn as the
     * game's GUI block icon, three shaded faces from its block textures. Anything not found keeps the library's stand-in.
     */
    @Override public void item(dev.dryaides.minuit.ItemView item, int x, int y) {
        String id = item.id();
        BufferedImage img = itemPicture(id);
        if (img != null) {
            int side = img.getWidth();
            // Animated pictures are frames stacked downwards: the first one is enough.
            paint(img, x, y, 16, 16, 0, 0, side, Math.min(side, img.getHeight()), 1F);
            return;
        }
        BufferedImage top = blockFace(id, "_top", "_end", "");
        BufferedImage side = blockFace(id, "_side", "_front", "");
        if (top == null && side == null) {
            super.item(item, x, y);
            return;
        }
        blockIcon(top != null ? top : side, side != null ? side : top, x, y);
    }

    @Override public void item(String id, int x, int y) {
        item(dev.dryaides.minuit.ItemView.of(id), x, y);
    }

    private BufferedImage itemPicture(String id) {
        int colon = id.indexOf(':');
        String ns = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = id.substring(colon + 1);
        for (String suffix : new String[] {"", "_top", "_side", "_front", "_0", "_00"}) {
            BufferedImage img = textures.peek(ns + ":textures/item/" + path + suffix + ".png");
            if (img != null) {
                return img;
            }
        }
        return null;
    }

    /** The first block texture found for the id with one of the suffixes, or null. */
    private BufferedImage blockFace(String id, String... suffixes) {
        int colon = id.indexOf(':');
        String ns = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = id.substring(colon + 1);
        for (String suffix : suffixes) {
            BufferedImage img = textures.peek(ns + ":textures/block/" + path + suffix + ".png");
            if (img != null) {
                return img;
            }
        }
        return null;
    }

    // The game's GUI block model (rotation 30/225, scale 0.625) projected on the 16 px icon: the cube's corners.
    private static final double[] TOP = {8, 0.13};
    private static final double[] RIGHT_TOP = {15.07, 3.67};
    private static final double[] LEFT_TOP = {0.93, 3.67};
    private static final double[] FRONT_TOP = {8, 7.2};
    private static final double[] LEFT_BOTTOM = {0.93, 12.33};
    private static final double[] FRONT_BOTTOM = {8, 15.87};
    /** The game's GUI light on a block: the top full, the left face 0.8, the right 0.6. */
    private static final float LEFT_SHADE = 0.8F;
    private static final float RIGHT_SHADE = 0.6F;

    private void blockIcon(BufferedImage top, BufferedImage side, int x, int y) {
        face(top, 1F, TOP, RIGHT_TOP, LEFT_TOP, x, y);
        face(side, LEFT_SHADE, LEFT_TOP, FRONT_TOP, LEFT_BOTTOM, x, y);
        face(side, RIGHT_SHADE, FRONT_TOP, RIGHT_TOP, FRONT_BOTTOM, x, y);
    }

    /** Maps the texture's first square onto the parallelogram {@code origin, origin + u axis, origin + v axis}. */
    private void face(BufferedImage tex, float shade, double[] origin, double[] uEnd, double[] vEnd, int x, int y) {
        int side = Math.min(tex.getWidth(), tex.getHeight());
        BufferedImage lit = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        for (int j = 0; j < side; j++) {
            for (int i = 0; i < side; i++) {
                int argb = tex.getRGB(i, j);
                int r = Math.round(((argb >> 16) & 0xFF) * shade);
                int gr = Math.round(((argb >> 8) & 0xFF) * shade);
                int b = Math.round((argb & 0xFF) * shade);
                lit.setRGB(i, j, (argb & 0xFF000000) | (r << 16) | (gr << 8) | b);
            }
        }
        double k = 1.0 / side;
        AffineTransform m = new AffineTransform((uEnd[0] - origin[0]) * k, (uEnd[1] - origin[1]) * k,
                (vEnd[0] - origin[0]) * k, (vEnd[1] - origin[1]) * k, x + origin[0], y + origin[1]);
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(lit, m, null);
        if (hint != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
        }
    }

    /** A texture the bench cannot find: the game's missing-texture colours, so it is seen. */
    private void missing(int x, int y, int w, int h) {
        g.setColor(new Color(0xFFF800F8, true));
        g.fillRect(x, y, w, h);
        g.setColor(Color.BLACK);
        g.fillRect(x, y, w / 2, h / 2);
        g.fillRect(x + w / 2, y + h / 2, w - w / 2, h - h / 2);
    }

    @Override public void push(float x, float y, float scale) {
        poses.push(g.getTransform());
        g.translate(x, y);
        g.scale(scale, scale);
    }

    @Override public void pop() {
        g.setTransform(poses.pop());
    }

    /** In device space; nested scissors intersect, as the game's scissor stack does. */
    @Override public void scissor(int x1, int y1, int x2, int y2) {
        Rectangle now = g.getTransform().createTransformedShape(
                new Rectangle(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1))).getBounds();
        Rectangle top = clips.peek();
        Rectangle next = top == null ? now : now.intersection(top);
        clips.push(next);
        setDeviceClip(next);
    }

    @Override public void endScissor() {
        clips.pop();
        setDeviceClip(clips.peek());
    }

    private void setDeviceClip(Shape clip) {
        AffineTransform t = g.getTransform();
        g.setTransform(new AffineTransform());
        g.setClip(clip);
        g.setTransform(t);
    }

    @Override public void tooltip(List<String> lines, int x, int y) {
        // As the game does on 26.3: an empty list draws nothing. Without this, a zero-line tip showed as a dark blob.
        if (lines.isEmpty()) {
            return;
        }
        tips.add(new Tip(new ArrayList<>(lines), x, y));
    }

    /** The game's tooltip look, drawn last: 12 px right of the cursor, 12 px up, kept on screen. */
    private void drawTip(Tip t) {
        List<String> rows = new ArrayList<>();
        for (String line : t.lines) {
            rows.addAll(wrapAll(line, TOOLTIP_WIDTH));
        }
        int w = 0;
        for (String row : rows) {
            w = Math.max(w, width(row));
        }
        int h = rows.size() == 1 ? 8 : 10 + (rows.size() - 1) * 10 - 2;
        int x = t.x + 12;
        int y = t.y - 12;
        if (x + w + 6 > guiW) {
            x = Math.max(4, t.x - 16 - w);
        }
        if (y + h + 6 > guiH) {
            y = guiH - h - 6;
        }
        if (y < 6) {
            y = 6;
        }
        Shapes.gameTooltip(this, x, y, w, h);
        int ty = y;
        for (int i = 0; i < rows.size(); i++) {
            text(rows.get(i), x, ty, i == 0 ? 0xFFFFFFFF : 0xFFAAAAAA);
            ty += i == 0 ? 12 : 10;
        }
    }
}
