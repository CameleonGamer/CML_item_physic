package cml.itemphysic.ui;

import cml.itemphysic.forms.PhysicItemForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.item.ItemStack;

/**
 * Default property panel for {@link PhysicItemForm}. Mirrors the stock item form
 * panel (color, model transform, dropped animation, item stack) but is typed to
 * {@link PhysicItemForm} so it can be used as the editor's default panel.
 */
public class PhysicItemFormPanel extends UIFormPanel<PhysicItemForm>
{
    public UIColor color = new UIColor(c -> ((PhysicItemForm) this.form).color.set(Color.rgba(c))).withAlpha();
    public UIButton modelTransform = new UIButton(IKey.EMPTY, b -> this.getContext().replaceContextMenu(menu -> {
        Object current = ((PhysicItemForm) this.form).modelTransform.get();

        for (Object value : current.getClass().getEnumConstants())
        {
            if (current == value)
            {
                menu.action(Icons.LINE, IKey.constant(modeName(value)), true, () -> {});
                continue;
            }
            menu.action(Icons.LINE, IKey.constant(modeName(value)), () -> this.setModelTransform(value));
        }
    }));
    public UIToggle sameAnimationWhenDropped = new UIToggle(
        UIKeys.FORMS_EDITORS_ITEM_SAME_ANIMATION_WHEN_DROPPED,
        b -> ((PhysicItemForm) this.form).sameAnimationWhenDropped.set(b.getValue())
    );
    public UIItemStack itemStackEditor;

    public PhysicItemFormPanel(UIForm editor)
    {
        super(editor);
        this.sameAnimationWhenDropped.tooltip(UIKeys.FORMS_EDITORS_ITEM_SAME_ANIMATION_WHEN_DROPPED_TOOLTIP);
        this.itemStackEditor = new UIItemStack(itemStack -> ((PhysicItemForm) this.form).stack.set(itemStack.copy()));
        this.options.add(this.color, UI.label(UIKeys.FORMS_EDITORS_ITEM_TRANSFORMS), this.modelTransform, this.sameAnimationWhenDropped, this.itemStackEditor);
    }

    private void setModelTransform(Object value)
    {
        /* modelTransform.set(...) expects the Minecraft ModelTransformationMode
         * type, whose Yarn package differs between MC versions. Invoke it
         * reflectively so this source compiles for both 1.21.1 and 1.21.4. */
        var mt = ((PhysicItemForm) this.form).modelTransform;

        try
        {
            mt.getClass().getMethod("set", Object.class).invoke(mt, value);
        }
        catch (Exception e)
        {
            for (var m : mt.getClass().getMethods())
            {
                if (m.getName().equals("set") && m.getParameterCount() == 1)
                {
                    try
                    {
                        m.invoke(mt, value);
                        break;
                    }
                    catch (Exception ignored)
                    {
                    }
                }
            }
        }

        this.modelTransform.label = IKey.constant(modeName(value));
    }

    /**
     * Returns the string id of a {@code ModelTransformationMode} enum constant
     * without importing the class. The Yarn package of that class differs
     * between Minecraft versions (1.21.1 vs 1.21.4), so we resolve
     * {@code asString()} reflectively to keep the source version-agnostic.
     */
    private static String modeName(Object value)
    {
        try
        {
            return (String) value.getClass().getMethod("asString").invoke(value);
        }
        catch (Exception e)
        {
            return value.toString();
        }
    }

    @Override
    public void startEdit(PhysicItemForm form)
    {
        super.startEdit(form);
        this.color.setColor(((Color) form.color.get()).getARGBColor());
        this.modelTransform.label = IKey.constant(modeName(form.modelTransform.get()));
        this.sameAnimationWhenDropped.setValue((Boolean) form.sameAnimationWhenDropped.get());
        this.itemStackEditor.setStack((ItemStack) form.stack.get());
    }
}
