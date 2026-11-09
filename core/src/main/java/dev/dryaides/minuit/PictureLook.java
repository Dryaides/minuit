package dev.dryaides.minuit;

/**
 * How a {@link Picture} is placed and drawn, shared by every component that shows one. The picture is
 * placed in screen pixels, not GUI units, so that its size and aspect are as exact as the screen allows.
 */
final class PictureLook {
    /** A shrink past this many source pixels per screen pixel turns smoothing on. */
    private static final float SMOOTH_FROM = 1.5F;
    /** Past this, 3 by 3 samples instead of 2 by 2. */
    private static final float NINE_FROM = 2.5F;
    private static final int DISABLED_VEIL = 0x90;
    private static final int LABEL_H = 11;

    private PictureLook() {
    }

    /** A zoom and the point of the source at the middle of the box, from 0 to 1 on each side. */
    static final class View {
        float zoom = 1F;
        float cx = 0.5F;
        float cy = 0.5F;

        View set(float zoom, float cx, float cy) {
            this.zoom = zoom;
            this.cx = cx;
            this.cy = cy;
            return this;
        }
    }

    /** Device pixels of the screen per unit of the source (pixel, texel or GUI unit), at the fit, before any zoom. */
    static float[] fitScale(Picture pic, int bw, int bh, int gs) {
        int sw = pic.sourceWidth();
        int sh = pic.sourceHeight();
        if (sw <= 0 || sh <= 0 || bw <= 0 || bh <= 0) {
            return null;
        }
        float natural = pic.kind() == Picture.Kind.IMAGE ? 1F : gs;
        float contain = Math.min(bw / (float) sw, bh / (float) sh);
        float cover = Math.max(bw / (float) sw, bh / (float) sh);
        boolean art = pic.art() != null;
        return switch (pic.fit()) {
            case COVER -> new float[] {cover, cover};
            case STRETCH -> art ? new float[] {contain, contain} : new float[] {bw / (float) sw, bh / (float) sh};
            case SCALE_DOWN -> new float[] {Math.min(contain, natural), Math.min(contain, natural)};
            case NATURAL -> new float[] {natural, natural};
            case INTEGER -> {
                float s = contain >= 1F ? (float) Math.floor(contain) : 1F / (float) Math.ceil(1F / contain);
                yield new float[] {s, s};
            }
            default -> new float[] {contain, contain};
        };
    }

    /** {x, y, w, h} in device pixels, or null when there is nothing to place. */
    static int[] place(Picture pic, int bx, int by, int bw, int bh, int gs, View view) {
        float[] s = fitScale(pic, bw, bh, gs);
        if (s == null) {
            return null;
        }
        float zoom = view == null ? 1F : view.zoom;
        int dw = Math.max(1, Math.round(pic.sourceWidth() * s[0] * zoom));
        int dh = Math.max(1, Math.round(pic.sourceHeight() * s[1] * zoom));
        int dx;
        int dy;
        if (view == null) {
            dx = bx + Math.round((bw - dw) * (dw > bw ? pic.focusX() : 0.5F));
            dy = by + Math.round((bh - dh) * (dh > bh ? pic.focusY() : 0.5F));
        } else {
            dx = dw <= bw ? bx + Math.round((bw - dw) / 2F) : clamp(Math.round(bx + bw / 2F - view.cx * dw), bx + bw - dw, bx);
            dy = dh <= bh ? by + Math.round((bh - dh) / 2F) : clamp(Math.round(by + bh / 2F - view.cy * dh), by + bh - dh, by);
        }
        return new int[] {dx, dy, dw, dh};
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- drawing ----

    /** The full look with a theme: states, letterbox, picture, and the disabled veil. */
    static void paint(Draw d, Theme t, Picture pic, int x, int y, int w, int h, View view, boolean live, float alpha) {
        if (pic == null || w <= 0 || h <= 0) {
            return;
        }
        Palette p = t.palette();
        if (pic.state() != AsyncImage.State.READY) {
            AsyncImage.draw(d, p, pic.image(), x, y, w, h);
            if (pic.state() == AsyncImage.State.FAILED) {
                failed(d, t, pic, x, y, w, h);
            }
            if (!live) {
                veil(d, p, x, y, w, h);
            }
            return;
        }
        int ink = pic.kind() == Picture.Kind.ICON ? (live ? p.text() : p.disabledText()) : p.text();
        int band = pic.letterbox() == Picture.THEME ? p.well() : pic.letterbox();
        content(d, pic, x, y, w, h, view, band, ink, alpha);
        if (!live && pic.kind() != Picture.Kind.ICON) {
            veil(d, p, x, y, w, h);
        }
    }

    /** As an icon, without a theme: a faint block while loading or failed. */
    static void paintBare(Draw d, Picture pic, int x, int y, int w, int h, int color, float alpha) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (pic.state() != AsyncImage.State.READY) {
            d.fill(x, y, x + w, y + h, Shapes.alpha(color, 0x30));
            return;
        }
        int band = pic.letterbox() == Picture.THEME ? 0 : pic.letterbox();
        content(d, pic, x, y, w, h, null, band, color, alpha);
    }

    private static void content(Draw d, Picture pic, int x, int y, int w, int h, View view, int band, int ink, float alpha) {
        int gs = Math.max(1, d.guiScale());
        int[] r = place(pic, x * gs, y * gs, w * gs, h * gs, gs, view);
        if (r == null) {
            return;
        }
        boolean covers = r[0] <= x * gs && r[1] <= y * gs && r[0] + r[2] >= (x + w) * gs && r[1] + r[3] >= (y + h) * gs;
        if (!covers && band >>> 24 != 0) {
            d.fill(x, y, x + w, y + h, band);
        }
        boolean cut = r[0] < x * gs || r[1] < y * gs || r[0] + r[2] > (x + w) * gs || r[1] + r[3] > (y + h) * gs;
        if (cut) {
            d.scissor(x, y, x + w, y + h);
        }
        String texture = pic.textureId();
        if (texture != null) {
            texture(d, texture, pic.sourceWidth(), pic.sourceHeight(), r, gs, pic.smooth() && alpha >= 1F, alpha);
        } else if (pic.art() != null) {
            float s = r[2] / (float) Math.max(1, pic.sourceWidth());
            d.push(r[0] / (float) gs, r[1] / (float) gs, s / gs);
            pic.art().draw(d, 0, 0, ink, alpha);
            d.pop();
        }
        if (cut) {
            d.endScissor();
        }
    }

    /**
     * A texture over a device rectangle. Shrunk a lot, it is drawn n x n times, each shifted by a fraction of
     * a screen pixel in the source and laid over the last with 1/k opacity: the average of n x n samples per
     * screen pixel instead of one. The shift drops at most one screen pixel of the far edges.
     */
    private static void texture(Draw d, String texture, int sw, int sh, int[] r, int gs, boolean smooth, float alpha) {
        float kx = sw / (float) r[2];
        float ky = sh / (float) r[3];
        float k = Math.min(kx, ky);
        d.push(0F, 0F, 1F / gs);
        if (!smooth || k < SMOOTH_FROM) {
            d.blitRegion(texture, r[0], r[1], r[2], r[3], 0, 0, sw, sh, sw, sh, alpha);
        } else {
            int n = k >= NINE_FROM ? 3 : 2;
            int lastX = Math.round((n - 1) * kx / n);
            int lastY = Math.round((n - 1) * ky / n);
            int rw = sw - lastX;
            int rh = sh - lastY;
            int pass = 0;
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    int u = Math.round(i * kx / n);
                    int v = Math.round(j * ky / n);
                    d.blitRegion(texture, r[0], r[1], r[2], r[3], u, v, rw, rh, sw, sh, 1F / (++pass));
                }
            }
        }
        d.pop();
    }

    /** The failed look: the library's empty well, a warning sign and the picture's words if there is room. */
    private static void failed(Draw d, Theme t, Picture pic, int x, int y, int w, int h) {
        Palette p = t.palette();
        IconArt icon = t.icon(Icon.WARN);
        int ink = Ink.soft(p, p.well());
        boolean words = !pic.alt().isEmpty() && h >= icon.height() + 16 && w >= 40;
        int block = icon.height() + (words ? 4 + SurfaceLook.FONT_H : 0);
        if (w >= icon.width() + 4 && h >= icon.height() + 4) {
            int top = y + (h - block) / 2;
            icon.draw(d, x + (w - icon.width()) / 2, top, ink, 1F);
            if (words) {
                String line = d.ellipsize(pic.alt(), w - 8);
                d.text(line, x + (w - d.width(line) + 1) / 2, top + icon.height() + 4, ink);
            }
        }
    }

    private static void veil(Draw d, Palette p, int x, int y, int w, int h) {
        d.fill(x, y, x + w, y + h, Shapes.alpha(p.surface() | 0xFF000000, DISABLED_VEIL));
    }

    /** The colour around the box over its corners, the same steps as {@link Shapes#rounded}. */
    static void corners(Draw d, int x, int y, int w, int h, int radius, int outside) {
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5D;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0D, r * r - dy * dy)));
            if (inset <= 0) {
                continue;
            }
            d.fill(x, y + i, x + inset, y + i + 1, outside);
            d.fill(x + w - inset, y + i, x + w, y + i + 1, outside);
            d.fill(x, y + h - 1 - i, x + inset, y + h - i, outside);
            d.fill(x + w - inset, y + h - 1 - i, x + w, y + h - i, outside);
        }
    }

    /** True when the picture should get rounded corners here: a plain surface and a rounded theme. */
    static boolean canRound(Theme t) {
        return t.frame().corner() > 0 && t.palette().surface() >>> 24 == 0xFF;
    }

    /** A picture's frame: rounded corners on a plain surface, then the theme's well edge. */
    static void frame(Draw d, Theme t, int x, int y, int w, int h, int outside) {
        if (canRound(t) && outside >>> 24 == 0xFF) {
            corners(d, x, y, w, h, t.frame().plateCorner(w, h), outside);
        }
        t.frame().plate(d, t.palette(), x, y, w, h, 0, t.palette().wellEdge());
    }

    /**
     * A flat inner backing, for a part inside a component that has its own frame: the well colour
     * and a faint hairline except over a see-through family (glass), where fill alone reads.
     */
    static void flat(Draw d, Theme t, int x, int y, int w, int h, boolean hairline) {
        Palette p = t.palette();
        boolean edged = hairline && p.surface() >>> 24 == 0xFF;
        t.frame().plate(d, p, x, y, w, h, p.well(), edged ? Shapes.alpha(p.wellEdge(), 0x50) : 0);
    }

    /** One cell of a picture grid: the picture over the cell, a caption band, and the glow of the hover. */
    static void cell(Draw d, Theme t, Picture pic, String label, Box cell, float glow, boolean selected) {
        Palette p = t.palette();
        int x = cell.x();
        int y = cell.y();
        int w = cell.w();
        int h = cell.h();
        if (pic != null) {
            Picture shown = pic.kind() == Picture.Kind.IMAGE && pic.fit() == Picture.Fit.CONTAIN ? pic.fit(Picture.Fit.COVER) : pic;
            if (shown.art() != null) {
                flat(d, t, x, y, w, h, false);
                paint(d, t, shown, x + 2, y + 2, w - 4, h - 4 - (label.isEmpty() ? 0 : LABEL_H - 2), null, true, 1F);
            } else {
                paint(d, t, shown, x, y, w, h, null, true, 1F);
            }
        }
        if (!label.isEmpty() && h >= LABEL_H + 6 && w >= 16) {
            d.nextLayer();
            d.fill(x, y + h - LABEL_H, x + w, y + h, Shapes.alpha(p.shadow(), 0xB0));
            String line = d.ellipsize(label, w - 6);
            d.text(line, x + (w - d.width(line) + 1) / 2, y + h - LABEL_H + 2, 0xFFFFFFFF);
        }
        int edge = selected ? p.primary() : Shapes.mix(p.wellEdge(), p.accent(), Math.max(0F, Math.min(1F, glow)));
        if (canRound(t)) {
            corners(d, x, y, w, h, t.frame().plateCorner(w, h), p.surface());
        }
        t.frame().plateEdge(d, p, x, y, w, h, selected ? 2 : 1, edge);
    }
}
