package bench.api;

/** One component's page in the gallery. Put an implementation in bench/demos: it is found on its own. */
public interface Demo {
    /** The gallery title. Defaults to the class name without "Demo". */
    default String name() {
        return getClass().getSimpleName().replaceFirst("Demo$", "");
    }

    /** Declares the states to draw. */
    void states(Stage stage);
}
