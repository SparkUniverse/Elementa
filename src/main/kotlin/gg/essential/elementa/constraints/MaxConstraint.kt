package gg.essential.elementa.constraints

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import kotlin.math.max

class MaxConstraint(val first: SuperConstraint<Float>, val second: SuperConstraint<Float>) : MasterConstraint {
    override var cachedValue = 0f
    override var recalculate = true
    override var constrainTo: UIComponent? = null

    @Deprecated("See [ElementaVersion.V8].")
    @Suppress("DEPRECATION")
    override fun animationFrame() {
        super.animationFrame()
        first.animationFrame()
        second.animationFrame()
    }

    override fun getWidthImpl(component: UIComponent): Float {
        return max((first as WidthConstraint).getWidth(component), (second as WidthConstraint).getWidth(component))
    }

    override fun getHeightImpl(component: UIComponent): Float {
        return max((first as HeightConstraint).getHeight(component), (second as HeightConstraint).getHeight(component))
    }

    override fun getRadiusImpl(component: UIComponent): Float {
        return max((first as RadiusConstraint).getRadius(component), (second as RadiusConstraint).getRadius(component))
    }

    override fun getXPositionImpl(component: UIComponent): Float {
        return max((first as XConstraint).getXPosition(component), (second as XConstraint).getXPosition(component))
    }

    override fun getYPositionImpl(component: UIComponent): Float {
        return max((first as YConstraint).getYPosition(component), (second as YConstraint).getYPosition(component))
    }

    override fun to(component: UIComponent) = apply {
        throw UnsupportedOperationException("Constraint.to(UIComponent) is not available in this context, please apply this to the components beforehand.")
    }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {
        first.visit(visitor, type, setNewConstraint = false)
        second.visit(visitor, type, setNewConstraint = false)
    }
}
