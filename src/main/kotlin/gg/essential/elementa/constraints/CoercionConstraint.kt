package gg.essential.elementa.constraints

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.resolution.ConstraintVisitor

/**
 * Clamps [constraint] to be AT MOST [maxConstraint]
 */
class CoerceAtMostConstraint(
    val constraint: SuperConstraint<Float>,
    val maxConstraint: SuperConstraint<Float>
) : MasterConstraint {
    override var cachedValue = 0f
    override var recalculate = true
    override var constrainTo: UIComponent? = null

    @Deprecated("See [ElementaVersion.V8].")
    @Suppress("DEPRECATION")
    override fun animationFrame() {
        super.animationFrame()
        constraint.animationFrame()
        maxConstraint.animationFrame()
    }

    override fun getWidthImpl(component: UIComponent): Float {
        return (constraint as WidthConstraint).getWidth(component)
            .coerceAtMost((maxConstraint as WidthConstraint).getWidth(component))
    }

    override fun getHeightImpl(component: UIComponent): Float {
        return (constraint as HeightConstraint).getHeight(component)
            .coerceAtMost((maxConstraint as HeightConstraint).getHeight(component))
    }

    override fun getRadiusImpl(component: UIComponent): Float {
        return (constraint as RadiusConstraint).getRadius(component)
            .coerceAtMost((maxConstraint as RadiusConstraint).getRadius(component))
    }

    override fun getXPositionImpl(component: UIComponent): Float {
        return (constraint as XConstraint).getXPosition(component)
            .coerceAtMost((maxConstraint as XConstraint).getXPosition(component))
    }

    override fun getYPositionImpl(component: UIComponent): Float {
        return (constraint as YConstraint).getYPosition(component)
            .coerceAtMost((maxConstraint as YConstraint).getYPosition(component))
    }

    override fun to(component: UIComponent) = apply {
        throw UnsupportedOperationException("Constraint.to(UIComponent) is not available in this context, please apply this to the components beforehand.")
    }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {
        constraint.visit(visitor, type, setNewConstraint = false)
        maxConstraint.visit(visitor, type, setNewConstraint = false)
    }
}

/**
 * Clamps [constraint] to be AT LEAST [minConstraint]
 */
class CoerceAtLeastConstraint(
    val constraint: SuperConstraint<Float>,
    val minConstraint: SuperConstraint<Float>
) : MasterConstraint {
    override var cachedValue = 0f
    override var recalculate = true
    override var constrainTo: UIComponent? = null

    @Deprecated("See [ElementaVersion.V8].")
    @Suppress("DEPRECATION")
    override fun animationFrame() {
        super.animationFrame()
        constraint.animationFrame()
        minConstraint.animationFrame()
    }

    override fun getWidthImpl(component: UIComponent): Float {
        return (constraint as WidthConstraint).getWidth(component)
            .coerceAtLeast((minConstraint as WidthConstraint).getWidth(component))
    }

    override fun getHeightImpl(component: UIComponent): Float {
        return (constraint as HeightConstraint).getHeight(component)
            .coerceAtLeast((minConstraint as HeightConstraint).getHeight(component))
    }

    override fun getRadiusImpl(component: UIComponent): Float {
        return (constraint as RadiusConstraint).getRadius(component)
            .coerceAtLeast((minConstraint as RadiusConstraint).getRadius(component))
    }

    override fun getXPositionImpl(component: UIComponent): Float {
        return (constraint as XConstraint).getXPosition(component)
            .coerceAtLeast((minConstraint as XConstraint).getXPosition(component))
    }

    override fun getYPositionImpl(component: UIComponent): Float {
        return (constraint as YConstraint).getYPosition(component)
            .coerceAtLeast((minConstraint as YConstraint).getYPosition(component))
    }

    override fun to(component: UIComponent) = apply {
        throw UnsupportedOperationException("Constraint.to(UIComponent) is not available in this context, please apply this to the components beforehand.")
    }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {
        constraint.visit(visitor, type, setNewConstraint = false)
        minConstraint.visit(visitor, type, setNewConstraint = false)
    }
}

/**
 * Clamps constraint to be within [minConstraint] and [maxConstraint]
 */
class CoerceInConstraint(
    val constraint: SuperConstraint<Float>,
    val minConstraint: SuperConstraint<Float>,
    val maxConstraint: SuperConstraint<Float>
) : MasterConstraint {
    override var cachedValue = 0f
    override var recalculate = true
    override var constrainTo: UIComponent? = null

    @Deprecated("See [ElementaVersion.V8].")
    @Suppress("DEPRECATION")
    override fun animationFrame() {
        super.animationFrame()
        constraint.animationFrame()
        minConstraint.animationFrame()
        maxConstraint.animationFrame()
    }

    override fun getWidthImpl(component: UIComponent): Float {
        return (constraint as WidthConstraint).getWidth(component)
            .coerceIn(
                (minConstraint as WidthConstraint).getWidth(component),
                (maxConstraint as WidthConstraint).getWidth(component)
            )
    }

    override fun getHeightImpl(component: UIComponent): Float {
        return (constraint as HeightConstraint).getHeight(component)
            .coerceIn(
                (minConstraint as HeightConstraint).getHeight(component),
                (maxConstraint as HeightConstraint).getHeight(component)
            )
    }

    override fun getRadiusImpl(component: UIComponent): Float {
        return (constraint as RadiusConstraint).getRadius(component)
            .coerceIn(
                (minConstraint as RadiusConstraint).getRadius(component),
                (maxConstraint as RadiusConstraint).getRadius(component)
            )
    }

    override fun getXPositionImpl(component: UIComponent): Float {
        return (constraint as XConstraint).getXPosition(component)
            .coerceIn(
                (minConstraint as XConstraint).getXPosition(component),
                (maxConstraint as XConstraint).getXPosition(component)
            )
    }

    override fun getYPositionImpl(component: UIComponent): Float {
        return (constraint as YConstraint).getYPosition(component)
            .coerceIn(
                (minConstraint as YConstraint).getYPosition(component),
                (maxConstraint as YConstraint).getYPosition(component)
            )
    }

    override fun to(component: UIComponent) = apply {
        throw UnsupportedOperationException("Constraint.to(UIComponent) is not available in this context, please apply this to the components beforehand.")
    }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {
        constraint.visit(visitor, type, setNewConstraint = false)
        minConstraint.visit(visitor, type, setNewConstraint = false)
    }
}
