package dev.dryaides.minuit.client;

import dev.dryaides.minuit.internal.ContainerLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.Container;
import net.minecraftforge.client.event.GuiOpenEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Puts a kit screen in place of the vanilla screen the game opens for a container, from its first frame:
 * same container, same window id, the server sees nothing. The factory gets the container and the vanilla
 * screen it replaces, which the kit screen can hand back ({@link MinuitContainerScreen#toVanilla()})
 * without closing the container; it returns null to keep the vanilla screen.
 *
 * <pre>{@code
 * MinuitMenuScreens.over((menu, vanilla) -> menu instanceof ContainerMerchant ? new TradeScreen((ContainerMerchant) menu, vanilla) : null);
 * }</pre>
 *
 * <p>Client only. Register from the client entry point.
 */
public final class MinuitMenuScreens {

    private static final List<BiFunction<Container, GuiScreen, GuiScreen>> FACTORIES = new CopyOnWriteArrayList<>();
    /** The container whose vanilla screen is being handed back; that one opening stays vanilla. */
    private static Container handedBack;

    private MinuitMenuScreens() {
    }

    public static void over(BiFunction<Container, GuiScreen, GuiScreen> factory) {
        FACTORIES.add(factory);
    }

    /** Forge's screen-opening event, the hook 1.12.2 has instead of a mixin on {@code displayGuiScreen}. */
    public static void onOpen(GuiOpenEvent event) {
        GuiScreen next = event.getGui();
        GuiScreen swapped = swap(next);
        if (swapped != next) {
            event.setGui(swapped);
        }
    }

    /** The screen to open in place of {@code next}. */
    public static GuiScreen swap(GuiScreen next) {
        if (!(next instanceof GuiContainer) || next instanceof MinuitContainerScreen<?> || FACTORIES.isEmpty()) {
            return next;
        }
        Container menu = ((GuiContainer) next).inventorySlots;
        if (menu == handedBack) {
            handedBack = null;
            return next;
        }
        for (BiFunction<Container, GuiScreen, GuiScreen> factory : FACTORIES) {
            GuiScreen kit = factory.apply(menu, next);
            if (kit != null) {
                return kit;
            }
        }
        return next;
    }

    /** What a key does on a kit screen over a container; {@code pressed} tells whether the key is a given binding. */
    static ContainerLogic.OverKey overKey(Predicate<KeyBinding> pressed, boolean hasVanilla, boolean closesOnMovement) {
        GameSettings o = Minecraft.getMinecraft().gameSettings;
        boolean moves = pressed.test(o.keyBindForward) || pressed.test(o.keyBindBack) || pressed.test(o.keyBindLeft)
                || pressed.test(o.keyBindRight) || pressed.test(o.keyBindJump);
        return ContainerLogic.overKey(pressed.test(o.keyBindInventory), moves, hasVanilla, closesOnMovement);
    }

    /** Lets the vanilla screen of {@code menu} open as it is, for the switch back from the kit. */
    static void handBack(Container menu) {
        handedBack = menu;
    }
}
