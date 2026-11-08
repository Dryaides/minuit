package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A widget that takes an item handed in from outside the kit: a recipe viewer's ingredient dropped
 * on a display slot or a picker, the shape of JEI's ghost ingredient handler. Nothing moves in a menu:
 * the widget's callback decides what the item means.
 */
public interface ItemDropTarget {

    /** Whether a screen point lies in a target's area. */
    @FunctionalInterface
    interface Hit {
        boolean at(int x, int y);
    }

    /**
     * One place an item may land, screen coordinates. A rect target is {@code area} alone; a target of
     * another shape (a projected 3D zone) gives {@code hit}, and {@code area} is then its bounding box.
     * {@code fits} says whether the carried item suits the place, so the hover cue shows only for it.
     */
    record Drop(Box area, Hit hit, Predicate<ItemView> fits, Predicate<ItemView> accept) {

        /** A rectangle that takes any item {@code accept} takes. */
        public Drop(Box area, Predicate<ItemView> accept) {
            this(area, null, null, accept);
        }

        /** True when the point lies on the target. */
        public boolean contains(int x, int y) {
            return this.hit != null ? this.hit.at(x, y) : this.area.contains(x, y);
        }

        /** True when the carried item suits this target. */
        public boolean fits(ItemView item) {
            return this.fits == null || this.fits.test(item);
        }

        /** True when the item, carried over the point, may be dropped: the cue shows for it. */
        public boolean canDrop(int x, int y, ItemView item) {
            return contains(x, y) && fits(item);
        }

        /** Hands the item in; true when the target took it. */
        public boolean acceptDrop(ItemView item) {
            return this.accept.test(item);
        }
    }

    /** Where this widget takes an item now; empty when it takes none. */
    List<Drop> dropTargets();

    /** Every drop target of the panel's visible widgets, for a recipe viewer plugin to offer. */
    static List<Drop> all(PanelComponent panel) {
        List<Drop> out = new ArrayList<>();
        for (BaseComponent widget : panel.widgets()) {
            if (widget.visible && widget instanceof ItemDropTarget target) {
                out.addAll(target.dropTargets());
            }
        }
        return out;
    }
}
