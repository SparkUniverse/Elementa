package gg.essential.elementa.unstable.common.constraints

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.PositionConstraint
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import kotlin.math.ceil
import kotlin.math.floor

/** Centers the component to whole pixels, rounding down unless [roundUp] is true */
class CenterPixelConstraint(private val roundUp: Boolean = false) : PositionConstraint {

    override var cachedValue = 0f
    override var recalculate = true
    override var constrainTo: UIComponent? = null

    override fun getXPositionImpl(component: UIComponent): Float {
        val parent = constrainTo ?: component.parent

        val center = if (component.isPositionCenter()) {
            parent.getWidth() / 2
        } else {
            parent.getWidth() / 2 - component.getWidth() / 2
        }

        return parent.getLeft() + if (roundUp) ceil(center) else floor(center)
    }

    override fun getYPositionImpl(component: UIComponent): Float {
        val parent = constrainTo ?: component.parent

        val center = if (component.isPositionCenter()) {
            parent.getHeight() / 2
        } else {
            parent.getHeight() / 2 - component.getHeight() / 2
        }

        return parent.getTop() + if (roundUp) ceil(center) else floor(center)
    }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {}
}
