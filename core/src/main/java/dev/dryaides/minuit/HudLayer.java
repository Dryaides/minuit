package dev.dryaides.minuit;

import java.util.Arrays;
import java.util.function.Function;

/**
 * The HUD parts of every mod, drawn by the loader side each frame outside any screen, between the game's HUD and
 * its chat. Mods add parts to {@link #main()}:
 *
 * <pre>{@code
 * HudBar thirst = new HudBar("mymod", "thirst", HudAnchor.BOTTOM_CENTER, 81).label("Thirst").tone(HudBar.Tone.ACCENT);
 * HudLayer.main().add(thirst.visibleWhen(() -> Thirst.enabled()));
 * ClientTickEvents.END_CLIENT_TICK.register(mc -> thirst.value(Thirst.level() / 20F));
 * }</pre>
 *
 * <p>Placement: per anchor, in the order added, a part starts at its anchor's origin plus its offset; while it
 * meets a box of the vanilla HUD ({@link VanillaHud}) or a part placed before, it moves past that box the way its
 * anchor pushes, 2 px clear; then it is clamped to the screen. Positions are whole GUI pixels. The layout runs
 * again only when the vanilla HUD, the screen size, the set of visible parts or a part's size changes. A player
 * position from {@link #positions} pins a part where the player put it.
 *
 * <p>Render thread only. Nothing is allocated per frame: arrays grow when parts are added.
 */
@Incubating
public final class HudLayer {

    /** Space between a part and what it moved past. */
    public static final int GAP = 2;

    private static final HudLayer MAIN = new HudLayer();

    private HudPart[] parts = new HudPart[0];
    private HudPart[] drawOrder = new HudPart[0];
    private int[] taken = new int[4 * 32];
    private int[] takenFor = new int[32];
    private int takenCount;
    private final VanillaHud last = new VanillaHud();
    private boolean laidOut;
    private Function<String, Theme> themes = Themes::byId;
    private int themeGeneration = Integer.MIN_VALUE;
    private LayoutStore positions;

    /** The layer the game draws. */
    public static HudLayer main() {
        return MAIN;
    }

    /** Adds {@code part}, replacing one with the same mod and id. */
    public <P extends HudPart> P add(P part) {
        remove(part.modId, part.id);
        HudPart[] more = Arrays.copyOf(this.parts, this.parts.length + 1);
        more[more.length - 1] = part;
        this.parts = more;
        HudPart[] order = Arrays.copyOf(more, more.length);
        // Stable, so equal z keeps the order added.
        Arrays.sort(order, (a, b) -> Integer.compare(a.z, b.z));
        this.drawOrder = order;
        this.laidOut = false;
        return part;
    }

    public boolean remove(String modId, String id) {
        for (int i = 0; i < this.parts.length; i++) {
            HudPart p = this.parts[i];
            if (p.modId.equals(modId) && p.id.equals(id)) {
                HudPart[] fewer = new HudPart[this.parts.length - 1];
                System.arraycopy(this.parts, 0, fewer, 0, i);
                System.arraycopy(this.parts, i + 1, fewer, i, fewer.length - i);
                this.parts = fewer;
                HudPart[] order = new HudPart[fewer.length];
                int n = 0;
                for (HudPart q : this.drawOrder) {
                    if (q != p) {
                        order[n++] = q;
                    }
                }
                this.drawOrder = order;
                this.laidOut = false;
                return true;
            }
        }
        return false;
    }

    public HudPart get(String modId, String id) {
        for (HudPart p : this.parts) {
            if (p.modId.equals(modId) && p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    /** The theme each part wears, by mod id; {@link Themes#byId} by default. A bench passes its own. */
    public HudLayer themes(Function<String, Theme> source) {
        this.themes = source == null ? Themes::byId : source;
        this.themeGeneration = Integer.MIN_VALUE;
        return this;
    }

    /**
     * Where the player moved parts, by the key {@code hud/<modId>:<id>}: a stored offset from the anchor's origin
     * replaces the mod's and pins the part (no push). Read on layout passes only. Null for none.
     */
    public HudLayer positions(LayoutStore store) {
        this.positions = store;
        this.laidOut = false;
        return this;
    }

    /** Places the parts again on the next frame, after a player moved one through {@link #positions}. */
    public void relayout() {
        this.laidOut = false;
    }

    /** One frame: updates the visible parts, places them if anything moved, draws them in z order. */
    public void render(Draw d, VanillaHud hud, float partial) {
        if (hud.hidden || this.parts.length == 0) {
            for (HudPart p : this.parts) {
                p.shown = false;
            }
            return;
        }
        int generation = Themes.generation();
        if (generation != this.themeGeneration) {
            for (HudPart p : this.parts) {
                p.theme = null;
            }
            this.themeGeneration = generation;
        }
        boolean layout = !this.laidOut || !this.last.sameAs(hud);
        for (HudPart p : this.parts) {
            boolean visible = p.visible.getAsBoolean();
            if (visible) {
                p.update(d, partial);
            }
            if (visible != p.shown || visible && p.resized) {
                layout = true;
            }
            p.shown = visible;
        }
        if (layout) {
            layout(hud);
        }
        for (HudPart p : this.drawOrder) {
            if (p.shown) {
                if (p.theme == null) {
                    p.theme = this.themes.apply(p.modId);
                }
                p.draw(d, p.theme, p.x, p.y, partial);
            }
        }
    }

    private void layout(VanillaHud hud) {
        hud.reserve();
        int needed = 4 * (hud.count() + this.parts.length);
        if (this.taken.length < needed) {
            this.taken = new int[needed];
            this.takenFor = new int[needed / 4];
        }
        this.takenCount = 0;
        for (int i = 0; i < hud.count(); i++) {
            take(hud.x0(i), hud.y0(i), hud.x1(i), hud.y1(i), hud.anchors(i));
        }
        int sw = hud.guiWidth;
        int sh = hud.guiHeight;
        for (HudAnchor anchor : HudAnchor.values()) {
            for (HudPart p : this.parts) {
                if (!p.shown || p.anchor != anchor) {
                    continue;
                }
                p.resized = false;
                LayoutStore.Layout pinned = this.positions == null ? null : this.positions.get(p.key);
                int x = anchor.originX(sw, p.w) + (pinned != null ? pinned.dx() : p.dx);
                int y = anchor.originY(sh, p.h) + (pinned != null ? pinned.dy() : p.dy);
                if (pinned == null) {
                    int py = anchor.pushY();
                    // Each push leaves one box behind for good, so there are at most as many pushes as boxes.
                    for (int guard = 0; guard <= this.takenCount; guard++) {
                        int hit = firstHit(x, y, p.w, p.h, 1 << anchor.ordinal());
                        if (hit < 0) {
                            break;
                        }
                        int b = 4 * hit;
                        if (py > 0) {
                            y = this.taken[b + 3] + GAP;
                        } else {
                            y = this.taken[b + 1] - GAP - p.h;
                        }
                    }
                }
                x = Math.max(0, Math.min(x, sw - p.w));
                y = Math.max(0, Math.min(y, sh - p.h));
                p.x = x;
                p.y = y;
                take(x, y, x + p.w, y + p.h, -1);
            }
        }
        this.last.copyFrom(hud);
        this.laidOut = true;
    }

    private int firstHit(int x, int y, int w, int h, int anchorBit) {
        for (int i = 0; i < this.takenCount; i++) {
            int b = 4 * i;
            if ((this.takenFor[i] & anchorBit) != 0 && x < this.taken[b + 2] && x + w > this.taken[b] && y < this.taken[b + 3] && y + h > this.taken[b + 1]) {
                return i;
            }
        }
        return -1;
    }

    private void take(int x0, int y0, int x1, int y1, int anchors) {
        this.takenFor[this.takenCount] = anchors;
        int b = 4 * this.takenCount++;
        this.taken[b] = x0;
        this.taken[b + 1] = y0;
        this.taken[b + 2] = x1;
        this.taken[b + 3] = y1;
    }
}
