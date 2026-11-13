package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Draw;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PartTable.Region} drawn into a box of any size the way 1.20.1's {@code GuiGraphics.blitNineSliced}
 * draws it: the corners at their source size, the edges and the centre repeated, never stretched. A span
 * {@code n} px long over a source {@code s} px long is cut into {@code ceil(n / s)} near-equal parts (the
 * larger ones last, as the game's {@code Divisor} hands them out), each taking the middle of the source.
 * Not API.
 */
public final class NineSlice {

    private NineSlice() {
    }

    /** One drawn piece: its target box, then its source rectangle in the texture, always the same size. */
    public record Piece(int x, int y, int w, int h, int u, int v, int uw, int vh) {
    }

    public static void draw(Draw d, PartTable.Region r, int x, int y, int w, int h, float alpha) {
        blit(d, r, pieces(r, x, y, w, h), alpha);
    }

    /** What {@link #draw(Draw, PartTable.Region, int, int, int, int, float)} draws, without building the pieces. */
    public static void tile(Draw d, PartTable.Region r, int x, int y, int w, int h, float alpha) {
        int left = Math.min(r.left(), w / 2);
        int right = Math.min(r.right(), w / 2);
        int top = Math.min(r.top(), h / 2);
        int bottom = Math.min(r.bottom(), h / 2);
        for (int row = 0; row < 3; row++) {
            int ty = cut(row, y, top, bottom, h);
            int th = cut(row + 1, y, top, bottom, h) - ty;
            int sv = cut(row, r.v(), top, bottom, r.h());
            int vh = cut(row + 1, r.v(), top, bottom, r.h()) - sv;
            for (int col = 0; col < 3; col++) {
                int tx = cut(col, x, left, right, w);
                int tw = cut(col + 1, x, left, right, w) - tx;
                int su = cut(col, r.u(), left, right, r.w());
                int uw = cut(col + 1, r.u(), left, right, r.w()) - su;
                repeat(d, r, tx, ty, tw, th, su, sv, uw, vh, alpha);
            }
        }
    }

    /** The {@code i}th of the four cuts {start, start + low, start + length - high, start + length}. */
    static int cut(int i, int start, int low, int high, int length) {
        return switch (i) {
            case 0 -> start;
            case 1 -> start + low;
            case 2 -> start + length - high;
            default -> start + length;
        };
    }

    /** The other {@code repeat}, blitted as it goes. */
    private static void repeat(Draw d, PartTable.Region r, int x, int y, int w, int h, int u, int v, int uw, int vh,
                               float alpha) {
        if (w <= 0 || h <= 0 || uw <= 0 || vh <= 0) {
            return;
        }
        int nx = (w + uw - 1) / uw;
        int ny = (h + vh - 1) / vh;
        int px = x;
        int cx = 0;
        for (int i = 0; i < nx; i++) {
            int pw = w / nx;
            cx += w % nx;
            if (cx >= nx) {
                cx -= nx;
                pw++;
            }
            int py = y;
            int cy = 0;
            for (int j = 0; j < ny; j++) {
                int ph = h / ny;
                cy += h % ny;
                if (cy >= ny) {
                    cy -= ny;
                    ph++;
                }
                d.blitRegion(r.texture(), px, py, pw, ph, u + (uw - pw) / 2, v + (vh - ph) / 2, pw, ph, r.texW(),
                        r.texH(), alpha);
                py += ph;
            }
            px += pw;
        }
    }

    /** A {@link PartTable.Region} or {@link PartTable.Framed}; false for a sprite or no art, which the caller draws. */
    public static boolean draw(Draw d, PartTable.Art art, int x, int y, int w, int h, float alpha) {
        if (art instanceof PartTable.Region r) {
            draw(d, r, x, y, w, h, alpha);
            return true;
        }
        if (art instanceof PartTable.Framed f) {
            blit(d, f.frame(), pieces(f, x, y, w, h), alpha);
            return true;
        }
        return false;
    }

    /**
     * The pieces of {@code f.frame()} but its centre, then the centre box tiled from {@code f.fill()}, the
     * fill's own borders ignored.
     */
    public static List<Piece> pieces(PartTable.Framed f, int x, int y, int w, int h) {
        PartTable.Region r = f.frame();
        int left = Math.min(r.left(), w / 2);
        int right = Math.min(r.right(), w / 2);
        int top = Math.min(r.top(), h / 2);
        int bottom = Math.min(r.bottom(), h / 2);
        List<Piece> out = new ArrayList<>();
        for (Piece p : pieces(r, x, y, w, h)) {
            boolean centre = p.x() >= x + left && p.x() < x + w - right && p.y() >= y + top && p.y() < y + h - bottom;
            if (!centre) {
                out.add(p);
            }
        }
        PartTable.Region fill = f.fill();
        repeat(out, x + left, y + top, w - left - right, h - top - bottom, fill.u(), fill.v(), fill.w(), fill.h());
        return out;
    }

    private static void blit(Draw d, PartTable.Region r, List<Piece> pieces, float alpha) {
        for (Piece p : pieces) {
            d.blitRegion(r.texture(), p.x(), p.y(), p.w(), p.h(), p.u(), p.v(), p.uw(), p.vh(), r.texW(), r.texH(), alpha);
        }
    }

    /** The non-empty pieces, row by row; a border wider than half the box is cut to half, as in the game. */
    public static List<Piece> pieces(PartTable.Region r, int x, int y, int w, int h) {
        int left = Math.min(r.left(), w / 2);
        int right = Math.min(r.right(), w / 2);
        int top = Math.min(r.top(), h / 2);
        int bottom = Math.min(r.bottom(), h / 2);
        int[] tx = {x, x + left, x + w - right, x + w};
        int[] ty = {y, y + top, y + h - bottom, y + h};
        int[] su = {r.u(), r.u() + left, r.u() + r.w() - right, r.u() + r.w()};
        int[] sv = {r.v(), r.v() + top, r.v() + r.h() - bottom, r.v() + r.h()};
        List<Piece> out = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                repeat(out, tx[col], ty[row], tx[col + 1] - tx[col], ty[row + 1] - ty[row],
                        su[col], sv[row], su[col + 1] - su[col], sv[row + 1] - sv[row]);
            }
        }
        return out;
    }

    /** {@code GuiGraphics.blitRepeating} of 1.20.1. */
    private static void repeat(List<Piece> out, int x, int y, int w, int h, int u, int v, int uw, int vh) {
        if (w <= 0 || h <= 0 || uw <= 0 || vh <= 0) {
            return;
        }
        int px = x;
        for (int pw : parts(w, uw)) {
            int py = y;
            for (int ph : parts(h, vh)) {
                out.add(new Piece(px, py, pw, ph, u + (uw - pw) / 2, v + (vh - ph) / 2, pw, ph));
                py += ph;
            }
            px += pw;
        }
    }

    /** {@code slices} of 1.20.1: {@code length} split into {@code ceil(length / source)} parts by {@code Divisor}. */
    static int[] parts(int length, int source) {
        int n = (length + source - 1) / source;
        int[] out = new int[n];
        int quotient = length / n;
        int mod = length % n;
        int carry = 0;
        for (int i = 0; i < n; i++) {
            out[i] = quotient;
            carry += mod;
            if (carry >= n) {
                carry -= n;
                out[i]++;
            }
        }
        return out;
    }
}
