package cml.itemphysic.physics;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.AABB;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-world collision helpers used at render time to resolve the baked
 * {@link ItemDropSimulation} trajectory against the live Minecraft world:
 * <ul>
 *     <li>solid ground / block collision (downward raycast against the world);</li>
 *     <li>BBS actors and other Minecraft entities (their AABBs).</li>
 * </ul>
 *
 * <p>Everything here is intentionally world-query only: the baked trajectory is
 * computed deterministically so the timeline can be scrubbed, then these helpers
 * clamp/offset that trajectory to whatever the actual scene contains.</p>
 */
public final class WorldCollision
{
    /** Half-extent (blocks) of an item's collision box. */
    public static final double ITEM_HALF = 0.18D;

    private WorldCollision()
    {
    }

    /** Returns the Y (block coordinate) of the highest solid surface directly
     * below {@code originX/originY/originZ}, or {@code fallbackY} if there is no
     * world or no solid block within a sane range. The returned value already
     * accounts for the item's half-height so the item rests on top. */
    public static double groundY(World world, double originX, double originY, double originZ, double fallbackY)
    {
        if (world == null)
        {
            return fallbackY;
        }

        Vec3d start = new Vec3d(originX, originY + 2.0D, originZ);
        Vec3d end = new Vec3d(originX, originY - 64.0D, originZ);

        net.minecraft.util.hit.BlockHitResult hit = world.raycast(new RaycastContext(
            start, end,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            (Entity) null
        ));

        if (hit != null && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK)
        {
            BlockPos pos = hit.getBlockPos();
            double surface = pos.getY() + 1.0D;
            return surface - ITEM_HALF;
        }

        return fallbackY;
    }

    /** All solid/collidable AABBs (BBS format) near the given point: one for
     * each nearby Minecraft entity / BBS actor. Used to push items off actors. */
    public static List<AABB> entityBoxes(World world, double ox, double oy, double oz, double range)
    {
        List<AABB> boxes = new ArrayList<>();

        if (world == null)
        {
            return boxes;
        }

        Box area = new Box(ox - range, oy - range, oz - range, ox + range, oy + range, oz + range);

        for (Entity entity : world.getOtherEntities((Entity) null, area))
        {
            Box bb = entity.getBoundingBox();

            if (bb != null)
            {
                boxes.add(new AABB(bb.minX, bb.minY, bb.minZ, bb.maxX - bb.minX, bb.maxY - bb.minY, bb.maxZ - bb.minZ));
            }
        }

        return boxes;
    }

    /** Resolves the item AABB at {@code (x, y, z)} against a set of obstacle
     * AABBs, pushing it out along the smallest-penetration axis. Returns the
     * corrected Y (the only axis we nudge for resting support). */
    public static double resolveAgainst(double x, double y, double z, List<AABB> obstacles)
    {
        AABB item = new AABB(x - ITEM_HALF, y - ITEM_HALF, z - ITEM_HALF, ITEM_HALF * 2.0D, ITEM_HALF * 2.0D, ITEM_HALF * 2.0D);

        double bestY = y;

        for (AABB obstacle : obstacles)
        {
            if (item.intersects(obstacle))
            {
                double push = obstacle.maxY() + ITEM_HALF;
                bestY = Math.max(bestY, push);
            }
        }

        return bestY;
    }

    /** Convenience: world accessor from a rendering context's entity. */
    public static World worldOf(IEntity entity)
    {
        return entity == null ? null : entity.getWorld();
    }
}
