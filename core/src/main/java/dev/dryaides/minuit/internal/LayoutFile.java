package dev.dryaides.minuit.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.dryaides.minuit.LayoutStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link LayoutStore} over {@code config/minuit-layouts.json}, one entry per window key. Read once,
 * written on each {@code put}, which a window calls when a drag ends or it folds.
 * {@code collapsed} may be missing: the window is open.
 *
 * <pre>{"mymod:paint": {"dx": 12, "dy": -30, "scale": 1.1, "collapsed": true}}</pre>
 */
public final class LayoutFile implements LayoutStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<String, Layout> layouts = new ConcurrentHashMap<>();

    public LayoutFile(Path file) {
        this.file = file;
        try {
            if (Files.isRegularFile(file)) {
                this.layouts.putAll(parse(Files.readString(file, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // An unreadable file starts every window at its default place.
        }
    }

    static Map<String, Layout> parse(String text) {
        Map<String, Layout> out = new ConcurrentHashMap<>();
        try {
            for (Map.Entry<String, JsonElement> e : JsonParser.parseString(text).getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonObject()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    out.put(e.getKey(), new Layout(o.get("dx").getAsInt(), o.get("dy").getAsInt(),
                            o.get("scale").getAsFloat(), o.has("collapsed") && o.get("collapsed").getAsBoolean()));
                }
            }
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException e) {
            out.clear();
        }
        return out;
    }

    @Override
    public Layout get(String key) {
        return this.layouts.get(key);
    }

    @Override
    public void put(String key, Layout layout) {
        this.layouts.put(key, layout);
        JsonObject root = new JsonObject();
        this.layouts.forEach((k, l) -> {
            JsonObject o = new JsonObject();
            o.addProperty("dx", l.dx());
            o.addProperty("dy", l.dy());
            o.addProperty("scale", l.scale());
            o.addProperty("collapsed", l.collapsed());
            root.add(k, o);
        });
        try {
            Files.createDirectories(this.file.toAbsolutePath().getParent());
            Path tmp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // A read-only config keeps the layout for this session.
        }
    }
}
