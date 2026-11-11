package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ItemDropTarget;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.Slide;
import dev.dryaides.minuit.internal.ScreenLogic;
import dev.dryaides.minuit.port.mc.GameInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The body {@link MinuitScreen} shares between 1.21.1 and 1.20.1; each version adds only what differs. */
abstract class MinuitScreenBody extends Screen {

    protected PanelComponent panel;
    protected final ScreenHost host = new ScreenHost(this);

    {
        this.host.themeTab(this::showsThemeTab);
    }
    final ScreenLogic logic = new ScreenLogic(this.host);

    MinuitScreenBody(Component title) {
        super(title);
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial);
        if (this.panel != null) {
            this.logic.draw(new GuiDraw(graphics), this.panel, mouseX, mouseY);
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
