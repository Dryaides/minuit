package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * A key cap: the key a player presses, as a help line or a tutorial shows it. Several keys show as a combination,
 * joined by "+". It shows, it does not take input.
 *
 * <pre>{@code
 * add(new KbdComponent(at(new Box("open", 10, 10, 0, KbdComponent.HEIGHT)), "E"));
 * add(new KbdComponent(at(new Box("find", 30, 10, 0, KbdComponent.HEIGHT)), "Ctrl", "F"));
 * add(new KbdComponent(at(new Box("zoom", 90, 10, 0, KbdComponent.HEIGHT)), zoomBinding));
 * }</pre>
 *
 * <p>Given a {@link KeyBinding}, it reads the bound key on every draw, so the cap follows the player's rebinding; an
 * unbound key shows as an empty cap. A box of width 0 takes the width of its keys; a wider box centres them.
 */
public class KbdComponent extends BaseComponent {

    /** The cap's face holds a line of text with its margin; the key's side shows under it. */
    public static final int HEIGHT = 13;

    /** How far the key's side shows under the face; a pressed key shows half of it. */
    private static final int SIDE = 2;
    private static final int PAD = 4;
    /** Space on each side of the "+" between two caps. */
    private static final int JOIN = 2;
    private static final String PLUS = "+";

    private final int givenW;
    private List<String> keys;
    private KeyBinding binding;
    private boolean pressed;

    public KbdComponent(Box box, String... keys) {
        super(box);
        this.givenW = box.w();
        keys(keys);
    }

    public KbdComponent(Box box, KeyBinding binding) {
        this(box);
        this.binding = binding;
    }

    /** The keys to show, in the order they are pressed; this drops a binding given before. */
    public KbdComponent keys(String... keys) {
        List<String> out = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                if (key != null) {
                    out.add(key);
                }
            }
        }
        this.keys = out;
        this.binding = null;
        return this;
    }

    /** Shows the key {@code binding} is bound to now, read on every draw. */
    public KbdComponent binding(KeyBinding binding) {
        this.binding = binding;
        return this;
    }

    /** The keys shown now. */
    public List<String> keys() {
        if (this.binding == null) {
            return this.keys;
        }
        String name = this.binding.keyName();
        return List.of(name == null ? "" : name);
    }

    /** Draws the keys held down: the face sinks one pixel, as a tutorial shows the key the player must hold. */
    public KbdComponent pressed(boolean pressed) {
        this.pressed = pressed;
        return this;
    }

    public boolean pressed() {
        return this.pressed;
    }

    public KbdComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public KbdComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** The width of one cap at this height: never narrower than it is tall, so a single letter sits on a square. */
    private int capWidth(String key) {
        int text = key.isEmpty() ? 0 : TextMeasure.width(key) - 1;
        return Math.max(this.h - SIDE, text + 2 * PAD);
    }

    private static int plusWidth() {
        return TextMeasure.width(PLUS) - 1 + 2 * JOIN;
    }

    /** The width the keys want. */
    public int naturalWidth() {
        List<String> shown = keys();
        int total = 0;
        for (String key : shown) {
            total += capWidth(key);
        }
        return total + Math.max(0, shown.size() - 1) * plusWidth();
    }

    private void fit() {
        if (this.givenW <= 0) {
            this.w = naturalWidth();
        }
    }

    @Override
    public boolean isOver(double mx, double my) {
        fit();
        return super.isOver(mx, my);
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        fit();
        List<String> shown = keys();
        if (shown.isEmpty() || this.h <= SIDE) {
            return;
        }
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        int face = this.active ? FormLook.quietFace(p) : FormLook.disabledFace(p);
        int side = this.active ? FormLook.solid(Shapes.bevelDark(FormLook.solid(FormLook.edge(p)))) : FormLook.solid(p.disabledText());
        int ink = this.active ? Ink.on(p, face) : p.disabledText();
        int joinInk = this.active ? FormLook.soft(p) : p.disabledText();
        int faceH = this.h - SIDE;
        int sink = this.pressed ? SIDE / 2 : 0;
        int textY = RowLook.textY(this.y + sink, faceH) + faceH % 2;
        int left = this.x + Math.max(0, (this.w - naturalWidth()) / 2);
        for (int i = 0; i < shown.size(); i++) {
            if (i > 0) {
                d.text(PLUS, left + JOIN, RowLook.textY(this.y, faceH) + faceH % 2, joinInk);
                left += plusWidth();
            }
            String key = shown.get(i);
            int cw = capWidth(key);
            frame.plate(d, p, left, this.y + SIDE, cw, faceH, side, 0);
            frame.plate(d, p, left, this.y + sink, cw, faceH, face, FormLook.edge(p));
            if (!key.isEmpty()) {
                d.text(key, left + (cw - (TextMeasure.width(key) - 1)) / 2, textY, ink);
            }
            left += cw;
        }
    }
}
