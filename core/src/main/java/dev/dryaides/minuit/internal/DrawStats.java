package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Draw;

/**
 * A dev overlay at the top left of a minuit screen, on with {@code -Dminuit.perf=true}: per frame, averaged over
 * about a second, the frame time, the CPU time of the panel's draw calls, and the fills, gradients, texts and raster
 * cache blits it sent. On 1.12.2 to 1.20.x each fill is one draw call, so the fill count is the frame's cost there.
 * Not API.
 */
public final class DrawStats {
    public static final boolean ENABLED = Boolean.getBoolean("minuit.perf");
    private static final long WINDOW = 1_000_000_000L;

    /** The renderer whose calls count: recorders and bakes drawing off screen are left out. */
    public static Draw target;
    public static int fills;
    public static int gradients;
    public static int texts;
    public static int rasterHits;
    public static int rasterBaked;
    public static int rectsSaved;

    private static long windowStart;
    private static long lastFrame;
    private static int frames;
    private static long frameNanos;
    private static long drawNanos;
    private static long sumFills;
    private static long sumGradients;
    private static long sumTexts;
    private static long sumHits;
    private static long sumSaved;
    private static int sumBaked;
    private static String line = "minuit perf: measuring";

    private DrawStats() {
    }

    /** Before the panel draws: counts what {@code d} sends from here on. */
    public static long begin(Draw d) {
        target = d;
        fills = 0;
        gradients = 0;
        texts = 0;
        rasterHits = 0;
        rasterBaked = 0;
        rectsSaved = 0;
        return System.nanoTime();
    }

    /** After the panel drew: folds this frame into the window and draws the last window's averages. */
    public static void end(Draw d, long began) {
        long now = System.nanoTime();
        target = null;
        drawNanos += now - began;
        if (lastFrame != 0) {
            frameNanos += began - lastFrame;
        }
        lastFrame = began;
        frames++;
        sumFills += fills;
        sumGradients += gradients;
        sumTexts += texts;
        sumHits += rasterHits;
        sumSaved += rectsSaved;
        sumBaked += rasterBaked;
        if (windowStart == 0) {
            windowStart = now;
        }
        if (now - windowStart >= WINDOW && frames > 1) {
            line = String.valueOf(Math.round(frameNanos / 1e4 / frames) / 100D) + " ms/frame  draw "
                    + Math.round(drawNanos / 1e3 / frames) + " us  fills " + sumFills / frames
                    + "  grad " + sumGradients / frames + "  text " + sumTexts / frames
                    + "  raster " + sumHits / frames + " (-" + sumSaved / frames + " rects, " + sumBaked + " baked)";
            windowStart = now;
            frames = 0;
            frameNanos = 0;
            drawNanos = 0;
            sumFills = 0;
            sumGradients = 0;
            sumTexts = 0;
            sumHits = 0;
            sumSaved = 0;
            sumBaked = 0;
        }
        d.fill(1, 1, 3 + d.width(line), 11, 0xC0000000);
        d.text(line, 2, 2, 0xFFFFFF);
    }
}
