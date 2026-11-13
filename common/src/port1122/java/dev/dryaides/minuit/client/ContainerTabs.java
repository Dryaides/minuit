package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Internal;
import dev.dryaides.minuit.internal.ContainerTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.input.Mouse;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * The theme tab on the game's container screens ({@link ContainerTab}) through Forge's screen events,
 * since 1.12.2 runs no mixins. Drawn with the dimmed background, under the container, which it does not
 * reach. The screen's {@code onGuiClosed} still runs on the way to the theme page and empties the
 * client's craft result slots (the server resends them only when the grid changes), so the tab keeps
 * them and puts them back when the screen initialises again; back from the page, {@code initGui} also
 * hands the player its container again. A click while carrying a stack is left to the
 * screen, so no stack drops on the way.
 */
@Internal
public final class ContainerTabs {

    private static WeakReference<GuiContainer> savedFor = new WeakReference<>(null);
    private static final Map<Integer, ItemStack> SAVED = new HashMap<>();

    private ContainerTabs() {
    }

    /** Puts back the craft results the screen emptied on its way to the theme page. */
    public static void initialised(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiContainer back = savedFor.get();
        if (back != null && back == event.getGui()) {
            for (Map.Entry<Integer, ItemStack> e : SAVED.entrySet()) {
                back.inventorySlots.getSlot(e.getKey()).putStack(e.getValue());
            }
        }
        if (back == event.getGui() || back == null) {
            SAVED.clear();
            savedFor = new WeakReference<>(null);
        }
    }

    private static void saveResults(GuiContainer gui) {
        SAVED.clear();
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot.inventory instanceof InventoryCraftResult && slot.getHasStack()) {
                SAVED.put(slot.slotNumber, slot.getStack().copy());
            }
        }
        savedFor = new WeakReference<>(gui);
    }

    public static void background(GuiScreenEvent.BackgroundDrawnEvent event) {
        GuiContainer gui = shown(event.getGui());
        if (gui != null) {
            Minecraft mc = gui.mc;
            ContainerTab.draw(gui, new GuiDraw(), 0, 0, gui.getGuiLeft(), gui.getGuiTop(),
                    Mouse.getX() * gui.width / mc.displayWidth, gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1);
        }
    }

    public static void drawn(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiContainer gui = shown(event.getGui());
        if (gui != null) {
            ContainerTab.drawTip(gui, new GuiDraw(), gui.getGuiLeft(), gui.getGuiTop(), event.getMouseX(), event.getMouseY());
        }
    }

    public static void mouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        GuiContainer gui = shown(event.getGui());
        if (gui == null || !Mouse.getEventButtonState() || Mouse.getEventButton() != 0
                || gui.mc.player == null || !gui.mc.player.inventory.getItemStack().isEmpty()) {
            return;
        }
        Minecraft mc = gui.mc;
        saveResults(gui);
        if (ContainerTab.click(gui, gui.getGuiLeft(), gui.getGuiTop(), Mouse.getEventX() * gui.width / mc.displayWidth,
                gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1)) {
            event.setCanceled(true);
        } else {
            SAVED.clear();
            savedFor = new WeakReference<>(null);
        }
    }

    /** minuit's own container screens draw their tab; the creative tabs fill the top edge. */
    private static GuiContainer shown(GuiScreen screen) {
        return screen instanceof GuiContainer && !(screen instanceof MinuitContainerScreen)
                && !(screen instanceof GuiContainerCreative) ? (GuiContainer) screen : null;
    }
}
