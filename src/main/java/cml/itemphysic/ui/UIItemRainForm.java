package cml.itemphysic.ui;

import cml.itemphysic.forms.ItemRainForm;
import cml.itemphysic.l10n.CMLKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * Editor for the {@link ItemRainForm}. It exposes the custom "Rain" section
 * (see {@link UIItemRainPanel}) alongside the stock default form panels.
 */
public class UIItemRainForm extends UIForm<ItemRainForm>
{
    public UIItemRainForm()
    {
        this.defaultPanel = new UIItemRainPanel(this);
        this.registerPanel(this.defaultPanel, CMLKeys.FORMS_EDITORS_RAIN_TITLE, Icons.GEAR);

        this.registerDefaultPanels();
    }
}
