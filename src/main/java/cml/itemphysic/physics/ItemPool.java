package cml.itemphysic.physics;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;

/**
 * Lazily-built, cached list of every non-empty registered game item, used by the
 * {@link cml.itemphysic.forms.ItemRainForm} when it should rain "all items".
 */
public final class ItemPool
{
    private static List<ItemStack> allItems;

    private ItemPool()
    {
    }

    /** All non-empty game items (built once, then cached). */
    public static List<ItemStack> all()
    {
        if (allItems == null)
        {
            List<ItemStack> stacks = new ArrayList<>();

            for (Item item : Registries.ITEM)
            {
                ItemStack stack = new ItemStack(item);

                if (!stack.isEmpty())
                {
                    stacks.add(stack);
                }
            }

            allItems = stacks;
        }

        return allItems;
    }

    /** A deterministic item from the full pool for a given seed. */
    public static ItemStack pick(int seed)
    {
        List<ItemStack> pool = all();

        if (pool.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        long mixed = (seed & 0xFFFFFFFFL) * 2654435761L >>> 16;
        int index = (int) Math.floorMod(mixed, (long) pool.size());

        return pool.get(index);
    }
}
