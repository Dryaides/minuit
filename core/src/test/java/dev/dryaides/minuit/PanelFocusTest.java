package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PanelFocusTest {

    @Test
    void aClickOnAWindowOverAFieldDoesNotFocusTheField() {
        TextFieldComponent field = new TextFieldComponent(new Box("f", 10, 10, 100, 20), "", t -> { });
        PanelComponent panel = new PanelComponent(null, null, 0, 0) {
            {
                add(field);
                // a plain card drawn over the field, taking no keyboard
                add(new BaseComponent(new Box("w", 0, 0, 200, 100)) {
                    @Override
                    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
                    }
                });
            }

            @Override
            public int width() {
                return 200;
            }

            @Override
            public int height() {
                return 100;
            }

            @Override
            protected void drawBackground(Draw d, int mx, int my) {
            }
        };
        panel.mouseClicked(20, 20, 0);
        assertEquals(null, panel.focus());
    }

    @Test
    void onlyAFocusedCapturingWidgetGetsRawKeys() {
        List<KeyPress> got = new ArrayList<>();
        class Field extends BaseComponent {
            final boolean captures;

            Field(Box box, boolean captures) {
                super(box);
                this.captures = captures;
            }

            @Override
            protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
            }

            @Override
            protected boolean takesFocus() {
                return true;
            }

            @Override
            protected boolean capturesKeys() {
                return this.captures;
            }

            @Override
            public boolean keyCaptured(KeyPress key) {
                got.add(key);
                return true;
            }
        }
        Field plain = new Field(new Box("p", 0, 0, 50, 20), false);
        Field binder = new Field(new Box("b", 0, 40, 50, 20), true);
        PanelComponent panel = new PanelComponent(null, null, 0, 0) {
            {
                add(plain);
                add(binder);
            }

            @Override
            public int width() {
                return 200;
            }

            @Override
            public int height() {
                return 100;
            }

            @Override
            protected void drawBackground(Draw d, int mx, int my) {
            }
        };
        KeyPress r = KeyPress.key(82, 19);
        panel.focus(plain);
        assertEquals(false, panel.rawKey(r));
        panel.focus(binder);
        assertEquals(true, panel.rawKey(r));
        assertEquals(List.of(r), got);
    }
}
