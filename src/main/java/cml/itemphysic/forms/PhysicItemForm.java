package cml.itemphysic.forms;

import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

/**
 * A "Physic Item" form: an {@link ItemForm} whose falling physics is driven by
 * a single keyframable progress slider rather than a live simulation.
 *
 * <p>Instead of running a real-time integrator (which cannot be scrubbed on the
 * film timeline), the fall is <b>parametric / baked</b>: a {@code physics}
 * progress value in {@code [0, 1]} drives an analytic trajectory:</p>
 * <ul>
 *     <li>{@code 0} - the item starts up in the air ({@code drop_height} above
 *     the form's final position, un-rotated);</li>
 *     <li>{@code 1} - the item has landed at the form's position, rotated to its
 *     resting pose after {@code spins} tumbling turns, with an optional
 *     {@code bounce} near the end.</li>
 * </ul>
 *
 * <p>The user animates the fall simply by keyframing {@code physics} from 0 to
 * 1 on the film timeline. All values below are added directly to the form as
 * <b>visible</b> {@link mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue}s
 * (via {@link ValueFloat}/{@link ValueBoolean}), which is what allows BBS to
 * expose them as animatable keyframe channels.</p>
 */
public class PhysicItemForm extends ItemForm
{
    public static final Link FORM_ID = Link.bbs("physic_item");

    /** Fall progress in [0, 1]; the main keyframable channel. */
    public final ValueFloat physicsProgress = new ValueFloat("physics", 0.0F, 0.0F, 1.0F);

    /** Start height (blocks) above the final resting position at progress 0. */
    public final ValueFloat dropHeight = new ValueFloat("drop_height", 1.5F, 0.0F, 10.0F);

    /** Number of full tumbling rotations performed during the fall. */
    public final ValueFloat spins = new ValueFloat("spins", 1.0F, 0.0F, 10.0F);

    /** Bounce strength near landing (0 = no bounce, 1 = strong bounce). */
    public final ValueFloat bounce = new ValueFloat("bounce", 0.2F, 0.0F, 1.0F);

    /** Whether items are allowed to overlap/stack ("se superposer"). */
    public final ValueBoolean canOverlap = new ValueBoolean("can_overlap", false);

    public PhysicItemForm()
    {
        super();

        this.add(this.physicsProgress);
        this.add(this.dropHeight);
        this.add(this.spins);
        this.add(this.bounce);
        this.add(this.canOverlap);
    }

    public float getPhysicsProgress()
    {
        return ((Float) this.physicsProgress.get()).floatValue();
    }

    public float getDropHeight()
    {
        return ((Float) this.dropHeight.get()).floatValue();
    }

    public float getSpins()
    {
        return ((Float) this.spins.get()).floatValue();
    }

    public float getBounce()
    {
        return ((Float) this.bounce.get()).floatValue();
    }

    public boolean canOverlap()
    {
        return ((Boolean) this.canOverlap.get()).booleanValue();
    }
}
