package dev.dryaides.minuit;

/**
 * Any component as a HUD part: a ZoneGauge minigame bar over the world, a second hotbar from
 * {@link HotbarComponent#hud}, a {@link GaugeComponent}. The component keeps its own box; the layer draws it
 * shifted to where it placed the part, so components that keep child boxes move whole. It takes no mouse (the
 * HUD has no pointer): a mod drives it from its own keys, as {@code ZoneGaugeComponent.hit()} from a key handler.
 *
 * <pre>{@code
 * ZoneGaugeComponent forge = new ZoneGaugeComponent(new Box("forge", 0, 0, 120, 30)).zones(ZoneGaugeComponent.centred(0.4F, 0.1F));
 * HudLayer.main().add(new HudWidget<>("mymod", "forge", HudAnchor.CENTER, forge).visibleWhen(forge::running));
 * }</pre>
 */
@Incubating
public class HudWidget<C extends BaseComponent> extends HudPart {

    public final C component;
    private long lastMs = Long.MIN_VALUE;
    private float frameTicks = 1F;

    public HudWidget(String modId, String id, HudAnchor anchor, C component) {
        super(modId, id, anchor, component.w, component.h);
        this.component = component;
    }

    @Override
    protected void update(Draw d, float partial) {
        size(this.component.w, this.component.h);
        long now = BaseComponent.clock().getAsLong();
        if (!this.shown) {
            this.lastMs = Long.MIN_VALUE;
        }
        this.frameTicks = this.lastMs == Long.MIN_VALUE ? 1F : Math.max(0F, (now - this.lastMs) / 50F);
        this.lastMs = now;
    }

    @Override
    protected void draw(Draw d, Theme theme, int x, int y, float partial) {
        int shiftX = x - this.component.x;
        int shiftY = y - this.component.y;
        d.push(shiftX, shiftY, 1F);
        this.component.draw(d, theme, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, this.frameTicks);
        d.pop();
    }
}
