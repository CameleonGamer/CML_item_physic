package cml.itemphysic.physics;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A small, deterministic rigid-body drop simulation that is <b>baked</b> into a
 * lookup table so it can be sampled by a normalized progress value
 * {@code p in [0, 1]} and scrubbed on the film timeline.
 *
 * <p>The integration uses <b>semi-implicit (symplectic) Euler</b>, which
 * updates velocity before position and preserves energy far better than plain
 * Euler for this kind of ballistic motion:</p>
 * <pre>
 *     v += (F / m) * dt
 *     x += v * dt
 * </pre>
 *
 * <p>Ground contact is resolved with a <b>coefficient of restitution</b>
 * {@code e} ({@code v_y = -e * v_y}) which produces bounces whose height decays
 * geometrically like a real object, and Coulomb-style ground friction that
 * bleeds off horizontal and angular velocity on contact. Orientation is
 * integrated from an angular velocity that damps over time and drops sharply on
 * impact (friction), so the tumble naturally freezes once the item settles.</p>
 *
 * <p>The whole simulation is a pure function of its parameters, so identical
 * parameters always yield the same motion (required for timeline scrubbing).</p>
 */
public class ItemDropSimulation
{
    private static final float GRAVITY = 9.81F;
    private static final int STEPS = 600;
    private static final float DT = 1.0F / 120.0F;

    /** Baked vertical offset (blocks above resting position) per step. */
    private final float[] heights = new float[STEPS + 1];

    /** Baked orientation per step. */
    private final Quaternionf[] orientations = new Quaternionf[STEPS + 1];

    /**
     * The step at which the item is fully at rest (on the ground and no longer
     * rotating). Progress p in [0, 1] is mapped onto [0, restStep] so that p=1
     * corresponds exactly to the item settled at its final position/orientation.
     */
    private int restStep = STEPS;

    private ItemDropSimulation()
    {
    }

    /**
     * Runs the simulation once and bakes the result.
     *
     * @param dropHeight start height (blocks) above the resting position
     * @param bounce     0..1 -> coefficient of restitution 0..~0.8
     * @param spins      desired number of tumbling turns during the fall
     * @param seed       deterministic seed (e.g. item stack hash) for the axis
     */
    public static ItemDropSimulation bake(float dropHeight, float bounce, float spins, int seed)
    {
        ItemDropSimulation sim = new ItemDropSimulation();
        sim.run(dropHeight, bounce, spins, seed);
        return sim;
    }

    private static float hash01(int seed, int salt)
    {
        int h = seed * 374761393 + salt * 668265263;
        h = (h ^ h >>> 13) * 1274126177;
        return ((h ^ h >>> 16) & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private void run(float dropHeight, float bounce, float spins, int seed)
    {
        float e = Math.max(0.0F, Math.min(bounce, 1.0F)) * 0.8F;

        /* Pseudo-random but deterministic tumble axis. */
        float ax = hash01(seed, 1) * 2.0F - 1.0F;
        float ay = (hash01(seed, 2) * 2.0F - 1.0F) * 0.5F;
        float az = hash01(seed, 3) * 2.0F - 1.0F;
        Vector3f axis = new Vector3f(ax, ay, az);

        if (axis.lengthSquared() < 1.0E-4F)
        {
            axis.set(1.0F, 0.0F, 0.0F);
        }

        axis.normalize();

        /* Time of free fall from dropHeight: t = sqrt(2h/g). The tumble rate is
         * chosen so that roughly `spins` turns happen before the first impact. */
        float fallTime = (float) Math.sqrt(Math.max(dropHeight, 1.0E-3F) * 2.0F / GRAVITY);
        float angularSpeed = fallTime > 1.0E-3F ? spins * (float) (Math.PI * 2.0) / fallTime : 0.0F;

        float y = dropHeight;
        float vy = 0.0F;

        /* Angular resistance applied each step (air/rolling resistance). */
        float angularDamping = 0.015F;

        Quaternionf orientation = new Quaternionf();

        /* Simulate the raw physics into local buffers. We keep integrating until
         * the object has provably come to rest (sitting on the ground, no
         * residual velocity), so we capture every bounce regardless of how long
         * it takes. */
        float[] rawH = new float[STEPS * 4 + 1];
        Quaternionf[] rawO = new Quaternionf[STEPS * 4 + 1];
        int rawSteps = 0;

        boolean atRest = false;

        for (int i = 0; i < rawH.length; i++)
        {
            rawH[i] = Math.max(y, 0.0F);
            rawO[i] = new Quaternionf(orientation);
            rawSteps = i;

            if (atRest)
            {
                continue;
            }

            /* Semi-implicit Euler: velocity first, then position. */
            vy -= GRAVITY * DT;
            y += vy * DT;

            /* Ground collision with a coefficient of restitution. */
            if (y < 0.0F)
            {
                y = 0.0F;

                if (vy < 0.0F)
                {
                    float impactSpeed = -vy;
                    vy = impactSpeed * e;

                    /* Impact kills a chunk of spin (ground friction). */
                    angularSpeed *= 0.35F;

                    /* The object is at rest once the rebound height is
                     * negligible (energy loss model: each bounce keeps a
                     * fraction e of the speed, so rebound height decays as
                     * h_n = h_0 * e^(2n)). This gives a smooth, geometrically
                     * decaying tail of bounces instead of a hard snap. */
                    float reboundHeight = (vy * vy) / (2.0F * GRAVITY);

                    if (reboundHeight < 0.003F || e <= 0.0F)
                    {
                        vy = 0.0F;
                        angularSpeed = 0.0F;
                        atRest = true;
                    }
                }
            }

            /* Angular velocity damps over time (air/rolling resistance). */
            angularSpeed *= 1.0F - angularDamping;

            if (Math.abs(angularSpeed) > 1.0E-4F)
            {
                orientation.rotateAxis(angularSpeed * DT, axis.x, axis.y, axis.z);
            }
        }

        /* Re-sample the raw trajectory so the ENTIRE fall (every bounce) is
         * stretched across the full [0, 1] progress range. Without this the
         * bounce happens in a tiny sliver of the timeline and looks like the
         * item snaps to a stop. */
        this.restStep = STEPS;
        int last = Math.max(1, rawSteps);

        for (int i = 0; i <= STEPS; i++)
        {
            float t = (float) i / (float) STEPS;
            float f = t * last;
            int a = (int) f;
            int b = Math.min(a + 1, last);
            float w = f - a;

            this.heights[i] = rawH[a] + (rawH[b] - rawH[a]) * w;
            this.orientations[i] = new Quaternionf(rawO[a]).slerp(rawO[b], w);
        }
    }

    /** Vertical offset (blocks) at normalized progress p in [0, 1]. */
    public float heightAt(float p)
    {
        float f = this.clamp01(p) * this.restStep;
        int i = (int) f;
        float t = f - i;

        if (i >= this.restStep)
        {
            return this.heights[this.restStep];
        }

        return this.heights[i] + (this.heights[i + 1] - this.heights[i]) * t;
    }

    /** Interpolated orientation at normalized progress p in [0, 1],
     *  stored into {@code out} to avoid allocation. */
    public void orientationAt(float p, Quaternionf out)
    {
        float f = this.clamp01(p) * this.restStep;
        int i = (int) f;
        float t = f - i;

        if (i >= this.restStep)
        {
            out.set(this.orientations[this.restStep]);
        }
        else
        {
            this.orientations[i].slerp(this.orientations[i + 1], t, out);
        }
    }

    /** Deprecated — allocates. Prefer {@link #orientationAt(float, Quaternionf)}. */
    @Deprecated
    public Quaternionf orientationAt(float p)
    {
        float f = this.clamp01(p) * this.restStep;
        int i = (int) f;
        float t = f - i;

        if (i >= this.restStep)
        {
            return new Quaternionf(this.orientations[this.restStep]);
        }

        return new Quaternionf(this.orientations[i]).slerp(this.orientations[i + 1], t);
    }

    private float clamp01(float v)
    {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }
}
