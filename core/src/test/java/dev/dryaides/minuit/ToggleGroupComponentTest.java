package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToggleGroupComponentTest {

    private final List<Set<Integer>> heard = new ArrayList<>();

    private ToggleGroupComponent group(ToggleGroupComponent.Mode mode, Set<Integer> on) {
        return new ToggleGroupComponent(new Box("", 0, 0, 90, 16), List.of("A", "B", "C"), mode, on, heard::add);
    }

    /** Item i spans x 30 i to 30 i + 30; a click in its middle. */
    private static void click(ToggleGroupComponent g, int i) {
        g.mouseClicked(30 * i + 15, 8, 0);
    }

    @Test
    void singleKeepsOneOnAndMayEmptyUnlessRequired() {
        ToggleGroupComponent g = group(ToggleGroupComponent.Mode.SINGLE, Set.of(0));
        click(g, 2);
        assertEquals(Set.of(2), g.on());
        click(g, 2);
        assertEquals(Set.of(), g.on());
        g.required(true);
        click(g, 1);
        click(g, 1);
        assertEquals(Set.of(1), g.on());
        assertEquals(List.of(Set.of(2), Set.of(), Set.of(1)), heard);
    }

    @Test
    void multipleTogglesEachItemAlone() {
        ToggleGroupComponent g = group(ToggleGroupComponent.Mode.MULTIPLE, Set.of(0));
        click(g, 1);
        click(g, 2);
        click(g, 0);
        assertEquals(Set.of(1, 2), g.on());
    }

    @Test
    void disabledItemIgnoresClicksAndLeavesTheKeyboardPath() {
        ToggleGroupComponent g = group(ToggleGroupComponent.Mode.MULTIPLE, Set.of());
        g.disabled(1, true, "Not here");
        click(g, 1);
        assertEquals(Set.of(), g.on());
        assertEquals(List.of(), heard);
        assertEquals(2, g.stops().size());
        g.stops().get(1).activate();
        assertEquals(Set.of(2), g.on());
    }

    @Test
    void silentSetInSingleKeepsOnlyOne() {
        ToggleGroupComponent g = group(ToggleGroupComponent.Mode.SINGLE, Set.of(1, 2));
        assertEquals(1, g.on().size());
        assertEquals(List.of(), heard);
    }
}
