package dev.dryaides.minuit;

import java.util.List;

/**
 * A key binding as the game's Controls screen shows it: the key's name in a well, a reset button while
 * it is not the default. A click, Enter or Space starts listening, {@code > R <}; the next key or mouse
 * button binds, Escape unbinds. A key another binding also uses draws in {@code bad}, its tooltip
 * naming the others. One key or button: NeoForge's Ctrl, Shift and Alt combos stay in the game's screen.
 */
public class KeybindFieldComponent extends BaseComponent {

    /** GLFW's Escape. The field compares with the running game's code, which differs on SDL builds (26.3). */
    public static final int ESCAPE = 256;
    private static final int RESET = 14;

    /** True while the next key binds. */
    public boolean listening;

    private final Words words;
    private final KeyBinding binding;
    private final ButtonComponent reset;
    private Runnable onChange = () -> { };

    public KeybindFieldComponent(Box box, Words words, KeyBinding binding) {
        super(box);
        this.words = words;
        this.binding = binding;
        this.reset = new ButtonComponent(new Box("reset", this.x + this.w - RESET, this.y + (this.h - RESET) / 2, RESET, RESET), "",
                this::resetToDefault).icon(Icon.REFRESH);
        this.reset.tooltip(words.get("minuit.keybind.reset"));
    }

    /** Runs after every change of the binding: bound, unbound or reset. */
    public KeybindFieldComponent onChange(Runnable changed) {
        this.onChange = changed;
        return this;
    }

    private void resetToDefault() {
        this.binding.reset();
        this.listening = false;
        this.onChange.run();
    }

    private int wellWidth() {
        return this.w - RESET - 2;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        if (!this.focused) {
            this.listening = false;
        }
        int ww = wellWidth();
        theme.frame().field(d, p, this.x, this.y, ww, this.h, this.focused);
        String name = this.binding.keyName();
        int color = !this.binding.conflicts().isEmpty() ? p.bad() : theme.frame().fieldText(p);
        String shown = name;
        if (this.listening) {
            shown = "> " + (name == null || name.isEmpty() ? this.words.get("minuit.keybind.press") : name) + " <";
            color = p.accent();
        }
        int ty = this.y + (this.h - 8) / 2;
        String fit = d.ellipsize(shown, ww - 6);
        d.text(fit, this.x + (ww - d.width(fit)) / 2, ty, color);
        this.reset.x = this.x + this.w - RESET;
        this.reset.y = this.y + (this.h - RESET) / 2;
        this.reset.visible = !this.binding.isDefault();
        this.reset.draw(d, theme, mx, my, this.delta);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (this.reset.visible && this.reset.isOver(mx, my)) {
            return this.reset.tooltip;
        }
        List<String> others = this.binding.conflicts();
        if (!others.isEmpty() && isOver(mx, my) && mx < this.x + wellWidth()) {
            return List.of(this.words.get("minuit.keybind.conflict", String.join(", ", others)));
        }
        return super.tooltipAt(mx, my);
    }

    @Override
    protected boolean takesFocus() {
        return true;
    }

    @Override
    protected boolean capturesKeys() {
        return this.listening;
    }

    @Override
    public boolean keyCaptured(KeyPress key) {
        if (!this.listening) {
            return false;
        }
        if (key.kind() == KeyPress.Kind.KEY && key.code() == dev.dryaides.minuit.internal.port.Platform.keyCodes().escape()) {
            this.binding.unbind();
        } else {
            this.binding.bind(key);
        }
        this.listening = false;
        this.onChange.run();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !isOver(mx, my)) {
            return false;
        }
        if (this.reset.visible && this.reset.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0) {
            click();
            this.listening = true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return this.reset.mouseReleased(mx, my, button);
    }

    @Override
    protected boolean activate() {
        if (!this.active) {
            return false;
        }
        click();
        this.listening = true;
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (this.active && !this.listening && (key == InputKey.ENTER || key == InputKey.SPACE)) {
            return activate();
        }
        return false;
    }
}
