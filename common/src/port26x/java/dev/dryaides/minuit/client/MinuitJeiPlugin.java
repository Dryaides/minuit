package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Internal;
import java.util.ArrayList;
import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import dev.dryaides.minuit.Box;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

/**
 * JEI's view of a kit container screen. When {@code showsContainerAddons} is false the gui fills the screen,
 * so JEI finds no room for its item list, search bar or bookmark buttons; otherwise null hands the screen to
 * JEI's own container handler, and the panel's parts outside the container box are JEI's exclusion zones.
 * Found by JEI only ({@code @JeiPlugin}, Fabric entrypoint {@code jei_mod_plugin}).
 */
@Internal
@JeiPlugin
public final class MinuitJeiPlugin implements IModPlugin {

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("minuit", "viewers");
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler((Class) MinuitContainerScreen.class,
                screen -> ((MinuitContainerScreen<?>) screen).host.viewers().showsAddons() ? null : filling((Screen) screen));
        registration.addGuiContainerHandler((Class) MinuitContainerScreen.class, new IGuiContainerHandler() {
            @Override
            public List<Rect2i> getGuiExtraAreas(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen screen) {
                List<Rect2i> areas = new ArrayList<>();
                for (Box box : ((MinuitContainerScreen<?>) screen).extraAreas()) {
                    areas.add(new Rect2i(box.x(), box.y(), box.w(), box.h()));
                }
                return areas;
            }
        });
    }

    private static IGuiProperties filling(Screen screen) {
        int width = Math.max(1, screen.width);
        int height = Math.max(1, screen.height);
        return new IGuiProperties() {
            @Override public Class<? extends Screen> screenClass() { return screen.getClass(); }
            @Override public int guiLeft() { return 0; }
            @Override public int guiTop() { return 0; }
            @Override public int guiXSize() { return width; }
            @Override public int guiYSize() { return height; }
            @Override public int screenWidth() { return width; }
            @Override public int screenHeight() { return height; }
        };
    }
}
