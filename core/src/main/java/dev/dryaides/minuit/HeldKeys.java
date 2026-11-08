package dev.dryaides.minuit;

/**
 * The modifier keys held right now, from the host's input bridge, so a widget can read Ctrl or Shift
 * on a wheel turn or a click, which carry no key state of their own.
 */
public interface HeldKeys {

    HeldKeys NONE = new HeldKeys() {
        @Override
        public boolean ctrl() {
            return false;
        }

        @Override
        public boolean shift() {
            return false;
        }
    };

    /** Ctrl, or Command on macOS. */
    boolean ctrl();

    boolean shift();
}
