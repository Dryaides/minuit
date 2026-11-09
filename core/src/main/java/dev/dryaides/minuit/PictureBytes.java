package dev.dryaides.minuit;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * Shrinks a PNG before it becomes a texture, with a proper average of the pixels. The game samples a texture
 * to the nearest pixel without mipmaps, so a 1920 x 1080 screenshot shown 384 pixels wide keeps one pixel
 * in five and sparkles; shrunk here first, it is as sharp as the screen allows. Pure Java, any thread: call
 * it where the file is read, never on the render thread.
 *
 * <pre>{@code
 * AsyncImage shot = AsyncImage.pending();
 * CompletableFuture.runAsync(() -> {
 *     try {
 *         byte[] png = Files.readAllBytes(mc.gameDirectory.toPath().resolve("screenshots").resolve(name));
 *         AsyncImages.fill(shot, "mymod:shots/" + name, PictureBytes.shrink(png, 640, 360));
 *     } catch (IOException e) { shot.failed(); }
 * });
 * }</pre>
 *
 * <p>Pick the size in screen pixels: the box's GUI size times {@code Draw.guiScale()}, or the window's size
 * for a full-screen viewer. Uses {@code javax.imageio}, part of the Java runtime the game ships.
 */
@Incubating
public final class PictureBytes {
    private PictureBytes() {
    }

    /**
     * The PNG made at most {@code maxW} x {@code maxH}, aspect kept, never enlarged. Returns the same bytes
     * when it already fits, and null for bytes that are not an image.
     */
    public static byte[] shrink(byte[] png, int maxW, int maxH) {
        BufferedImage src = read(png);
        if (src == null) {
            return null;
        }
        int sw = src.getWidth();
        int sh = src.getHeight();
        double s = Math.min(1D, Math.min(Math.max(1, maxW) / (double) sw, Math.max(1, maxH) / (double) sh));
        if (s >= 1D) {
            return png;
        }
        int dw = Math.max(1, (int) Math.round(sw * s));
        int dh = Math.max(1, (int) Math.round(sh * s));
        return write(resize(src, dw, dh));
    }

    /** {width, height} of a PNG, or null. Reads the whole picture. */
    public static int[] size(byte[] png) {
        BufferedImage img = read(png);
        return img == null ? null : new int[] {img.getWidth(), img.getHeight()};
    }

    private static BufferedImage read(byte[] png) {
        if (png == null || png.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] write(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * An area average, one axis then the other: each new pixel is the mean of the source pixels it covers,
     * partial ones weighted. Colours are weighted by their opacity, so a see-through edge does not darken.
     */
    static BufferedImage resize(BufferedImage src, int dw, int dh) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        int[] in = src.getRGB(0, 0, sw, sh, null, 0, sw);
        float[] wide = new float[dw * sh * 4];
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < dw; x++) {
                accumulate(in, y * sw, 1, sw, dw, x, wide, (y * dw + x) * 4);
            }
        }
        int[] out = new int[dw * dh];
        float[] col = new float[sh * 4];
        float[] acc = new float[4];
        for (int x = 0; x < dw; x++) {
            for (int y = 0; y < sh; y++) {
                System.arraycopy(wide, (y * dw + x) * 4, col, y * 4, 4);
            }
            for (int y = 0; y < dh; y++) {
                average(col, sh, dh, y, acc);
                out[y * dw + x] = pack(acc);
            }
        }
        BufferedImage img = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, dw, dh, out, 0, dw);
        return img;
    }

    /** Premultiplied sums over the span of destination pixel {@code d} in a row of packed pixels. */
    private static void accumulate(int[] in, int start, int step, int sn, int dn, int d, float[] to, int at) {
        double a = d * (double) sn / dn;
        double b = (d + 1) * (double) sn / dn;
        float sa = 0F;
        float sr = 0F;
        float sg = 0F;
        float sb = 0F;
        float total = 0F;
        for (int i = (int) Math.floor(a); i < Math.min(sn, (int) Math.ceil(b)); i++) {
            float wgt = (float) (Math.min(b, i + 1) - Math.max(a, i));
            int c = in[start + i * step];
            float alpha = (c >>> 24) / 255F;
            sa += alpha * wgt;
            sr += ((c >> 16) & 255) * alpha * wgt;
            sg += ((c >> 8) & 255) * alpha * wgt;
            sb += (c & 255) * alpha * wgt;
            total += wgt;
        }
        to[at] = sa / total;
        to[at + 1] = sr / total;
        to[at + 2] = sg / total;
        to[at + 3] = sb / total;
    }

    private static void average(float[] col, int sn, int dn, int d, float[] acc) {
        double a = d * (double) sn / dn;
        double b = (d + 1) * (double) sn / dn;
        acc[0] = acc[1] = acc[2] = acc[3] = 0F;
        float total = 0F;
        for (int i = (int) Math.floor(a); i < Math.min(sn, (int) Math.ceil(b)); i++) {
            float wgt = (float) (Math.min(b, i + 1) - Math.max(a, i));
            for (int c = 0; c < 4; c++) {
                acc[c] += col[i * 4 + c] * wgt;
            }
            total += wgt;
        }
        for (int c = 0; c < 4; c++) {
            acc[c] /= total;
        }
    }

    private static int pack(float[] acc) {
        float alpha = acc[0];
        int a = Math.round(alpha * 255F);
        if (a == 0) {
            return 0;
        }
        int r = Math.min(255, Math.round(acc[1] / alpha));
        int g = Math.min(255, Math.round(acc[2] / alpha));
        int b = Math.min(255, Math.round(acc[3] / alpha));
        return a << 24 | r << 16 | g << 8 | b;
    }
}
