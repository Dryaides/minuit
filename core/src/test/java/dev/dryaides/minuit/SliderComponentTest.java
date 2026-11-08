package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SliderComponentTest {

    /** Rounds in fives from 0 to 22: the grid 0, 5 ... 20, and 22 as the last stop. */
    @Test
    void snapsToTheNearestStepAndKeepsMaxAsAStop() {
        assertEquals(5, SliderComponent.snap(7, 0, 22, 5));
        assertEquals(10, SliderComponent.snap(8, 0, 22, 5));
        assertEquals(20, SliderComponent.snap(20, 0, 22, 5));
        assertEquals(22, SliderComponent.snap(22, 0, 22, 5));
        assertEquals(22, SliderComponent.snap(40, 0, 22, 5));
        assertEquals(0, SliderComponent.snap(-3, 0, 22, 5));
        assertEquals(13, SliderComponent.snap(14, 3, 30, 10));
    }

    /** A drag sends every value as it moves and one commit, the last, on release. */
    @Test
    void dragSendsLiveAndCommitsOnRelease() {
        List<Integer> live = new ArrayList<>();
        List<Integer> commits = new ArrayList<>();
        SliderComponent s = new SliderComponent(new Box("s", 0, 0, 132, 12), 0, 100, 0, live::add)
                .step(10).onCommit(commits::add);
        s.mouseClicked(4, 6, 0);
        s.mouseDragged(54, 6, 0, 50, 0);
        s.mouseDragged(104, 6, 0, 50, 0);
        s.mouseReleased(104, 6, 0);
        assertEquals(List.of(50, 100), live);
        assertEquals(List.of(100), commits);
        live.forEach(v -> assertEquals(0, v % 10));
    }

    /** Range 0 to 10 in twos on a 100 px track starting at x 4: a drag lands on a tick, and so does the handle. */
    @Test
    void dragSnapsValueAndHandleToTheTicks() {
        SliderComponent s = new SliderComponent(new Box("s", 0, 0, 132, 12), 0, 10, 0, v -> { }).step(2);
        s.mouseClicked(4, 6, 0);
        s.mouseDragged(53, 6, 0, 49, 0);
        assertEquals(4, s.value);
        assertEquals(40, s.handleOffset());
        s.mouseDragged(55, 6, 0, 2, 0);
        assertEquals(6, s.value);
        assertEquals(60, s.handleOffset());
        s.mouseReleased(55, 6, 0);
    }

    /** Max 22 in fives: five ticks, the last one at max, so 20 sits on the 4th and 22 on the end. */
    @Test
    void offGridMaxIsTheLastTick() {
        SliderComponent s = new SliderComponent(new Box("s", 0, 0, 132, 12), 0, 22, 20, v -> { }).step(5);
        assertEquals(80, s.handleOffset());
        s.value = 22;
        assertEquals(100, s.handleOffset());
    }
}
