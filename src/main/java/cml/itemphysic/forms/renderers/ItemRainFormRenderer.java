package cml.itemphysic.forms.renderers;

import cml.itemphysic.forms.ItemRainForm;
import cml.itemphysic.physics.ItemDropSimulation;
import cml.itemphysic.physics.ItemPool;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ItemFormRenderer;
import net.minecraft.item.ItemStack;
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
 * <p>Everything is derived deterministically from the item index and the form's
 * parameters, so the animation is stable when scrubbing the film timeline. The
 * baked simulations are cached per item and only rebuilt when a physics input
 * (drop height, bounce, spins, count or mode) changes.</p>
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

        if (this.sims != null
            && count == this.cachedCount
            && mode == this.cachedMode
            && allItems == this.cachedAllItems
            && itemsHash == this.cachedItemsHash
            && spread == this.cachedSpread
            && dropHeight == this.cachedDropHeight
            && bounce == this.cachedBounce
            && spins == this.cachedSpins)
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
            Random random = new Random(0x9E3779B9L ^ (i * 0x100000001B3L));

            /* Scatter position: a disc of radius "spread" for rain, a tight
             * cluster (10% of the radius, min a little) for the heap mode. */
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
                this.stacks[i] = ItemPool.pick(random.nextInt());
            }
            else
            {
                ItemStack stack = selected.get(random.nextInt(selected.size())).get();
                this.stacks[i] = stack == null ? ItemStack.EMPTY : stack;
            }

            int seed = this.stacks[i].hashCode() * 31 + i;

            this.sims[i] = ItemDropSimulation.bake(dropHeight, bounce, spins, seed);
        }

        this.cachedCount = count;
        this.cachedMode = mode;
        this.cachedAllItems = allItems;
        this.cachedItemsHash = itemsHash;
        this.cachedSpread = spread;
        this.cachedDropHeight = dropHeight;
        this.cachedBounce = bounce;
        this.cachedSpins = spins;
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        ItemRainForm form = this.rainForm();

        this.ensureBaked(form);

        float progress = form.getPhysicsProgress();
        ItemStack original = form.stack.get();

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

            float offsetY = this.sims[i].heightAt(p);
            Quaternionf orientation = this.sims[i].orientationAt(p);

            form.stack.set(stack);

            context.stack.push();
            context.stack.translate(this.offsetX[i], offsetY, this.offsetZ[i]);
            context.stack.multiply(orientation);

            super.render3D(context);

            context.stack.pop();
        }

        form.stack.set(original);
    }
}
