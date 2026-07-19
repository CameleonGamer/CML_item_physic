package cml.itemphysic;

import cml.itemphysic.forms.ItemRainForm;
import cml.itemphysic.forms.PhysicItemForm;
import cml.itemphysic.forms.renderers.ItemRainFormRenderer;
import cml.itemphysic.forms.renderers.PhysicItemFormRenderer;
import cml.itemphysic.ui.UIItemPhysicForm;
import cml.itemphysic.ui.UIItemRainForm;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.events.BBSAddonMod;
import mchorse.bbs_mod.events.Subscribe;
import mchorse.bbs_mod.events.register.RegisterFormEditorsEvent;
import mchorse.bbs_mod.events.register.RegisterFormsRenderersEvent;
import mchorse.bbs_mod.events.register.RegisterL10nEvent;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.Collections;

/**
 * Client-side entry point for the CML Item Physic addon. Registers:
 * <ul>
 *     <li>the {@link PhysicItemFormRenderer} for {@link PhysicItemForm},</li>
 *     <li>the {@link UIItemPhysicForm} editor (with the "Physique" section),</li>
 *     <li>the form into the form palette's "Miscellaneous" (a.k.a. "Extra")
 *         category. This must happen <b>after</b> BBS rebuilds its categories in
 *         {@code FormCategories.setup()} (called from BBSResources.init()), so we
 *         defer it to the first client tick,</li>
 *     <li>the localization string tables.</li>
 * </ul>
 *
 * <p><b>Important:</b> BBS's {@code EventBus.register(Object)} discovers
 * {@code @Subscribe} handlers via {@code getClass().getDeclaredMethods()},
 * which only returns methods declared <i>directly</i> on the concrete class.
 * Inherited handlers (e.g. from {@code BBSClientAddon}) are therefore ignored,
 * so the {@code @Subscribe} methods must live on this class itself.</p>
 */
public class ItemPhysicAddonClient implements BBSAddonMod
{
    private static boolean formRegistered;

    @Subscribe
    public void onRegisterFormsRenderers(RegisterFormsRenderersEvent event)
    {
        /* PhysicItemFormRenderer extends ItemFormRenderer (FormRenderer<ItemForm>);
         * a raw cast bridges the invariant generic so it can be registered for
         * the PhysicItemForm subclass. Safe because of type erasure. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        FormUtilsClient.IFormRendererFactory factory = form -> new PhysicItemFormRenderer((PhysicItemForm) form);
        event.registerRenderer(PhysicItemForm.class, factory);

        @SuppressWarnings({"unchecked", "rawtypes"})
        FormUtilsClient.IFormRendererFactory rainFactory = form -> new ItemRainFormRenderer((ItemRainForm) form);
        event.registerRenderer(ItemRainForm.class, rainFactory);
    }

    @Subscribe
    public void onRegisterFormEditors(RegisterFormEditorsEvent event)
    {
        event.register(PhysicItemForm.class, UIItemPhysicForm::new);
        event.register(ItemRainForm.class, UIItemRainForm::new);
    }

    @Subscribe
    public void onRegisterL10n(RegisterL10nEvent event)
    {
        /* BBS resolves assets:<path> through its AssetProvider, which only knows
         * about BBS's own classpath ("assets/bbs/assets"). Register an extra
         * source pack for the "assets" prefix that points at this addon's
         * classpath root ("assets/cml_item_physic/assets") so that
         * assets:cml_item_physic/strings/<lang>.json resolves to the classpath
         * resource assets/cml_item_physic/assets/cml_item_physic/strings/<lang>.json. */
        mchorse.bbs_mod.BBSMod.getProvider().register(
            new mchorse.bbs_mod.resources.packs.InternalAssetsSourcePack(
                "assets", "assets/cml_item_physic/assets", ItemPhysicAddonClient.class
            )
        );

        /* Provide per-language string tables from the addon's assets. The
         * L10n.register signature is Function<String, List<Link>>. */
        event.l10n.register(lang -> Collections.singletonList(
            mchorse.bbs_mod.resources.Link.assets("cml_item_physic/strings/" + lang + ".json")
        ));

        /* Register the form into the palette after BBS finished building its
         * categories (which happens later, in BBSResources.init()). */
        ClientTickEvents.END_CLIENT_TICK.register(client -> this.ensureFormRegistered());
    }

    /**
     * Adds the PhysicItemForm template to the Miscellaneous/Extra category once
     * the categories have been set up by BBS.
     */
    private void ensureFormRegistered()
    {
        if (formRegistered)
        {
            return;
        }

        FormCategories categories = BBSModClient.getFormCategories();

        if (categories == null || categories.getExtraForms() == null)
        {
            return;
        }

        var extra = categories.getExtraForms().getExtraCategory();

        if (extra == null)
        {
            /* Categories not set up yet. */
            return;
        }

        boolean hasPhysic = false;
        boolean hasRain = false;

        for (Form form : extra.getForms())
        {
            if (form instanceof PhysicItemForm)
            {
                hasPhysic = true;
            }
            else if (form instanceof ItemRainForm)
            {
                hasRain = true;
            }
        }

        if (!hasPhysic)
        {
            PhysicItemForm template = new PhysicItemForm();
            template.stack.set(new ItemStack(Items.STONE));
            extra.addForm(template);
        }

        if (!hasRain)
        {
            ItemRainForm rain = new ItemRainForm();
            rain.stack.set(new ItemStack(Items.STONE));
            extra.addForm(rain);
        }

        categories.markDirty();
        formRegistered = true;
    }
}
