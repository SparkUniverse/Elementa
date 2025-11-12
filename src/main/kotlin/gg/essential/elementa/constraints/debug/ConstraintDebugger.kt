package gg.essential.elementa.constraints.debug

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.HeightConstraint
import gg.essential.elementa.constraints.RadiusConstraint
import gg.essential.elementa.constraints.SuperConstraint
import gg.essential.elementa.constraints.WidthConstraint
import gg.essential.elementa.constraints.XConstraint
import gg.essential.elementa.constraints.YConstraint
import gg.essential.elementa.utils.roundToRealPixels

internal interface ConstraintDebugger {
    fun evaluate(constraint: SuperConstraint<Float>, type: ConstraintType, component: UIComponent): Float

    fun invokeImpl(constraint: SuperConstraint<Float>, type: ConstraintType, component: UIComponent): Float =
        when (type) {
            ConstraintType.X -> (constraint as XConstraint).getXPositionImpl(component).roundToRealPixels()
            ConstraintType.Y -> (constraint as YConstraint).getYPositionImpl(component).roundToRealPixels()
            ConstraintType.WIDTH -> (constraint as WidthConstraint).getWidthImpl(component).roundToRealPixels()
            ConstraintType.HEIGHT -> (constraint as HeightConstraint).getHeightImpl(component).roundToRealPixels()
            ConstraintType.RADIUS -> (constraint as RadiusConstraint).getRadiusImpl(component)
            ConstraintType.TEXT_SCALE -> (constraint as HeightConstraint).getHeightImpl(component).let { if (it == 1f) 1f else it.roundToRealPixels() }
            else -> throw UnsupportedOperationException()
        }
}

internal var constraintDebugger: ConstraintDebugger? = null

internal inline fun withDebugger(debugger: ConstraintDebugger, block: () -> Unit) {
    val prevDebugger = constraintDebugger
    constraintDebugger = debugger
    try {
        block()
    } finally {
        constraintDebugger = prevDebugger
    }
}
