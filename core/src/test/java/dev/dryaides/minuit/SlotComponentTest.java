package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotComponentTest {

    /** The owner's examples (AE2's terminal): 1234 reads 1.2k, 34 million reads 34M; under 1000 stays exact. */
    @Test
    void countsReadShortAsAStorageTerminal() {
        assertEquals("999", SlotComponent.shortCount(999));
        assertEquals("1.2k", SlotComponent.shortCount(1234));
        assertEquals("34M", SlotComponent.shortCount(34_000_000));
    }
}
