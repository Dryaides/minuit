package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.ToastArt;
import dev.dryaides.minuit.ToastSpec;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.internal.ToastTimer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;

/**
 * A minuit toast in the game's toast stack, drawn by {@link ToastArt} in the mod's theme as it is each
 * frame, so a theme change applies to a toast on screen. The stack plays the in and out sounds and
 * scales the time by the player's Notification Time option. Client only, not API.
 */
public final class ThemedToast implements Toast {

    private final String modId;
    private ToastSpec toast;
    private final ToastTimer timer = new ToastTimer();

    private ThemedToast(String modId, ToastSpec toast) {
        this.modId = modId;
        this.toast = toast;
    }

    /** On the client thread: adds {@code toast}, or replaces the text of the one on screen with its key. */
    public static void show(String modId, ToastSpec toast) {
        ToastComponent toasts = GameScreens.toasts();
        if (toast.key() != null) {
            ThemedToast shown = toasts.getToast(ThemedToast.class, token(modId, toast.key()));
            if (shown != null) {
                shown.toast = toast;
                shown.timer.restart();
                return;
            }
        }
        toasts.addToast(new ThemedToast(modId, toast));
    }

    private static String token(String modId, String key) {
        return modId + "/" + key;
    }

    @Override
    public Object getToken() {
        return this.toast.key() == null ? NO_TOKEN : token(this.modId, this.toast.key());
    }

    /** One call per frame draws and answers whether to stay: the stack has no separate update here. */
    @Override
    public Toast.Visibility render(GuiGraphics graphics, ToastComponent toasts, long visibleForMs) {
        ToastArt.draw(new GuiDraw(graphics), Themes.byId(this.modId), this.toast, 0, 0, this.timer.age(visibleForMs));
        return this.timer.visible(visibleForMs, this.toast.millis(), toasts.getNotificationDisplayTimeMultiplier())
                ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }
}
