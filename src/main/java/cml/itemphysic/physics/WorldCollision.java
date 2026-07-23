package cml.itemphysic.physics;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.AABB;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
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

public final class WorldCollision
{
    public static final double ITEM_HALF = 0.5D;

    public static double[] itemHalfExtents(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
            return new double[] { ITEM_HALF, ITEM_HALF, ITEM_HALF };

        double rawX, rawY, rawZ;

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

        /* Non-block items: damageable items (tools, weapons) are 3D; stackable
         * items (food, plates, etc.) are flat.  This avoids depending on the
         * version-specific item model API (ItemRenderState vs BakedModel). */
        if (stack.isDamageable())
        {
            return new double[] { ITEM_HALF, ITEM_HALF, ITEM_HALF };
        }
        else
        {
            return new double[] { ITEM_HALF, ITEM_HALF * 0.25D, ITEM_HALF };
        }
    }

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

    public static double itemHalf(ItemStack stack)
    {
        double[] extents = itemHalfExtents(stack);
        return Math.max(extents[0], Math.max(extents[1], extents[2]));
    }

    private WorldCollision()
    {
    }

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

    public static World worldOf(IEntity entity)
    {
        return entity == null ? null : entity.getWorld();
    }
}
