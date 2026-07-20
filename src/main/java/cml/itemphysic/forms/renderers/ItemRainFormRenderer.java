package cml.itemphysic.forms.renderers;

import cml.itemphysic.forms.ItemRainForm;
import cml.itemphysic.physics.ItemDropSimulation;
import cml.itemphysic.physics.ItemPool;
import cml.itemphysic.physics.WorldCollision;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ItemFormRenderer;
import mchorse.bbs_mod.utils.AABB;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Random;

/**
 * Renderer for {@link ItemRainForm}. It draws many item instances by reusing the
 * stock {@link ItemFormRenderer} once per item: for each instance it temporarily
 * swaps the form's {@code stack}, offsets the matrix stack by a scatter position
 * plus a per-item {@link ItemDropSimulation} fall, draws, then restores the
 * stack.
 *
 * <p>Collisions are resolved at render time against the live world:</p>
 * <ul>
 *     <li><b>Solid ground / blocks</b> via a downward raycast so items rest on
 *     the actual geometry below the form instead of always at y = 0.</li>
 *     <li><b>Inter-item</b> overlap is pushed apart (when "overlap" is off) by a
 *     deterministic post-pass over the baked trajectories.</li>
 *     <li><b>BBS actors / entities</b> are pushed off their AABBs so items do
 *     not sink into characters.</li>
 * </ul>
 *
 * <p>Everything is derived deterministically from the item index and the form's
 * parameters (and a per-item seed), so the animation is stable when scrubbing
 * the film timeline. The baked simulations are cached per item and only rebuilt
 * when a physics input changes.</p>
 */
public class ItemRainFormRenderer extends ItemFormRenderer
{
    private ItemDropSimulation[] sims;
    private float[] offsetX;
    private float[] offsetZ;
    private float[] timeOffset;
    private ItemStack[] stacks;

    private int cachedCount = -1;
    private int cachedMode = -1;
    private boolean cachedAllItems;
    private int cachedItemsHash;
    private float cachedSpread = Float.NaN;
    private float cachedDropHeight = Float.NaN;
    private float cachedBounce = Float.NaN;
    private float cachedSpins = Float.NaN;
    private int cachedSeed;

    public ItemRainFormRenderer(ItemRainForm form)
    {
        super(form);
    }

    private ItemRainForm rainForm()
    {
        return (ItemRainForm) this.form;
    }

    private int itemsHash(ItemRainForm form)
    {
        int hash = 1;

        for (var value : form.items.getList())
        {
            ItemStack stack = value.get();
            hash = hash * 31 + (stack == null ? 0 : stack.getItem().hashCode());
        }

        return hash;
    }

    private void ensureBaked(ItemRainForm form)
    {
        int count = form.getCount();
        int mode = form.getMode();
        boolean allItems = form.isAllItems();
        int itemsHash = this.itemsHash(form);
        float spread = form.getSpread();
        float dropHeight = form.getDropHeight();
        float bounce = form.getBounce();
        float spins = form.getSpins();
        int seed = form.isUseRandomSeed() ? form.getRandomSeed() : form.getSeed();

        if (this.sims != null
            && count == this.cachedCount
            && mode == this.cachedMode
            && allItems == this.cachedAllItems
            && itemsHash == this.cachedItemsHash
            && spread == this.cachedSpread
            && dropHeight == this.cachedDropHeight
            && bounce == this.cachedBounce
            && spins == this.cachedSpins
            && seed == this.cachedSeed)
        {
            return;
        }

        this.sims = new ItemDropSimulation[count];
        this.offsetX = new float[count];
        this.offsetZ = new float[count];
        this.timeOffset = new float[count];
        this.stacks = new ItemStack[count];

        List<mchorse.bbs_mod.settings.values.mc.ValueItemStack> selected = form.items.getList();

        for (int i = 0; i < count; i++)
        {
            int itemSeed = form.seedFor(i);
            Random random = new Random(itemSeed & 0xFFFFFFFFL);

            /* Scatter position: a disc of radius "spread" for rain, a tight
             * cluster (15% of the radius, min a little) for the heap mode. */
            float radius = mode == ItemRainForm.MODE_HEAP ? spread * 0.15F : spread;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(random.nextDouble()) * radius;

            this.offsetX[i] = (float) (Math.cos(angle) * r);
            this.offsetZ[i] = (float) (Math.sin(angle) * r);

            /* Randomised staggered start times: every item (both heap and rain
             * mode) starts falling at its own random moment in [0, 0.85] of the
             * timeline, so they never all fall together. The item is only drawn
             * from its start time on (see render3D), so it appears to spawn and
             * drop at a random time. The last 15% is reserved so even the latest
             * starter can complete its fall by progress = 1. */
            this.timeOffset[i] = count <= 1 ? 0.0F : random.nextFloat() * 0.85F;

            /* Item selection. */
            if (allItems)
            {
                this.stacks[i] = ItemPool.pick(itemSeed);
            }
            else
            {
                if (selected.isEmpty())
                {
                    this.stacks[i] = ItemStack.EMPTY;
                }
                else
                {
                    ItemStack stack = selected.get(random.nextInt(selected.size())).get();
                    this.stacks[i] = stack == null ? ItemStack.EMPTY : stack;
                }
            }

            int simSeed = this.stacks[i].hashCode() * 31 + i;

            this.sims[i] = ItemDropSimulation.bake(dropHeight, bounce, spins, simSeed);
        }

        this.cachedCount = count;
        this.cachedMode = mode;
        this.cachedAllItems = allItems;
        this.cachedItemsHash = itemsHash;
        this.cachedSpread = spread;
        this.cachedDropHeight = dropHeight;
        this.cachedBounce = bounce;
        this.cachedSpins = spins;
        this.cachedSeed = seed;
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        ItemRainForm form = this.rainForm();

        this.ensureBaked(form);

        float progress = form.getPhysicsProgress();
        ItemStack original = form.stack.get();

        World world = WorldCollision.worldOf(context.entity);
        mchorse.bbs_mod.utils.pose.Transform transform = this.form.transform.get();
        double formX = transform.translate.x;
        double formY = transform.translate.y;
        double formZ = transform.translate.z;

        /* Real-world ground height (in local space relative to the form). */
        double groundLocal = 0.0D;

        try
        {
            groundLocal = WorldCollision.groundY(world, formX, formY, formZ, formY) - formY;
        }
        catch (Exception ignored)
        {
        }

        /* Neighbouring entity / actor AABBs for resting support. */
        List<AABB> entityBoxes = java.util.Collections.emptyList();

        try
        {
            entityBoxes = WorldCollision.entityBoxes(world, formX, formY, formZ, Math.max(form.getSpread(), 4.0D) + 2.0D);
        }
        catch (Exception ignored)
        {
        }

        boolean overlap = form.canOverlap();

        /* Footprint used to group items into stacking columns (so they rest on
         * top of each other instead of overlapping). */
        double cell = WorldCollision.ITEM_HALF * 2.0D;

        /* Pre-compute a continuous rest height for every item: the real ground
         * plus an inter-item stack offset (items sharing a column pile up) plus
         * any nearby entity/actor top. This value is added to the whole baked
         * trajectory, so the fall ends exactly at rest with NO end-of-fall jump. */
        float[] restY = new float[this.sims.length];
        int[] columnCount = new int[this.sims.length];

        for (int i = 0; i < this.sims.length; i++)
        {
            if (this.stacks[i] == null || this.stacks[i].isEmpty())
            {
                restY[i] = (float) groundLocal;
                continue;
            }

            /* How many earlier items already share this column? They stack. */
            int stackIndex = 0;

            for (int j = 0; j < i; j++)
            {
                if (this.stacks[j] == null || this.stacks[j].isEmpty())
                {
                    continue;
                }

                if (Math.abs(this.offsetX[j] - this.offsetX[i]) <= cell && Math.abs(this.offsetZ[j] - this.offsetZ[i]) <= cell)
                {
                    stackIndex++;
                }
            }

            columnCount[i] = stackIndex;

            double base = groundLocal + (overlap ? 0.0D : stackIndex * (WorldCollision.ITEM_HALF * 2.0D * 0.85D));

            /* When an entity/actor sits under this column, rest on top of it
             * instead of (or above) the ground. Continuous: it only shifts the
             * whole trajectory up, no end jump. */
            double lx = formX + this.offsetX[i];
            double lz = formZ + this.offsetZ[i];

            if (!entityBoxes.isEmpty())
            {
                double pushed = WorldCollision.resolveAgainst(lx, formY + base, lz, entityBoxes) - formY;

                if (pushed > base)
                {
                    base = pushed;
                }
            }

            restY[i] = (float) base;
        }

        for (int i = 0; i < this.sims.length; i++)
        {
            ItemStack stack = this.stacks[i];

            if (stack == null || stack.isEmpty())
            {
                continue;
            }

            /* Each item falls over a fixed portion of the timeline starting at
             * its own staggered offset, so they all fall at the same speed but
             * begin at different times (progressive rain). An item is NOT drawn
             * before its start time, so it literally appears and starts falling
             * only when its turn comes (staggered spawn), then stays landed. */
            float to = this.timeOffset[i];

            if (progress < to)
            {
                continue;
            }

            float duration = 1.0F - to;

            if (duration < 0.15F)
            {
                duration = 0.15F;
            }

            float p = (progress - to) / duration;

            if (p > 1.0F)
            {
                p = 1.0F;
            }

            /* Baked fall ends at 0; the rest height is added over the WHOLE
             * trajectory so the motion is continuous and settles exactly on the
             * real ground / on top of the pile. */
            float offsetY = this.sims[i].heightAt(p) + restY[i];
            Quaternionf orientation = this.sims[i].orientationAt(p);

            form.stack.set(stack);

            context.stack.push();

            try
            {
                context.stack.translate(this.offsetX[i], offsetY, this.offsetZ[i]);
                context.stack.multiply(orientation);

                super.render3D(context);
            }
            finally
            {
                context.stack.pop();
            }
        }

        form.stack.set(original);
    }
}
