package dev.dryaides.minuit;

/**
 * An orbit camera: it looks at a target from a distance, turned by yaw and pitch in degrees. World y is up. At
 * yaw 0 and pitch 0 it stands on +z, looking towards -z, with +x on its right. Pitch stops short of the poles so
 * that up stays defined. Setters count a {@link #revision()}, which lets a renderer skip a picture that has not
 * changed.
 */
final class Camera3D {
    /** How points reach the picture. */
    public enum Projection {
        /** Far things look smaller. */
        PERSPECTIVE,
        /** Parallel: sizes do not change with depth. {@link #isometric()} gives the classic angle. */
        ISOMETRIC
    }

    /** The pitch of a true isometric view: every axis looks the same length. */
    public static final float ISO_PITCH = 35.264F;
    public static final float ISO_YAW = 45F;
    public static final float MAX_PITCH = 89F;
    private static final float DEFAULT_FOV = 40F;
    private static final float MIN_ZOOM = 0.35F;
    private static final float MAX_ZOOM = 3F;
    private static final float NEAR_SHARE = 0.02F;

    private Projection projection = Projection.PERSPECTIVE;
    private float yaw;
    private float pitch;
    private float distance = 3F;
    private float minDistance = 0.01F;
    private float maxDistance = 1000F;
    private float fov = DEFAULT_FOV;
    private float targetX;
    private float targetY;
    private float targetZ;
    private long revision;
    private boolean dirty = true;

    private final Vec3f right = new Vec3f();
    private final Vec3f up = new Vec3f();
    private final Vec3f forward = new Vec3f();
    private final Vec3f eye = new Vec3f();

    public Camera3D orbit(float yawDegrees, float pitchDegrees) {
        yaw = wrap(yawDegrees);
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitchDegrees));
        changed();
        return this;
    }

    /** Turns from where it is. */
    public Camera3D orbitBy(float yawDegrees, float pitchDegrees) {
        return orbit(yaw + yawDegrees, pitch + pitchDegrees);
    }

    public Camera3D distance(float distance) {
        this.distance = Math.max(minDistance, Math.min(maxDistance, distance));
        changed();
        return this;
    }

    /** Sets how near and far {@link #zoomBy(float)} and {@link #distance(float)} may go. */
    public Camera3D limits(float minDistance, float maxDistance) {
        this.minDistance = Math.max(1e-3F, minDistance);
        this.maxDistance = Math.max(this.minDistance, maxDistance);
        return distance(distance);
    }

    /** Multiplies the distance: below 1 comes closer. Returns true if the distance changed: false at a limit. */
    public boolean zoomBy(float factor) {
        float before = distance;
        distance(distance * factor);
        return before != distance;
    }

    public Camera3D target(float x, float y, float z) {
        targetX = x;
        targetY = y;
        targetZ = z;
        changed();
        return this;
    }

    public Camera3D projection(Projection projection) {
        this.projection = projection == null ? Projection.PERSPECTIVE : projection;
        changed();
        return this;
    }

    /** Parallel projection at the classic isometric angle. */
    public Camera3D isometric() {
        projection(Projection.ISOMETRIC);
        return orbit(ISO_YAW, ISO_PITCH);
    }

    /** The full vertical (or narrow side's) angle of view, from 10 to 120 degrees. It also sets how much a parallel view shows. */
    public Camera3D fov(float degrees) {
        fov = Math.max(10F, Math.min(120F, degrees));
        changed();
        return this;
    }

    /** Aims at a centre and stands where a sphere of that radius just fills the picture, with room to zoom either way. */
    public Camera3D fit(float cx, float cy, float cz, float radius) {
        float r = Math.max(1e-3F, radius);
        float half = (float) Math.toRadians(fov / 2F);
        float fitted = projection == Projection.PERSPECTIVE ? r / (float) Math.sin(half) : r / (float) Math.tan(half);
        minDistance = fitted * MIN_ZOOM;
        maxDistance = fitted * MAX_ZOOM;
        distance = fitted;
        return target(cx, cy, cz);
    }

    public Camera3D fit(Mesh3D mesh) {
        return fit(mesh.centerX(), mesh.centerY(), mesh.centerZ(), mesh.radius());
    }

    /** Copies everything but the revision's history. */
    public Camera3D set(Camera3D o) {
        projection = o.projection;
        yaw = o.yaw;
        pitch = o.pitch;
        distance = o.distance;
        minDistance = o.minDistance;
        maxDistance = o.maxDistance;
        fov = o.fov;
        targetX = o.targetX;
        targetY = o.targetY;
        targetZ = o.targetZ;
        changed();
        return this;
    }

    private void changed() {
        revision++;
        dirty = true;
    }

    static float wrap(float degrees) {
        float out = degrees % 360F;
        return out < 0F ? out + 360F : out;
    }

    public Projection projection() { return projection; }
    /** From 0 up to 360. */
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public float distance() { return distance; }
    public float minDistance() { return minDistance; }
    public float maxDistance() { return maxDistance; }
    public float fov() { return fov; }
    public long revision() { return revision; }

    /** Things nearer than this are cut away. A parallel view has no near plane: 0 and below still draw. */
    public float near() {
        return projection == Projection.PERSPECTIVE ? distance * NEAR_SHARE : -Float.MAX_VALUE;
    }

    /** Works out the camera's axes when something changed. The vectors returned below hold the result. */
    public void update() {
        if (!dirty) {
            return;
        }
        dirty = false;
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        float sy = (float) Math.sin(y);
        float cy = (float) Math.cos(y);
        float sp = (float) Math.sin(p);
        float cp = (float) Math.cos(p);
        forward.set(-sy * cp, -sp, -cy * cp);
        right.set(cy, 0F, -sy);
        up.cross(right, forward);
        eye.set(targetX - forward.x * distance, targetY - forward.y * distance, targetZ - forward.z * distance);
    }

    public Vec3f right() { update(); return right; }
    public Vec3f up() { update(); return up; }
    public Vec3f forward() { update(); return forward; }
    public Vec3f eye() { update(); return eye; }

    /** Pixels per world unit at one unit of depth (perspective), or per world unit (parallel), for a picture this size. */
    public float focal(int pictureW, int pictureH) {
        float half = Math.min(pictureW, pictureH) / 2F;
        float tan = (float) Math.tan(Math.toRadians(fov / 2F));
        return projection == Projection.PERSPECTIVE ? half / tan : half / (distance * tan);
    }

    /**
     * The ray through a point of a picture, for picking or a test.
     *
     * @param px the point, in cells of the picture
     * @param origin receives where the ray starts
     * @param direction receives its unit direction
     */
    public void ray(float px, float py, int pictureW, int pictureH, Vec3f origin, Vec3f direction) {
        update();
        float f = focal(pictureW, pictureH);
        float sx = (px - pictureW / 2F) / f;
        float sy = (pictureH / 2F - py) / f;
        if (projection == Projection.PERSPECTIVE) {
            origin.set(eye);
            direction.set(forward.x + right.x * sx + up.x * sy, forward.y + right.y * sx + up.y * sy, forward.z + right.z * sx + up.z * sy)
                    .normalize();
        } else {
            origin.set(eye.x + right.x * sx + up.x * sy, eye.y + right.y * sx + up.y * sy, eye.z + right.z * sx + up.z * sy);
            direction.set(forward);
        }
    }
}
