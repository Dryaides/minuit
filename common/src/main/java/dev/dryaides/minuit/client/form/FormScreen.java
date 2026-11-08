package dev.dryaides.minuit.client.form;

import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.TextMeasure;
import dev.dryaides.minuit.client.MinuitScreen;
import dev.dryaides.minuit.formui.ClientForms;
import dev.dryaides.minuit.formui.FormClient;
import dev.dryaides.minuit.formui.FormPanel;
import dev.dryaides.minuit.formui.FormScreenState;
import dev.dryaides.minuit.internal.port.Platform;
import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.ScreenMessage;

import java.util.function.Consumer;

/**
 * The screen of one form. The panel is made once per open and laid out again on every
 * {@code init}, so a resize or a theme change keeps its values. Escape closes it as the player's;
 * the theme tab opens the theme page of the sending mod and comes back to the form; any other
 * screen opening over it replaces it. Not API.
 *
 * <p>Client only.
 */
public final class FormScreen extends MinuitScreen {

    /** The forms' host: the loader client entry hands it to {@link ClientForms#connect}. */
    public static final FormClient.Host HOST = new FormClient.Host() {
        @Override
        public FormPanel show(ScreenMessage.Open open, Consumer<InputMessage> out) {
            FormPanel panel = FormPanel.of(FormClient.theme(open.screenId()), words(), TextMeasure.font,
                    Platform.screens().guiWidth(), Platform.screens().guiHeight(), open, out);
            MinuitScreen.show(new FormScreen(panel));
            return panel;
        }

        @Override
        public void hide(FormPanel panel) {
            if (FormScreenState.close(panel, up != null && up.host.away())) {
                MinuitScreen.show(null);
            }
        }
    };

    /** The form screen up, or away on the theme page, or null: tells whether it is away. */
    private static FormScreen up;

    private final FormScreenState state;

    private FormScreen(FormPanel form) {
        super(form.screenId());
        this.state = new FormScreenState(form);
    }

    @Override
    protected PanelComponent build(int screenWidth, int screenHeight) {
        up = this;
        if (this.state.built()) {
            onGameThread(() -> show(null));
            return null;
        }
        FormPanel form = this.state.form();
        form.fit(FormClient.theme(form.screenId()), screenWidth, screenHeight);
        return form;
    }

    @Override
    public void onClose() {
        this.state.closedByPlayer();
        super.onClose();
    }

    @Override
    public void removed() {
        if (this.host.away()) {
            // Off to the theme page, which comes back here: the form stays open on the server.
            this.panel = null;
            super.removed();
            return;
        }
        if (up == this) {
            up = null;
        }
        if (this.state.removed(ClientForms.client())) {
            this.panel = null;
        }
        super.removed();
    }
}
