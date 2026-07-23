package cml.itemphysic.forms;

import cml.itemphysic.l10n.CMLKeys;
import cml.itemphysic.physics.ItemBody;
import cml.itemphysic.physics.ItemPool;
import cml.itemphysic.physics.ItemRainSim;
import cml.itemphysic.physics.WorldCollision;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import net.minecraft.item.ItemStack;
import java.util.List;

/**
 * An "Item Rain" form: spawns a configurable number of items that fall using the
 * same deterministic drop simulation as the {@link PhysicItemForm}. The items
 * are picked randomly, either from a user-selected {@link #items} list or from
 * every registered game item ({@link #allItems}).
 *
 * <p>Two fall modes are provided via {@link #mode}:</p>
 * <ul>
 *     <li><b>Heap (0)</b> - all items fall towards roughly the same point and
 *     pile up into a small mound.</li>
 *     <li><b>Rain (1)</b> - items are scattered over a disc of radius
 *     {@link #spread} and each starts falling at a slightly different time, so
 *     they rain down progressively.</li>
 * </ul>
 *
 * <p>Like the Physic Item, the whole animation is driven by the keyframable
 * {@link #physicsProgress} value in {@code [0, 1]} so it can be scrubbed on the
 * film timeline.</p>
 */
public class ItemRainForm extends ItemForm
{
    public static final Link FORM_ID = Link.bbs("item_rain");

    public static final int MODE_HEAP = 0;
    public static final int MODE_RAIN = 1;

    /** Fall/rain progress in [0, 1]; the main keyframable channel. */
    public final ValueFloat physicsProgress = new ValueFloat("physics", 0.0F, 0.0F, 1.0F);

    /** How many items to spawn. */
    public final ValueInt count = new ValueInt("count", 20, 0, 500);

    /** Fall mode: heap or rain. Exposed as a switchable "modes" dropdown so it
     * can be toggled (and keyframed) in the editor. */
    public final ValueInt mode = new ValueInt("mode", MODE_RAIN, 0, 1).modes(
        CMLKeys.FORMS_EDITORS_RAIN_MODE_HEAP,
        CMLKeys.FORMS_EDITORS_RAIN_MODE_RAIN
    );

    /** When true (or the list is empty), pick from all game items. */
    public final ValueBoolean allItems = new ValueBoolean("all_items", true);

    /** Scatter radius (blocks) used by the rain mode. */
    public final ValueFloat spread = new ValueFloat("spread", 3.0F, 0.0F, 32.0F);

    /** Start height (blocks) above the resting position. */
    public final ValueFloat dropHeight = new ValueFloat("drop_height", 4.0F, 0.0F, 32.0F);

    /** Bounce strength (coefficient of restitution) near landing. */
    public final ValueFloat bounce = new ValueFloat("bounce", 0.3F, 0.0F, 1.0F);

    /** Number of tumbling rotations during the fall. */
    public final ValueFloat spins = new ValueFloat("spins", 1.5F, 0.0F, 10.0F);

    /** Visual scale multiplier for GROUND-mode item rendering. */
    public final ValueFloat itemScale = new ValueFloat("item_scale", 4.0F, 0.1F, 32.0F);

    /** Vertical offset (blocks) applied on top of the ground-rest position.
     *  Compensates for the GROUND-mode rendering origin shift and allows
     *  per-form fine-tuning of how items sit on the surface. */
    public final ValueFloat itemOffsetY = new ValueFloat("item_offset_y", 0.0F, -16.0F, 16.0F);

    /** The user-selected pool of items (may be empty -> all items). */
    public final ItemStackList items = new ItemStackList("items");

    /** When true the seed is randomized every evaluation; otherwise the fixed
     * {@link #seed} value is used. Affects the scatter, the staggered start
     * times, the tumble axis and (with all-items) the item pick. */
    public final ValueBoolean useRandomSeed = new ValueBoolean("use_random_seed", true);

    /** Deterministic seed used when {@link #useRandomSeed} is false. */
    public final ValueInt seed = new ValueInt("seed", 1, Integer.MIN_VALUE, Integer.MAX_VALUE);

    /** Whether items are allowed to overlap/stack ("se superposer"). */
    public final ValueBoolean canOverlap = new ValueBoolean("can_overlap", false);

    /** A persistent random seed, regenerated only via {@link #reshuffle()}. In
     * random-seed mode this (not the wall clock) drives the scatter/stagger/
     * tumble and item pick, so the rain is stable while playing and only
     * changes when the user asks. */
    private int randomSeed = (int) (Math.random() * 0x7FFFFFFF);

    /* Baked simulation cache lives on the Form because BBS creates a fresh
     * Renderer per render pass — a renderer-local cache would trigger a full
     * re-bake every frame. */
    public ItemRainSim sim;
    public float[] bakedOffsetX;
    public float[] bakedOffsetZ;

    private int cachedCount = -1;
    private int cachedMode = -1;
    private boolean cachedAllItems;
    private int cachedItemsHash;
    private float cachedSpread = Float.NaN;
    private float cachedDropHeight = Float.NaN;
    private float cachedBounce = Float.NaN;
    private float cachedSpins = Float.NaN;
    private float cachedItemScale = Float.NaN;
    private int cachedSeed;

    public ItemRainForm()
    {
        super();

        this.add(this.physicsProgress);
        this.add(this.count);
        this.add(this.mode);
        this.add(this.allItems);
        this.add(this.spread);
        this.add(this.dropHeight);
        this.add(this.bounce);
        this.add(this.spins);
        this.add(this.itemScale);
        this.add(this.itemOffsetY);
        this.add(this.items);
        this.add(this.useRandomSeed);
        this.add(this.seed);
        this.add(this.canOverlap);
    }

    /** Re-rolls the persistent random seed used in random-seed mode. */
    public void reshuffle()
    {
        this.randomSeed = (int) (Math.random() * 0x7FFFFFFF);
    }

    public float getPhysicsProgress()
    {
        return ((Float) this.physicsProgress.get()).floatValue();
    }

    public int getCount()
    {
        return ((Integer) this.count.get()).intValue();
    }

    public int getMode()
    {
        return ((Integer) this.mode.get()).intValue();
    }

    public boolean isAllItems()
    {
        return ((Boolean) this.allItems.get()).booleanValue() || this.items.getList().isEmpty();
    }

    public float getSpread()
    {
        return ((Float) this.spread.get()).floatValue();
    }

    public float getDropHeight()
    {
        return ((Float) this.dropHeight.get()).floatValue();
    }

    public float getBounce()
    {
        return ((Float) this.bounce.get()).floatValue();
    }

    public float getSpins()
    {
        return ((Float) this.spins.get()).floatValue();
    }

    public float getItemScale()
    {
        return ((Float) this.itemScale.get()).floatValue();
    }

    public float getItemOffsetY()
    {
        return ((Float) this.itemOffsetY.get()).floatValue();
    }

    public boolean isUseRandomSeed()
    {
        return ((Boolean) this.useRandomSeed.get()).booleanValue();
    }

    /** The current persistent random seed (random-seed mode). */
    public int getRandomSeed()
    {
        return this.randomSeed;
    }

    public int getSeed()
    {
        return ((Integer) this.seed.get()).intValue();
    }

    public boolean canOverlap()
    {
        return ((Boolean) this.canOverlap.get()).booleanValue();
    }

    /** Deterministic per-item seed used for the scatter, stagger, tumble and
     * (with all-items) item pick. In random-seed mode the persistent
     * {@link #randomSeed} is the base (stable while playing, re-rollable via
     * {@link #reshuffle()}); with a fixed seed the user {@link #seed} value is
     * the base so the layout is identical and timeline-scrubbable. */
    public int seedFor(int index)
    {
        int base = this.isUseRandomSeed() ? this.randomSeed : this.getSeed();

        return base * 31 + index;
    }

    private int itemsHash()
    {
        int hash = 1;

        for (var value : this.items.getList())
        {
            ItemStack stack = value.get();
            hash = hash * 31 + (stack == null ? 0 : stack.getItem().hashCode());
        }

        return hash;
    }

    private static float rand01(int seed, int salt)
    {
        int h = seed * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    public void ensureBaked()
    {
        int count = Math.max(0, this.getCount());
        int mode = this.getMode();
        boolean allItems = this.isAllItems();
        int itemsHash = this.itemsHash();
        float spread = this.getSpread();
        float dropHeight = this.getDropHeight();
        float bounce = this.getBounce();
        float spins = this.getSpins();
        float itemScale = this.getItemScale();
        int seed = this.isUseRandomSeed() ? this.getRandomSeed() : this.getSeed();

        boolean cacheOk = this.sim != null
            && count == this.cachedCount
            && mode == this.cachedMode
            && allItems == this.cachedAllItems
            && itemsHash == this.cachedItemsHash
            && spread == this.cachedSpread
            && dropHeight == this.cachedDropHeight
            && bounce == this.cachedBounce
            && spins == this.cachedSpins
            && itemScale == this.cachedItemScale
            && seed == this.cachedSeed;

        if (cacheOk)
        {
            return;
        }

        this.bakedOffsetX = new float[count];
        this.bakedOffsetZ = new float[count];

        List<mchorse.bbs_mod.settings.values.mc.ValueItemStack> selected = this.items.getList();

        boolean noDrift = Boolean.getBoolean("cml.rain.nodrift");
        boolean minimal = Boolean.getBoolean("cml.rain.minimal");

        if (minimal)
        {
            mode = ItemRainForm.MODE_RAIN;
            dropHeight = Math.max(dropHeight, 3.0F);
            spread = Math.max(spread, 2.0F);
            allItems = true;
        }

        ItemBody[] bodies = new ItemBody[count];
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
        double minHalf = WorldCollision.ITEM_HALF * 1.1D;
        double minArea = minHalf * Math.max(1, cols - 1);
        float area = mode == ItemRainForm.MODE_HEAP
            ? (float) Math.max(minArea, Math.max(1.2F, spread * 0.5F))
            : (float) Math.max(minArea, Math.max(2.0F, spread));
        double step = (area * 2.0D) / Math.max(1, cols - 1);

        for (int i = 0; i < count; i++)
        {
            int itemSeed = this.seedFor(i);

            int gx = i % cols;
            int gz = i / cols;

            double ox = -area + gx * step;
            double oz = -area + gz * step;

            double jitter = step * 0.15D;
            ox += (rand01(itemSeed, 91) - 0.5D) * jitter;
            oz += (rand01(itemSeed, 92) - 0.5D) * jitter;

            this.bakedOffsetX[i] = (float) ox;
            this.bakedOffsetZ[i] = (float) oz;

            ItemStack stack;

            if (allItems)
            {
                stack = ItemPool.pick(itemSeed);
            }
            else if (selected.isEmpty())
            {
                stack = ItemStack.EMPTY;
            }
            else
            {
                int pick = (int) (rand01(itemSeed, 53) * selected.size());
                ItemStack s = selected.get(pick).get();
                stack = s == null ? ItemStack.EMPTY : s;
            }

                if (stack == null || stack.isEmpty())
            {
                bodies[i] = new ItemBody(ItemStack.EMPTY, 0.18D);
                bodies[i].spawned = true;
                bodies[i].settled = true;
                bodies[i].pos.set(ox, 0.18D, oz);
                continue;
            }

            double[] halfExtents = WorldCollision.itemHalfExtents(stack, net.minecraft.item.ModelTransformationMode.GROUND);
            ItemBody body = new ItemBody(stack,
                halfExtents[0] * itemScale, halfExtents[1] * itemScale, halfExtents[2] * itemScale);

            body.pos.set(ox, dropHeight + halfExtents[1] * itemScale, oz);

            if (!noDrift && !minimal)
            {
                float ax = rand01(itemSeed, 71) * 2.0F - 1.0F;
                float ay = rand01(itemSeed, 72) * 2.0F - 1.0F;
                float az = rand01(itemSeed, 73) * 2.0F - 1.0F;
                org.joml.Vector3f axis = new org.joml.Vector3f(ax, ay, az);

                if (axis.lengthSquared() < 1.0E-4F)
                {
                    axis.set(0.0F, 1.0F, 0.0F);
                }

                axis.normalize();
                body.spinAxis.set(axis);
                body.spinSpeed = spins * (float) (Math.PI * 2.0) * (0.5F + rand01(itemSeed, 74));

                double spread0 = Math.max(0.1D, spread);
                double driftX = (rand01(itemSeed, 81) - 0.5D) * 0.4D * spread0;
                double driftZ = (rand01(itemSeed, 82) - 0.5D) * 0.4D * spread0;

                body.vel.x = driftX;
                body.vel.z = driftZ;
            }

            bodies[i] = body;
        }

        double spawnInterval = count <= 1 ? 0.0D : (minimal ? 0.0D : Math.min(0.6D, 2.0D / count));
        this.sim = ItemRainSim.bake(bodies, spawnInterval);

        this.cachedCount = count;
        this.cachedMode = mode;
        this.cachedAllItems = allItems;
        this.cachedItemsHash = itemsHash;
        this.cachedSpread = spread;
        this.cachedDropHeight = dropHeight;
        this.cachedBounce = bounce;
        this.cachedSpins = spins;
        this.cachedItemScale = itemScale;
        this.cachedSeed = seed;
    }
}
