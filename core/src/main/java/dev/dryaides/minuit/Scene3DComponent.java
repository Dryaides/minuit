package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * A viewport on a small 3D scene, drawn in software through {@link Draw} only: no OpenGL and no Minecraft class, so
 * it runs in any screen, in every version, and in a test.
 *
 * <pre>{@code
 * add(new Scene3DComponent(at(new Box("view", 10, 10, 160, 120)))
 *         .model(crate)
 *         .floor(Scene3DComponent.Floor.GRID)
 *         .autoRotate(true));
 * }</pre>
 *
 * <h2>Input</h2>
 * Drag turns the scene, and keeps turning it outside the box until release. The wheel zooms; at a limit it lets the
 * wheel go on to what is around. With focus, the arrows turn by 15 degrees (5 with shift), {@code +} and {@code -}
 * zoom, Home resets, and Space pauses the rotation. The small button at the top right resets; it shows on hover or focus
 * once the view has moved. Under {@link BaseComponent#reducedMotion} nothing rotates by itself and a reset snaps.
 *
 * <h2>Cost</h2>
 * What costs in the game is the rectangles, not the triangles, and they are sent every frame even when the picture is
 * idle. Measured in 26.3 on the PC this was built on, and it climbs faster than their number: 844 took 6 ms, 2475 took
 * 28 ms, 7048 took 221 ms. Large boxes cost more for the same number: 844 took 22 ms in 400x300.
 * A frame therefore keeps to about 1000 rectangles by making the picture coarser. A picture is only made when the camera, a mesh or the theme changed, and allocates nothing.
 *
 * <p>Faces are opaque, and the picture is cut to whole cells of the box. Only a direct child of a panel has been tried:
 * not one inside a {@link ScrollComponent}, a {@link WindowComponent} or a dialog.
 */
@Incubating
public class Scene3DComponent extends BaseComponent {
    /** How points reach the picture. */
    public enum Projection {
        /** Far things look smaller. */
        PERSPECTIVE,
        /** Parallel: sizes do not change with depth. {@link #isometric()} gives the classic angle. */
        ISOMETRIC
    }

    /** What the scene stands on. It takes no part in fitting the camera. */
    public enum Floor { NONE, GRID, DISC }

    private static final int INSET = 1;
    /** A box seldom shows its full diagonal: without this, a fitted model leaves half the picture empty. */
    private static final float FIT_SHARE = 0.95F;
    private static final int MIN_CELLS = 2;
    private static final float KEY_DEGREES = 15F;
    private static final float KEY_FINE_DEGREES = 5F;
    private static final float WHEEL_ZOOM = 1.15F;
    private static final float KEY_ZOOM = 1.25F;
    private static final float DEFAULT_SPEED = 30F;
    private static final long MAX_STEP_MS = 100L;
    private static final int RESET_MS = 180;
    private static final float DEFAULT_YAW = 30F;
    private static final float DEFAULT_PITCH = 25F;
    private static final int RESET_SIZE = 13;
    private static final int RESET_GAP = 2;
    private static final int MIN_W_FOR_RESET = 44;
    private static final int MIN_H_FOR_RESET = 32;
    private static final int GRID_CELLS = 8;
    private static final int DISC_SIDES = 24;
    private static final float DISC_DROP = 0.02F;
    private static final float DISC_SHARE = 1.25F;
    private static final int AXIS_LENGTH = 7;
    private static final int AXIS_PAD = 3;
    private static final int MIN_FOR_AXES = 48;
    private static final int DISABLED_ALPHA = 0x70;
    /** About 6 ms per frame in a small box, where it was measured. */
    private static final int MAX_RECTS = 1000;
    private static final int MAX_CELL_SIZE = 8;

    private final Camera3D camera = new Camera3D();
    private final Renderer3D renderer = new Renderer3D();
    private final List<Mesh3D> meshes = new ArrayList<>();
    private final Mesh3D floorMesh = new Mesh3D();
    private final Glide resetGlide = new Glide(Slide.DOWN, RESET_MS);

    private VoxelModel model;
    private long contentRevision;
    private Floor floor = Floor.NONE;
    private boolean showAxes;
    private boolean resetButton = true;
    private int pixelSize = 1;
    private int cellSize = 1;
    private boolean smooth;
    /** Step of the smooth ladder in use, {@code -1} when it fell back to the blocky picture. */
    private int smoothStep;
    private int smoothScale = -1;
    private int drawnSub = 1;
    private long budgetContent = Long.MIN_VALUE;
    private int budgetW;
    private int budgetH;
    private String emptyText = "";
    private Runnable onViewChange;

    private boolean autoRotate;
    private boolean paused;
    private float speed = DEFAULT_SPEED;
    private long lastFrame = -1L;

    private float homeYaw = DEFAULT_YAW;
    private float homePitch = DEFAULT_PITCH;
    private float homeDistance = 3F;
    private boolean resetting;
    private float fromYaw;
    private float fromPitch;
    private float fromDistance;

    private boolean dragging;
    private boolean resetHeld;
    private double lastX;
    private double lastY;

    public Scene3DComponent(Box box) {
        super(box);
        camera.orbit(homeYaw, homePitch);
    }

    // ---- setup ----

    /** What the scene shows: replaces all meshes and fits the camera. Null or an empty mesh shows the empty text. */
    public Scene3DComponent mesh(Mesh3D mesh) {
        meshes.clear();
        model = null;
        if (mesh != null) {
            meshes.add(mesh);
        }
        contentRevision++;
        return fit();
    }

    /** Adds a mesh beside those there, and fits the camera. */
    public Scene3DComponent addMesh(Mesh3D mesh) {
        if (mesh != null) {
            meshes.add(mesh);
            contentRevision++;
            fit();
        }
        return this;
    }

    /** Shows a model and fits the camera. Later changes to the model show at once; the fit stays. */
    public Scene3DComponent model(VoxelModel model) {
        meshes.clear();
        this.model = model;
        if (model != null) {
            meshes.add(model.mesh());
        }
        contentRevision++;
        return fit();
    }

    /** Aims the camera at everything in the scene, and makes that the view a reset comes back to. */
    public Scene3DComponent fit() {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        boolean any = false;
        for (int i = 0; i < meshes.size(); i++) {
            Mesh3D m = meshes.get(i);
            if (m.vertexCount() > 0) {
                any = true;
                minX = Math.min(minX, m.minX());
                minY = Math.min(minY, m.minY());
                minZ = Math.min(minZ, m.minZ());
                maxX = Math.max(maxX, m.maxX());
                maxY = Math.max(maxY, m.maxY());
                maxZ = Math.max(maxZ, m.maxZ());
            }
        }
        if (any) {
            float dx = maxX - minX;
            float dy = maxY - minY;
            float dz = maxZ - minZ;
            float radius = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 2F;
            camera.fit((minX + maxX) / 2F, (minY + maxY) / 2F, (minZ + maxZ) / 2F, radius * (floor == Floor.DISC ? 1F : FIT_SHARE));
            homeDistance = camera.distance();
            buildFloor((minX + maxX) / 2F, minY, (minZ + maxZ) / 2F, Math.max(dx, dz) / 2F, radius);
        }
        resetting = false;
        return this;
    }

    private void buildFloor(float cx, float y, float cz, float halfSide, float radius) {
        floorMesh.clear();
        if (floor == Floor.GRID) {
            float cell = Math.max(1e-3F, 2F * radius / GRID_CELLS);
            int cells = GRID_CELLS;
            floorMesh.gridAt(cx, y, cz, cell * cells / 2F, cells, Mesh3D.tone(Mesh3D.Tone.GRID));
        } else if (floor == Floor.DISC) {
            floorMesh.disc(cx, y - DISC_DROP * radius, cz, halfSide * DISC_SHARE, DISC_SIDES, Mesh3D.tone(Mesh3D.Tone.TRACK));
        }
    }

    public Scene3DComponent floor(Floor floor) {
        this.floor = floor == null ? Floor.NONE : floor;
        contentRevision++;
        float yaw = camera.yaw();
        float pitch = camera.pitch();
        float distance = camera.distance();
        fit();
        camera.orbit(yaw, pitch).distance(distance);
        return this;
    }

    /** The view at the start and after a reset, in degrees. */
    public Scene3DComponent view(float yaw, float pitch) {
        homeYaw = Camera3D.wrap(yaw);
        homePitch = pitch;
        camera.orbit(yaw, pitch);
        homePitch = camera.pitch();
        resetting = false;
        return this;
    }

    public Scene3DComponent projection(Projection projection) {
        camera.projection(projection == Projection.ISOMETRIC ? Camera3D.Projection.ISOMETRIC : Camera3D.Projection.PERSPECTIVE);
        return fit();
    }

    /** Parallel projection at the classic isometric angle, kept by a reset. */
    public Scene3DComponent isometric() {
        camera.isometric();
        fit();
        return view(Camera3D.ISO_YAW, Camera3D.ISO_PITCH);
    }

    /** Turns by itself at 30 degrees a second, unless {@link BaseComponent#reducedMotion} is on. Dragging pauses it. */
    public Scene3DComponent autoRotate(boolean autoRotate) {
        this.autoRotate = autoRotate;
        paused = false;
        return this;
    }

    public Scene3DComponent autoRotate(boolean autoRotate, float degreesPerSecond) {
        speed = degreesPerSecond;
        return autoRotate(autoRotate);
    }

    /** Small x, y and z axes in the bottom left corner, in the theme's red, green and accent. Boxes under 48 cells have none. */
    public Scene3DComponent axes(boolean axes) {
        showAxes = axes;
        renderer.invalidate();
        return this;
    }

    /** False hides the small reset button. Home still resets. */
    public Scene3DComponent resetButton(boolean resetButton) {
        this.resetButton = resetButton;
        return this;
    }

    /** How many GUI units one picture cell takes at least: 1 by default, 2 is about four times cheaper and coarser. */
    public Scene3DComponent pixelSize(int pixelSize) {
        this.pixelSize = Math.max(1, pixelSize);
        cellSize = this.pixelSize;
        renderer.invalidate();
        return this;
    }

    /**
     * Smooth edges, off by default. On, the picture is made at screen-pixel size (the GUI scale's cells to a unit),
     * with two by two samples a pixel so that edges turn see-through instead of stepped. It costs rectangles: about
     * two to four times the blocky picture's in a small box. the rectangle budget still holds: past it the picture takes
     * fewer edge levels, then GUI-unit cells with see-through edges, then the blocky picture and its coarsening.
     * {@link #pixelSize} above 1 turns it off.
     */
    public Scene3DComponent smooth(boolean smooth) {
        this.smooth = smooth;
        smoothStep = 0;
        renderer.invalidate();
        return this;
    }

    public boolean smooth() {
        return smooth;
    }

    /** Screen pixels or samples to a GUI unit in the last smooth picture; 1 when blocky. */
    int smoothSub() {
        return drawnSub;
    }

    /** The GUI units one picture cell takes right now: {@link #pixelSize} or more, coarser over the rectangle budget. */
    int cellSize() {
        return cellSize;
    }

    /** What shows when the scene has nothing to draw. None by default. Pass it through your {@link Words}. */
    public Scene3DComponent emptyText(String emptyText) {
        this.emptyText = emptyText == null ? "" : emptyText;
        return this;
    }

    /** Where the light comes from. Up and a little to the side by default. */
    public Scene3DComponent light(float x, float y, float z) {
        renderer.light(x, y, z);
        return this;
    }

    /** Called when the player turns, zooms or resets the view; read it with {@link #yaw()} and {@link #pitch()}. Not for the auto-rotation. */
    public Scene3DComponent onViewChange(Runnable onViewChange) {
        this.onViewChange = onViewChange;
        return this;
    }

    public Scene3DComponent active(boolean active) {
        this.active = active;
        if (!active) {
            dragging = false;
            resetHeld = false;
        }
        return this;
    }

    @Override
    public Scene3DComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- state ----

    /** The view's turn around the vertical axis, in degrees. */
    public float yaw() {
        return camera.yaw();
    }

    /** The view's tilt, in degrees: positive looks down. */
    public float pitch() {
        return camera.pitch();
    }

    /** The camera. Turning it here does not change the view a reset comes back to. */
    Camera3D camera() {
        return camera;
    }

    /** The renderer, for what the last picture took. */
    Renderer3D renderer() {
        return renderer;
    }

    public boolean isEmpty() {
        for (int i = 0; i < meshes.size(); i++) {
            if (!meshes.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    boolean dragging() {
        return dragging;
    }

    /** True while the scene is turning by itself: on, not paused, not dragged, not disabled, motion allowed. */
    boolean rotating() {
        return autoRotate && !paused && !dragging && !resetting && active && !reducedMotion;
    }

    public boolean atHome() {
        float yawOff = Math.abs(shortest(camera.yaw(), homeYaw));
        return yawOff < 0.01F && Math.abs(camera.pitch() - homePitch) < 0.01F
                && Math.abs(camera.distance() - homeDistance) <= homeDistance * 1e-4F;
    }

    /** Where the reset button is, in the box's coordinates. It may be hidden. */
    Box resetBox() {
        return new Box("reset", resetX(), resetY(), RESET_SIZE, RESET_SIZE);
    }

    /** Goes back to the view of the start, smoothly unless {@link BaseComponent#reducedMotion} is on. */
    public void reset() {
        dragging = false;
        if (reducedMotion) {
            resetting = false;
            home();
        } else {
            fromYaw = camera.yaw();
            fromPitch = camera.pitch();
            fromDistance = camera.distance();
            resetting = true;
            resetGlide.replay();
        }
        told();
    }

    private void home() {
        camera.orbit(homeYaw, homePitch);
        camera.distance(homeDistance);
    }

    private static float shortest(float from, float to) {
        float off = (to - from) % 360F;
        if (off > 180F) {
            off -= 360F;
        } else if (off < -180F) {
            off += 360F;
        }
        return off;
    }

    private void told() {
        if (onViewChange != null) {
            onViewChange.run();
        }
    }

    // ---- what a frame does ----

    /** Moves time on: the rotation and a reset under way. Once a frame. */
    void step() {
        long now = BaseComponent.clock.getAsLong();
        long elapsed = lastFrame < 0L ? 0L : Math.max(0L, Math.min(MAX_STEP_MS, now - lastFrame));
        lastFrame = now;
        if (resetting) {
            if (reducedMotion || !resetGlide.moving()) {
                resetting = false;
                home();
            } else {
                float t = Math.max(0F, Math.min(1F, resetGlide.progress()));
                camera.orbit(fromYaw + shortest(fromYaw, homeYaw) * t, fromPitch + (homePitch - fromPitch) * t);
                camera.distance(fromDistance + (homeDistance - fromDistance) * t);
            }
        }
        if (rotating() && elapsed > 0L) {
            camera.orbitBy(speed * elapsed / 1000F, 0F);
        }
    }

    /** For a host that drives the rotation itself. */
    void spinning(boolean on) {
        autoRotate = on;
    }

    /** Makes the picture if it is out of date and draws it in this area. */
    void drawScene(Draw d, Theme t, int ax, int ay, int aw, int ah, int corner) {
        if (model != null) {
            model.mesh();
        }
        if (aw / pixelSize < MIN_CELLS || ah / pixelSize < MIN_CELLS || isEmpty()) {
            return;
        }
        Palette p = t.palette();
        long content = contentRevision + floorMesh.version() * 31L;
        for (int i = 0; i < meshes.size(); i++) {
            content += meshes.get(i).version() * (i * 2L + 37L);
        }
        if (content != budgetContent || aw != budgetW || ah != budgetH) {
            budgetContent = content;
            budgetW = aw;
            budgetH = ah;
            cellSize = pixelSize;
            smoothStep = 0;
        }
        if (smooth && pixelSize == 1 && drawSmooth(d, p, content, ax, ay, aw, ah, corner)) {
            return;
        }
        drawnSub = 1;
        renderer.smooth(1, 4).lineWidth(1);
        while (true) {
            makePicture(p, content, aw / cellSize, ah / cellSize, Math.max(0, corner) / cellSize);
            int next = cellSize + 1;
            if (renderer.rectCount() <= MAX_RECTS || next > MAX_CELL_SIZE
                    || aw / next < MIN_CELLS || ah / next < MIN_CELLS) {
                break;
            }
            cellSize = next;
        }
        int left = ax + (aw - aw / cellSize * cellSize) / 2;
        int top = ay + (ah - ah / cellSize * cellSize) / 2;
        if (active) {
            renderer.emit(d, left, top, cellSize);
        } else {
            renderer.emit(d, left, top, cellSize, DISABLED_ALPHA);
        }
    }

    /**
     * The smooth ladder, see {@link #smooth(boolean)}. Each step is {@code sub, samples}: a step that needs more
     * rectangles than the budget gives way to the next one, and that choice holds until the content or the
     * size changes. False when every step was too costly.
     */
    private boolean drawSmooth(Draw d, Palette p, long content, int ax, int ay, int aw, int ah, int corner) {
        int gs = Smooth.scale(d);
        if (gs != smoothScale) {
            smoothScale = gs;
            smoothStep = 0;
        }
        int[] steps = gs > 1 ? new int[] {gs, 2, 5, gs, 1, 1, 1, 4, 3} : new int[] {1, 4, 4, 1, 2, 3};
        while (smoothStep >= 0 && smoothStep * 3 < steps.length) {
            int sub = steps[smoothStep * 3];
            int samples = steps[smoothStep * 3 + 1];
            long cells = (long) aw * sub * samples * ah * sub * samples;
            if (cells <= Smooth.MAX_CELLS * 2L) {
                renderer.smooth(samples, steps[smoothStep * 3 + 2]).lineWidth(sub);
                makeSmooth(p, content, aw * sub, ah * sub, sub, Math.max(0, corner) * sub, aw, ah);
                if (renderer.rectCount() <= MAX_RECTS) {
                    drawnSub = sub;
                    if (sub > 1) {
                        d.push(ax, ay, 1F / sub);
                        renderer.emit(d, 0, 0, 1, active ? 255 : DISABLED_ALPHA);
                        d.pop();
                    } else {
                        renderer.emit(d, ax, ay, 1, active ? 255 : DISABLED_ALPHA);
                    }
                    return true;
                }
            }
            smoothStep++;
        }
        smoothStep = -1;
        return false;
    }

    private void makeSmooth(Palette p, long content, int cellsW, int cellsH, int sub, int cut, int aw, int ah) {
        if (!renderer.stale(camera, content, p, cellsW, cellsH, cut)) {
            return;
        }
        renderer.made(camera, content, p, cut);
        renderer.begin(camera, p, cellsW, cellsH);
        if (floor != Floor.NONE) {
            renderer.draw(floorMesh);
        }
        for (int i = 0; i < meshes.size(); i++) {
            renderer.draw(meshes.get(i));
        }
        if (showAxes && aw >= MIN_FOR_AXES && ah >= MIN_FOR_AXES) {
            int at = (AXIS_PAD + AXIS_LENGTH) * sub;
            renderer.axes(at, cellsH - at, AXIS_LENGTH * sub, p.bad(), p.good(), p.accent());
        }
        renderer.cutCorners(cut);
        renderer.end();
    }

    private void makePicture(Palette p, long content, int cellsW, int cellsH, int cut) {
        if (!renderer.stale(camera, content, p, cellsW, cellsH, cut)) {
            return;
        }
        renderer.made(camera, content, p, cut);
        renderer.begin(camera, p, cellsW, cellsH);
        if (floor != Floor.NONE) {
            renderer.draw(floorMesh);
        }
        for (int i = 0; i < meshes.size(); i++) {
            renderer.draw(meshes.get(i));
        }
        if (showAxes && cellsW >= MIN_FOR_AXES && cellsH >= MIN_FOR_AXES) {
            int at = AXIS_PAD + AXIS_LENGTH;
            renderer.axes(at, cellsH - at, AXIS_LENGTH, p.bad(), p.good(), p.accent());
        }
        renderer.cutCorners(cut);
        renderer.end();
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        frame.well(d, p, x, y, w, h, focused && active);
        step();
        int ax = x + INSET;
        int ay = y + INSET;
        int aw = w - 2 * INSET;
        int ah = h - 2 * INSET;
        if (aw <= 0 || ah <= 0) {
            return;
        }
        if (isEmpty()) {
            RowLook.drawEmpty(d, p, emptyText, active, ax, ay, aw, ah);
            return;
        }
        drawScene(d, t, ax, ay, aw, ah, frame.corner() - INSET);
        if (resetShows(hovered)) {
            drawReset(d, t, mx, my);
        }
    }

    private boolean resetFits() {
        return resetButton && w >= MIN_W_FOR_RESET && h >= MIN_H_FOR_RESET;
    }

    private boolean resetShows(boolean hovered) {
        return active && resetFits() && !isEmpty() && (hovered || focused || resetHeld) && (!atHome() || resetting || resetHeld);
    }

    private int resetX() {
        return x + w - INSET - RESET_GAP - RESET_SIZE;
    }

    private int resetY() {
        return y + INSET + RESET_GAP;
    }

    private boolean overReset(double mx, double my) {
        return mx >= resetX() && mx < resetX() + RESET_SIZE && my >= resetY() && my < resetY() + RESET_SIZE;
    }

    private void drawReset(Draw d, Theme t, int mx, int my) {
        Palette p = t.palette();
        boolean over = overReset(mx, my) && !dragging;
        int face = p.raised();
        int rx = resetX();
        int ry = resetY();
        // A solid face: a see-through one would let the model's colours fight the icon.
        if (over) {
            face = p.raisedHover();
        }
        t.frame().raised(d, p, rx, ry, RESET_SIZE, RESET_SIZE, face | 0xFF000000, !resetHeld);
        IconArt icon = t.icon(Icon.REFRESH);
        int ink = Shapes.contrast(p.raisedText(), face | 0xFF000000) >= Ink.TEXT ? p.raisedText() : Ink.on(p, face);
        d.icon(icon, rx + (RESET_SIZE - icon.width()) / 2, ry + (RESET_SIZE - icon.height()) / 2, ink);
    }

    // ---- input ----

    private boolean accepts() {
        return visible && active && settled();
    }

    @Override
    protected boolean takesFocus() {
        return visible && active;
    }

    @Override
    protected boolean navigable() {
        return accepts();
    }

    private boolean inBox(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!accepts() || button != 0 || !inBox(mx, my)) {
            return false;
        }
        if (resetFits() && !isEmpty() && !atHome() && overReset(mx, my)) {
            resetHeld = true;
            return true;
        }
        if (isEmpty()) {
            return true;
        }
        resetting = false;
        dragging = true;
        lastX = mx;
        lastY = my;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) {
            return false;
        }
        if (!visible || !active) {
            dragging = false;
            return false;
        }
        turnBy(mx, my);
        return true;
    }

    /** Follows the pointer: the front of the scene goes the way it moves. */
    void turnBy(double mx, double my) {
        float yaw = (float) (lastX - mx) * EntityPreviewComponent.DRAG_YAW_DEGREES;
        float pitch = (float) (my - lastY) * EntityPreviewComponent.DRAG_PITCH_DEGREES;
        lastX = mx;
        lastY = my;
        if (yaw != 0F || pitch != 0F) {
            camera.orbitBy(yaw, pitch);
            told();
        }
    }

    /** Starts a turn from this point, for a host that decides when a press becomes a drag. */
    void startTurn(double mx, double my) {
        resetting = false;
        dragging = true;
        lastX = mx;
        lastY = my;
    }

    void endTurn() {
        dragging = false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean held = resetHeld;
        boolean was = dragging;
        resetHeld = false;
        dragging = false;
        if (held && accepts() && overReset(mx, my)) {
            BaseComponent.click();
            reset();
            return true;
        }
        return was || held;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!accepts() || !inBox(mx, my) || isEmpty() || amount == 0D) {
            return false;
        }
        return zoomSteps(-amount, WHEEL_ZOOM);
    }

    private boolean zoomSteps(double out, float factor) {
        resetting = false;
        boolean moved = camera.zoomBy((float) Math.pow(factor, out));
        if (moved) {
            told();
        }
        return moved;
    }

    /** The keys that turn and reset. Returns false for any other. */
    boolean turnKey(InputKey key, boolean shift) {
        float by = shift ? KEY_FINE_DEGREES : KEY_DEGREES;
        switch (key) {
            case LEFT -> turnStep(by, 0F);
            case RIGHT -> turnStep(-by, 0F);
            case UP -> turnStep(0F, -by);
            case DOWN -> turnStep(0F, by);
            case HOME -> {
                if (!atHome()) {
                    BaseComponent.click();
                    reset();
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void turnStep(float yaw, float pitch) {
        if (resetting) {
            resetting = false;
            home();
        }
        camera.orbitBy(yaw, pitch);
        told();
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!accepts() || isEmpty()) {
            return false;
        }
        if (key == InputKey.SPACE && autoRotate) {
            paused = !paused;
            BaseComponent.click();
            return true;
        }
        return turnKey(key, shift);
    }

    @Override
    public boolean charTyped(int codePoint) {
        if (!accepts() || isEmpty()) {
            return false;
        }
        if (codePoint == '+' || codePoint == '=') {
            zoomSteps(-1D, KEY_ZOOM);
            return true;
        }
        if (codePoint == '-' || codePoint == '_') {
            zoomSteps(1D, KEY_ZOOM);
            return true;
        }
        return false;
    }

    /** True when Space has paused the rotation. */
    boolean paused() {
        return paused;
    }
}
