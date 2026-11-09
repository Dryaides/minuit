package dev.dryaides.minuit.client;

import dev.dryaides.minuit.internal.ContainerLogic;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Puts a kit screen in place of the vanilla screen the game opens for a menu, from its first frame:
 * same menu, same container id, the server sees nothing. The factory gets the menu and the vanilla
 * screen it replaces, which the kit screen can hand back ({@link MinuitContainerScreen#toVanilla()})
 * without closing the menu; it returns null to keep the vanilla screen.
 *
 * <pre>{@code
 * MinuitMenuScreens.over((menu, vanilla) -> menu instanceof MerchantMenu m ? new TradeScreen(m, vanilla) : null);
 * }</pre>
 *
 * <p>Client only. Register from the client entry point.
 */
public final class MinuitMenuScreens {

    private static final List<BiFunction<AbstractContainerMenu, Screen, Screen>> FACTORIES = new CopyOnWriteArrayList<>();
    /** The menu whose vanilla screen is being handed back; that one opening stays vanilla. */
    private static AbstractContainerMenu handedBack;

    private MinuitMenuScreens() {
    }

    public static void over(BiFunction<AbstractContainerMenu, Screen, Screen> factory) {
        FACTORIES.add(factory);
    }

    /** From the screen-opening hook: the screen to open in place of {@code next}. */
    public static Screen swap(Screen next) {
        if (!(next instanceof MenuAccess<?>) || next instanceof MinuitContainerScreen<?> || FACTORIES.isEmpty()) {
            return next;
        }
        AbstractContainerMenu menu = ((MenuAccess<?>) next).getMenu();
        if (menu == handedBack) {
            handedBack = null;
            return next;
        }
        for (BiFunction<AbstractContainerMenu, Screen, Screen> factory : FACTORIES) {
            Screen kit = factory.apply(menu, next);
            if (kit != null) {
                return kit;
            }
        }
        return next;
    }

    /** What a key does on a kit screen over a menu; {@code pressed} tells whether the key is a given mapping. */
    static ContainerLogic.OverKey overKey(Predicate<KeyMapping> pressed, boolean hasVanilla, boolean closesOnMovement) {
        Options o = Minecraft.getInstance().options;
        boolean moves = pressed.test(o.keyUp) || pressed.test(o.keyDown) || pressed.test(o.keyLeft)
                || pressed.test(o.keyRight) || pressed.test(o.keyJump);
        return ContainerLogic.overKey(pressed.test(o.keyInventory), moves, hasVanilla, closesOnMovement);
    }

    /** Lets the vanilla screen of {@code menu} open as it is, for the switch back from the kit. */
    static void handBack(AbstractContainerMenu menu) {
        handedBack = menu;
    }
}
