package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.TextMeasure;
import dev.dryaides.minuit.form.Form;
import dev.dryaides.minuit.form.Role;
import dev.dryaides.minuit.form.Text;
import dev.dryaides.minuit.internal.formui.FormPanel;

/** A server form's buttons in their four roles, as the client draws them from the wire. */
public final class FormRolesDemo implements Demo {
    @Override
    public void states(Stage stage) {
        stage.state("roles", 320, 140, s -> {
            Form form = Form.custom("bench:roles").title(Text.lit("Roles"))
                    .row(r -> r.button("hero", Text.lit("Hero"), Role.HERO)
                            .button("primary", Text.lit("Primary"), Role.PRIMARY))
                    .row(r -> r.button("secondary", Text.lit("Secondary"), Role.SECONDARY)
                            .button("destructive", Text.lit("Destroy"), Role.DESTRUCTIVE))
                    .build();
            FormPanel panel = FormPanel.of(s.theme(), s.words(), TextMeasure::width, 320, 140, form.open(1), in -> { });
            s.paint((d, t) -> panel.draw(d, -1, -1, 0F));
        });
    }
}
