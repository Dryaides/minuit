package dev.dryaides.minuit.form;

import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.ScreenMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The FormServer contract: at most one open form per player, and a newer form replaces an older one. */
class FormServerTest {

    private final List<byte[]> sent = new ArrayList<>();
    private final FormServer<String> server = new FormServer<String>((p, b) -> this.sent.add(b), () -> 5_000);

    private static Form form(String screenId) {
        return Form.custom(screenId).title(Text.lit("T"))
                .footer(r -> r.button("next", Text.lit("Next"), Role.PRIMARY))
                .build();
    }

    @Test
    void aFormOpenedFromAPressStaysTheOpenForm() {
        List<String> secondPresses = new ArrayList<>();
        FormHandler secondHandler = new FormHandler() {
            @Override
            public void pressed(FormSession s, String id) {
                secondPresses.add(id);
            }
        };
        FormSession[] second = new FormSession[1];
        FormSession first = this.server.open("p", form("t:first"), new FormHandler() {
            @Override
            public void pressed(FormSession s, String id) {
                second[0] = FormServerTest.this.server.open("p", form("t:second"), secondHandler);
            }
        });

        this.server.receive("p", new InputMessage.Press(first.id(), 1, "next").encode());

        assertFalse(first.isOpen());
        assertSame(second[0], this.server.session("p"));
        this.server.receive("p", new InputMessage.Press(second[0].id(), 1, "next").encode());
        assertEquals(List.of("next"), secondPresses);
    }

    @Test
    void aNestedOpenEndsTheOuterSessionAsReplaced() {
        List<CloseReason> outerClosed = new ArrayList<>();
        FormSession first = this.server.open("p", form("t:first"), new FormHandler() {
            @Override
            public void pressed(FormSession s, String id) {
                FormServerTest.this.server.open("p", form("t:second"), new FormHandler() {
                });
            }

            @Override
            public void closed(FormSession s, CloseReason why) {
                outerClosed.add(why);
            }
        });

        this.server.receive("p", new InputMessage.Press(first.id(), 1, "next").encode());

        assertEquals(List.of(CloseReason.REPLACED), outerClosed);
    }

    @Test
    void aFormOpenedWhileTheOldOneClosesIsTheOneShown() {
        FormSession[] third = new FormSession[1];
        this.server.open("p", form("t:first"), new FormHandler() {
            @Override
            public void closed(FormSession s, CloseReason why) {
                if (third[0] == null) {
                    third[0] = FormServerTest.this.server.open("p", form("t:third"), new FormHandler() {
                    });
                }
            }
        });
        this.sent.clear();

        FormSession second = this.server.open("p", form("t:second"), new FormHandler() {
        });

        FormSession current = this.server.session("p");
        assertSame(third[0], current);
        assertTrue(current.isOpen());
        assertFalse(second.isOpen());
        assertEquals(1, this.sent.size(), "one form reaches the client");
        ScreenMessage.Open shown = (ScreenMessage.Open) ScreenMessage.decode(this.sent.get(0));
        assertEquals(current.id(), shown.session());
    }

    /** Vanilla respawn builds a new player object: the form must survive it and still end on leave. */
    @Test
    void aRespawnedPlayerObjectKeepsTheFormAndLeavingEndsIt() {
        List<String> to = new ArrayList<>();
        List<String> presses = new ArrayList<>();
        List<CloseReason> closed = new ArrayList<>();
        FormServer<String> keyed = new FormServer<String>((p, b) -> to.add(p), () -> 5_000)
                .keyedBy(p -> p.substring(0, p.indexOf('#')));
        FormSession s = keyed.open("steve#1", form("t:a"), new FormHandler() {
            @Override
            public void pressed(FormSession x, String id) {
                presses.add(id);
            }

            @Override
            public void closed(FormSession x, CloseReason reason) {
                closed.add(reason);
            }
        });

        assertSame(s, keyed.session("steve#2"));
        keyed.receive("steve#2", new InputMessage.Press(s.id(), 1, "next").encode());
        assertEquals(List.of("next"), presses);
        keyed.left("steve#2");

        assertEquals(List.of(CloseReason.PLAYER), closed);
        assertEquals(null, keyed.session("steve#1"));
        assertEquals(List.of("steve#1"), to);
    }

    /** Owner rule (ROADMAP, backport): without vanilla dialogs, a client lacking form/1 is closed at once and sent nothing. */
    @Test
    void aClientWithoutFormsIsClosedAtOnceWhenFormsAreRequired() {
        List<CloseReason> closed = new ArrayList<>();
        FormServer<String> strict = new FormServer<String>((p, b) -> this.sent.add(b), () -> 5_000)
                .requireForms(p -> !p.equals("vanilla"));
        FormSession s = strict.open("vanilla", form("t:a"), new FormHandler() {
            @Override
            public void closed(FormSession x, CloseReason reason) {
                closed.add(reason);
            }
        });

        assertEquals(List.of(CloseReason.UNSUPPORTED), closed);
        assertFalse(s.isOpen());
        assertEquals(null, strict.session("vanilla"));
        assertTrue(this.sent.isEmpty());
    }

    @Test
    void aFormOverTheScreenCapIsRefusedAndTheShownFormStays() {
        FormSession shown = this.server.open("p", form("t:shown"), new FormHandler() {
        });
        Form.Builder big = Form.custom("t:big").title(Text.lit("T"));
        // 300 texts of 1000 chars: about 300 KB, over the 256 KiB cap and under the node cap
        String line = "x".repeat(1000);
        for (int i = 0; i < 300; i++) {
            big.text("t" + i, Text.lit(line));
        }
        int before = this.sent.size();

        // the builder already encodes the form, so an oversized one never reaches the server
        assertThrows(dev.dryaides.minuit.wire.WireException.class, big::build);

        assertTrue(shown.isOpen());
        assertSame(shown, this.server.session("p"));
        assertEquals(before, this.sent.size());
    }
}
