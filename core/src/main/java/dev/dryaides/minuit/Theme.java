package dev.dryaides.minuit;

import java.util.EnumMap;
import java.util.Map;

/**
 * Everything that makes a screen look like one mod and not another: colours by role, the shapes
 * of the frame, and any icon the mod draws itself instead of the kit's glyph.
 *
 * @param id    the name a screen is opened with, e.g. {@code "mymod"}
 * @param icons replacements for the default glyphs, may be empty
 */
public record Theme(String id, Palette palette, FrameStyle frame, Map<Icon, IconArt> icons) {

    public Theme {
        icons = icons.isEmpty() ? Map.of() : Map.copyOf(icons);
    }

    public Theme(String id, Palette palette, FrameStyle frame) {
        this(id, palette, frame, Map.of());
    }

    public IconArt icon(Icon icon) {
        IconArt custom = this.icons.get(icon);
        return custom != null ? custom : icon.glyph();
    }

    /** A copy of this theme under another id, for a mod that keeps a preset as is. */
    public Theme as(String newId) {
        return new Theme(newId, this.palette, this.frame, this.icons);
    }

    /** A copy with one icon drawn from the mod's own art. */
    public Theme withIcon(Icon icon, IconArt art) {
        Map<Icon, IconArt> copy = new EnumMap<>(Icon.class);
        copy.putAll(this.icons);
        copy.put(icon, art);
        return new Theme(this.id, this.palette, this.frame, copy);
    }
}
