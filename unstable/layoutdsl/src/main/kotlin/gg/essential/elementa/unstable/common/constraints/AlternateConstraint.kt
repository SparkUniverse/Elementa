package gg.essential.elementa.unstable.common.constraints

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.SizeConstraint
import gg.essential.elementa.constraints.resolution.ConstraintVisitor

/**
 * Constraint which tries to evaluate the given constraint but falls back to another constraint if the first constraint
 * results in a circular constraint chain.
 *
 * You probably shouldn't use this. With great power comes great responsibility.
 * Be sure to fully understand how this works and interacts with other constraints before using, otherwise you may see
 * undefined behavior such as unstable results, stack overflow, etc. if any of the involved constraints are not pure
 * or more generally not safe to evaluate recursively (this one for example isn't, so don't ever use multiple).
 */
class AlternateConstraint(
    val primary: SizeConstraint,
    val fallback: SizeConstraint,
) : SizeConstraint {
    override var recalculate: Boolean = true
    override var cachedValue: Float = 0f
    override var constrainTo: UIComponent?
        get() = null
        set(value) = throw UnsupportedOperationException()

    private var tryingPrimary = false
    private var primaryWasRecursive = false

    override fun animationFrame() {
        primary.animationFrame()
        fallback.animationFrame()

        super.animationFrame()
    }

    private inline fun eval(eval: (SizeConstraint) -> Float): Float {
        if (!tryingPrimary) {
            tryingPrimary = true
            try {
                primaryWasRecursive = false
                val value = eval(primary)
                if (!primaryWasRecursive) {
                    return value
                }
            } finally {
                tryingPrimary = false
            }
        } else {
            primaryWasRecursive = true
        }
        return eval(fallback)
    }


    override fun getWidthImpl(component: UIComponent): Float =
        eval { it.getWidth(component) }

    override fun getHeightImpl(component: UIComponent): Float =
        eval { it.getHeight(component) }

    override fun getRadiusImpl(component: UIComponent): Float =
        eval { it.getRadius(component) }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {}
}