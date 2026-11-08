package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;
import java.util.List;
import java.util.function.Consumer;

/**
 * An entity in a well, turned by a drag: 1 px across is 2 degrees of yaw, 1 px up or down a degree of
 * pitch; Left and Right turn it 15 degrees. Where the renderer draws no entity (bench, site, title
 * screen) a cube stands in, its faces following the same angles.
 */
public class EntityPreviewComponent extends BaseComponent {

    public EntityView view;
    /** Degrees; 210 is a three-quarter view with the face toward the player. */
    public float yaw = 210F;
    /** Degrees, -30 to 30. */
    public float pitch;

    private final String turnTip;
    private boolean followMouse;
    private boolean dragging;
    private boolean turned;
    private Consumer<Float> onTurn = yaw -> { };

    public EntityPreviewComponent(Box box, EntityView view) {
        this(box, null, view);
    }

    /** With {@code words}, the tooltip says the preview turns until the player first drags it. */
    public EntityPreviewComponent(Box box, Words words, EntityView view) {
        super(box);
        this.view = view;
        this.turnTip = words == null ? null : words.get("minuit.entity.turn");
    }

    /** Looks toward the mouse, as the inventory's player does, instead of turning on a drag. */
    public EntityPreviewComponent followMouse() {
        this.followMouse = true;
        return this;
    }

    public EntityPreviewComponent onTurn(Consumer<Float> yaw) {
        this.onTurn = yaw;
        return this;
    }

    private void turn(float byYaw, float byPitch) {
        this.yaw = ((this.yaw + byYaw) % 360F + 360F) % 360F;
        this.pitch = Maths.clamp(this.pitch + byPitch, -30F, 30F);
        this.turned = true;
        this.onTurn.accept(this.yaw);
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        theme.frame().well(d, p, this.x, this.y, this.w, this.h, this.focused);
        float yaw = this.yaw;
        float pitch = this.pitch;
        if (this.followMouse) {
            float cx = this.x + this.w / 2F;
            float cy = this.y + this.h / 2F;
            yaw = 180F + (float) Math.toDegrees(Math.atan((mx - cx) / 40F)) * 0.7F;
            pitch = Maths.clamp((float) Math.toDegrees(Math.atan((cy - my) / 40F)) * 0.35F, -30F, 30F);
        }
        if (this.view != null && d.entity(this.view, this.x + 1, this.y + 1, this.x + this.w - 1, this.y + this.h - 1, yaw, pitch)) {
            return;
        }
        standIn(d, p, yaw, pitch);
    }

    /** A cube: the front or the back face by the cosine of the yaw, the side by its sine, the top by the pitch. */
    private void standIn(Draw d, Palette p, float yaw, float pitch) {
        double r = Math.toRadians(yaw);
        int size = Math.max(6, Math.min(this.w, this.h) / 2);
        int front = (int) Math.round(Math.abs(Math.cos(r)) * size);
        int side = (int) Math.round(Math.abs(Math.sin(r)) * size);
        int top = (int) Math.round(size * (0.2 + pitch / 100.0));
        int left = this.x + (this.w - front - side) / 2;
        int y0 = this.y + (this.h - size - top) / 2 + top;
        boolean sideLeft = Math.sin(r) < 0;
        int faceX = sideLeft ? left + side : left;
        int sideX = sideLeft ? left : left + front;
        int faceColor = Math.cos(r) < 0 ? p.raised() : p.accent();
        d.fill(faceX, y0, faceX + front, y0 + size, faceColor);
        d.fill(sideX, y0, sideX + side, y0 + size, Shapes.mix(faceColor == p.raised() ? p.accent() : p.raised(), p.shadow(), 0.35F));
        if (top > 0) {
            d.fill(left, y0 - top, left + front + side, y0, Shapes.mix(p.raisedHover(), 0xFFFFFFFF, 0.25F));
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!this.turned && !this.followMouse && this.turnTip != null && isOver(mx, my)) {
            return List.of(this.turnTip);
        }
        return super.tooltipAt(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || this.followMouse || button != 0 || !isOver(mx, my)) {
            return false;
        }
        this.dragging = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!this.dragging) {
            return false;
        }
        turn((float) dx * 2F, (float) dy);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.dragging = false;
        return false;
    }

    @Override
    protected boolean navigable() {
        return !this.followMouse;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!this.active || this.followMouse) {
            return false;
        }
        return switch (key) {
            case LEFT -> {
                turn(-15F, 0F);
                yield true;
            }
            case RIGHT -> {
                turn(15F, 0F);
                yield true;
            }
            default -> false;
        };
    }
}
