package gg.essential.elementa.constraints.debug

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.SuperConstraint
import gg.essential.elementa.constraints.getCached

internal class NoopConstraintDebugger : ConstraintDebugger {
    override fun evaluate(constraint: SuperConstraint<Float>, type: ConstraintType, component: UIComponent): Float {
        return constraint.getCached(component) { invokeImpl(constraint, type, component) }
    }
}
