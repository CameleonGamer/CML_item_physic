package cml.itemphysic.forms;

import cml.itemphysic.l10n.CMLKeys;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

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
    public final ValueInt count = new ValueInt("count", 20, 1, 500);

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

    /** The user-selected pool of items (may be empty -> all items). */
    public final ItemStackList items = new ItemStackList("items");

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
        this.add(this.items);
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
}
