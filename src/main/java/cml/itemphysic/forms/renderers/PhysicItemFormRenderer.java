package cml.itemphysic.forms.renderers;

import cml.itemphysic.forms.PhysicItemForm;
import cml.itemphysic.physics.ItemDropSimulation;
import cml.itemphysic.physics.WorldCollision;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ItemFormRenderer;
import net.minecraft.world.World;
import org.joml.Quaternionf;

/**
 * Renderer for {@link PhysicItemForm}. It delegates the item drawing to the
 * stock {@link ItemFormRenderer} but, before drawing, offsets the matrix stack
 * according to a real {@link ItemDropSimulation}.
 *
 * <p>Rather than an ad-hoc analytic curve, the fall is a genuine rigid-body
 * simulation (semi-implicit Euler with a coefficient of restitution and ground
 * friction) that is <b>baked once</b> into a lookup table. The form's
 * keyframable {@code physics} value in {@code [0, 1]} then samples that table,
 * so the motion is deterministic and can be scrubbed on the film timeline while
 * still showing physically-decaying bounces and a tumble that freezes on
 * impact.</p>
 *
 * <p>The baked simulation is cached and only re-computed when one of its input
 * parameters (drop height, bounce, spins or the item seed) actually changes.</p>
 */
public class PhysicItemFormRenderer extends ItemFormRenderer
{
    private ItemDropSimulation sim;
    private final Quaternionf orientationBuf = new Quaternionf();
    private float cachedDropHeight = Float.NaN;
    private float cachedBounce = Float.NaN;
    private float cachedSpins = Float.NaN;
    private int cachedSeed;

    public PhysicItemFormRenderer(PhysicItemForm form)
    {
        super(form);
    }

    private PhysicItemForm physicForm()
    {
        return (PhysicItemForm) this.form;
    }

    private ItemDropSimulation simulation(float dropHeight, float bounce, float spins, int seed)
    {
        if (this.sim == null
            || dropHeight != this.cachedDropHeight
            || bounce != this.cachedBounce
            || spins != this.cachedSpins
            || seed != this.cachedSeed)
        {
            this.sim = ItemDropSimulation.bake(dropHeight, bounce, spins, seed);
            this.cachedDropHeight = dropHeight;
            this.cachedBounce = bounce;
            this.cachedSpins = spins;
            this.cachedSeed = seed;
        }

        return this.sim;
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        PhysicItemForm form = this.physicForm();

        float p = form.getPhysicsProgress();

        /* At exactly 0 there is nothing to offset: render normally. The fall is
         * applied for every 3D render type (world forms in a film replay are NOT
         * rendered with FormRenderType.ITEM), so we do not gate on render type. */
        if (p <= 0.0F)
        {
            super.render3D(context);
            return;
        }

        float dropHeight = form.getDropHeight();
        float bounce = form.getBounce();
        float spins = form.getSpins();
        int seed = form.effectiveSeed();

        ItemDropSimulation simulation = this.simulation(dropHeight, bounce, spins, seed);

        float offsetY = simulation.heightAt(p);
        simulation.orientationAt(p, this.orientationBuf);

        /* Real-world ground: clamp the item so it rests on whatever solid block
         * sits below the form's position instead of always at y = 0. */
        World world = WorldCollision.worldOf(context.entity);
        mchorse.bbs_mod.utils.pose.Transform transform = this.form.transform.get();
        double formY = transform.translate.y;
        double groundLocal = 0.0D;

        try
        {
            groundLocal = WorldCollision.groundY(world, transform.translate.x, formY, transform.translate.z, formY) - formY;
        }
        catch (Exception ignored)
        {
        }

        if (!form.canOverlap() && groundLocal > 0.0D)
        {
            /* Add the real ground height over the WHOLE trajectory (not just at
             * rest) so the item settles on the actual surface with no end jump. */
            offsetY = offsetY + (float) groundLocal;
        }

        context.stack.push();

        try
        {
            context.stack.translate(0.0F, offsetY, 0.0F);
            context.stack.multiply(this.orientationBuf);

            super.render3D(context);
        }
        finally
        {
            context.stack.pop();
        }
    }
}
