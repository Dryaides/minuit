package dev.dryaides.minuit;

import java.util.List;
import java.util.function.Consumer;

/**
 * A box to tick, with a label, and a third "some of them" state that {@link ToggleComponent} has no way to
 * show: use it above a list of options to tell that only part of the list is on.
 *
 * <pre>{@code
 * CheckboxComponent all = add(new CheckboxComponent(at(new Box("all", 10, 10, 160, 12)), "Every biome", false, on -> setAll(on)));
 * all.state(CheckboxComponent.State.MIXED);
 * }</pre>
 *
 * <p>The whole box takes the click, as on a {@link ToggleComponent}: on the press, not the release. A
 * click on a mixed box turns it on, the same as a native tri-state box. In the Vanilla family it wears the
 * game's own check box for off and on. Space and Enter tick it, through the panel.
 */
public class CheckboxComponent extends BaseComponent {
    /** The three states. Only a script or your code sets {@link #MIXED}: a click never goes back to it. */
    public enum State { OFF, ON, MIXED }

    /** Between the box and its label. */
    private static final int LABEL_GAP = 4;
    private static final int MIN_SIDE = 5;

    public String label;

    private final Consumer<Boolean> onChange;
    private State state;
    private boolean cut;

    /**
     * @param box the box and label together; its height sets the box's size
     * @param label what the box stands for; may be empty
     * @param checked on or off at the start
     * @param onChange called when the player changes it, with the new value; may be null
     */
    public CheckboxComponent(Box box, String label, boolean checked, Consumer<Boolean> onChange) {
        super(box);
        this.label = label == null ? "" : label;
        this.state = checked ? State.ON : State.OFF;
        this.onChange = onChange;
    }

    /** The state now. */
    public State state() {
        return state;
    }

    /** Sets the state, without calling back. Null means off. */
    public CheckboxComponent state(State state) {
        this.state = state == null ? State.OFF : state;
        return this;
    }

    /** True only when fully on: a mixed box is not checked. */
    public boolean checked() {
        return state == State.ON;
    }

    /** Sets on or off, without calling back. */
    public CheckboxComponent checked(boolean checked) {
        state = checked ? State.ON : State.OFF;
        return this;
    }

    public CheckboxComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public CheckboxComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    private int side() {
        return Math.min(Math.min(w, h), FrameStyle.checkboxSide(h));
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        int side = side();
        if (side < MIN_SIDE) {
            cut = !label.isEmpty();
            return;
        }
        int top = y + (h - side) / 2;
        boolean lit = hovered && active;
        frame.checkbox(d, p, x, top, side, state, lit, active);
        int left = x + side + LABEL_GAP;
        int room = x + w - left;
        cut = !label.isEmpty() && TextMeasure.width(label) > room;
        if (room > 0 && !label.isEmpty() && h >= FormLook.FONT_H) {
            d.textFit(label, left, RowLook.textY(y, h), room, active ? p.text() : p.disabledText());
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        if (tooltip != null) {
            return tooltip;
        }
        return cut ? List.of(label) : null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return button == 0 && isOver(mx, my) && activate();
    }

    @Override
    protected boolean navigable() {
        return visible && active && settled();
    }

    @Override
    protected boolean activate() {
        if (!visible || !active) {
            return false;
        }
        BaseComponent.click();
        state = state == State.ON ? State.OFF : State.ON;
        if (onChange != null) {
            onChange.accept(state == State.ON);
        }
        return true;
    }

    @Override
    protected int focusCorner() {
        return 0;
    }
}
