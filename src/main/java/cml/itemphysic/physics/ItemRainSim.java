package cml.itemphysic.physics;

import org.joml.Quaternionf;
import org.joml.Vector3d;

/**
 * Deterministic baked sandbox simulation for {@link cml.itemphysic.forms.ItemRainForm}.
 *
 * <p>Many {@link ItemBody} bodies fall under gravity, bounce on a flat floor
 * (y = 0), and collide with each other as solid axis-aligned boxes using a real
 * Minimum Translation Vector (MTV) solver. The solver runs several iterative
 * passes per step and only sleeps a body once it is at rest and no longer
 * overlapping any neighbour, so objects push, pile and roll instead of
 * interpenetrating.</p>
 *
 * <p>The whole simulation is evolved once with a fixed timestep and recorded
 * frame-by-frame, so the renderer can sample the exact state at any normalized
 * {@code progress} in [0, 1] — keeping the motion scrubbable on the film
 * timeline while still showing genuine solid-vs-solid collisions.</p>
 */
public class ItemRainSim
{
    private static final double GRAVITY = 18.0D;
    private static final double RESTITUTION = 0.35D;
    private static final double FRICTION = 0.78D;
    private static final double STOP_SPEED = 0.03D;
    private static final int FRAMES = 240;
    private static final double DT = 1.0D / 60.0D;
    private static final double EPSILON = 1.0E-4D;
    private static final double SETTLE_OVERLAP_EPSILON = 5.0E-4D;
    private static final int SOLVER_ITERATIONS = 128;
    private static final double MAX_VELOCITY = 40.0D;
    private static final double MAX_VELOCITY_SQ = MAX_VELOCITY * MAX_VELOCITY;

    /** Maximum vertical gap (blocks) between a body's bottom and another body's
     *  top for the pair to be considered "resting contact" in the support-chain
     *  propagation ({@link #computeSupported}).  Must be well above
     *  {@link #EPSILON} (the minimum separation the position solver guarantees),
     *  but small enough to reject bodies that are merely near each other.  0.01
     *  blocks (~1 cm in Minecraft) is a safe middle ground. */
    private static final double SUPPORT_GAP_THRESHOLD = 0.02D;
    private static final double MIN_SUPPORT_HEIGHT = 0.04D;  // Minimum height for support detection
    private static final double MIN_SUPPORT_THICKNESS = 0.06D;  // Minimum effective thickness for support detection

    /** Per-frame world-space state of every body. */
    public static class Frame
    {
        public final float[] x;
        public final float[] y;
        public final float[] z;
        public final Quaternionf[] q;

        Frame(int n)
        {
            this.x = new float[n];
            this.y = new float[n];
            this.z = new float[n];
            this.q = new Quaternionf[n];
        }
    }

    public final ItemBody[] bodies;
    public final Frame[] frames;
    public final float[] spawnProgress;

    private ItemRainSim(ItemBody[] bodies, float[] spawnProgress)
    {
        this.bodies = bodies;
        this.spawnProgress = spawnProgress;
        this.frames = new Frame[FRAMES + 1];

        for (int f = 0; f <= FRAMES; f++)
        {
            this.frames[f] = new Frame(bodies.length);
        }
    }

    /** Bakes the sandbox once. {@code spawnInterval} is the fraction of the
     * timeline over which items appear progressively (rain). */
    public static ItemRainSim bake(ItemBody[] bodies, double spawnInterval)
    {
        int n = bodies.length;
        float[] spawn = new float[n];

        for (int i = 0; i < n; i++)
        {
            spawn[i] = (float) (i * spawnInterval / Math.max(1, n));
        }

        ItemRainSim sim = new ItemRainSim(bodies, spawn);

        for (int f = 0; f <= FRAMES; f++)
        {
            double t = (double) f / FRAMES;

            for (int i = 0; i < n; i++)
            {
                if (!bodies[i].spawned && t >= sim.spawnProgress[i])
                {
                    bodies[i].spawned = true;
                }
            }

            for (int i = 0; i < n; i++)
            {
                sim.frames[f].x[i] = (float) bodies[i].pos.x;
                sim.frames[f].y[i] = (float) bodies[i].pos.y;
                sim.frames[f].z[i] = (float) bodies[i].pos.z;
                sim.frames[f].q[i] = new Quaternionf(bodies[i].quat);
            }

            step(bodies);
        }

        return sim;
    }

    /* ---- Pipeline: forces -> integrate velocity -> integrate position ->
     * compute rotated AABBs -> floor collision -> iterated positional
     * resolution (mass-based, no impulses) -> velocity resolution (once) ->
     * sleep/settle. ------------------------------------------------------ */

    private static void step(ItemBody[] bodies)
    {
        /* 1-2. Forces + velocity integration. */
        for (ItemBody b : bodies)
        {
            if (!b.spawned || b.settled)
            {
                continue;
            }

            b.vel.y -= GRAVITY * DT;
        }

        /* 3. Position integration + spin. */
        for (ItemBody b : bodies)
        {
            if (!b.spawned || b.settled)
            {
                continue;
            }

            b.pos.x += b.vel.x * DT;
            b.pos.y += b.vel.y * DT;
            b.pos.z += b.vel.z * DT;

            if (Math.abs(b.spinSpeed) > 1.0E-4F)
            {
                b.quat.rotateAxis(b.spinSpeed * (float) DT, b.spinAxis.x, b.spinAxis.y, b.spinAxis.z);
                b.spinSpeed *= (float) (1.0D - 0.02D);
            }
        }

        /* 3b. Compute rotation-expanded AABBs (effective half-extents) so the
         * collision box matches the visual mesh even when the item is rotated. */
        computeRotatedAABBs(bodies);

        /* 4-7. Iterated positional resolution.  The floor clamp is applied
         * at the START of every iteration so that the MTV resolver cannot
         * push bodies above the ground.  Without this, items on the ground
         * gradually float upward as lateral MTV corrections accumulate. */
        for (int iteration = 0; iteration < SOLVER_ITERATIONS; iteration++)
        {
            /* 4a. Floor collision (uses effY for the rest height so the
             * rotated box sits flush on the ground). */
            for (ItemBody b : bodies)
            {
                if (!b.spawned || b.settled)
                {
                    continue;
                }

                double restY = b.effY;

                if (b.pos.y < restY)
                {
                    b.pos.y = restY;

                    if (b.vel.y < 0.0D)
                    {
                        /* Apply restitution with damping to prevent perpetual bouncing */
                        b.vel.y = -b.vel.y * RESTITUTION * 0.95D;
                    }

                    b.vel.x *= FRICTION;
                    b.vel.z *= FRICTION;
                    b.spinSpeed *= 0.4F;

                    if (Math.abs(b.vel.y) < STOP_SPEED)
                    {
                        b.vel.y = 0.0D;
                    }
                }
            }

            boolean any = resolvePositions(bodies);

            if (!any)
            {
                break;
            }
        }

        /* 8. Final floor clamp — the iterated loop above clamps BEFORE each
         * resolvePositions pass, but the LAST pass can push bodies below the
         * floor.  A final clamp after the loop guarantees no body clips
         * through the ground. */
        for (ItemBody b : bodies)
        {
            if (!b.spawned || b.settled) continue;
            if (b.pos.y < b.effY)
            {
                b.pos.y = b.effY;
                if (b.vel.y < 0.0D)
                {
                    b.vel.y = -b.vel.y * RESTITUTION;
                    if (Math.abs(b.vel.y) < STOP_SPEED) b.vel.y = 0.0D;
                }
                b.vel.x *= FRICTION;
                b.vel.z *= FRICTION;
            }
        }

        /* 8b. Final support correction — ensure all items are properly supported
         * after the main solver pass. This catches items that might have been
         * pushed slightly above their support during collision resolution. */
        boolean[] finalSupported = computeSupported(bodies);
        for (int i = 0; i < bodies.length; i++)
        {
            ItemBody b = bodies[i];
            if (!b.spawned || b.settled || finalSupported[i]) continue;
            
            /* If an item is not supported but is very close to the ground,
             * force it to be supported to prevent floating */
            if (b.pos.y <= b.effY + SUPPORT_GAP_THRESHOLD + MIN_SUPPORT_THICKNESS)
            {
                b.pos.y = b.effY;
            }
        }

        /* 8c. Final velocity damping — eliminate any residual velocity that might
         * cause items to jitter or "fly" at the end of the simulation. */
        for (ItemBody b : bodies)
        {
            if (!b.spawned || b.settled) continue;
            
            /* Damp any remaining velocity to prevent final-frame jitter */
            b.vel.x *= 0.8D;
            b.vel.y *= 0.8D;
            b.vel.z *= 0.8D;
            
            /* If velocity is very small, zero it to prevent micro-movement */
            if (b.vel.lengthSquared() < STOP_SPEED * STOP_SPEED)
            {
                b.vel.set(0.0D, 0.0D, 0.0D);
            }
        }

        /* 9. Velocity resolution — called once after positions are settled.
         * Applies restitution + friction along the collision normal. */
        resolveVelocities(bodies);

        /* 9b. Velocity cap — prevents explosion-like behavior by limiting
         * the maximum speed of any body to MAX_VELOCITY blocks/sec.  This
         * allows realistic falling and bouncing (max fall speed from 16
         * blocks is ~24 blocks/sec) while preventing cumulative lateral
         * scattering from dense multi-body collisions. */
        for (ItemBody b : bodies)
        {
            if (!b.spawned || b.settled) continue;
            double vSq = b.vel.x * b.vel.x + b.vel.y * b.vel.y + b.vel.z * b.vel.z;
            if (vSq > MAX_VELOCITY_SQ)
            {
                double scale = MAX_VELOCITY / Math.sqrt(vSq);
                b.vel.x *= scale; b.vel.y *= scale; b.vel.z *= scale;
            }
        }

        computeRotatedAABBs(bodies);

        /* 9c. Keep settled items locked to their support surface.  Spin
         * damping changes effY gradually, and without adjustment settled
         * items would float above or sink into the ground.  Only items near
         * the floor (pos.y ≈ effY) are adjusted — items on top of other
         * items are handled naturally by the solver in subsequent frames. */
        for (ItemBody b : bodies)
        {
            if (!b.spawned || !b.settled) continue;
            if (b.pos.y <= b.effY + SUPPORT_GAP_THRESHOLD)
            {
                b.pos.y = b.effY;
            }
        }

        /* 10. Compute support chain — which bodies rest on the floor or on
         * another body that ultimately touches the floor.  A body floating
         * in mid-air (no support) must NOT settle, even if it happens to be
         * slow and non-overlapping, or it would freeze mid-fall. */
        boolean[] supported = computeSupported(bodies);

        /* 11. Kill residual vertical velocity on supported bodies and on
         * non-overlapping bodies with tiny Y velocity.  The PBD velocity
         * resolver applies impulses along collision normals which, for
         * stacked bodies, creates tiny persistent upward velocities (~0.078
         * m/s) that never dissipate below STOP_SPEED.  Non-overlapping items
         * with tiny Y velocity are floating above the pile — zeroing Y lets
         * them drop back onto the stack (or freeze in place) instead of
         * bouncing at the limit cycle forever. */
        for (int i = 0; i < bodies.length; i++)
        {
            ItemBody b = bodies[i];
            if (!b.spawned || b.settled) continue;

            if (supported[i])
            {
                if (Math.abs(b.vel.y) < STOP_SPEED * 4.0D)
                {
                    b.vel.y = 0.0D;
                }
            }
            else if (Math.abs(b.vel.y) < STOP_SPEED * 4.0D)
            {
                /* Non-supported item with tiny Y velocity — check it's not
                 * overlapping (if it overlaps, the resolver is still working). */
                if (!overlapsAny(b, bodies, SETTLE_OVERLAP_EPSILON))
                {
                    b.vel.y = 0.0D;
                }
            }
        }

        /* 12. Sleep / settle.  Only bodies that are NOT overlapping AND
         * slow AND supported may go to sleep. */
        for (int i = 0; i < bodies.length; i++)
        {
            ItemBody b = bodies[i];

            if (!b.spawned)
            {
                continue;
            }

            if (!Double.isFinite(b.pos.x) || !Double.isFinite(b.pos.y) || !Double.isFinite(b.pos.z))
            {
                b.pos.set(0.0D, b.effY, 0.0D);
                b.vel.set(0.0D, 0.0D, 0.0D);
                b.settled = true;
                continue;
            }

            boolean overlapping = overlapsAny(b, bodies, SETTLE_OVERLAP_EPSILON);
            boolean slow = b.vel.lengthSquared() < (STOP_SPEED * 2.0D) * (STOP_SPEED * 2.0D);  // Stricter threshold
            boolean stableOrientation = Math.abs(b.quat.w) > 0.999f;  // Nearly identity

            if (!overlapping && slow && supported[i] && stableOrientation)
            {
                b.vel.set(0.0D, 0.0D, 0.0D);
                b.spinSpeed = 0.0F;
                b.settled = true;
            }
        }

        /* 13. Gentle righting toward identity for supported NON-settled items only.
         * Settled items should already be stable and not move. The righting rate
         * is proportional to tilt so the motion is unnoticeable per-frame. */
        Quaternionf id = new Quaternionf();
        for (int i = 0; i < bodies.length; i++)
        {
            ItemBody b = bodies[i];
            if (!b.spawned || b.settled) continue;
            if (!supported[i]) continue;

            float align = Math.abs(b.quat.w);
            if (align > 0.9999f) continue;
            float rate = 0.005f + (1.0f - align) * 0.03f;
            if (rate > 0.04f) rate = 0.04f;
            b.quat.slerp(id, rate);
        }
    }

    /** Computes {@link ItemBody#effX}, {@link ItemBody#effY}, {@link ItemBody#effZ}
     * from the unrotated half-extents and each body's current {@code quat}.
     * The effective AABB is the axis-aligned bounding box of the fully rotated
     * OBB, so the collision mesh always matches the visual surface. */
    private static void computeRotatedAABBs(ItemBody[] bodies)
    {
        for (ItemBody b : bodies)
        {
            if (!b.spawned)
            {
                b.effX = b.halfX;
                b.effY = b.halfY;
                b.effZ = b.halfZ;
                continue;
            }

            org.joml.Quaternionf q = b.quat;
            float x = q.x, y = q.y, z = q.z, w = q.w;

            float m00 = 1 - 2*(y*y + z*z);
            float m01 = 2*(x*y + z*w);
            float m02 = 2*(x*z - y*w);
            float m10 = 2*(x*y - z*w);
            float m11 = 1 - 2*(x*x + z*z);
            float m12 = 2*(y*z + x*w);
            float m20 = 2*(x*z + y*w);
            float m21 = 2*(y*z - x*w);
            float m22 = 1 - 2*(x*x + y*y);

            b.effX = b.halfX * Math.abs(m00) + b.halfY * Math.abs(m01) + b.halfZ * Math.abs(m02);
            b.effY = b.halfX * Math.abs(m10) + b.halfY * Math.abs(m11) + b.halfZ * Math.abs(m12);
            b.effZ = b.halfX * Math.abs(m20) + b.halfY * Math.abs(m21) + b.halfZ * Math.abs(m22);

            b.ax = m00; b.ay = m10; b.az = m20;  // local X axis in world
            b.bx = m01; b.by = m11; b.bz = m21;  // local Y axis in world
            b.cx = m02; b.cy = m12; b.cz = m22;  // local Z axis in world
        }
    }

    /** Tests two OBBs for overlap using the Separating Axis Theorem (SAT).
     *  Returns the penetration depth (positive if overlapping, &le;0 if not)
     *  and sets {@code normal} to the unit-length collision normal (pointing
     *  from {@code b} toward {@code a}).  No allocations.
     *
     *  <p>A tolerance of {@link #EPSILON} is used on the separating-axis test so
     *  that floating-point noise on nearly-degenerate cross-product axes does
     *  not produce false-positive overlaps.</p> */
    private static double satOverlap(ItemBody a, ItemBody b, Vector3d normal)
    {
        double dx = a.pos.x - b.pos.x;
        double dy = a.pos.y - b.pos.y;
        double dz = a.pos.z - b.pos.z;

        if (Math.abs(dx) < EPSILON && Math.abs(dy) < EPSILON && Math.abs(dz) < EPSILON)
        {
            normal.set(0.0D, 1.0D, 0.0D);
            return a.effY + b.effY;
        }

        /* Local axes (columns of the 3×3 rotation matrix). */
        float a0x = a.ax, a0y = a.ay, a0z = a.az;
        float a1x = a.bx, a1y = a.by, a1z = a.bz;
        float a2x = a.cx, a2y = a.cy, a2z = a.cz;
        float b0x = b.ax, b0y = b.ay, b0z = b.az;
        float b1x = b.bx, b1y = b.by, b1z = b.bz;
        float b2x = b.cx, b2y = b.cy, b2z = b.cz;

        double ahX = a.halfX, ahY = a.halfY, ahZ = a.halfZ;
        double bhX = b.halfX, bhY = b.halfY, bhZ = b.halfZ;

        double bestDepth = Double.MAX_VALUE;
        float bestNx = 0, bestNy = 0, bestNz = 0;

        /* Test the 15 axes: 3 body A axes, 3 body B axes, 9 cross products. */
        for (int pass = 0; pass < 3; pass++)
        {
            int start = pass == 0 ? 0 : pass == 1 ? 3 : 6;
            int end   = pass == 0 ? 3 : pass == 1 ? 6 : 15;
            for (int idx = start; idx < end; idx++)
            {
                boolean cross = idx >= 6;
                double nx, ny, nz;

                if (cross)
                {
                    int ai = (idx - 6) / 3, bi = (idx - 6) % 3;
                    float axx = ai == 0 ? a0x : ai == 1 ? a1x : a2x;
                    float axy = ai == 0 ? a0y : ai == 1 ? a1y : a2y;
                    float axz = ai == 0 ? a0z : ai == 1 ? a1z : a2z;
                    float bxx = bi == 0 ? b0x : bi == 1 ? b1x : b2x;
                    float bxy = bi == 0 ? b0y : bi == 1 ? b1y : b2y;
                    float bxz = bi == 0 ? b0z : bi == 1 ? b1z : b2z;
                    float cx = axy * bxz - axz * bxy;
                    float cy = axz * bxx - axx * bxz;
                    float cz = axx * bxy - axy * bxx;
                    double lenSq = (double) cx * cx + (double) cy * cy + (double) cz * cz;
                    if (lenSq < 1.0E-10D) continue;
                    double inv = 1.0D / Math.sqrt(lenSq);
                    nx = cx * inv; ny = cy * inv; nz = cz * inv;
                }
                else if (idx < 3)
                {
                    nx = idx == 0 ? a0x : idx == 1 ? a1x : a2x;
                    ny = idx == 0 ? a0y : idx == 1 ? a1y : a2y;
                    nz = idx == 0 ? a0z : idx == 1 ? a1z : a2z;
                }
                else
                {
                    int bi = idx - 3;
                    nx = bi == 0 ? b0x : bi == 1 ? b1x : b2x;
                    ny = bi == 0 ? b0y : bi == 1 ? b1y : b2y;
                    nz = bi == 0 ? b0z : bi == 1 ? b1z : b2z;
                }

                double dA0 = nx * a0x + ny * a0y + nz * a0z;
                double dA1 = nx * a1x + ny * a1y + nz * a1z;
                double dA2 = nx * a2x + ny * a2y + nz * a2z;
                double rA = ahX * Math.abs(dA0) + ahY * Math.abs(dA1) + ahZ * Math.abs(dA2);

                double dB0 = nx * b0x + ny * b0y + nz * b0z;
                double dB1 = nx * b1x + ny * b1y + nz * b1z;
                double dB2 = nx * b2x + ny * b2y + nz * b2z;
                double rB = bhX * Math.abs(dB0) + bhY * Math.abs(dB1) + bhZ * Math.abs(dB2);

                double cDist = Math.abs(dx * nx + dy * ny + dz * nz);

                double depth = rA + rB - cDist;
                /* Separating axis with tolerance for FP noise. */
                if (depth <= -EPSILON) return -1.0D;

                if (depth < bestDepth)
                {
                    bestDepth = depth;
                    bestNx = (float) nx; bestNy = (float) ny; bestNz = (float) nz;
                }
            }
        }

        /* If the best depth is below noise threshold, treat as non-overlapping. */
        if (bestDepth <= EPSILON) return -1.0D;

        /* Ensure normal points from B toward A. */
        double dot = dx * bestNx + dy * bestNy + dz * bestNz;
        if (dot < 0.0D) { bestNx = -bestNx; bestNy = -bestNy; bestNz = -bestNz; }

        normal.set(bestNx, bestNy, bestNz);
        return bestDepth;
    }

    /** Resolves every overlapping pair using SAT-based OBB collision detection
     *  with position-only correction along the true collision normal.  Per-axis
     *  inverse masses are preserved so floor-contact bodies keep infinite mass
     *  on Y (preventing ground penetration) while still sliding laterally. */
    private static boolean resolvePositions(ItemBody[] bodies)
    {
        boolean any = false;
        Vector3d normal = new Vector3d();

        for (int i = 0; i < bodies.length; i++)
        {
            ItemBody a = bodies[i];

            if (!a.spawned || !finite(a.pos))
            {
                continue;
            }

            for (int j = 0; j < i; j++)
            {
                ItemBody b = bodies[j];

                if (!b.spawned) continue;

                /* Both fully settled → skip (should not overlap if stable). */
                if (a.settled && b.settled) continue;

                /* SAT OBB-OBB test. */
                double depth = satOverlap(a, b, normal);
                if (depth <= 0.0D) continue;

                any = true;

                /* Per-axis inverse masses (same model as before). */
                double imX_A = a.settled ? 0.0D : 1.0D;
                double imY_A = (a.settled || a.pos.y <= a.effY + EPSILON) ? 0.0D : 1.0D;
                double imZ_A = a.settled ? 0.0D : 1.0D;
                double imX_B = b.settled ? 0.0D : 1.0D;
                double imY_B = (b.settled || b.pos.y <= b.effY + EPSILON) ? 0.0D : 1.0D;
                double imZ_B = b.settled ? 0.0D : 1.0D;

                double nx = normal.x, ny = normal.y, nz = normal.z;
                double pen = depth;

                /* Push along the full 3D SAT normal, weighted by per-axis
                 * inverse masses.  This lets items slide apart along the
                 * true collision direction instead of being constrained to
                 * a single world axis. */
                double totalX = imX_A + imX_B;
                double totalY = imY_A + imY_B;
                double totalZ = imZ_A + imZ_B;

                double sx = pen * nx;
                double sy = pen * ny;
                double sz = pen * nz;

                if (totalX >= EPSILON)
                {
                    double rA = imX_A / totalX, rB = imX_B / totalX;
                    a.pos.x += sx * rA;
                    b.pos.x -= sx * rB;
                }
                if (totalY >= EPSILON)
                {
                    double rA = imY_A / totalY, rB = imY_B / totalY;
                    a.pos.y += sy * rA;
                    b.pos.y -= sy * rB;
                }
                if (totalZ >= EPSILON)
                {
                    double rA = imZ_A / totalZ, rB = imZ_B / totalZ;
                    a.pos.z += sz * rA;
                    b.pos.z -= sz * rB;
                }
            }
        }

        return any;
    }

    /** Finds the SAT collision normal between two OBBs regardless of whether
     *  they currently overlap.  Returns the normal (pointing from B toward A)
     *  and the penetration depth (negative if separated).  Used by the
     *  velocity resolver so that restitution is applied even to pairs that
     *  are exactly touching (depth ≈ 0) after position resolution. */
    private static double satContact(ItemBody a, ItemBody b, Vector3d normal)
    {
        double depth = satOverlap(a, b, normal);
        if (depth > 0.0D) return depth;
        /* Find the minimum separation / maximum overlap axis without the
         * EPSILON threshold — this gives us the contact normal even for
         * exactly-touching pairs. */
        double dx = a.pos.x - b.pos.x;
        double dy = a.pos.y - b.pos.y;
        double dz = a.pos.z - b.pos.z;

        if (Math.abs(dx) < EPSILON && Math.abs(dy) < EPSILON && Math.abs(dz) < EPSILON)
        {
            normal.set(0.0D, 1.0D, 0.0D);
            return a.effY + b.effY;
        }

        float a0x = a.ax, a0y = a.ay, a0z = a.az;
        float a1x = a.bx, a1y = a.by, a1z = a.bz;
        float a2x = a.cx, a2y = a.cy, a2z = a.cz;
        float b0x = b.ax, b0y = b.ay, b0z = b.az;
        float b1x = b.bx, b1y = b.by, b1z = b.bz;
        float b2x = b.cx, b2y = b.cy, b2z = b.cz;

        double ahX = a.halfX, ahY = a.halfY, ahZ = a.halfZ;
        double bhX = b.halfX, bhY = b.halfY, bhZ = b.halfZ;

        double bestDepth = Double.MAX_VALUE;
        float bestNx = 0, bestNy = 0, bestNz = 0;

        for (int pass = 0; pass < 3; pass++)
        {
            int start = pass == 0 ? 0 : pass == 1 ? 3 : 6;
            int end   = pass == 0 ? 3 : pass == 1 ? 6 : 15;
            for (int idx = start; idx < end; idx++)
            {
                boolean cross = idx >= 6;
                double nx, ny, nz;

                if (cross)
                {
                    int ai = (idx - 6) / 3, bi = (idx - 6) % 3;
                    float axx = ai == 0 ? a0x : ai == 1 ? a1x : a2x;
                    float axy = ai == 0 ? a0y : ai == 1 ? a1y : a2y;
                    float axz = ai == 0 ? a0z : ai == 1 ? a1z : a2z;
                    float bxx = bi == 0 ? b0x : bi == 1 ? b1x : b2x;
                    float bxy = bi == 0 ? b0y : bi == 1 ? b1y : b2y;
                    float bxz = bi == 0 ? b0z : bi == 1 ? b1z : b2z;
                    float cx = axy * bxz - axz * bxy;
                    float cy = axz * bxx - axx * bxz;
                    float cz = axx * bxy - axy * bxx;
                    double lenSq = (double) cx * cx + (double) cy * cy + (double) cz * cz;
                    if (lenSq < 1.0E-10D) continue;
                    double inv = 1.0D / Math.sqrt(lenSq);
                    nx = cx * inv; ny = cy * inv; nz = cz * inv;
                }
                else if (idx < 3)
                {
                    nx = idx == 0 ? a0x : idx == 1 ? a1x : a2x;
                    ny = idx == 0 ? a0y : idx == 1 ? a1y : a2y;
                    nz = idx == 0 ? a0z : idx == 1 ? a1z : a2z;
                }
                else
                {
                    int bi = idx - 3;
                    nx = bi == 0 ? b0x : bi == 1 ? b1x : b2x;
                    ny = bi == 0 ? b0y : bi == 1 ? b1y : b2y;
                    nz = bi == 0 ? b0z : bi == 1 ? b1z : b2z;
                }

                double dA0 = nx * a0x + ny * a0y + nz * a0z;
                double dA1 = nx * a1x + ny * a1y + nz * a1z;
                double dA2 = nx * a2x + ny * a2y + nz * a2z;
                double rA = ahX * Math.abs(dA0) + ahY * Math.abs(dA1) + ahZ * Math.abs(dA2);

                double dB0 = nx * b0x + ny * b0y + nz * b0z;
                double dB1 = nx * b1x + ny * b1y + nz * b1z;
                double dB2 = nx * b2x + ny * b2y + nz * b2z;
                double rB = bhX * Math.abs(dB0) + bhY * Math.abs(dB1) + bhZ * Math.abs(dB2);

        double cDist = Math.abs(dx * nx + dy * ny + dz * nz);
        double d = rA + rB - cDist;

        /* Add small epsilon to prevent micro-collisions from being missed */
        if (d < bestDepth && d > -1.0E-4D)
                {
                    bestDepth = d;
                    bestNx = (float) nx; bestNy = (float) ny; bestNz = (float) nz;
                }
            }
        }

        double dot = dx * bestNx + dy * bestNy + dz * bestNz;
        if (dot < 0.0D) { bestNx = -bestNx; bestNy = -bestNy; bestNz = -bestNz; }

        normal.set(bestNx, bestNy, bestNz);
        return bestDepth;
    }

    /** Velocity resolution — called ONCE per step after positional resolution
     * has settled. Uses SAT OBB contact normal to find the collision axis,
     * then applies restitution + friction along that normal for pairs that
     * are in contact (overlapping or touching) AND approaching.  Per-axis
     * inverse masses match {@link #resolvePositions}. */
    private static void resolveVelocities(ItemBody[] bodies)
    {
        Vector3d normal = new Vector3d();

        for (int i = 0; i < bodies.length; i++)
        {
            ItemBody a = bodies[i];

            if (!a.spawned || !finite(a.pos)) continue;

            for (int j = 0; j < i; j++)
            {
                ItemBody b = bodies[j];

                if (!b.spawned) continue;
                if (a.settled && b.settled) continue;

                /* Broad-phase AABB check — skip well-separated pairs. */
                double dx = Math.abs(a.pos.x - b.pos.x);
                double dy = Math.abs(a.pos.y - b.pos.y);
                double dz = Math.abs(a.pos.z - b.pos.z);
                double rx = a.effX + b.effX;
                double ry = a.effY + b.effY;
                double rz = a.effZ + b.effZ;
                if (dx > rx * 4.0D || dy > ry * 4.0D || dz > rz * 4.0D) continue;

                /* SAT contact normal (finds the minimum-penetration axis even
                 * for exactly-touching pairs after position resolution). */
                double depth = satContact(a, b, normal);
                double nx = normal.x, ny = normal.y, nz = normal.z;

                /* Only process overlapping or touching pairs (depth >= -EPSILON
                 * to allow exactly-touching stacked bodies to receive impulses). */
                if (depth < -EPSILON) continue;

                /* Relative velocity along the contact normal. */
                double relVn = (a.vel.x - b.vel.x) * nx
                             + (a.vel.y - b.vel.y) * ny
                             + (a.vel.z - b.vel.z) * nz;

                /* Only resolve if bodies are approaching (relVn < 0). */
                if (relVn >= 0.0D) continue;

                /* Per-axis inverse masses. */
                double imX_A = a.settled ? 0.0D : 1.0D;
                double imY_A = (a.settled || a.pos.y <= a.effY + EPSILON) ? 0.0D : 1.0D;
                double imZ_A = a.settled ? 0.0D : 1.0D;
                double imX_B = b.settled ? 0.0D : 1.0D;
                double imY_B = (b.settled || b.pos.y <= b.effY + EPSILON) ? 0.0D : 1.0D;
                double imZ_B = b.settled ? 0.0D : 1.0D;

                /* Effective inverse mass along the normal direction. */
                double wA = imX_A * nx * nx + imY_A * ny * ny + imZ_A * nz * nz;
                double wB = imX_B * nx * nx + imY_B * ny * ny + imZ_B * nz * nz;
                double totalW = wA + wB;

                if (totalW < EPSILON) continue;

                /* Adaptive restitution: if the approach velocity is less than
                 * one frame of gravity (GRAVITY * DT = 0.3), use fully inelastic
                 * (e=0) to break the gravity × restitution limit cycle (~0.078
                 * m/s).  The limit cycle's approach velocity (0.222 after
                 * gravity) is below 0.3, so e is forced to 0 and the bounce
                 * dissipates.  Normal high-speed impacts (>0.3 m/s) still use
                 * the full restitution with slight damping for stability. */
                double e = Math.abs(relVn) < GRAVITY * DT ? 0.0D : RESTITUTION * 0.95D;
                double impulse = -(1.0D + e) * relVn / totalW;

                /* Apply the impulse along the full 3D SAT normal, weighted by
                 * per-axis inverse masses.  This matches the position resolver
                 * and lets momentum exchange occur along the true collision
                 * direction. */
                /* Apply impulse with slight damping to reduce jitter */
                a.vel.x += impulse * nx * imX_A * 0.99D;
                a.vel.y += impulse * ny * imY_A;
                a.vel.z += impulse * nz * imZ_A * 0.99D;
                b.vel.x -= impulse * nx * imX_B * 0.99D;
                b.vel.y -= impulse * ny * imY_B;
                b.vel.z -= impulse * nz * imZ_B * 0.99D;

                /* Friction: damp only the TANGENTIAL component of the relative
                 * velocity (perpendicular to the collision normal).  The normal
                 * component is handled by the restitution impulse above. */
                double relVx = a.vel.x - b.vel.x;
                double relVy = a.vel.y - b.vel.y;
                double relVz = a.vel.z - b.vel.z;

                double relVnFriction = relVx * nx + relVy * ny + relVz * nz;

                double tx = relVx - relVnFriction * nx;
                double ty = relVy - relVnFriction * ny;
                double tz = relVz - relVnFriction * nz;

                a.vel.x -= tx * FRICTION * imX_A;
                a.vel.y -= ty * FRICTION * imY_A;
                a.vel.z -= tz * FRICTION * imZ_A;
                b.vel.x += tx * FRICTION * imX_B;
                b.vel.y += ty * FRICTION * imY_B;
                b.vel.z += tz * FRICTION * imZ_B;
            }
        }
    }

    /** Computes the set of bodies that are supported from below, propagating
     *  contact chains from the floor upward.  A body is supported if:
     *  <ol>
     *    <li>it directly touches the floor ({@code pos.y &le; effY + EPSILON}), or</li>
     *    <li>it rests on (X/Z overlap + vertical gap near zero &amp; center above)
     *        another body that is itself supported (transitive closure).</li>
     *  </ol>
     *
     *  <p>Used by the settle check to prevent bodies from sleeping in mid-air
     *  when mutual collisions cancel their velocity before anyone hits the
     *  ground.  Propagation iterates at most {@code n} times, which bounds
     *  the maximum stack height and guarantees termination.
     *
     *  @return a boolean array parallel to {@code bodies}, {@code true} if the
     *          body at that index is ultimately supported. */
    private static boolean[] computeSupported(ItemBody[] bodies)
    {
        int n = bodies.length;
        boolean[] supported = new boolean[n];

        /* Phase 1: bodies touching the floor are directly supported. */
        for (int i = 0; i < n; i++)
        {
            if (!bodies[i].spawned)
            {
                continue;
            }

            /* For thin items (like pressure plates), use a minimum effective thickness
             * to ensure they're detected as supported even if slightly above ground */
            double minHeight = Math.max(bodies[i].effY, 0.04D);  // At least 0.04 blocks high
            if (bodies[i].pos.y <= bodies[i].effY + SUPPORT_GAP_THRESHOLD + (minHeight - bodies[i].effY))
            {
                supported[i] = true;
            }
        }

        /* Phase 2: propagate support upward (max n iterations guards against
         * infinite loops from any theoretical cycle). */
        for (int iter = 0; iter < n; iter++)
        {
            boolean changed = false;

            for (int i = 0; i < n; i++)
            {
                if (!bodies[i].spawned || supported[i])
                {
                    continue;
                }

                for (int j = 0; j < n; j++)
                {
                    if (!bodies[j].spawned || !supported[j] || i == j)
                    {
                        continue;
                    }

                    double dx = bodies[i].pos.x - bodies[j].pos.x;
                    double dz = bodies[i].pos.z - bodies[j].pos.z;

                    /* Must overlap on X/Z to be resting on j. */
                    if (Math.abs(dx) >= bodies[i].effX + bodies[j].effX
                        || Math.abs(dz) >= bodies[i].effZ + bodies[j].effZ)
                    {
                        continue;
                    }

                    /* Vertical gap between i's bottom and j's top. */
                    double gap = (bodies[i].pos.y - bodies[i].effY)
                               - (bodies[j].pos.y + bodies[j].effY);

                    /* i rests on j if its bottom is close to j's top AND i's center is above j's center */
                    if (Math.abs(gap) < SUPPORT_GAP_THRESHOLD * 1.5  // More tolerant for item-item support
                        && bodies[i].pos.y > bodies[j].pos.y)
                    {
                        supported[i] = true;
                        changed = true;
                        break;
                    }
                }
            }

            if (!changed)
            {
                break;
            }
        }

        return supported;
    }

    private static boolean overlapsAny(ItemBody b, ItemBody[] bodies, double tolerance)
    {
        Vector3d normal = new Vector3d();

        for (ItemBody o : bodies)
        {
            if (o == b || !o.spawned) continue;

            double depth = satOverlap(b, o, normal);
            if (depth > tolerance) return true;
        }

        return false;
    }

    private static boolean finite(org.joml.Vector3d v)
    {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    /** Returns the baked frame index for a normalized progress in [0, 1]. */
    public int frameIndex(float progress)
    {
        float f = Math.max(0.0F, Math.min(1.0F, progress)) * FRAMES;
        int i = (int) f;

        return Math.min(FRAMES, i);
    }

}
