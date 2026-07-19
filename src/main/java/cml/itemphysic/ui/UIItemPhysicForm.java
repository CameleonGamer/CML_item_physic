package cml.itemphysic.ui;

import cml.itemphysic.forms.PhysicItemForm;
import cml.itemphysic.l10n.CMLKeys;
import cml.itemphysic.ui.PhysicItemFormPanel;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * Editor for the {@link PhysicItemForm}. It reuses the stock item property panel
 * and adds the custom "Physique" section (see {@link UIItemPhysiquePanel}).
 */
public class UIItemPhysicForm extends UIForm<PhysicItemForm>
{
    public UIItemPhysicForm()
    {
        this.defaultPanel = new PhysicItemFormPanel(this);
        this.registerPanel(this.defaultPanel, mchorse.bbs_mod.ui.UIKeys.FORMS_EDITORS_ITEM_TITLE, Icons.LINE);

        this.registerPanel(new UIItemPhysiquePanel(this), CMLKeys.FORMS_EDITORS_PHYSIC_TITLE, Icons.GEAR);

        this.registerDefaultPanels();
    }
}
