package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.HeldKeys;
import dev.dryaides.minuit.ItemPickerComponent;
import dev.dryaides.minuit.MotionStyle;
import dev.dryaides.minuit.RichTextComponent;
import dev.dryaides.minuit.UiSound;
import java.util.function.LongSupplier;

/**
 * The host hooks shared by every mod in the game. Only minuit's host glue and benches write them; mods read them
 * through {@link dev.dryaides.minuit.BaseComponent}'s getters.
 */
public final class HostState {

    private static volatile UiSound sound = UiSound.NONE;
    private static volatile LongSupplier clock = System::currentTimeMillis;
    private static volatile boolean reducedMotion;
    private static volatile MotionStyle screenMotion = MotionStyle.SUBTLE;
    private static volatile MotionStyle playerMotion;
    private static volatile HeldKeys keys = HeldKeys.NONE;
    private static volatile ItemPickerComponent.SortStore sorts = ItemPickerComponent.SortStore.memory();
    private static volatile RichTextComponent.LinkOpener links = url -> { };

    private HostState() {
    }

    public static UiSound sound() {
        return sound;
    }

    public static void sound(UiSound now) {
        sound = now;
    }

    public static LongSupplier clock() {
        return clock;
    }

    public static void clock(LongSupplier now) {
        clock = now;
    }

    public static boolean reducedMotion() {
        return reducedMotion;
    }

    public static void reducedMotion(boolean now) {
        reducedMotion = now;
    }

    public static MotionStyle screenMotion() {
        return screenMotion;
    }

    public static void screenMotion(MotionStyle now) {
        screenMotion = now;
    }

    public static MotionStyle playerMotion() {
        return playerMotion;
    }

    public static void playerMotion(MotionStyle now) {
        playerMotion = now;
    }

    public static HeldKeys keys() {
        return keys;
    }

    public static void keys(HeldKeys now) {
        keys = now;
    }

    public static ItemPickerComponent.SortStore sorts() {
        return sorts;
    }

    public static void sorts(ItemPickerComponent.SortStore now) {
        sorts = now;
    }

    public static RichTextComponent.LinkOpener links() {
        return links;
    }

    public static void links(RichTextComponent.LinkOpener now) {
        links = now;
    }
}
