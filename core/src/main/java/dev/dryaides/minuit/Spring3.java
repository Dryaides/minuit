// Adapted from Cinematic Interactions (MIT, Copyright (c) 2026 Southware), client/Anim.java.
package dev.dryaides.minuit;

/**
 * A critically damped spring in three dimensions: a point that follows a moving target without overshoot, stepped
 * exactly for any frame time, so a slow frame lands where many fast ones would. {@code omega} is its stiffness in
 * radians per second; it closes about 99% of a gap in {@code 6.6 / omega} seconds.
 */
public final class Spring3 {
    private double x, y, z;
    private double vx, vy, vz;

    /** Puts the point on {@code (px, py, pz)} at rest. */
    public void snap(double px, double py, double pz) {
        this.x = px;
        this.y = py;
        this.z = pz;
        this.vx = this.vy = this.vz = 0;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    /** The point's speed, units per second. */
    public double speed() {
        return Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz);
    }

    /** Moves the point {@code dt} seconds toward {@code (tx, ty, tz)}. */
    public void update(double tx, double ty, double tz, double omega, double dt) {
        double e = Math.exp(-omega * dt);
        double dx = this.x - tx, kx = (this.vx + omega * dx) * dt;
        this.vx = (this.vx - omega * kx) * e;
        this.x = tx + (dx + kx) * e;
        double dy = this.y - ty, ky = (this.vy + omega * dy) * dt;
        this.vy = (this.vy - omega * ky) * e;
        this.y = ty + (dy + ky) * e;
        double dz = this.z - tz, kz = (this.vz + omega * dz) * dt;
        this.vz = (this.vz - omega * kz) * e;
        this.z = tz + (dz + kz) * e;
    }
}
