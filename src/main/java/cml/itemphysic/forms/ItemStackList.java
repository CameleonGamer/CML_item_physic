package cml.itemphysic.forms;

import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.mc.ValueItemStack;

/**
 * A serializable list of {@link ValueItemStack} entries used by the
 * {@link ItemRainForm} to hold the pool of items the user selected. When the
 * list is empty (or the form's "all items" flag is on) the rain picks from all
 * registered game items instead.
 *
 * <p>This mirrors the pattern used by BBS's own {@code AnimationStates} and
 * {@code BodyPartManager} ({@code ValueList} subclasses): {@link #create(String)}
 * builds a fresh element, and {@link #sync()} re-indexes ids after edits.</p>
 */
public class ItemStackList extends ValueList<ValueItemStack>
{
    public ItemStackList(String id)
    {
        super(id);
    }

    @Override
    protected ValueItemStack create(String id)
    {
        return new ValueItemStack(id);
    }
}
