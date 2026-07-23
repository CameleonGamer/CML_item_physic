package cml.itemphysic.ui;

import cml.itemphysic.forms.ItemRainForm;
import cml.itemphysic.l10n.CMLKeys;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.mc.ValueItemStack;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import net.minecraft.item.ItemStack;

/**
 * The "Rain" section shown inside the Item Rain form editor. It exposes the
 * physics/scatter parameters (progress, count, mode, spread, drop height, spins,
 * bounce), an "all items" toggle, and a small item-pool editor: an item picker
 * that appends to the pool and a button to clear it.
 */
public class UIItemRainPanel extends UIFormPanel<ItemRainForm>
{
    public final UITrackpad physics = new UITrackpad(
        v -> ((ItemRainForm) this.form).physicsProgress.set(Float.valueOf(v.floatValue()))
    );

    public final UITrackpad count = new UITrackpad(
        v -> ((ItemRainForm) this.form).count.set(Integer.valueOf(v.intValue()))
    ).integer();

    public final UICirculate mode;

    public final UITrackpad spread = new UITrackpad(
        v -> ((ItemRainForm) this.form).spread.set(Float.valueOf(v.floatValue()))
    );

    public final UITrackpad dropHeight = new UITrackpad(
        v -> ((ItemRainForm) this.form).dropHeight.set(Float.valueOf(v.floatValue()))
    );

    public final UITrackpad spins = new UITrackpad(
        v -> ((ItemRainForm) this.form).spins.set(Float.valueOf(v.floatValue()))
    );

    public final UITrackpad bounce = new UITrackpad(
        v -> ((ItemRainForm) this.form).bounce.set(Float.valueOf(v.floatValue()))
    );

    public final UITrackpad itemOffsetY = new UITrackpad(
        v -> ((ItemRainForm) this.form).itemOffsetY.set(Float.valueOf(v.floatValue()))
    );

    public final UITrackpad itemScale = new UITrackpad(
        v -> ((ItemRainForm) this.form).itemScale.set(Float.valueOf(v.floatValue()))
    );

    public final UIToggle allItems = new UIToggle(
        CMLKeys.FORMS_EDITORS_RAIN_ALL_ITEMS,
        b -> ((ItemRainForm) this.form).allItems.set(b.getValue())
    );

    public final UIToggle useRandomSeed = new UIToggle(
        CMLKeys.FORMS_EDITORS_RAIN_RANDOM_SEED,
        b -> ((ItemRainForm) this.form).useRandomSeed.set(b.getValue())
    );

    public final UITrackpad seed = new UITrackpad(
        v -> ((ItemRainForm) this.form).seed.set(Integer.valueOf(v.intValue()))
    ).integer();

    public final UIButton reshuffle = new UIButton(
        CMLKeys.FORMS_EDITORS_RAIN_RESHUFFLE,
        b -> ((ItemRainForm) this.form).reshuffle()
    );

    public final UIItemStack addItem;

    public final UIButton clearItems;

    public final UIButton itemsLabel;

    public UIItemRainPanel(UIForm editor)
    {
        super(editor);

        this.mode = new UICirculate(b -> ((ItemRainForm) this.form).mode.set(Integer.valueOf(b.getValue())));
        this.mode.addLabel(CMLKeys.FORMS_EDITORS_RAIN_MODE_HEAP);
        this.mode.addLabel(CMLKeys.FORMS_EDITORS_RAIN_MODE_RAIN);
        this.mode.tooltip(CMLKeys.FORMS_EDITORS_RAIN_MODE_TOOLTIP, Direction.RIGHT);

        this.addItem = new UIItemStack(stack -> this.appendItem(stack));

        this.clearItems = new UIButton(CMLKeys.FORMS_EDITORS_RAIN_CLEAR_ITEMS, b -> this.clearItems());
        this.itemsLabel = new UIButton(IKey.EMPTY, b -> {});
        this.itemsLabel.setEnabled(false);

        this.physics.tooltip(CMLKeys.FORMS_EDITORS_RAIN_PHYSICS_TOOLTIP, Direction.RIGHT);
        this.count.tooltip(CMLKeys.FORMS_EDITORS_RAIN_COUNT_TOOLTIP, Direction.RIGHT);
        this.spread.tooltip(CMLKeys.FORMS_EDITORS_RAIN_SPREAD_TOOLTIP, Direction.RIGHT);
        this.dropHeight.tooltip(CMLKeys.FORMS_EDITORS_RAIN_DROP_HEIGHT_TOOLTIP, Direction.RIGHT);
        this.spins.tooltip(CMLKeys.FORMS_EDITORS_RAIN_SPINS_TOOLTIP, Direction.RIGHT);
        this.bounce.tooltip(CMLKeys.FORMS_EDITORS_RAIN_BOUNCE_TOOLTIP, Direction.RIGHT);
        this.itemOffsetY.tooltip(CMLKeys.FORMS_EDITORS_RAIN_ITEM_OFFSET_Y_TOOLTIP, Direction.RIGHT);
        this.itemScale.tooltip(CMLKeys.FORMS_EDITORS_RAIN_ITEM_SCALE_TOOLTIP, Direction.RIGHT);
        this.allItems.tooltip(CMLKeys.FORMS_EDITORS_RAIN_ALL_ITEMS_TOOLTIP, Direction.RIGHT);
        this.useRandomSeed.tooltip(CMLKeys.FORMS_EDITORS_RAIN_RANDOM_SEED_TOOLTIP, Direction.RIGHT);
        this.seed.tooltip(CMLKeys.FORMS_EDITORS_RAIN_SEED_TOOLTIP, Direction.RIGHT);

        this.options.add(
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_TITLE),
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_PHYSICS),
            this.physics,
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_COUNT),
            this.count,
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_MODE),
            this.mode,
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_SPREAD),
            this.spread,
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_DROP_HEIGHT),
            this.dropHeight,
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_SPINS),
            this.spins,
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_BOUNCE),
            this.bounce,
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_ITEM_OFFSET_Y),
            this.itemOffsetY,
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_ITEM_SCALE),
            this.itemScale,
            this.allItems,
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_RANDOM_SEED),
            this.useRandomSeed,
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_SEED),
            this.seed,
            this.reshuffle,
            UI.label(CMLKeys.FORMS_EDITORS_RAIN_ITEMS),
            this.itemsLabel,
            this.addItem,
            this.clearItems
        );
    }

    private void appendItem(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }

        ItemRainForm form = (ItemRainForm) this.form;
        ValueItemStack value = new ValueItemStack(String.valueOf(form.items.getList().size()));
        value.set(stack.copy());
        form.items.add(value);
        form.items.sync();
        this.updateItemsLabel();
    }

    private void clearItems()
    {
        ItemRainForm form = (ItemRainForm) this.form;

        while (!form.items.getList().isEmpty())
        {
            form.items.remove(form.items.getList().size() - 1);
        }

        this.updateItemsLabel();
    }

    private void updateItemsLabel()
    {
        int size = ((ItemRainForm) this.form).items.getList().size();
        this.itemsLabel.label = CMLKeys.FORMS_EDITORS_RAIN_ITEMS_COUNT.format(Integer.valueOf(size));
    }

    @Override
    public void startEdit(ItemRainForm form)
    {
        super.startEdit(form);

        this.physics.limit(form.physicsProgress).setValue(form.getPhysicsProgress());
        this.count.limit(form.count).setValue(form.getCount());
        this.spread.limit(form.spread).setValue(form.getSpread());
        this.dropHeight.limit(form.dropHeight).setValue(form.getDropHeight());
        this.spins.limit(form.spins).setValue(form.getSpins());
        this.bounce.limit(form.bounce).setValue(form.getBounce());
        this.itemOffsetY.limit(form.itemOffsetY).setValue(form.getItemOffsetY());
        this.itemScale.limit(form.itemScale).setValue(form.getItemScale());
        this.allItems.setValue((Boolean) form.allItems.get());
        this.useRandomSeed.setValue((Boolean) form.useRandomSeed.get());
        this.seed.limit(form.seed).setValue(form.getSeed());

        this.mode.setValue(form.getMode());
        this.updateItemsLabel();
    }
}
