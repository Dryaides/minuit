package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListComponentTest {

    /**
     * A room list, rows 16 px from y 10, each with a Join button on its right. Scrolled by one row,
     * a click on the second visible row's text reports "c", index 2 and the mouse inside that row;
     * a click on its Join button goes to the button only.
     */
    @Test
    void rowClicksAndRowButtonsRouteToTheRightRow() {
        List<String> rows = new ArrayList<>();
        List<String> joined = new ArrayList<>();
        ListComponent<String> list = new ListComponent<String>(new Box("l", 0, 10, 120, 32), 16,
                (d, theme, item, row, hovered, selected) -> { },
                (item, index, mouseX, mouseY) -> rows.add(item + " " + index + " " + mouseX + "," + mouseY));
        list.setItems(List.of("a", "b", "c", "d"));
        list.rowWidgets((item, index, row) -> List.of(
                new ButtonComponent(new Box("join", row.right() - 30, row.y() + 2, 30, 12), "Join", () -> joined.add(item))));
        list.setScroll(16);
        list.mouseClicked(20, 10 + 16 + 5, 0);
        assertEquals(List.of("c 2 16,5"), rows);
        list.mouseClicked(100, 10 + 16 + 5, 0);
        assertEquals(List.of("c"), joined);
        assertEquals(1, rows.size());
    }
}
