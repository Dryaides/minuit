package dev.dryaides.minuit;

/**
 * The bench's way to the places the components lay out for a script or a test. Those getters are package-private in
 * core, and this class, in the same package but compiled only by the bench, forwards to them. It never ships.
 */
public final class BenchAccess {
    private BenchAccess() {
    }

    public static Box closeBox(AlertComponent c) {
        return c.closeBox();
    }

    public static Box headBox(AvatarGroupComponent c, int index) {
        return c.headBox(index);
    }

    public static Box moreBox(AvatarGroupComponent c) {
        return c.moreBox();
    }

    public static Box slotBox(BreadcrumbComponent c, int slot) {
        return c.slotBox(slot);
    }

    public static Box arrowBox(CarouselComponent c, boolean next) {
        return c.arrowBox(next);
    }

    public static Box dotBox(CarouselComponent c, int dot) {
        return c.dotBox(dot);
    }

    public static Box cardBox(DialogComponent c) {
        return c.cardBox();
    }

    public static Box handleBox(DrawerComponent c) {
        return c.handleBox();
    }

    public static Box closeBox(DrawerComponent c) {
        return c.closeBox();
    }

    public static Box rowBox(FormSummaryComponent c, int row) {
        return c.rowBox(row);
    }

    public static Box cardBox(HoverCardComponent c) {
        return c.cardBox();
    }

    public static Box columnBox(KanbanComponent<?> c, int column) {
        return c.columnBox(column);
    }

    public static Box cardBox(KanbanComponent<?> c, int column, int row) {
        return c.cardBox(column, row);
    }

    public static Box arrowBox(LightboxComponent c, boolean next) {
        return c.arrowBox(next);
    }

    public static Box closeBox(LightboxComponent c) {
        return c.closeBox();
    }

    public static Box minusBox(NumberFieldComponent c) {
        return c.minusBox();
    }

    public static Box plusBox(NumberFieldComponent c) {
        return c.plusBox();
    }

    public static Box cellBox(OtpInputComponent c, int index) {
        return c.cellBox(index);
    }

    public static Box slotBox(PaginationComponent c, int slot) {
        return c.slotBox(slot);
    }

    public static Box sizeBox(PaginationComponent c) {
        return c.sizeBox();
    }

    public static Box cardBox(PopoverComponent c) {
        return c.cardBox();
    }

    public static Box cellBox(SlotGridComponent c, int index) {
        return c.cellBox(index);
    }

    public static Box dividerBox(SplitPaneComponent c) {
        return c.dividerBox();
    }

    public static Box paneBox(SplitPaneComponent c, SplitPaneComponent.Side side) {
        return c.paneBox(side);
    }

    public static Box paneArea(SplitPaneComponent c, SplitPaneComponent.Side side) {
        return c.paneArea(side);
    }

    public static Box chipBox(TagInputComponent c, int index) {
        return c.chipBox(index);
    }

    public static Box closeBox(TagInputComponent c, int index) {
        return c.closeBox(index);
    }

    public static Box inputBox(TagInputComponent c) {
        return c.inputBox();
    }

    public static Box popupBox(TagInputComponent c) {
        return c.popupBox();
    }

    public static Box rowBox(TagInputComponent c, int row) {
        return c.rowBox(row);
    }

    public static Box arrowBox(TreeTableComponent<?> c, int index) {
        return c.arrowBox(index);
    }
    public static int popupRow() {
        return RowLook.POPUP_ROW;
    }
}
