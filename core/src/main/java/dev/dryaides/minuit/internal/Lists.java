package dev.dryaides.minuit.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Copies of the lists authors hand to components, made safe to draw: a null list is empty. */
public final class Lists {

    private Lists() {
    }

    /** An unmodifiable copy without the null entries. */
    public static <T> List<T> withoutNulls(Collection<? extends T> items) {
        if (items == null) {
            return List.of();
        }
        List<T> out = new ArrayList<>(items.size());
        for (T item : items) {
            if (item != null) {
                out.add(item);
            }
        }
        return List.copyOf(out);
    }

    /** An unmodifiable copy where a null name becomes "", so each label keeps its place. */
    public static List<String> labels(Collection<String> names) {
        if (names == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(names.size());
        for (String name : names) {
            out.add(name == null ? "" : name);
        }
        return List.copyOf(out);
    }
}
