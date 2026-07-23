package cml.itemphysic.physics;

import net.minecraft.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * A single simulated item body for the baked sandbox rain/physics. Holds a
 * position, linear velocity, orientation and angular velocity. Evolved
 * deterministically by {@link ItemRainSim} so the whole pile can be baked once
 * and scrubbed on the film timeline.
 */
public class ItemBody
{
    public final ItemStack stack;
    public final double half;
    public final double halfX;
    public final double halfY;
    public final double halfZ;

    public final Vector3d pos = new Vector3d();
    public final Vector3d vel = new Vector3d();

    public final Quaternionf quat = new Quaternionf();
    public final Vector3f spinAxis = new Vector3f(0.0F, 1.0F, 0.0F);
    public float spinSpeed = 0.0F;
    public boolean settled = false;
    public boolean spawned = false;

    /** Effective axis-aligned half-extents after accounting for the body's
     *  current rotation. Updated each solver step by
     *  {@link ItemRainSim#computeRotatedAABBs}. */
    public double effX;
    public double effY;
    public double effZ;

    /** Local axes in world space (columns of the rotation matrix). Computed
     *  from {@link #quat} each step alongside effX/Y/Z. Used by the SAT-based
     *  OBB-OBB collision resolver. */
    public float ax, ay, az;  // local X axis → world
    public float bx, by, bz;  // local Y axis → world
    public float cx, cy, cz;  // local Z axis → world

    public ItemBody(ItemStack stack, double half)
    {
        this.stack = stack;
        this.half = half;
        this.halfX = half;
        this.halfY = half;
        this.halfZ = half;
        this.effX = half;
        this.effY = half;
        this.effZ = half;
    }

    public ItemBody(ItemStack stack, double halfX, double halfY, double halfZ)
    {
        this.stack = stack;
        this.half = Math.min(halfX, Math.min(halfY, halfZ));
        this.halfX = halfX;
        this.halfY = halfY;
        this.halfZ = halfZ;
        this.effX = halfX;
        this.effY = halfY;
        this.effZ = halfZ;
    }
}
