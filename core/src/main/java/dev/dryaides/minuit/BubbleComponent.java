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

    public BubbleComponent(Box box, String text) {
        super(box);
        this.text = text;
        this.anchorX = box.x() + box.w() / 2;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        int maxWidth = Math.min(Bubble.MAX_LINE_WIDTH, this.w - 4 - Bubble.PAD_X * 2);
        List<String> lines = Bubble.wrap(d, this.text, Math.max(1, maxWidth), Bubble.MAX_LINES);
        Box placed = Bubble.box(d, lines, this.anchorX - this.x, this.h, this.w, this.h).offset(this.x, this.y);
        Bubble.draw(d, this.style, lines, placed, this.anchorX, 1F);
    }
}
