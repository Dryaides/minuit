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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A plain screen hosting one {@link PanelComponent}: everything the panel cannot do without the game, which
 * is the mouse, the keyboard, the clipboard, the tick, the sound cues, the frame clock and the theme tab ({@link ScreenHost}). A mod's own screen extends it and
 * builds its panel in {@link #build}; it never draws anything itself.
 *
 * <p>Client only.
 */
public abstract class MinuitScreen extends Screen {

    /** A screen that takes a fresh page from the server while it stays open on the same thing. */
    public interface Refreshable<P> extends ScreenSlot.Page<P> {

        @Override
        boolean isSame(P page);

        void refresh(P page);
    }

    /**
     * The one open screen of a kind, so a fresh page finds it without asking Minecraft which screen
     * is up: the answer differs between 26.1.2 and 26.2. Set in {@code init},
     * cleared in {@code removed}, both on the render thread.
     */
    public static final class Open<P, S extends Refreshable<P>> extends ScreenSlot<P, S> {

        // Overridden so the descriptors stay on Refreshable, as before the cut.
        @Override
        public void set(S opened) {
            super.set(opened);
        }

        @Override
        public void clear(S closing) {
            super.clear(closing);
        }

        /** The open screen, or null. */
        @Override
        public S get() {
            return super.get();
        }

        /** The open screen when it shows the same thing as {@code page}, or null. */
        @Override
        public S showing(P page) {
            return super.showing(page);
        }
    }

    protected PanelComponent panel;
    protected final ScreenHost host = new ScreenHost(this);

    {
        this.host.themeTab(this::showsThemeTab);
    }
    private final ScreenLogic logic = new ScreenLogic(this.host);

    protected MinuitScreen(Component title) {
        super(title);
    }

    protected MinuitScreen(String titleKey) {
        super(Component.translatable(titleKey));
    }

    /** The player's language, for a panel's texts. */
    protected static Words words() {
        return I18n::get;
    }

    /** Runs {@code task} on the render thread, where a packet handler opens or updates a screen. */
    protected static void onGameThread(Runnable task) {
        Platform.screens().onGameThread(task);
    }

    /** Opens {@code screen}, or closes the current one for null, the way this game version wants it; call this rather than {@code Minecraft.setScreen}. */
    public static void show(@org.jetbrains.annotations.Nullable Screen screen) {
        Minecraft.getInstance().setScreenAndShow(screen);
    }

    /**
     * Opens {@code screen} with its panel gliding in from {@code from} ({@link Slide}): a menu tab that
     * opens another screen. A screen that is not a {@code MinuitScreen} opens at once.
     */
    public static void show(Screen screen, Slide from) {
        if (screen instanceof MinuitScreen next) {
            next.logic.slideFrom(from);
        }
        show(screen);
    }

    protected static boolean singleplayer() {
        return Platform.screens().singleplayer();
    }

    /** False hides the theme tab beside the panel; read on every frame and click. */
    protected boolean showsThemeTab() {
        return true;
    }

    /** Called on every {@code init}, which the game runs again on a resize. */
    protected abstract PanelComponent build(int screenWidth, int screenHeight);

    @Override
    protected void init() {
        super.init();
        this.host.init();
        this.panel = build(this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        if (this.panel != null) {
            this.logic.draw(new GuiDraw(graphics), this.panel, mouseX, mouseY, MinuitClient.background(), this.width, this.height);
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
    public void tick() {
        super.tick();
        this.logic.tick(this.panel);
        if (GamepadPoll.tick(this.host, this.panel)) {
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return this.logic.mouseClicked(this.panel, event.x(), event.y(), event.button())
                || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.logic.mouseReleased(this.panel, event.x(), event.y(), event.button());
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.logic.mouseDragged(this.panel, event.x(), event.y(), event.button(), dx, dy)
                || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.logic.mouseScrolled(this.panel, mouseX, mouseY, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.logic.keyPressed(this.panel, Platform.screens().stroke(event)) || super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return this.logic.keyReleased(this.panel, Platform.screens().stroke(event)) || super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return this.logic.charTyped(this.panel, event.codepoint()) || super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (!this.logic.closeOverlay(this.panel)) {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        this.logic.removed(this.panel);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
