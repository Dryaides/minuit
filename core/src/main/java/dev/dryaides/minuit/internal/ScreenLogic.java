package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.Glide;
import dev.dryaides.minuit.ItemDropTarget;
import dev.dryaides.minuit.ItemSource;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.ScreenAreas;
import dev.dryaides.minuit.Slide;
import dev.dryaides.minuit.TabsComponent;
import dev.dryaides.minuit.internal.port.KeyStroke;
import dev.dryaides.minuit.internal.port.Platform;

import java.util.List;

/**
 * What {@code client.MinuitScreen} does with its panel: the glide in, the draw order, the mouse and
 * keys routed with the tab click and the capture rules. The screen converts each game event and
 * passes its panel; a false return lets the screen hand the event to the game. Not API.
 */
public final class ScreenLogic {

    private final HostCore host;
    /** The panel's glide in on its first frames, or null; its clock starts on the first frame drawn. */
    private Glide slide;
    private boolean slideDrawn;
    /** The mouse at the last frame, in the panel's coordinates. */
    private double mouseX = -1;
    private double mouseY = -1;

    public ScreenLogic(HostCore host) {
        this.host = host;
    }

    /** The panel glides in from {@code from} on the next frames. */
    public void slideFrom(Slide from) {
        this.slide = new Glide(from, TabsComponent.SLIDE_MS);
        this.slide.replay();
        this.slideDrawn = false;
    }

    /** Tab, panel, then the tooltips once the panel has landed. */
    public void draw(Draw d, PanelComponent current, int mouseX, int mouseY) {
        if (current == null) {
            return;
        }
        int[] off = slideOffset(current);
        boolean moved = off[0] != 0 || off[1] != 0;
        if (moved) {
            d.push(off[0], off[1], 1F);
            mouseX -= off[0];
            mouseY -= off[1];
        }
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.host.drawTab(d, current.theme(), current.bounds(), mouseX, mouseY);
        current.draw(d, mouseX, mouseY, this.host.frame());
        if (moved) {
            d.pop();
            return;
        }
        current.drawTooltip(d, mouseX, mouseY);
        this.host.drawTabTip(d, current.bounds(), mouseX, mouseY);
    }

    /** Where the panel still is from its place while it glides in; {0, 0} once it has landed. */
    private int[] slideOffset(PanelComponent current) {
        if (this.slide == null) {
            return new int[]{0, 0};
        }
        this.slideDrawn = true;
        int[] off = offset(this.slide, current);
        if (!this.slide.moving()) {
            this.slide = null;
        }
        return off;
    }

    /** A mouse point moved by the glide still under way, without ending it; as is once landed. */
    public double[] toPanel(PanelComponent current, double mx, double my) {
        if (this.slide == null || !this.slideDrawn || current == null) {
            return new double[]{mx, my};
        }
        int[] off = offset(this.slide, current);
        return new double[]{mx - off[0], my - off[1]};
    }

    /** A quarter of the panel's size away at the start, as {x, y}. */
    private static int[] offset(Glide glide, PanelComponent current) {
        Box b = current.bounds();
        return new int[]{Math.round(glide.offsetX(b.w() / 4F)), Math.round(glide.offsetY(b.h() / 4F))};
    }

    public ItemView itemUnderMouse(PanelComponent current) {
        return current == null ? null : ItemSource.under(current, this.mouseX, this.mouseY);
    }

    public List<Box> extraAreas(PanelComponent current) {
        return current == null ? List.of() : ScreenAreas.outside(current, null);
    }

    public List<ItemDropTarget.Drop> dropTargets(PanelComponent current) {
        return current == null ? List.of() : ItemDropTarget.all(current);
    }

    public void tick(PanelComponent current) {
        if (current != null) {
            current.tickAll();
        }
    }

    public boolean mouseClicked(PanelComponent current, double mx, double my, int gameButton) {
        if (current != null && current.capturing() && current.rawKey(KeyPress.mouse(gameButton))) {
            return true;
        }
        int button = KeyRouter.button(gameButton, Platform.keyCodes());
        if (button < 0) {
            return false;
        }
        double[] at = toPanel(current, mx, my);
        if (current != null && current.mouseClicked(at[0], at[1], button)) {
            return true;
        }
        return current != null && this.host.clickTab(current.theme(), current.bounds(), at[0], at[1]);
    }

    public void mouseReleased(PanelComponent current, double mx, double my, int gameButton) {
        int button = KeyRouter.button(gameButton, Platform.keyCodes());
        if (current != null && button >= 0) {
            double[] at = toPanel(current, mx, my);
            current.mouseReleased(at[0], at[1], button);
        }
    }

    public boolean mouseDragged(PanelComponent current, double mx, double my, int gameButton, double dx, double dy) {
        int button = KeyRouter.button(gameButton, Platform.keyCodes());
        double[] at = toPanel(current, mx, my);
        return current != null && button >= 0 && current.mouseDragged(at[0], at[1], button, dx, dy);
    }

    public boolean mouseScrolled(PanelComponent current, double mx, double my, double scrollY) {
        double[] at = toPanel(current, mx, my);
        return current != null && current.mouseScrolled(at[0], at[1], scrollY);
    }

    public boolean keyPressed(PanelComponent current, KeyStroke stroke) {
        return current != null && this.host.keyPressed(current, stroke);
    }

    public boolean charTyped(PanelComponent current, int codepoint) {
        return current != null && current.charTyped(codepoint);
    }

    /** The theme page only steps away: the panel comes back, so it is not closing. */
    public void removed(PanelComponent current) {
        if (current != null && !this.host.away()) {
            current.closing();
        }
    }
}
