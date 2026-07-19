package cml.itemphysic.ui;

import cml.itemphysic.forms.PhysicItemForm;
import cml.itemphysic.l10n.CMLKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;

/**
 * The "Physique" section shown inside the Physic Item form editor. It exposes:
 * <ul>
 *     <li><b>Physics</b> - a 0..1 progress slider driving the parametric fall
 *     (0 = in the air, 1 = landed). This value is keyframable on the film
 *     timeline.</li>
 *     <li><b>Drop height</b> - start height above the resting position.</li>
 *     <li><b>Spins</b> - number of tumbling rotations during the fall.</li>
 *     <li><b>Bounce</b> - bounce strength near landing.</li>
 *     <li><b>Overlap ("se superposer")</b> - allow items to overlap/stack.</li>
 * </ul>
 */
public class UIItemPhysiquePanel extends UIFormPanel<PhysicItemForm>
{
    public final UITrackpad physics = new UITrackpad(
        v -> ((PhysicItemForm) this.form).physicsProgress.set(Float.valueOf(v.floatValue()))
    );

    public final UITrackpad dropHeight = new UITrackpad(
        v -> ((PhysicItemForm) this.form).dropHeight.set(Float.valueOf(v.floatValue()))
    );

    public final UITrackpad spins = new UITrackpad(
        v -> ((PhysicItemForm) this.form).spins.set(Float.valueOf(v.floatValue()))
    );

    public final UITrackpad bounce = new UITrackpad(
        v -> ((PhysicItemForm) this.form).bounce.set(Float.valueOf(v.floatValue()))
    );

    public final UIToggle overlap = new UIToggle(
        CMLKeys.FORMS_EDITORS_PHYSIC_OVERLAP,
        b -> ((PhysicItemForm) this.form).canOverlap.set(b.getValue())
    );

    public UIItemPhysiquePanel(UIForm editor)
    {
        super(editor);

        this.physics.tooltip(CMLKeys.FORMS_EDITORS_PHYSIC_PHYSICS_TOOLTIP, Direction.RIGHT);
        this.dropHeight.tooltip(CMLKeys.FORMS_EDITORS_PHYSIC_DROP_HEIGHT_TOOLTIP, Direction.RIGHT);
        this.spins.tooltip(CMLKeys.FORMS_EDITORS_PHYSIC_SPINS_TOOLTIP, Direction.RIGHT);
        this.bounce.tooltip(CMLKeys.FORMS_EDITORS_PHYSIC_BOUNCE_TOOLTIP, Direction.RIGHT);
        this.overlap.tooltip(CMLKeys.FORMS_EDITORS_PHYSIC_OVERLAP_TOOLTIP, Direction.RIGHT);

        this.options.add(
            UI.label(CMLKeys.FORMS_EDITORS_PHYSIC_TITLE),
            UI.label(CMLKeys.FORMS_EDITORS_PHYSIC_PHYSICS),
            this.physics,
            UI.label(CMLKeys.FORMS_EDITORS_PHYSIC_DROP_HEIGHT),
            this.dropHeight,
            UI.label(CMLKeys.FORMS_EDITORS_PHYSIC_SPINS),
            this.spins,
            UI.label(CMLKeys.FORMS_EDITORS_PHYSIC_BOUNCE),
            this.bounce,
            this.overlap
        );
    }

    @Override
    public void startEdit(PhysicItemForm form)
    {
        super.startEdit(form);

        this.physics.limit(form.physicsProgress).setValue(form.getPhysicsProgress());
        this.dropHeight.limit(form.dropHeight).setValue(form.getDropHeight());
        this.spins.limit(form.spins).setValue(form.getSpins());
        this.bounce.limit(form.bounce).setValue(form.getBounce());
        this.overlap.setValue((Boolean) form.canOverlap.get());
    }
}
