package dev.dryaides.minuit;

/**
 * What a press or a release of the pointer does with the stack on the cursor of a menu screen whose
 * hotbar hands stacks to drop targets ({@link ExpandableHotbarComponent}). Picking and placing stay the
 * menu's own slot clicks, so the game keeps the carried stack and the server checks every move.
 */
@Incubating
public final class HotbarDrag {
    private HotbarDrag() {
    }

    /** What lies under the pointer. */
    public enum Under { SLOT, TARGET, NOTHING }

    public enum Act {
        /** Pick up the slot's stack (a slot click with an empty cursor). */
        PICK,
        /** Put the carried stack in the slot (a slot click; the game stacks or swaps). */
        PLACE,
        /** Hand the carried stack to the drop target under the pointer ({@code Drop.acceptDrop}). */
        DROP,
        /** Put the carried stack back on the slot it was picked from. */
        RETURN,
        /** Keep carrying: a click without a drag, as in a vanilla inventory. */
        KEEP,
        NOTHING
    }

    /**
     * A press. With an empty cursor it picks a stack from a filled slot; with a carried stack it places,
     * drops or returns it, as a vanilla click does after a click-to-pick.
     */
    public static Act press(boolean carrying, Under under, boolean slotFilled) {
        if (!carrying) {
            return under == Under.SLOT && slotFilled ? Act.PICK : Act.NOTHING;
        }
        return settle(under);
    }

    /**
     * A release. On the slot it was picked from the stack stays on the cursor (click to pick, click to
     * put); anywhere else it ends the drag there. Released over a target the carried stack does not fit,
     * pass {@link Under#NOTHING}: it goes back.
     */
    public static Act release(boolean carrying, Under under, boolean onOrigin) {
        if (!carrying) {
            return Act.NOTHING;
        }
        if (onOrigin) {
            return Act.KEEP;
        }
        return settle(under);
    }

    private static Act settle(Under under) {
        switch (under) {
            case SLOT:
                return Act.PLACE;
            case TARGET:
                return Act.DROP;
            default:
                return Act.RETURN;
        }
    }
}
