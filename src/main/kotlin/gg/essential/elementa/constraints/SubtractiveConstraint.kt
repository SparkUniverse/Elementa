package gg.essential.elementa.constraints

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.resolution.ConstraintVisitor

class SubtractiveConstraint(
    val constraint1: SuperConstraint<Float>,
    val constraint2: SuperConstraint<Float>
) : MasterConstraint {
    override var cachedValue = 0f
    override var recalculate = true
    override var constrainTo: UIComponent? = null

    @Deprecated("See [ElementaVersion.V8].")
    @Suppress("DEPRECATION")
    override fun animationFrame() {
        super.animationFrame()
        constraint1.animationFrame()
        constraint2.animationFrame()
    }

    override fun getXPositionImpl(component: UIComponent): Float {
        return (constraint1 as XConstraint).getXPosition(component) -
                (constraint2 as XConstraint).getXPosition(component) +
                (constrainTo ?: component.parent).getLeft()
    }

    override fun getYPositionImpl(component: UIComponent): Float {
        return (constraint1 as YConstraint).getYPosition(component) -
                (constraint2 as YConstraint).getYPosition(component) +
                (constrainTo?: component.parent).getTop()
    }

    override fun getWidthImpl(component: UIComponent): Float {
        return (constraint1 as WidthConstraint).getWidth(component) -
                (constraint2 as WidthConstraint).getWidth(component)
    }

    override fun getHeightImpl(component: UIComponent): Float {
        return (constraint1 as HeightConstraint).getHeightImpl(component) -
                (constraint2 as HeightConstraint).getHeightImpl(component)
    }

    override fun getRadiusImpl(component: UIComponent): Float {
        return (constraint1 as RadiusConstraint).getRadiusImpl(component) -
                (constraint2 as RadiusConstraint).getRadiusImpl(component)
    }

    override fun to(component: UIComponent) = apply {
        throw UnsupportedOperationException("Constraint.to(UIComponent) is not available in this context, please apply this to the components beforehand.")
    }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {
        constraint1.visit(visitor, type, setNewConstraint = false)
        constraint2.visit(visitor, type, setNewConstraint = false)
    }
}