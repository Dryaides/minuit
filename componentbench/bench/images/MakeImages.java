package bench.images;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Makes the bench's test pictures, all drawn here, none copied: a test chart that shows sampling and aspect
 * problems, fake screenshots of a blocky world, a portrait picture and two small pixel-art pictures.
 *
 * <pre>
 * java bench/images/MakeImages.java bench/images
 * </pre>
 */
public final class MakeImages {
    private MakeImages() {
    }

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : ".");
        BufferedImage pattern = pattern(1920, 1080);
        BufferedImage day = world(1920, 1080, Sky.DAY, 1);
        write(pattern, dir, "pattern.png");
        write(shrink(pattern, 384, 216), dir, "pattern-384.png");
        write(day, dir, "shot-day.png");
        write(shrink(day, 384, 216), dir, "shot-day-384.png");
        write(world(2560, 1080, Sky.SUNSET, 2), dir, "shot-sunset.png");
        write(world(1920, 1080, Sky.NIGHT, 3), dir, "shot-night.png");
        write(world(1920, 1080, Sky.SNOW, 4), dir, "shot-snow.png");
        write(world(1920, 1080, Sky.DESERT, 5), dir, "shot-desert.png");
        write(portrait(720, 1280), dir, "portrait.png");
        write(gem(), dir, "gem.png");
        write(face(), dir, "face.png");
    }

    /** Box-filter shrink, the way a picture is cut down before it becomes a texture. */
    private static BufferedImage shrink(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int sx = src.getWidth() / w;
        int sy = src.getHeight() / h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                long a = 0, r = 0, g = 0, b = 0;
                for (int j = 0; j < sy; j++) {
                    for (int i = 0; i < sx; i++) {
                        int c = src.getRGB(x * sx + i, y * sy + j);
                        a += c >>> 24;
                        r += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        b += c & 255;
                    }
                }
                long n = (long) sx * sy;
                out.setRGB(x, y, (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
            }
        }
        return out;
    }

    private static void write(BufferedImage img, File dir, String name) throws IOException {
        ImageIO.write(img, "png", new File(dir, name));
        System.out.println(name + " " + img.getWidth() + "x" + img.getHeight());
    }

    // ---- the test chart ----

    /**
     * Corners in four colours, a 1 px frame, a circle that must stay round, line gratings and a zone plate
     * that turn into moiré when sampled badly, thin grid lines that vanish unevenly, and ramps.
     */
    static BufferedImage pattern(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x808080));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0x606060));
        for (int x = 0; x < w; x += 60) {
            g.fillRect(x, 0, 1, h);
        }
        for (int y = 0; y < h; y += 60) {
            g.fillRect(0, y, w, 1);
        }
        // Corners: which way up, and what a crop cut off.
        int c = 160;
        corner(g, 0, 0, c, 0xE53935, "TL");
        corner(g, w - c, 0, c, 0x43A047, "TR");
        corner(g, 0, h - c, c, 0x1E88E5, "BL");
        corner(g, w - c, h - c, c, 0xFDD835, "BR");
        // Line gratings: 1 px black and white, vertical then horizontal, then a 2 px checkerboard.
        for (int x = 0; x < 360; x++) {
            g.setColor((x & 1) == 0 ? Color.BLACK : Color.WHITE);
            g.fillRect(200 + x, 380, 1, 320);
        }
        for (int y = 0; y < 320; y++) {
            g.setColor((y & 1) == 0 ? Color.BLACK : Color.WHITE);
            g.fillRect(w - 560, 380 + y, 360, 1);
        }
        for (int y = 0; y < 160; y += 2) {
            for (int x = 0; x < 360; x += 2) {
                g.setColor(((x + y) / 2 & 1) == 0 ? Color.BLACK : Color.WHITE);
                g.fillRect(780 + x, 20 + y, 2, 2);
            }
        }
        // Zone plate: rings closer and closer; any sampling fault shows as false rings.
        int zr = 170;
        for (int y = -zr; y < zr; y++) {
            for (int x = -zr; x < zr; x++) {
                double r2 = x * x + y * y;
                if (r2 > zr * zr) {
                    continue;
                }
                int v = (int) Math.round(127.5 + 127.5 * Math.cos(Math.PI * r2 / (2.0 * zr)));
                img.setRGB(w / 2 + x, h - 20 - zr + y, v << 16 | v << 8 | v);
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // A circle and a cross through the middle: aspect and focal point.
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(10));
        int d = 560;
        g.drawOval((w - d) / 2, (h - d) / 2 - 90, d, d);
        g.fillRect(w / 2 - 1, 0, 3, h);
        g.fillRect(0, h / 2 - 90, w, 3);
        g.setColor(new Color(0xE53935));
        g.fillOval(w / 2 - 24, h / 2 - 90 - 24, 48, 48);
        // Ramps: grey along the top, hue along the bottom.
        for (int x = 0; x < w - 2 * c; x++) {
            int v = x * 255 / (w - 2 * c - 1);
            g.setColor(new Color(v, v, v));
            g.fillRect(c + x, 0, 1, 14);
            g.setColor(Color.getHSBColor(x / (float) (w - 2 * c), 0.8F, 0.95F));
            g.fillRect(c + x, h - 14, 1, 14);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
        g.setColor(Color.WHITE);
        g.drawString("1920 x 1080  16:9", w / 2 - 210, 240);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString("small text, 14 px: the quick brown fox jumps over the lazy dog", w / 2 - 230, 270);
        // A 1 px frame in cyan: is the edge of the picture kept?
        g.setColor(new Color(0x00E5FF));
        g.drawRect(0, 0, w - 1, h - 1);
        g.dispose();
        return img;
    }

    private static void corner(Graphics2D g, int x, int y, int size, int rgb, String label) {
        g.setColor(new Color(rgb));
        g.fillRect(x, y, size, size);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 72));
        g.drawString(label, x + 30, y + size / 2 + 26);
    }

    // ---- fake screenshots ----

    enum Sky { DAY, SUNSET, NIGHT, SNOW, DESERT }

    /** A side view of a blocky world, with a hotbar and a crosshair, as a screenshot of a block game would be. */
    static BufferedImage world(int w, int h, Sky sky, long seed) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Random r = new Random(seed);
        int top;
        int bottom;
        switch (sky) {
            case SUNSET -> { top = 0x2B2D6E; bottom = 0xF08A4B; }
            case NIGHT -> { top = 0x05070F; bottom = 0x1A2340; }
            case SNOW -> { top = 0x9DB4C8; bottom = 0xDCE6EE; }
            case DESERT -> { top = 0x6FA8DC; bottom = 0xE8D9B0; }
            default -> { top = 0x4E86D8; bottom = 0xA9CCF2; }
        }
        for (int y = 0; y < h; y++) {
            g.setColor(new Color(mix(top, bottom, y / (float) h)));
            g.fillRect(0, y, w, 1);
        }
        if (sky == Sky.NIGHT) {
            for (int i = 0; i < 260; i++) {
                int s = r.nextInt(3) + 1;
                g.setColor(new Color(0xFFFFFF));
                g.fillRect(r.nextInt(w), r.nextInt(h / 2), s, s);
            }
            g.setColor(new Color(0xE8E8F0));
            g.fillRect(w * 3 / 4, h / 8, 90, 90);
        } else {
            g.setColor(new Color(sky == Sky.SUNSET ? 0xFFD27A : 0xFFF6C0));
            int sun = sky == Sky.SUNSET ? 150 : 110;
            g.fillRect(sky == Sky.SUNSET ? w / 3 : w / 5, sky == Sky.SUNSET ? h / 2 - 60 : h / 9, sun, sun);
            g.setColor(new Color(0xF4F6F8));
            for (int i = 0; i < 9; i++) {
                int cx = r.nextInt(w);
                int cy = 60 + r.nextInt(h / 4);
                for (int k = 0; k < 4; k++) {
                    g.fillRect(cx + k * 40 - r.nextInt(20), cy - (k % 2) * 20, 120 + r.nextInt(80), 40);
                }
            }
        }
        int block = 40;
        int cols = w / block + 1;
        int sea = h * 62 / 100;
        int[] ground = new int[cols];
        double a = r.nextDouble() * 10;
        for (int i = 0; i < cols; i++) {
            double n = Math.sin(i * 0.11 + a) * 3.2 + Math.sin(i * 0.031 + a * 2) * 4 + Math.sin(i * 0.47 + a) * 0.8;
            ground[i] = sea / block - 1 + (int) Math.round(n);
        }
        int grass = switch (sky) {
            case SNOW -> 0xF2F5F7;
            case DESERT -> 0xE0CC8E;
            case NIGHT -> 0x2E4A2A;
            default -> 0x5DA03A;
        };
        int dirt = sky == Sky.DESERT ? 0xD2B877 : 0x7A5634;
        int stone = sky == Sky.NIGHT ? 0x3C3C42 : 0x7D7D82;
        int water = sky == Sky.NIGHT ? 0x14244A : 0x2F62C8;
        for (int i = 0; i < cols; i++) {
            int gy = ground[i];
            for (int by = gy; by * block < h; by++) {
                int color = by == gy ? grass : by < gy + 3 ? dirt : stone;
                if (sky == Sky.SUNSET) {
                    color = mix(color, 0xF08A4B, 0.18F);
                }
                texturedBlock(img, i * block, by * block, block, color, r);
            }
            for (int by = sea / block; by < gy; by++) {
                fillBlock(img, i * block, by * block, block, water, 0.75F);
            }
            if (gy * block < sea && i % 7 == 3 && sky != Sky.DESERT) {
                int trunk = sky == Sky.NIGHT ? 0x3A2A1A : 0x6B4A2B;
                int leaves = sky == Sky.SNOW ? 0xD9E6DA : sky == Sky.NIGHT ? 0x1E3A1E : 0x3E8A2E;
                for (int k = 1; k <= 4; k++) {
                    texturedBlock(img, i * block, (gy - k) * block, block, trunk, r);
                }
                for (int lx = -2; lx <= 2; lx++) {
                    for (int ly = 4; ly <= 6; ly++) {
                        if (Math.abs(lx) == 2 && ly == 6) {
                            continue;
                        }
                        int at = i + lx;
                        if (at >= 0 && at < cols) {
                            texturedBlock(img, at * block, (gy - ly) * block, block, leaves, r);
                        }
                    }
                }
            } else if (sky == Sky.DESERT && i % 9 == 4 && gy * block < sea) {
                for (int k = 1; k <= 3; k++) {
                    texturedBlock(img, i * block, (gy - k) * block, block, 0x4E8B3A, r);
                }
            }
        }
        hud(g, img, w, h, r);
        g.dispose();
        return img;
    }

    /** One block, made of 8 by 8 texels with a little noise: the fine detail a downscale loses. */
    private static void texturedBlock(BufferedImage img, int x, int y, int size, int color, Random r) {
        int texel = size / 8;
        for (int ty = 0; ty < 8; ty++) {
            for (int tx = 0; tx < 8; tx++) {
                float shade = 0.82F + r.nextFloat() * 0.3F;
                if (tx == 0 || ty == 0) {
                    shade *= 1.08F;
                }
                if (tx == 7 || ty == 7) {
                    shade *= 0.8F;
                }
                int c = scale(color, shade);
                for (int py = 0; py < texel; py++) {
                    for (int px = 0; px < texel; px++) {
                        set(img, x + tx * texel + px, y + ty * texel + py, c);
                    }
                }
            }
        }
    }

    private static void fillBlock(BufferedImage img, int x, int y, int size, int color, float opacity) {
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int ix = x + px;
                int iy = y + py;
                if (ix < img.getWidth() && iy < img.getHeight()) {
                    img.setRGB(ix, iy, mix(img.getRGB(ix, iy) & 0xFFFFFF, color, opacity));
                }
            }
        }
    }

    /** A hotbar, a crosshair and two lines of small text, as a game's screen has. */
    private static void hud(Graphics2D g, BufferedImage img, int w, int h, Random r) {
        int slot = 44;
        int barW = slot * 9 + 4;
        int bx = (w - barW) / 2;
        int by = h - slot - 10;
        g.setColor(new Color(0x2A2A2A));
        g.fillRect(bx, by, barW, slot + 4);
        int[] items = {0xB0B0B0, 0x7A5634, 0x5DA03A, 0xE53935, 0x1E88E5, 0xFDD835, 0x6B4A2B, 0xFFFFFF, 0x43A047};
        for (int i = 0; i < 9; i++) {
            g.setColor(new Color(0x8B8B8B));
            g.fillRect(bx + 2 + i * slot + 2, by + 4, slot - 4, slot - 4);
            g.setColor(new Color(items[i]));
            g.fillRect(bx + 2 + i * slot + 12, by + 14, slot - 24, slot - 24);
        }
        g.setColor(Color.WHITE);
        g.drawRect(bx + 2 + 4 * slot, by + 2, slot - 1, slot);
        g.drawRect(bx + 3 + 4 * slot, by + 3, slot - 3, slot - 2);
        g.fillRect(w / 2 - 1, h / 2 - 10, 2, 20);
        g.fillRect(w / 2 - 10, h / 2 - 1, 20, 2);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
        g.setColor(new Color(0xE0E0E0));
        g.drawString("XYZ: " + (r.nextInt(2000) - 1000) + " / 64 / " + (r.nextInt(2000) - 1000), 8, 24);
        g.drawString("Facing: north (Towards negative Z)", 8, 46);
    }

    // ---- portrait and pixel art ----

    /** A tall picture: a tower of blocks with the same corner colours as the chart. */
    static BufferedImage portrait(int w, int h) {
        BufferedImage img = world(w, h, Sky.DAY, 7);
        Graphics2D g = img.createGraphics();
        Random r = new Random(8);
        for (int by = 6; by < 26; by++) {
            for (int bx = 7; bx < 11; bx++) {
                texturedBlock(img, bx * 40, by * 40, 40, (bx + by) % 2 == 0 ? 0x9A9AA0 : 0x86868C, r);
            }
        }
        corner(g, 0, 0, 80, 0xE53935, "");
        corner(g, w - 80, 0, 80, 0x43A047, "");
        corner(g, 0, h - 80, 80, 0x1E88E5, "");
        corner(g, w - 80, h - 80, 80, 0xFDD835, "");
        g.dispose();
        return img;
    }

    /** 16 by 16: a gem, every texel on purpose, to judge integer scaling. */
    static BufferedImage gem() {
        String[] rows = {
            "................",
            ".....AAAAAA.....",
            "....ABBBBBBA....",
            "...ABCCBBCCBA...",
            "..ABCCBBBBCCBA..",
            ".ABBBBBBBBBBBBA.",
            "ADDDDDDDDDDDDDDA",
            ".ADEEDDDDDDEEDA.",
            "..ADEEDDDDEEDA..",
            "...ADEDDDDEDA...",
            "....ADEDDEDA....",
            ".....ADEEDA.....",
            "......ADDA......",
            ".......AA.......",
            "................",
            "................",
        };
        return pixels(rows, new int[] {0xFF0B3B4F, 0xFF7FE6FF, 0xFFFFFFFF, 0xFF2CB5D8, 0xFF1A7FA0});
    }

    /** 16 by 16: a face, for an avatar picture. */
    static BufferedImage face() {
        String[] rows = {
            "AAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAA",
            "AABBBBBBBBBBBBAA",
            "ABBBBBBBBBBBBBBA",
            "BBBBBBBBBBBBBBBB",
            "BBCCBBBBBBBBCCBB",
            "BBCDBBBBBBBBDCBB",
            "BBBBBBBBBBBBBBBB",
            "BBBBBBBEEBBBBBBB",
            "BBBBBBBEEBBBBBBB",
            "BBBBBBBBBBBBBBBB",
            "BBBBEBBBBBBEBBBB",
            "BBBBBEEEEEEBBBBB",
            "BBBBBBBBBBBBBBBB",
            "BBBBBBBBBBBBBBBB",
            "BBBBBBBBBBBBBBBB",
        };
        return pixels(rows, new int[] {0xFF5A3A22, 0xFFE8B48A, 0xFFFFFFFF, 0xFF2E5FA8, 0xFFB57A55});
    }

    private static BufferedImage pixels(String[] rows, int[] colors) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                char ch = rows[y].charAt(x);
                img.setRGB(x, y, ch == '.' ? 0 : colors[ch - 'A']);
            }
        }
        return img;
    }

    // ---- colour ----

    private static void set(BufferedImage img, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, c);
        }
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    static int scale(int c, float s) {
        int r = Math.min(255, Math.round(((c >> 16) & 255) * s));
        int g = Math.min(255, Math.round(((c >> 8) & 255) * s));
        int b = Math.min(255, Math.round((c & 255) * s));
        return r << 16 | g << 8 | b;
    }
}
