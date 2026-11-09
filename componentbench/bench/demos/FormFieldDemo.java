package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.CheckboxComponent;
import dev.dryaides.minuit.FormFieldComponent;
import dev.dryaides.minuit.FormSummaryComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.NumberFieldComponent;
import dev.dryaides.minuit.RadioGroupComponent;
import dev.dryaides.minuit.TextFieldComponent;
import java.util.List;

/** Words around a widget, and the summary of what is wrong. */
public final class FormFieldDemo implements Demo {
    private static final int W = 180;
    private static final int X = 8;
    private static final int Y = 6;
    private static final int FW = 164;

    private static TextFieldComponent text(Scene s, String value) {
        return s.add(new TextFieldComponent(new Box("t", 0, 0, FW, 16), value, v -> { }));
    }

    private static FormFieldComponent field(Scene s, String label, TextFieldComponent control) {
        return s.add(new FormFieldComponent(new Box("f", X, Y, FW, 0), s.panel(), label, control));
    }

    @Override
    public void states(Stage stage) {
        stage.state("label only", W, 40, s -> {
            TextFieldComponent t = text(s, "");
            FormFieldComponent f = field(s, "World name", t);
        });
        stage.state("required mark", W, 40, s -> {
            FormFieldComponent f = field(s, "World name", text(s, "")).required(true);
        });
        stage.state("description wraps", W, 70, s -> {
            FormFieldComponent f = field(s, "Seed", text(s, "")).description("Leave it empty for a random world. The same seed gives the same land.");
        });
        stage.state("error", W, 50, s -> {
            FormFieldComponent f = field(s, "World name", text(s, "")).required(true);
            f.setError("A world needs a name");
        });
        stage.state("description and error", W, 60, s -> {
            FormFieldComponent f = field(s, "Port", text(s, "99999")).description("Between 1024 and 65535.");
            f.setError("Too high");
        });
        stage.state("long error wraps", W, 74, s -> {
            FormFieldComponent f = field(s, "Port", text(s, "x"));
            f.setError("This message is long enough that it cannot stay on a single line of the field");
        });
        stage.state("reserved line moves nothing", W, 50, s -> {
            FormFieldComponent f = field(s, "Name", text(s, "")).reserveError(true);
            int before = f.height();
            f.setError("Now it shows");
        });
        stage.state("validate finds and clears", W, 50, s -> {
            TextFieldComponent t = text(s, "");
            FormFieldComponent f = field(s, "World name", t).required(true).validator(() -> t.text().isBlank() ? "A world needs a name" : null);
            f.validate();
            t.setText("Home");
            s.tick(1);
            t.setText("");
            s.tick(1);
            f.reset();
            s.tick(1);
            f.validate();
        });
        stage.state("click on the label focuses", W, 40, s -> {
            TextFieldComponent t = text(s, "Value");
            field(s, "Click me", t);
            s.click(X + 10, Y + 4);
            s.settle(0);
        });
        stage.state("disabled field and widget", W, 62, s -> {
            TextFieldComponent t = text(s, "Value");
            FormFieldComponent f = field(s, "World name", t).required(true).description("Locked by the server.").active(false);
            s.click(X + 10, Y + 4);
        });
        stage.state("hidden hides the widget", W, 40, s -> {
            TextFieldComponent t = text(s, "Value");
            FormFieldComponent f = field(s, "World name", t).show(false);
            f.show(true);
            f.show(false);
        });
        stage.state("long label is cut, mark stays", W, 60, s -> {
            String text = "A label far too long for the width of this little field";
            field(s, text, text(s, "")).required(true);
            s.move(X + 40, Y + 4);
            s.settle(1500);
        });
        stage.state("around a check box", W, 50, s -> {
            CheckboxComponent c = s.add(new CheckboxComponent(new Box("c", 0, 0, FW, 12), "I accept the rules", false, v -> { }));
            FormFieldComponent f = s.add(new FormFieldComponent(new Box("f", X, Y, FW, 0), s.panel(), "Rules", c).required(true));
            f.setError("Needed to go on");
        });
        stage.state("around radios", W, 70, s -> {
            RadioGroupComponent r = s.add(new RadioGroupComponent(new Box("r", 0, 0, FW, 28), List.of("Survival", "Creative"), 0, v -> { }));
            s.add(new FormFieldComponent(new Box("f", X, Y, FW, 0), s.panel(), "Mode", r).description("You can change it later."));
        });
        stage.state("around a number", W, 50, s -> {
            NumberFieldComponent n = s.add(new NumberFieldComponent(new Box("n", 0, 0, FW, 16), 0, 100, 42, v -> { }));
            FormFieldComponent f = s.add(new FormFieldComponent(new Box("f", X, Y, FW, 0), s.panel(), "Players", n));
            f.setError("The server allows 20");
        });
        stage.state("no label no widget", W, 34, s -> {
            FormFieldComponent f = s.add(new FormFieldComponent(new Box("f", X, Y, FW, 0), s.panel(), "", null).description("Only a line of words."));
            f.focusControl();
        });
        stage.state("whole form with a summary", W, 158, s -> form(s));
        stage.state("summary goes when fixed", W, 158, s -> {
            Form form = form(s);
            form.name.setText("Home");
            form.port.setValue(25565);
            s.tick(1);
            form.summary.validateAll();
        });
        stage.state("summary click goes to the field", W, 158, s -> {
            Form form = form(s);
            Box row = BenchAccess.rowBox(form.summary, 1);
            s.click(row.x() + 12, row.y() + 4);
            s.settle(0);
        });
        stage.state("summary keys", W, 158, s -> {
            Form form = form(s);
            for (int i = 0; i < 6 && s.panel().cursor() != form.summary; i++) {
                s.key(InputKey.TAB);
            }
            s.key(InputKey.DOWN);
        });
        stage.state("summary hover", W, 158, s -> {
            Form form = form(s);
            Box row = BenchAccess.rowBox(form.summary, 0);
            s.move(row.x() + 12, row.y() + 4);
        });
        stage.state("summary with more than fits", W, 60, s -> {
            FormFieldComponent a = s.add(new FormFieldComponent(new Box("a", -500, 0, 10, 0), s.panel(), "One", null));
            FormFieldComponent b = s.add(new FormFieldComponent(new Box("b", -500, 0, 10, 0), s.panel(), "Two", null));
            FormFieldComponent c = s.add(new FormFieldComponent(new Box("c", -500, 0, 10, 0), s.panel(), "Three", null));
            FormFieldComponent e = s.add(new FormFieldComponent(new Box("e", -500, 0, 10, 0), s.panel(), "Four", null));
            for (FormFieldComponent f : List.of(a, b, c, e)) {
                f.setError("Wrong");
            }
            FormSummaryComponent sum = s.add(new FormSummaryComponent(new Box("s", X, Y, FW, 44), s.panel(), List.of(a, b, c, e)));
            s.tick(1);
        });
        stage.state("summary title passed in", W, 40, s -> {
            FormFieldComponent a = s.add(new FormFieldComponent(new Box("a", -500, 0, 10, 0), s.panel(), "Nom", null));
            a.setError("Requis");
            FormSummaryComponent sum = s.add(new FormSummaryComponent(new Box("sum", X, Y, FW, 30), s.panel(), List.of(a)).title(n -> n + " erreur"));
            s.tick(1);
        });
    }

    private static final class Form {
        TextFieldComponent name;
        NumberFieldComponent port;
        FormSummaryComponent summary;
    }

    private static Form form(Scene s) {
        Form form = new Form();
        form.name = s.add(new TextFieldComponent(new Box("n", 0, 0, FW, 16), "", v -> { }));
        form.port = s.add(new NumberFieldComponent(new Box("p", 0, 0, FW, 16), 0, 70000, 66000, v -> { }));
        FormFieldComponent nameField = s.add(new FormFieldComponent(new Box("fn", X, 48, FW, 0), s.panel(), "World name", form.name).required(true)
                .validator(() -> form.name.text().isBlank() ? "A world needs a name" : null));
        FormFieldComponent portField = s.add(new FormFieldComponent(new Box("fp", X, 90, FW, 0), s.panel(), "Port", form.port)
                .validator(() -> form.port.value() > 65535 ? "At most 65535" : null));
        form.summary = s.add(new FormSummaryComponent(new Box("s", X, 5, FW, 41), s.panel(), List.of(nameField, portField)));
        s.add(new ButtonComponent(new Box("b", X, 132, 70, 20), "Save", () -> form.summary.validateAll()));
        form.summary.validateAll();
        return form;
    }
}
