package dev.dryaides.minuit.client;

import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.Slide;
import dev.dryaides.minuit.Words;
import dev.dryaides.minuit.internal.ScreenSlot;
import dev.dryaides.minuit.internal.port.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * A plain screen hosting one {@link PanelComponent}: everything the panel cannot do without the game, which
 * is the mouse, the keyboard, the clipboard, the tick, the sound cues, the frame clock and the theme tab ({@link ScreenHost}). A mod's own screen extends it and
 * builds its panel in {@link #build}; it never draws anything itself.
 *
 * <p>Client only.
 */
public abstract class MinuitScreen extends MinuitScreenBody {

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

    /** Opens {@code screen}, or closes the current one for null. */
    protected static void show(@org.jetbrains.annotations.Nullable Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    /**
     * Opens {@code screen} with its panel gliding in from {@code from} ({@link Slide}): a menu tab that
     * opens another screen. A screen that is not a {@code MinuitScreen} opens at once.
     */
    protected static void show(Screen screen, Slide from) {
        if (screen instanceof MinuitScreen next) {
            next.slideFrom(from);
        }
        show(screen);
    }

    protected static boolean singleplayer() {
        return Platform.screens().singleplayer();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.logic.mouseScrolled(this.panel, mouseX, mouseY, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
