package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.DarkTwin;
import dev.dryaides.minuit.internal.port.KeyCodes;
import dev.dryaides.minuit.internal.port.Platform;

import java.util.List;

/**
 * A key binding as the game's Controls screen shows it: the key's name in a well, a reset button while
 * it is not the default. A click, Enter or Space starts listening, {@code > R <}; the next key or mouse
 * button binds, Escape unbinds. Ctrl, Shift, Alt or Meta held first make a combo, {@code Ctrl + Shift + M},
 * where the binding {@linkplain KeyBinding#supportsModifiers supports modifiers} (one of Ctrl, Shift, Alt on
 * NeoForge and Forge, any set with {@link KeyBinding#supportsMultipleModifiers}); elsewhere the key binds alone. A modifier let go with no other key binds itself, so Left Shift stays bindable. A key another
 * binding also uses draws in {@code bad}, its tooltip naming the others.
 */
public class KeybindFieldComponent extends BaseComponent {

    /** GLFW's Escape. The field compares with the running game's code, which differs on SDL builds (26.3). */
    public static final int ESCAPE = 256;

    /** True while the next key binds. */
    public boolean listening;

    private final Words words;
    private final KeyBinding binding;
    private final ButtonComponent reset;
    private Runnable onChange = () -> { };
    /** The codes of the modifier keys down since listening began; the first one let go binds alone if no other key came. */
    private final java.util.Set<Integer> heldModifiers = new java.util.LinkedHashSet<>();

    public KeybindFieldComponent(Box box, Words words, KeyBinding binding) {
        super(box);
        this.words = words;
        this.binding = binding;
        this.reset = new ButtonComponent(new Box("reset", this.x + this.w - this.h, this.y, this.h, this.h), "",
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
        stopListening();
        this.onChange.run();
    }

    private int wellWidth() {
        return this.w - this.h - 2;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        if (!this.focused) {
            stopListening();
        }
        int ww = wellWidth();
        theme.frame().field(d, p, this.x, this.y, ww, this.h, this.focused);
        String name = this.binding.keyName();
        int color = !this.binding.conflicts().isEmpty() ? p.bad() : theme.frame().fieldText(p);
        String shown = name;
        if (this.listening) {
            shown = "> " + (name == null || name.isEmpty() ? this.words.get("minuit.keybind.press") : name) + " <";
            color = Ink.readable(p, DarkTwin.of(p).markInk(), Ink.seen(p, FieldInk.face(p)), Ink.TEXT);
        }
        int ty = this.y + (this.h - 8) / 2;
        String fit = d.ellipsize(shown, ww - 6);
        d.text(fit, this.x + (ww - d.width(fit)) / 2, ty, color);
        this.reset.x = this.x + this.w - this.h;
        this.reset.y = this.y;
        this.reset.w = this.h;
        this.reset.h = this.h;
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
        KeyCodes codes = Platform.keyCodes();
        if (key.kind() == KeyPress.Kind.KEY && key.code() == codes.escape()) {
            this.binding.unbind();
            done();
            return true;
        }
        if (key.kind() == KeyPress.Kind.KEY && codes.modifier(key.code()) != KeyPress.Modifier.NONE) {
            this.heldModifiers.add(key.code());
            return true;
        }
        this.binding.bind(this.binding.supportsModifiers() ? key.with(heldMask(codes, -1)) : key);
        done();
        return true;
    }

    /** The mask of the modifier keys down, without the key {@code except}, cut to what the binding can store. */
    private int heldMask(KeyCodes codes, int except) {
        int mask = 0;
        for (int code : this.heldModifiers) {
            if (code != except) {
                mask |= codes.modifier(code).bit();
            }
        }
        if (!this.binding.supportsMultipleModifiers()) {
            mask = Integer.lowestOneBit(mask & KeyPress.ONE_MODIFIER_HOSTS);
        }
        return mask;
    }

    @Override
    public boolean keyReleasedCaptured(KeyPress key) {
        if (!this.listening || key.kind() != KeyPress.Kind.KEY || !this.heldModifiers.contains(key.code())) {
            return false;
        }
        int others = this.binding.supportsModifiers() ? heldMask(Platform.keyCodes(), key.code()) : 0;
        this.binding.bind(key.with(others));
        done();
        return true;
    }

    private void done() {
        stopListening();
        this.onChange.run();
    }

    private void stopListening() {
        this.listening = false;
        this.heldModifiers.clear();
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
