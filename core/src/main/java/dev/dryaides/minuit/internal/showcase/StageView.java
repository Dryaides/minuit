package dev.dryaides.minuit.internal.showcase;

import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.internal.Inspector;

import java.util.List;

/**
 * One {@link SiteScenes.Stage} as a widget: drawn in the middle of its box, shrunk only when the stage is larger
 * than the box, and fed the mouse and the keyboard in the stage's own pixels. A click the stage does not use runs
 * {@code missed} (a home card opening its component). The tooltip goes to the panel, which draws it unscaled.
 */
final class StageView extends BaseComponent implements Inspector.Host {

    private static final int AWAY = -10_000;

    private final SiteScenes.Stage stage;
    private final Runnable missed;

    StageView(Box box, SiteScenes.Stage stage, Runnable missed) {
        super(box);
        this.stage = stage;
        this.missed = missed;
    }

    private float scale() {
        return Math.min(1F, Math.min(this.w / (float) this.stage.w, this.h / (float) this.stage.h));
    }

    private float left() {
        return this.x + (this.w - this.stage.w * scale()) / 2F;
    }

    private float top() {
        return this.y + Math.max(0F, (this.h - this.stage.h * scale()) / 2F);
    }

    private double inX(double mx) {
        return (mx - Math.round(left())) / scale();
    }

    private double inY(double my) {
        return (my - Math.round(top())) / scale();
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        boolean in = isOver(mx, my);
        d.scissor(this.x, this.y, this.x + this.w, this.y + this.h);
        d.push(Math.round(left()), Math.round(top()), scale());
        this.stage.drawBody(d, in ? (int) Math.floor(inX(mx)) : AWAY, in ? (int) Math.floor(inY(my)) : AWAY);
        d.pop();
        d.endScissor();
    }

    @Override
    public void inspect(Inspector.Walk walk) {
        this.stage.inspect(walk, Math.round(left()), Math.round(top()), scale());
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        return isOver(mx, my) ? this.stage.tooltipAt(inX(mx), inY(my)) : null;
    }

    @Override
    public void tick() {
        this.stage.tick();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.visible || !isOver(mx, my)) {
            return false;
        }
        if (this.stage.click(inX(mx), inY(my), button)) {
            return true;
        }
        if (this.missed != null && button == 0) {
            click();
            this.missed.run();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.stage.release(inX(mx), inY(my), button);
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        this.stage.drag(inX(mx), inY(my), button, dx / scale(), dy / scale());
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return isOver(mx, my) && this.stage.scroll(inX(mx), inY(my), amount);
    }

    @Override
    protected boolean takesFocus() {
        return true;
    }

    @Override
    protected boolean passesKeys() {
        return true;
    }

    @Override
    protected boolean capturesKeys() {
        return this.stage.capturing();
    }

    @Override
    public boolean keyCaptured(KeyPress key) {
        return this.stage.rawKey(key);
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (key == InputKey.TAB) {
            return this.stage.tab(shift);
        }
        return this.stage.key(key, shift);
    }

    @Override
    public boolean charTyped(int codepoint) {
        return this.stage.typed(codepoint);
    }
}
