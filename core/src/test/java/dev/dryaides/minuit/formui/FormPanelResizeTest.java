package dev.dryaides.minuit.formui;

import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.form.Form;
import dev.dryaides.minuit.form.Role;
import dev.dryaides.minuit.form.Text;
import dev.dryaides.minuit.internal.ThemeFiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A window resized under an open form: the panel follows the new screen and keeps what the player typed. */
class FormPanelResizeTest {

    @Test
    void aResizedFormLaysOutOnTheNewScreenAndKeepsItsText() throws Exception {
        Themes.load(ThemeFiles.assemble(ThemeFiles.fromDirectory(
                Path.of("src/main/resources/assets/minuit/minuit"), "minuit"), s -> { }));
        Form form = Form.custom("example:shop").title(Text.lit("Shop"))
                .field("name", Text.lit("Name"), f -> f.maxLength(16))
                .footer(r -> r.button("save", Text.lit("Save"), Role.PRIMARY))
                .build();
        FormPanel panel = FormPanel.of(Themes.SLATE, (key, a) -> key, s -> 6 * s.length(), 427, 240,
                form.open(1), in -> { });
        BaseComponent field = panel.widget("name");
        panel.mouseClicked(field.x + 5, field.y + 5, 0);
        panel.charTyped('Z');

        panel.fit(Themes.SLATE, 216, 240);

        // A default-size form is 300 wide, capped at the screen less 16, and centred.
        assertEquals(200, panel.width());
        assertEquals(8, panel.bounds().x());
        assertEquals("Z", ((TextFieldComponent) panel.widget("name")).text());
    }
}
