package cml.itemphysic;

import cml.itemphysic.forms.ItemRainForm;
import cml.itemphysic.forms.PhysicItemForm;
import mchorse.bbs_mod.events.BBSAddonMod;
import mchorse.bbs_mod.events.Subscribe;
import mchorse.bbs_mod.events.register.RegisterFormsEvent;

/**
 * Common-side (logical server / both environments) entry point for the CML
 * Item Physic addon. Registers the {@link PhysicItemForm} form type so it can
 * be created, saved and loaded by BBS.
 *
 * <p><b>Important:</b> BBS's {@code EventBus.register(Object)} discovers
 * {@code @Subscribe} handlers via {@code getClass().getDeclaredMethods()},
 * which only returns methods declared <i>directly</i> on the concrete class.
 * Inherited handlers (e.g. from {@code BBSAddon}) are therefore ignored, so the
 * {@code @Subscribe} methods must live on this class itself.</p>
 */
public class ItemPhysicAddon implements BBSAddonMod
{
    @Subscribe
    public void onRegisterForms(RegisterFormsEvent event)
    {
        event.getForms().register(PhysicItemForm.FORM_ID, PhysicItemForm.class);
        event.getForms().register(ItemRainForm.FORM_ID, ItemRainForm.class);
    }
}
