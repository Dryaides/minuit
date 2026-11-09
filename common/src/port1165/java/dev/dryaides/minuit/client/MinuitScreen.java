package dev.dryaides.minuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
import dev.dryaides.minuit.port.mc.GameScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
     * is up: the answer differs between Minecraft versions. Set in {@code init},
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
    final ScreenLogic logic = new ScreenLogic(this.host);

    protected MinuitScreen(Component title) {
        super(title);
    }

    protected MinuitScreen(String titleKey) {
        super(GameScreens.translatable(titleKey));
    }

    void slideFrom(Slide from) {
        this.logic.slideFrom(from);
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
    public void render(PoseStack pose, int mouseX, int mouseY, float partial) {
        // Screen.render draws no background here: each screen asks for it.
        this.renderBackground(pose);
        super.render(pose, mouseX, mouseY, partial);
        if (this.panel != null) {
            this.logic.draw(new GuiDraw(pose), this.panel, mouseX, mouseY);
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
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.logic.mouseClicked(this.panel, mouseX, mouseY, button)
                || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.logic.mouseReleased(this.panel, mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return this.logic.mouseDragged(this.panel, mouseX, mouseY, button, dx, dy)
                || super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        return this.logic.keyPressed(this.panel, GameInput.stroke(key, scancode))
                || super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean charTyped(char typed, int modifiers) {
        return this.logic.charTyped(this.panel, typed) || super.charTyped(typed, modifiers);
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

    /** The player's language, for a panel's texts. */
    protected static Words words() {
        return I18n::get;
    }

    /** Runs {@code task} on the render thread, where a packet handler opens or updates a screen. */
    protected static void onGameThread(Runnable task) {
        Platform.screens().onGameThread(task);
    }

    /** Opens {@code screen}, or closes the current one for null. */
    protected static void show(@org.jetbrains.annotations.Nullable Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    /**
     * Opens {@code screen} with its panel gliding in from {@code from} ({@link Slide}): a menu tab that
     * opens another screen. A screen that is not a {@code MinuitScreen} opens at once.
     */
    protected static void show(Screen screen, Slide from) {
        if (screen instanceof MinuitScreen) {
            ((MinuitScreen) screen).slideFrom(from);
        }
        show(screen);
    }

    protected static boolean singleplayer() {
        return Platform.screens().singleplayer();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scroll) {
        return this.logic.mouseScrolled(this.panel, mouseX, mouseY, scroll)
                || super.mouseScrolled(mouseX, mouseY, scroll);
    }
}
