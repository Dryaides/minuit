package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.ToastArt;
import dev.dryaides.minuit.ToastSpec;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.internal.ToastTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;

/**
 * A minuit toast in the game's toast stack, drawn by {@link ToastArt} in the mod's theme as it is each
 * frame, so a theme change applies to a toast on screen. The stack plays the in and out sounds; 1.12.2
 * has no Notification Time option, so the time runs as given. Client only, not API.
 */
public final class ThemedToast implements IToast {

    private final String modId;
    private ToastSpec toast;
    private final ToastTimer timer = new ToastTimer();

    private ThemedToast(String modId, ToastSpec toast) {
        this.modId = modId;
        this.toast = toast;
    }

    /** On the client thread: adds {@code toast}, or replaces the text of the one on screen with its key. */
    public static void show(String modId, ToastSpec toast) {
        GuiToast toasts = Minecraft.getMinecraft().getToastGui();
        if (toast.key() != null) {
            ThemedToast shown = toasts.getToast(ThemedToast.class, token(modId, toast.key()));
            if (shown != null) {
                shown.toast = toast;
                shown.timer.restart();
                return;
            }
        }
        toasts.add(new ThemedToast(modId, toast));
    }

    private static String token(String modId, String key) {
        return modId + "/" + key;
    }

    @Override
    public Object getType() {
        return this.toast.key() == null ? NO_TOKEN : token(this.modId, this.toast.key());
    }

    /** One call per frame draws and answers whether to stay: the stack has no separate update here. */
    @Override
    public IToast.Visibility draw(GuiToast toasts, long visibleForMs) {
        ToastArt.draw(new GuiDraw(), Themes.byId(this.modId), this.toast, 0, 0, this.timer.age(visibleForMs));
        return this.timer.visible(visibleForMs, this.toast.millis(), 1D)
                ? IToast.Visibility.SHOW : IToast.Visibility.HIDE;
    }
}
