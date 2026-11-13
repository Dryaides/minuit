package bench.render;

import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.FrameStyles;
import dev.dryaides.minuit.internal.ImageSource;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.Shapes;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.internal.ImageFamily;
import dev.dryaides.minuit.internal.ThemeFiles;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Bytes the heap gives the drawing thread per sprite draw of a family from images, after warm-up: a tiled panel, a
 * stretched well, a tinted button under two roles and a tinted track. The Draw does nothing, so every byte counted
 * is the family's. Prints the bytes per draw and exits 1 when it is not 0.
 */
public final class ImageFamilyAlloc {

    public static void main(String[] args) throws Exception {
        String root = "bench:minuit/themes/alloc/sprites/";
        ImageFamily.install(new ImageSource() {
            @Override
            public Pixels read(String id) {
                return new Pixels(8, 8, new int[64]);
            }

            @Override
            public String bake(String key, Pixels image) {
                return "baked:" + key;
            }
        });
        ImageFamily.Sheet sheet = ImageFamily.parse(com.google.gson.JsonParser.parseString(
                "{\"parts\": {\"panel\": {\"inset\": 2}, \"well\": {\"inset\": 2, \"fill\": \"stretch\"},"
                        + " \"button\": {\"inset\": 2, \"tint\": {\"multiply\": \"face\"}},"
                        + " \"track\": {\"inset\": 1, \"tint\": {\"map\": [\"well\", \"face\", \"text\"]}}}}"));
        ImageFamily family = new ImageFamily(FrameStyles.SLATE, sheet, root);
        Palette p = ThemeFiles.builtIn("lilac", ThemeChoice.Variant.DARK);
        Draw d = new Nothing();

        for (int i = 0; i < 20_000; i++) {
            frame(family, d, p);
        }
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        int frames = 100_000;
        long before = bean.getThreadAllocatedBytes(tid);
        for (int i = 0; i < frames; i++) {
            frame(family, d, p);
        }
        long bytes = bean.getThreadAllocatedBytes(tid) - before;
        double perDraw = bytes / (frames * 5.0);
        System.out.printf("image family: %d bytes over %d draws, %.4f bytes per draw%n", bytes, frames * 5, perDraw);
        // The counter itself may cost a few bytes once; anything per draw scales with the 500k draws.
        System.exit(bytes < 1024 ? 0 : 1);
    }

    private static void frame(ImageFamily family, Draw d, Palette p) {
        family.panel(d, p, 0, 0, 120, 80);
        family.well(d, p, 4, 4, 60, 14, false);
        family.raised(d, p, 4, 20, 60, 20, p.primary(), Shapes.Relief.RAISED);
        family.raised(d, p, 4, 44, 60, 20, p.destructive(), Shapes.Relief.RAISED);
        family.track(d, p, 4, 66, 60, 4, p.accent());
    }

    private static final class Nothing extends Draw {
        @Override protected void rect(int x0, int y0, int x1, int y1, int argb) { }
        @Override protected void string(String text, int x, int y, int argb) { }
        @Override public int width(String text) { return 0; }
        @Override public String cut(String text, int maxWidth) { return text; }
        @Override public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) { }
        @Override public void blitRegion(String texture, int x, int y, int w, int h, int u, int v, int rw, int rh, int texW, int texH, float alpha) { }
        @Override protected void pushPose(float x, float y, float scale) { }
        @Override protected void popPose() { }
        @Override public void scissor(int x0, int y0, int x1, int y1) { }
        @Override public void endScissor() { }
        @Override public void tooltip(List<String> lines, int x, int y) { }
    }
}
