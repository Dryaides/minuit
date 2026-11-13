package dev.dryaides.minuit.internal.showcase;

import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.IconArt;
import dev.dryaides.minuit.ItemView;

import java.util.ArrayList;
import java.util.List;

/**
 * What the game's demo screen shows: its categories, and for each scene of {@link SiteScenes} the component it
 * stands for, one plain sentence about it and its page on the site. Demo text is English, as the site's.
 */
public final class Showcase {

    public static final String SITE = "https://minuit.dryaides.com/";

    public enum Category {
        HOME(Icon.PALETTE),
        INPUT(Icon.WRENCH),
        CHOICE(Icon.CHECK),
        DATA(Icon.SORT),
        MEDIA(Icon.PLAY),
        LAYOUT(Icon.FOLDER),
        OVERLAYS(Icon.CLIPBOARD),
        MINECRAFT(ItemView.of("minecraft:diamond"));

        private final Icon icon;
        private final IconArt art;

        Category(Icon icon) {
            this.icon = icon;
            this.art = null;
        }

        Category(IconArt art) {
            this.icon = null;
            this.art = art;
        }

        public Icon icon() {
            return this.icon;
        }

        public IconArt art() {
            return this.art;
        }

        public String nameKey() {
            return "minuit.demo.category." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * One scene of the demo.
     *
     * @param id        the scene's id in {@link SiteScenes#stage}
     * @param name      what the scene list calls it
     * @param className the component's class, as a modder looks it up
     * @param sentence  what it does, in one plain sentence
     */
    public record Entry(String id, Category category, String name, String className, String sentence) {

        /** The component's page on the site: the part of the id before the slash. */
        public String url() {
            return SITE + "components/" + this.id.substring(0, this.id.indexOf('/')) + ".html";
        }
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry("button/roles", Category.INPUT, "Button", "ButtonComponent",
                    "Runs an action on a click; its role marks the main, the quiet or the destructive one."),
            new Entry("textfield/states", Category.INPUT, "Text field", "TextFieldComponent",
                    "One line of text with a cursor, a selection and a placeholder."),
            new Entry("slider/values", Category.INPUT, "Slider", "SliderComponent",
                    "Picks a whole number between two bounds, dragged or set with the arrow keys."),
            new Entry("colorpicker/alpha", Category.INPUT, "Colour picker", "ColorPickerComponent",
                    "Picks a colour by hue, shade and opacity, and keeps the recent ones."),
            new Entry("toggle/states", Category.CHOICE, "Toggle", "ToggleComponent",
                    "Turns one setting on or off."),
            new Entry("multistop/states", Category.CHOICE, "Multi-stop toggle", "MultiStopToggleComponent",
                    "Picks one of two to five settings on one switch, an icon in each stop."),
            new Entry("toggle-group/rows", Category.CHOICE, "Toggle group", "ToggleGroupComponent",
                    "A row of buttons where one, or several, stay pressed."),
            new Entry("dropdown/open", Category.CHOICE, "Dropdown", "DropdownComponent",
                    "Opens a list of choices under a box and shows the pick in it."),
            new Entry("tabs/rows", Category.CHOICE, "Tabs", "TabsComponent",
                    "Switches between the pages of one screen."),
            new Entry("radial/wheel", Category.CHOICE, "Radial menu", "RadialComponent",
                    "A wheel of actions around the cursor, in pages."),
            new Entry("list/rows", Category.DATA, "List", "ListComponent",
                    "A scrolling list of rows the mod draws; a click picks one."),
            new Entry("grid/cards", Category.DATA, "Grid", "GridComponent",
                    "Cards in columns, scrolled, one of them selected."),
            new Entry("progress/states", Category.DATA, "Progress bar", "ProgressComponent",
                    "Shows how far a task has gone."),
            new Entry("meter/levels", Category.DATA, "Level meter", "MeterComponent",
                    "A live level with its gate marked, here a voice."),
            new Entry("empty/states", Category.DATA, "Empty state", "EmptyStateComponent",
                    "What a list or a search shows while it has nothing, with the next step to take."),
            new Entry("spinner/ring", Category.DATA, "Spinner", "SpinnerComponent",
                    "Says something is loading while the time it takes is unknown."),
            new Entry("reel/cards", Category.MEDIA, "Reel", "ReelComponent",
                    "Spins through cards with pictures and lands on one, as a map vote does."),
            new Entry("richtext/codes", Category.MEDIA, "Formatted text", "RichTextComponent",
                    "Draws the game's colour and style codes, and links."),
            new Entry("panel/sections", Category.LAYOUT, "Panel", "PanelComponent",
                    "The frame a screen is built in, with section cards inside."),
            new Entry("section/open-shut", Category.LAYOUT, "Section", "SectionComponent",
                    "A header that opens and shuts the widgets under it."),
            new Entry("scroll/rows", Category.LAYOUT, "Scroll box", "ScrollComponent",
                    "Scrolls any widgets inside a box."),
            new Entry("window/tools", Category.LAYOUT, "Window", "WindowComponent",
                    "A panel the player drags, scales and folds; its place is kept."),
            new Entry("button/tooltip", Category.OVERLAYS, "Tooltip", "BaseComponent",
                    "A note that shows when the pointer rests on a widget."),
            new Entry("toast/stack", Category.OVERLAYS, "Toast", "ToastComponent",
                    "A short message card with an item or an icon."),
            new Entry("slots/display", Category.MINECRAFT, "Slots", "SlotGridComponent",
                    "Item slots that play like a chest: pick up, split, drop, number keys."),
            new Entry("slots/hud", Category.MINECRAFT, "Hotbar", "HotbarComponent",
                    "The game's hotbar and the player inventory, drawn in the theme."),
            new Entry("itempicker/search", Category.MINECRAFT, "Item picker", "ItemPickerComponent",
                    "Searches every item of the game and picks one."),
            new Entry("entity/turn", Category.MINECRAFT, "Entity preview", "EntityPreviewComponent",
                    "A mob, the player or a block, turned with the mouse."),
            new Entry("keybind/states", Category.MINECRAFT, "Key binding", "KeybindFieldComponent",
                    "Sets a key or a combination and flags the clashes."));

    /** The home mosaic's cards and the scene a click on a card's frame opens; null opens none. */
    static final List<String[]> HOME = List.of(
            new String[]{"home/settings", "toggle/states"}, new String[]{"home/notifications", "toggle/states"},
            new String[]{"home/confirm", "button/roles"}, new String[]{"home/create", "textfield/states"},
            new String[]{"home/team", "dropdown/open"}, new String[]{"home/tabs", "tabs/rows"},
            new String[]{"home/stepper", null}, new String[]{"home/view", "toggle-group/rows"},
            new String[]{"home/progress", "progress/states"}, new String[]{"home/vote", "reel/cards"},
            new String[]{"home/color", "colorpicker/alpha"}, new String[]{"home/radial", "radial/wheel"},
            new String[]{"home/window", "window/tools"});

    private Showcase() {
    }

    public static List<Entry> of(Category category) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (e.category() == category) {
                out.add(e);
            }
        }
        return out;
    }

    /** The entry of scene {@code id}, or null. */
    public static Entry entry(String id) {
        for (Entry e : ENTRIES) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }
}
