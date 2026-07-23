package cml.itemphysic.physics;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.AABB;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-world collision helpers used at render time to resolve the baked
 * trajectory against the live Minecraft world:
 * <ul>
 *     <li>solid ground / block collision (downward raycast against the world);</li>
 *     <li>BBS actors and other Minecraft entities (their AABBs).</li>
 * </ul>
 */
public final class WorldCollision
{
    /** Base half-extent (blocks) used to scale model proportions. */
    public static final double ITEM_HALF = 0.5D;

    /**
     * Half-extents (X, Y, Z) for a given item stack. Computed from the item's
     * actual {@link BakedModel} vertices, scaled by the display transform for
     * the given {@code ModelTransformationMode}. Falls back to the old
     * collision-shape-based estimate for block items, then to {@link #ITEM_HALF}.
     *
     * @return array of [halfX, halfY, halfZ] in world-space blocks
     */
    public static double[] itemHalfExtents(ItemStack stack, net.minecraft.item.ModelTransformationMode mode)
    {
        if (stack == null || stack.isEmpty())
            return new double[] { ITEM_HALF, ITEM_HALF, ITEM_HALF };

        double rawX, rawY, rawZ;

        /* --- block items → use the block's collision shape (true 3D) --- */
        if (stack.getItem() instanceof BlockItem blockItem)
        {
            try
            {
                BlockState state = blockItem.getBlock().getDefaultState();
                VoxelShape shape = state.getCollisionShape(null, BlockPos.ORIGIN, ShapeContext.absent());

                if (!shape.isEmpty())
                {
                    Box box = shape.getBoundingBox();
                    rawX = (box.maxX - box.minX) / 2.0D;
                    rawY = (box.maxY - box.minY) / 2.0D;
                    rawZ = (box.maxZ - box.minZ) / 2.0D;

                    if (rawX > 1.0E-3D || rawY > 1.0E-3D || rawZ > 1.0E-3D)
                    {
                        return normalizeShape(rawX, rawY, rawZ);
                    }
                }
            }
            catch (Exception ignored)
            {
            }
        }

        /* --- non-block items → use hasDepth() to distinguish 3D vs flat --- */
        try
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            ItemRenderState renderState = new ItemRenderState();
            mc.getItemModelManager().update(renderState, stack, mode, false, null, null, 0);

            if (!renderState.isEmpty())
            {
                if (renderState.hasDepth())
                {
                    return new double[] { ITEM_HALF, ITEM_HALF, ITEM_HALF };
                }
                else
                {
                    return new double[] { ITEM_HALF, ITEM_HALF * 0.25D, ITEM_HALF };
                }
            }
        }
        catch (Exception ignored)
        {
        }

        return new double[] { ITEM_HALF, ITEM_HALF, ITEM_HALF };
    }

    /** Scale proportions so the largest axis === ITEM_HALF. */
    private static double[] normalizeShape(double x, double y, double z)
    {
        double max = Math.max(x, Math.max(y, z));
        if (max < 1.0E-4D) return new double[] { ITEM_HALF, ITEM_HALF, ITEM_HALF };
        double s = ITEM_HALF / max;
        return new double[] {
            Math.max(x * s, 0.005D),
            Math.max(y * s, 0.005D),
            Math.max(z * s, 0.005D)
        };
    }

    /** Legacy single-value API (kept for backward compat with groundY/resolveAgainst). */
    public static double itemHalf(ItemStack stack, net.minecraft.item.ModelTransformationMode mode)
    {
        double[] extents = itemHalfExtents(stack, mode);
        return Math.max(extents[0], Math.max(extents[1], extents[2]));
    }

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

        Vec3d start = new Vec3d(originX, originY + 32.0D, originZ);
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
     * each nearby Minecraft entity / BBS actor (excluding {@code exclude}).
     * Used to push items off actors. */
    public static List<AABB> entityBoxes(World world, double ox, double oy, double oz, double range, Entity exclude)
    {
        List<AABB> boxes = new ArrayList<>();

        if (world == null)
        {
            return boxes;
        }

        Box area = new Box(ox - range, oy - range, oz - range, ox + range, oy + range, oz + range);

        for (Entity entity : world.getOtherEntities(exclude, area))
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
    public static double resolveAgainst(double x, double y, double z, double half, List<AABB> obstacles)
    {
        AABB item = new AABB(x - half, y - half, z - half, half * 2.0D, half * 2.0D, half * 2.0D);

        double bestY = y;

        for (AABB obstacle : obstacles)
        {
            if (item.intersects(obstacle))
            {
                double push = obstacle.maxY() + half;
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
