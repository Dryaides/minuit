package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ItemDropTarget;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.Slide;
import dev.dryaides.minuit.Words;
import dev.dryaides.minuit.internal.ScreenLogic;
import dev.dryaides.minuit.internal.ScreenSlot;
import dev.dryaides.minuit.internal.port.Platform;
import dev.dryaides.minuit.port.mc.GameInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * A plain screen hosting one {@link PanelComponent}: everything the panel cannot do without the game, which
 * is the mouse, the keyboard, the clipboard, the tick, the sound cues, the frame clock and the theme tab
 * ({@link ScreenHost}). A mod's own screen extends it and builds its panel in {@link #build}; it never draws
 * anything itself.
 *
 * <p>1.12.2 hands a key and its char in one {@code keyTyped} call and the wheel through
 * {@code handleMouseInput}; this class splits them into the key press, typed char and wheel notches the
 * panel takes. Escape goes through {@link #onClose}, which a screen overrides to go back somewhere.
 *
 * <p>Client only.
 */
public abstract class MinuitScreen extends GuiScreen {

    /** A screen that takes a fresh page from the server while it stays open on the same thing. */
    public interface Refreshable<P> extends ScreenSlot.Page<P> {

        @Override
        boolean isSame(P page);

        void refresh(P page);
    }

    /**
     * The one open screen of a kind, so a fresh page finds it without asking Minecraft which screen
     * is up. Set in {@code initGui}, cleared in {@code onGuiClosed}, both on the render thread.
     */
    public static final class Open<P, S extends Refreshable<P>> extends ScreenSlot<P, S> {
    }

    protected PanelComponent panel;
    protected final ScreenHost host = new ScreenHost(this);

    {
        this.host.themeTab(this::showsThemeTab);
    }
    final ScreenLogic logic = new ScreenLogic(this.host);

    /** The screen's title, already translated; 1.12.2 screens carry none of their own. */
    protected final String title;

    private int lastX;
    private int lastY;

    protected MinuitScreen(String titleKey) {
        this.title = I18n.format(titleKey);
    }

    void slideFrom(Slide from) {
        this.logic.slideFrom(from);
    }

    /** False hides the theme tab beside the panel; read on every frame and click. */
    protected boolean showsThemeTab() {
        return true;
    }

    /** Called on every {@code initGui}, which the game runs again on a resize. */
    protected abstract PanelComponent build(int screenWidth, int screenHeight);

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.host.init();
        this.panel = build(this.width, this.height);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partial);
        if (this.panel != null) {
            this.logic.draw(new GuiDraw(), this.panel, mouseX, mouseY);
        }
    }

    /**
     * The item under the mouse in the panel (a display slot, a picker cell, an item icon), or null: what
     * a recipe viewer plugin reads for its R and U keys and bookmarks.
     */
    public ItemView itemUnderMouse() {
        return this.logic.itemUnderMouse(this.panel);
    }

    /** The screen rectangles of the panel, its windows and its open overlay, for a recipe viewer's exclusion zones. */
    public List<Box> extraAreas() {
        return this.logic.extraAreas(this.panel);
    }

    /** Where the panel takes an item dropped from a recipe viewer, screen coordinates. */
    public List<ItemDropTarget.Drop> dropTargets() {
        return this.logic.dropTargets(this.panel);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.logic.tick(this.panel);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        this.lastX = mouseX;
        this.lastY = mouseY;
        if (!this.logic.mouseClicked(this.panel, mouseX, mouseY, button)) {
            super.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        this.logic.mouseReleased(this.panel, mouseX, mouseY, button);
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long held) {
        int dx = mouseX - this.lastX;
        int dy = mouseY - this.lastY;
        this.lastX = mouseX;
        this.lastY = mouseY;
        if (!this.logic.mouseDragged(this.panel, mouseX, mouseY, button, dx, dy)) {
            super.mouseClickMove(mouseX, mouseY, button, held);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            this.logic.mouseScrolled(this.panel, x, y, GameInput.wheel(wheel));
        }
    }

    @Override
    protected void keyTyped(char typed, int key) throws IOException {
        boolean used = this.logic.keyPressed(this.panel, GameInput.stroke(key));
        // GLFW sends the char as its own event whether or not the key press was used.
        used |= GameInput.typed(typed) && this.logic.charTyped(this.panel, typed);
        if (used) {
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            onClose();
        } else {
            super.keyTyped(typed, key);
        }
    }

    /** Escape: closes the screen; a screen opened over another one goes back to it instead. */
    public void onClose() {
        show(null);
    }

    @Override
    public final void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        removed();
        super.onGuiClosed();
    }

    /** The screen goes away, closed or replaced; the modern name, so screens shared across versions keep it. */
    public void removed() {
        this.logic.removed(this.panel);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** The player's language, for a panel's texts. */
    protected static Words words() {
        return I18n::format;
    }

    /** Runs {@code task} on the render thread, where a packet handler opens or updates a screen. */
    protected static void onGameThread(Runnable task) {
        Platform.screens().onGameThread(task);
    }

    /** Opens {@code screen}, or closes the current one for null. */
    protected static void show(@Nullable GuiScreen screen) {
        Minecraft.getMinecraft().displayGuiScreen(screen);
    }

    /**
     * Opens {@code screen} with its panel gliding in from {@code from} ({@link Slide}): a menu tab that
     * opens another screen. A screen that is not a {@code MinuitScreen} opens at once.
     */
    protected static void show(GuiScreen screen, Slide from) {
        if (screen instanceof MinuitScreen) {
            ((MinuitScreen) screen).slideFrom(from);
        }
        show(screen);
    }

    protected static boolean singleplayer() {
        return Platform.screens().singleplayer();
    }
}
