package cml.itemphysic.forms.renderers;

import cml.itemphysic.forms.ItemRainForm;
import cml.itemphysic.physics.ItemBody;
import cml.itemphysic.physics.ItemRainSim;
import cml.itemphysic.physics.WorldCollision;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ItemFormRenderer;
import mchorse.bbs_mod.utils.AABB;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.List;

/**
 * Renderer for {@link ItemRainForm}. It runs a real baked sandbox simulation
 * ({@link ItemRainSim}): many item bodies fall under gravity, bounce on the
 * floor and collide with each other as solid boxes so they pile up like real
 * objects instead of interpenetrating.
 *
 * <p>The simulation is baked once (deterministic, seed-driven) and recorded
 * frame-by-frame, so the renderer can sample the exact pile state at the form's
 * normalized {@code physics} progress. This keeps the motion scrubbable on the
 * film timeline while still showing genuine solid-vs-solid collisions. The
 * baked pile is shifted onto the real world ground at draw time, and BBS actors
 * / entities are pushed off via their AABBs.</p>
 */
public class ItemRainFormRenderer extends ItemFormRenderer
{
    public ItemRainFormRenderer(ItemRainForm form)
    {
        super(form);
    }

    private ItemRainForm rainForm()
    {
        return (ItemRainForm) this.form;
    }

    private void ensureBaked(ItemRainForm form)
    {
        form.ensureBaked();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        ItemRainForm form = this.rainForm();

        this.ensureBaked(form);

        if (form.sim == null)
        {
            return;
        }

        float progress = form.getPhysicsProgress();
        ItemStack original = form.stack.get();

        World world = WorldCollision.worldOf(context.entity);
        mchorse.bbs_mod.utils.pose.Transform transform = this.form.transform.get();
        double formX = transform.translate.x;
        double formY = transform.translate.y;
        double formZ = transform.translate.z;

        /* Real-world ground (relative to the form) so the baked flat-floor pile
         * is shifted up onto whatever solid surface is below. */
        double groundLocal = 0.0D;

        try
        {
            groundLocal = WorldCollision.groundY(world, formX, formY, formZ, formY) - formY;
        }
        catch (Exception ignored)
        {
        }

        List<AABB> entityBoxes = java.util.Collections.emptyList();

        try
        {
            net.minecraft.entity.player.PlayerEntity localPlayer = net.minecraft.client.MinecraftClient.getInstance().player;
            entityBoxes = WorldCollision.entityBoxes(world, formX, formY, formZ, Math.max(form.getSpread(), 4.0D) + 2.0D, localPlayer);
        }
        catch (Exception ignored)
        {
        }

        int frame = form.sim.frameIndex(progress);
        ItemRainSim.Frame snap = form.sim.frames[frame];

        for (int i = 0; i < form.sim.bodies.length; i++)
        {
            ItemBody body = form.sim.bodies[i];

            if (!body.spawned || body.stack == null || body.stack.isEmpty())
            {
                continue;
            }

            if (progress < form.sim.spawnProgress[i])
            {
                continue;
            }

            ItemStack stack = body.stack;

            form.stack.set(stack);

            /* Final resting shift: move the baked flat-floor position up to the
             * real ground under the form (single raycast, cached per frame) plus
             * any entity/actor support under this column. */
            double rx = formX + form.bakedOffsetX[i];
            double rz = formZ + form.bakedOffsetZ[i];

            double restY = groundLocal + this.rainForm().getItemOffsetY();

            if (!entityBoxes.isEmpty())
            {
                double pushed = WorldCollision.resolveAgainst(rx, formY + restY, rz, body.half, entityBoxes) - formY;

                if (pushed > restY)
                {
                    restY = pushed;
                }
            }

            float px = snap.x[i];
            float py = snap.y[i] + (float) restY;
            float pz = snap.z[i];

            context.stack.push();

            try
            {
                context.stack.translate(px, py, pz);
                context.stack.multiply(snap.q[i]);

                float itemScale = form.getItemScale();
                context.stack.scale(itemScale, itemScale, itemScale);

                ItemSolidRender.render(context.stack, stack, world, context.light,
                    net.minecraft.item.ModelTransformationMode.GROUND);
            }
            finally
            {
                context.stack.pop();
            }
        }

        form.stack.set(original);
    }
}
