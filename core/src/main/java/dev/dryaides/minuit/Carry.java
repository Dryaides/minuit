package dev.dryaides.minuit;

/**
 * What a press or a release of the pointer does with a carried stack, as a vanilla inventory does it: click to pick
 * and click to put, or drag and release. The host turns each {@link Act} into the open menu's own slot click, so the
 * game keeps the carried stack and the server checks every move; {@link ItemDropTarget} drops are the
 * {@link Under#TARGET} case. Pure rules, no state: the host says whether a stack is carried and what lies under the pointer.
 */
@Incubating
public final class Carry {
    private Carry() {
    }

    /** What lies under the pointer. */
    public enum Under { SLOT, TARGET, NOTHING }

    public enum Act {
        /** Pick up the slot's stack (a slot click with an empty cursor). */
        PICK,
        /** Put the carried stack in the slot (a slot click; the game stacks or swaps). */
        PLACE,
        /** Hand the carried stack to the drop target under the pointer. */
        DROP,
        /** Put the carried stack back where it was picked. */
        RETURN,
        /** Keep carrying: a click without a drag. */
        KEEP,
        NOTHING
    }

    /** A press: with an empty cursor it picks from a filled slot; with a stack carried it places, drops or returns it. */
    public static Act press(boolean carrying, Under under, boolean slotFilled) {
        if (!carrying) {
            return under == Under.SLOT && slotFilled ? Act.PICK : Act.NOTHING;
        }
        return settle(under);
    }

    /**
     * A release: on the slot the stack was picked from it stays on the cursor (click to pick, click to put);
     * anywhere else it ends the drag there.
     */
    public static Act release(boolean carrying, Under under, boolean onOrigin) {
        if (!carrying) {
            return Act.NOTHING;
        }
        return onOrigin ? Act.KEEP : settle(under);
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
