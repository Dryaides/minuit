package dev.dryaides.minuit;

import java.util.List;

/**
 * A speech bubble inside a screen: a tutorial hint, a pet talking in a menu. The bubble sits in
 * the component's box with its tail on the bottom edge at {@link #anchorX}, and keeps inside the
 * box the way the world bubble keeps inside the screen. The theme plays no part: the look comes
 * from the {@link BubbleStyle}.
 */
public class BubbleComponent extends BaseComponent {

    public String text;
    /** Where the tail points, in screen x; by default the middle of the box. */
    public int anchorX;
    public BubbleStyle style = BubbleStyle.CLASSIC;

    private List<String> lines = List.of();
    private String laidText;
    private int laidWidth = -1;
    private Class<?> laidBy;
    private int laidFont = -1;
    private Box laidBox;
    private int laidAnchor;
    private int laidW = -1;
    private int laidH = -1;

    public BubbleComponent(Box box, String text) {
        super(box);
        this.text = text;
        this.anchorX = box.x() + box.w() / 2;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        int maxWidth = Math.min(Bubble.MAX_LINE_WIDTH, this.w - 4 - Bubble.PAD_X * 2);
        int width = Math.max(1, maxWidth);
        int anchor = this.anchorX - this.x;
        if (width != laidWidth || laidBy != d.getClass() || laidFont != TextMeasure.fontGeneration() || !this.text.equals(laidText)) {
            laidWidth = width;
            laidBy = d.getClass();
            laidFont = TextMeasure.fontGeneration();
            laidText = this.text;
            lines = Bubble.wrap(d, this.text, width, Bubble.MAX_LINES);
            laidBox = null;
        }
        if (laidBox == null || anchor != laidAnchor || this.w != laidW || this.h != laidH) {
            laidAnchor = anchor;
            laidW = this.w;
            laidH = this.h;
            laidBox = Bubble.box(d, lines, anchor, this.h, this.w, this.h);
        }
        Bubble.draw(d, this.style, lines, laidBox.offset(this.x, this.y), this.anchorX, 1F);
    }
}
