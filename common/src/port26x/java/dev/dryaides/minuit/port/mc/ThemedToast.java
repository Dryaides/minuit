package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.ToastArt;
import dev.dryaides.minuit.ToastSpec;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.internal.ToastTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

/**
 * A minuit toast in the game's toast stack, drawn by {@link ToastArt} in the mod's theme as it is each
 * frame, so a theme change applies to a toast on screen. The stack plays the in and out sounds and
 * scales the time by the player's Notification Time option. Client only, not API.
 */
public final class ThemedToast implements Toast {

    private final String modId;
    private ToastSpec toast;
    private final ToastTimer timer = new ToastTimer();
    private Toast.Visibility wanted = Toast.Visibility.SHOW;

    private ThemedToast(String modId, ToastSpec toast) {
        this.modId = modId;
        this.toast = toast;
    }

    /** On the client thread: adds {@code toast}, or replaces the text of the one on screen with its key. */
    public static void show(String modId, ToastSpec toast) {
        ToastManager manager = GameScreens.toasts();
        if (toast.key() != null) {
            ThemedToast shown = manager.getToast(ThemedToast.class, token(modId, toast.key()));
            if (shown != null) {
                shown.toast = toast;
                shown.timer.restart();
                return;
            }
        }
        manager.addToast(new ThemedToast(modId, toast));
    }

    private static String token(String modId, String key) {
        return modId + "/" + key;
    }

    @Override
    public Object getToken() {
        return this.toast.key() == null ? NO_TOKEN : token(this.modId, this.toast.key());
    }

    @Override
    public Toast.Visibility getWantedVisibility() {
        return this.wanted;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        this.wanted = this.timer.visible(fullyVisibleForMs, this.toast.millis(),
                manager.getNotificationDisplayTimeMultiplier()) ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        ToastArt.draw(new GuiDraw(graphics), Themes.byId(this.modId), this.toast, 0, 0,
                this.timer.age(fullyVisibleForMs));
    }
}
