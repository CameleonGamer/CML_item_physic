package cml.itemphysic.physics;

import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic baked sandbox simulation for {@link cml.itemphysic.forms.ItemRainForm}.
 *
 * <p>Items fall under gravity, collide with each other, and stabilize when at rest.</p>
 */
public class ItemRainSim
{
    private static final double GRAVITY = 18.0D;
    private static final double FRICTION = 0.78D;
    private static final int FRAMES = 240;
    private static final double DT = 1.0D / 60.0D;
    private static final double STABILITY_VELOCITY_THRESHOLD = 0.00001D;
    private static final double SUPPORT_GAP_THRESHOLD = 0.001D;
    private static final int COLLISION_ITERATIONS = 5;
    private static final int MAX_DRAIN_FRAMES = 600;

    /** Per-frame world-space state of every body. */
    public static final class Frame
    {
        public final float[] x, y, z;
        public final Quaternionf[] q;

        public Frame(int n)
        {
            this.x = new float[n];
            this.y = new float[n];
            this.z = new float[n];
            this.q = new Quaternionf[n];

            for (int i = 0; i < n; i++)
            {
                this.q[i] = new Quaternionf();
            }
        }
    }

    public final ItemBody[] bodies;
    public final Frame[] frames;
    public final float[] spawnProgress;
    public final int frameCount;

    private ItemRainSim(ItemBody[] bodies, float[] spawn, Frame[] frames)
    {
        this.bodies = bodies;
        this.spawnProgress = spawn;
        this.frames = frames;
        this.frameCount = frames.length;
    }

    public int frameIndex(double progress)
    {
        int max = frameCount - 1;
        return Math.min(max, (int) (progress * max));
    }

    public double frameProgress(int frame)
    {
        int max = frameCount - 1;
        return (double) frame / max;
    }

    public double getProgress()
    {
        return this.frameProgress(this.frameIndex(1.0D));
    }

    // ─── Physics bake (rain mode) ───

    public static ItemRainSim bake(ItemBody[] bodies, double spawnInterval)
    {
        int n = bodies.length;
        double totalTime = FRAMES * DT;
        float[] spawn = new float[n];
        for (int i = 0; i < n; i++) spawn[i] = (float) (i * spawnInterval);

        List<Frame> frameList = new ArrayList<>();

        for (int f = 0; f <= FRAMES; f++)
        {
            double t = (double) f * DT;

            for (int i = 0; i < n; i++)
            {
                if (!bodies[i].spawned && t >= spawn[i])
                {
                    bodies[i].spawned = true;
                    if (Math.abs(bodies[i].spinSpeed) < 1.0E-4F)
                    {
                        Random random = new Random(i);
                        bodies[i].spinSpeed = 0.1F + random.nextFloat() * 0.3F;
                        bodies[i].spinAxis.set(
                            random.nextFloat() * 2 - 1,
                            random.nextFloat() * 2 - 1,
                            random.nextFloat() * 2 - 1
                        ).normalize();
                    }
                }
            }

            stepPhysics(bodies);

            Frame frame = new Frame(n);
            for (int i = 0; i < n; i++)
            {
                frame.x[i] = (float) bodies[i].pos.x;
                frame.y[i] = (float) bodies[i].pos.y;
                frame.z[i] = (float) bodies[i].pos.z;
                frame.q[i].set(bodies[i].quat);
            }
            frameList.add(frame);
        }

        // Settle drain
        int drain = 0;
        while (drain < MAX_DRAIN_FRAMES)
        {
            boolean allSettled = true;
            for (ItemBody b : bodies) {
                if (b.spawned && !b.settled) { allSettled = false; break; }
            }
            if (allSettled) break;

            stepPhysics(bodies);

            Frame frame = new Frame(n);
            for (int i = 0; i < n; i++)
            {
                frame.x[i] = (float) bodies[i].pos.x;
                frame.y[i] = (float) bodies[i].pos.y;
                frame.z[i] = (float) bodies[i].pos.z;
                frame.q[i].set(bodies[i].quat);
            }
            frameList.add(frame);
            drain++;
        }

        float[] normSpawn = new float[n];
        for (int i = 0; i < n; i++) normSpawn[i] = Math.min(1.0F, (float) (spawn[i] / totalTime));
        return new ItemRainSim(bodies, normSpawn, frameList.toArray(new Frame[0]));
    }

    // ─── Heap bake (placement algorithm, no physics) ───

    public static ItemRainSim heapBake(ItemBody[] bodies, double spawnInterval, double dropHeight,
                                       double bounce, boolean canOverlap, double itemsOffset)
    {
        int n = bodies.length;
        double totalTime = FRAMES * DT;
        float[] spawn = new float[n];
        for (int i = 0; i < n; i++) spawn[i] = (float) (i * spawnInterval);

        // Compute final pile positions using placement algorithm
        double[] finalY = computeHeapPositions(bodies, canOverlap, itemsOffset);
        double minFall = 1.5D; // minimum fall height so every item gets visible falling animation
        double[] spawnY = new double[n];
        for (int i = 0; i < n; i++)
        {
            spawnY[i] = Math.max(dropHeight + bodies[i].halfY, finalY[i] + minFall);
        }

        List<Frame> frameList = new ArrayList<>();
        double gravity = GRAVITY;

        for (int f = 0; f <= FRAMES; f++)
        {
            double t = (double) f * DT;

            for (int i = 0; i < n; i++)
            {
                if (!bodies[i].spawned && t >= spawn[i])
                {
                    bodies[i].spawned = true;
                    // Set initial position at spawn height with final XZ
                    bodies[i].pos.set(bodies[i].pos.x, spawnY[i], bodies[i].pos.z);
                    // Random spin for visual effect
                    if (Math.abs(bodies[i].spinSpeed) < 1.0E-4F)
                    {
                        Random random = new Random(i);
                        bodies[i].spinSpeed = 0.1F + random.nextFloat() * 0.3F;
                        bodies[i].spinAxis.set(
                            random.nextFloat() * 2 - 1,
                            random.nextFloat() * 2 - 1,
                            random.nextFloat() * 2 - 1
                        ).normalize();
                    }
                }
            }

            // Move spawned, unsettled items toward their final Y with spin and bounce
            for (int i = 0; i < n; i++)
            {
                ItemBody b = bodies[i];
                if (!b.spawned || b.settled) continue;

                // Apply spin rotation while falling
                if (Math.abs(b.spinSpeed) > 1.0E-4F)
                {
                    b.quat.rotateAxis(b.spinSpeed * (float) DT, b.spinAxis.x, b.spinAxis.y, b.spinAxis.z);
                    b.spinSpeed *= (float) 0.98D;
                }

                double targetY = finalY[i];

                if (b.bounceState == 0)
                {
                    // First descent toward target
                    b.vel.y -= gravity * DT;
                    b.pos.y += b.vel.y * DT;

                    if (b.pos.y <= targetY)
                    {
                        if (bounce > 0.001D && Math.abs(b.vel.y) > 0.05D)
                        {
                            b.pos.y = targetY;
                            b.spinSpeed = 0.0F;
                            b.vel.y = -b.vel.y * bounce;
                            b.bounceState = 1;
                        }
                        else
                        {
                            b.pos.y = targetY;
                            b.spinSpeed = 0.0F;
                            b.settled = true;
                        }
                    }
                }
                else
                {
                    // Bounce ascent/descent (no spin during bounces)
                    b.vel.y -= gravity * DT;
                    b.pos.y += b.vel.y * DT;

                    if (b.pos.y <= targetY)
                    {
                        if (bounce > 0.001D && Math.abs(b.vel.y) > 0.05D)
                        {
                            b.pos.y = targetY;
                            b.vel.y = -b.vel.y * bounce * 0.6D;
                        }
                        else
                        {
                            b.pos.y = targetY;
                            b.spinSpeed = 0.0F;
                            b.settled = true;
                        }
                    }
                }
            }

            Frame frame = new Frame(n);
            for (int i = 0; i < n; i++)
            {
                frame.x[i] = (float) bodies[i].pos.x;
                frame.y[i] = (float) bodies[i].pos.y;
                frame.z[i] = (float) bodies[i].pos.z;
                frame.q[i].set(bodies[i].quat);
            }
            frameList.add(frame);
        }

        // Settle drain for any remaining unsettled items
        int drain = 0;
        while (drain < MAX_DRAIN_FRAMES)
        {
            boolean allSettled = true;
            for (ItemBody b : bodies) {
                if (b.spawned && !b.settled) { allSettled = false; break; }
            }
            if (allSettled) break;

            for (int i = 0; i < n; i++)
            {
                ItemBody b = bodies[i];
                if (!b.spawned || b.settled) continue;
                b.pos.y = finalY[i];
                b.vel.set(0, 0, 0);
                b.settled = true;
            }

            Frame frame = new Frame(n);
            for (int i = 0; i < n; i++)
            {
                frame.x[i] = (float) bodies[i].pos.x;
                frame.y[i] = (float) bodies[i].pos.y;
                frame.z[i] = (float) bodies[i].pos.z;
                frame.q[i].set(bodies[i].quat);
            }
            frameList.add(frame);
            drain++;
        }

        float[] normSpawn = new float[n];
        for (int i = 0; i < n; i++) normSpawn[i] = Math.min(1.0F, (float) (spawn[i] / totalTime));
        return new ItemRainSim(bodies, normSpawn, frameList.toArray(new Frame[0]));
    }

    // ─── Heap placement algorithm ───

    private static double[] computeHeapPositions(ItemBody[] bodies, boolean canOverlap, double itemsOffset)
    {
        int n = bodies.length;
        double[] finalY = new double[n];
        List<double[]> placed = new ArrayList<>();  // [x, z, radiusX, radiusZ, topY]

        for (int i = 0; i < n; i++)
        {
            ItemBody body = bodies[i];

            if (placed.isEmpty())
            {
                finalY[i] = body.halfY;
            }
            else
            {
                double bestSupportTop = 0;

                if (canOverlap)
                {
                    for (double[] p : placed)
                    {
                        double rX = body.halfX + p[2] + itemsOffset;
                        double rZ = body.halfZ + p[3] + itemsOffset;
                        double overlapX = rX - Math.abs(body.pos.x - p[0]);
                        double overlapZ = rZ - Math.abs(body.pos.z - p[1]);

                        if (overlapX > 0 && overlapZ > 0)
                        {
                            double pTop = p[4];
                            if (pTop > bestSupportTop) bestSupportTop = pTop;
                        }
                    }
                }

                if (bestSupportTop > 0)
                {
                    finalY[i] = bestSupportTop + body.halfY;
                }
                else
                {
                    finalY[i] = body.halfY;
                }
            }

            double radiusX = body.halfX + itemsOffset;
            double radiusZ = body.halfZ + itemsOffset;
            double topY = finalY[i] + body.halfY;
            placed.add(new double[]{ body.pos.x, body.pos.z, radiusX, radiusZ, topY });
        }

        return finalY;
    }

    // ─── Physics engine (used by bake, not heapBake) ───

    private static void stepPhysics(ItemBody[] bodies)
    {
        for (ItemBody b : bodies)
        {
            if (!b.spawned || b.settled) continue;
            b.vel.y -= GRAVITY * DT;
        }

        for (ItemBody b : bodies)
        {
            if (!b.spawned || b.settled) continue;

            b.pos.x += b.vel.x * DT;
            b.pos.y += b.vel.y * DT;
            b.pos.z += b.vel.z * DT;

            if (Math.abs(b.spinSpeed) > 1.0E-4F)
            {
                b.quat.rotateAxis(b.spinSpeed * (float) DT, b.spinAxis.x, b.spinAxis.y, b.spinAxis.z);
                b.spinSpeed *= (float) 0.98D;
            }
        }

        for (ItemBody b : bodies)
        {
            if (!b.spawned) continue;
            b.effX = b.halfX;
            b.effY = b.halfY;
            b.effZ = b.halfZ;
        }

        for (ItemBody b : bodies)
        {
            if (!b.spawned || b.settled) continue;

            if (b.pos.y < b.effY)
            {
                b.pos.y = b.effY;
                if (b.vel.y < 0) b.vel.y = -b.vel.y * b.bounce;
                b.vel.x *= FRICTION;
                b.vel.z *= FRICTION;
                b.spinSpeed *= 0.1F;
            }
            else
            {
                b.vel.x *= FRICTION;
                b.vel.z *= FRICTION;
            }
        }

        for (int iter = 0; iter < COLLISION_ITERATIONS; iter++)
        {
            for (int i = 0; i < bodies.length; i++)
            {
                ItemBody a = bodies[i];
                if (!a.spawned) continue;

                for (int j = i + 1; j < bodies.length; j++)
                {
                    ItemBody b = bodies[j];
                    if (!b.spawned) continue;
                    if (a.settled && b.settled) continue;

                    if (Math.abs(a.pos.x - b.pos.x) < a.effX + b.effX &&
                        Math.abs(a.pos.y - b.pos.y) < a.effY + b.effY &&
                        Math.abs(a.pos.z - b.pos.z) < a.effZ + b.effZ)
                    {
                        resolveCollision(a, b);
                    }
                }
            }
        }

        for (ItemBody b : bodies)
        {
            if (!b.spawned || b.settled) continue;

            if (b.vel.lengthSquared() < STABILITY_VELOCITY_THRESHOLD)
            {
                b.stableFrames++;
            }
            else
            {
                b.stableFrames = 0;
                continue;
            }

            if (isStable(b, bodies))
            {
                b.vel.set(0, 0, 0);
                b.spinSpeed = 0.0F;
                b.settled = true;
            }
        }
    }

    private static void resolveCollision(ItemBody a, ItemBody b)
    {
        double dx = a.pos.x - b.pos.x;
        double dy = a.pos.y - b.pos.y;
        double dz = a.pos.z - b.pos.z;

        double overlapX = (a.effX + b.effX) - Math.abs(dx);
        double overlapY = (a.effY + b.effY) - Math.abs(dy);
        double overlapZ = (a.effZ + b.effZ) - Math.abs(dz);

        if (overlapY < 0.3 * (a.effY + b.effY) && overlapY < overlapX && overlapY < overlapZ)
        {
            if (dy > 0) {
                a.pos.y += overlapY;
                a.vel.y = 0;
                b.vel.y = 0;
            } else {
                b.pos.y += overlapY;
                a.vel.y = 0;
                b.vel.y = 0;
            }
        }
        else if (overlapX < overlapY && overlapX < overlapZ)
        {
            if (dx > 0) { a.pos.x += overlapX / 2; b.pos.x -= overlapX / 2; }
            else { a.pos.x -= overlapX / 2; b.pos.x += overlapX / 2; }
            a.vel.x = 0; b.vel.x = 0;
        }
        else
        {
            if (dz > 0) { a.pos.z += overlapZ / 2; b.pos.z -= overlapZ / 2; }
            else { a.pos.z -= overlapZ / 2; b.pos.z += overlapZ / 2; }
            a.vel.z = 0; b.vel.z = 0;
        }
    }

    private static boolean hasSupportBelow(ItemBody b, ItemBody[] bodies)
    {
        if (Math.abs(b.pos.y - b.effY) < SUPPORT_GAP_THRESHOLD) return true;

        double bottom = b.pos.y - b.effY;
        for (ItemBody other : bodies)
        {
            if (other == b || !other.spawned) continue;
            double otherTop = other.pos.y + other.effY;
            double gap = bottom - otherTop;
            if (gap >= -SUPPORT_GAP_THRESHOLD && gap <= SUPPORT_GAP_THRESHOLD)
            {
                double ox = (b.effX + other.effX) - Math.abs(b.pos.x - other.pos.x);
                double oz = (b.effZ + other.effZ) - Math.abs(b.pos.z - other.pos.z);
                if (ox > 0 && oz > 0) return true;
            }
        }
        return false;
    }

    private static boolean isStable(ItemBody b, ItemBody[] bodies)
    {
        boolean stableOrientation = Math.abs(b.quat.w) > 0.9999;
        if (!stableOrientation) return false;

        if (Math.abs(b.pos.y - b.effY) < SUPPORT_GAP_THRESHOLD) return true;

        return b.stableFrames >= 8 && hasSupportBelow(b, bodies);
    }
}