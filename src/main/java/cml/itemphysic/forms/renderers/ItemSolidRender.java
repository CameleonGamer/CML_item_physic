package cml.itemphysic.forms.renderers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.world.World;

/**
 * Renders an item mesh as a solid, opaque, world-space object (ModelTransformationMode
 * GROUND) instead of going through BBS's form preview pipeline which renders forms
 * semi-transparently ("ghost" look). This makes the item meshes behave like real
 * solid blocks that properly occlude each other.
 *
 * <p>Since the mod uses Yarn mappings and is compiled per-MC-version, direct
 * compile-time API calls are safe (no reflection needed).</p>
 */
public final class ItemSolidRender
{
    public static void render(MatrixStack matrices, ItemStack stack, World world, int light, ModelTransformationMode mode)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ItemRenderer renderer = mc.getItemRenderer();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        renderer.renderItem(
            stack,
            mode,
            light,
            OverlayTexture.DEFAULT_UV,
            matrices,
            immediate,
            world,
            0
        );
    }

    private ItemSolidRender()
    {
    }
}
