package gg.essential.elementa.unstable.common.constraints

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.PaddingConstraint
import gg.essential.elementa.constraints.SizeConstraint
import gg.essential.elementa.constraints.resolution.ConstraintVisitor

/**
 * @see FillConstraint but includes padding in width and height calculations to correctly position the component
 */
class FillConstraintIncludingPadding @JvmOverloads constructor(private val useSiblings: Boolean = true) : SizeConstraint {
    override var cachedValue = 0f
    override var recalculate = true
    override var constrainTo: UIComponent? = null

    override fun getWidthImpl(component: UIComponent): Float {
        val target = constrainTo ?: component.parent

        return if (useSiblings) {
            target.getWidth() - target.children.sumOf {
                val width = if (it == component) 0 else it.getWidth()
                width.toDouble() + ((it.constraints.x as? PaddingConstraint)?.getHorizontalPadding(it) ?: 0f).toDouble()
            }.toFloat()
        } else target.getRight() - component.getLeft() + ((target.constraints.x as? PaddingConstraint)?.getHorizontalPadding(target) ?: 0f)
    }

    override fun getHeightImpl(component: UIComponent): Float {
        val target = constrainTo ?: component.parent

        return if (useSiblings) {
            target.getHeight() - target.children.sumOf {
                val height = if (it == component) 0 else it.getHeight()
                height.toDouble() + ((it.constraints.y as? PaddingConstraint)?.getVerticalPadding(it) ?: 0f).toDouble()
            }.toFloat()
        } else target.getBottom() - component.getTop() + ((target.constraints.y as? PaddingConstraint)?.getVerticalPadding(target) ?: 0f)
    }

    override fun getRadiusImpl(component: UIComponent): Float {
        val target = constrainTo ?: component.parent

        return if (useSiblings) {
            target.getRadius() - target.children.filter { it != component }.sumOf {
                it.getRadius().toDouble()
            }.toFloat()
        } else (target.getRadius() - component.getLeft()) / 2f
    }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {
        when (type) {
            ConstraintType.WIDTH -> {
                visitor.visitParent(ConstraintType.WIDTH)

                if (useSiblings) {
                    val indexInParent = visitor.component.let { it.parent.children.indexOf(it) }
                    val numParentChildren = visitor.component.parent.children.size

                    for (i in 0 until numParentChildren) {
                        if (indexInParent != i)
                            visitor.visitSibling(ConstraintType.WIDTH, i)
                    }
                } else {
                    visitor.visitParent(ConstraintType.X)
                    visitor.visitSelf(ConstraintType.X)
                }
            }
            ConstraintType.HEIGHT -> {
                visitor.visitParent(ConstraintType.HEIGHT)

                if (useSiblings) {
                    val indexInParent = visitor.component.let { it.parent.children.indexOf(it) }
                    val numParentChildren = visitor.component.parent.children.size

                    for (i in 0 until numParentChildren) {
                        if (indexInParent != i)
                            visitor.visitSibling(ConstraintType.HEIGHT, i)
                    }
                } else {
                    visitor.visitParent(ConstraintType.Y)
                    visitor.visitSelf(ConstraintType.Y)
                }
            }
            ConstraintType.RADIUS -> {
                visitor.visitParent(ConstraintType.RADIUS)

                if (useSiblings) {
                    val indexInParent = visitor.component.let { it.parent.children.indexOf(it) }
                    val numParentChildren = visitor.component.parent.children.size

                    for (i in 0 until numParentChildren) {
                        if (indexInParent != i)
                            visitor.visitSibling(ConstraintType.RADIUS, i)
                    }
                } else {
                    visitor.visitSelf(ConstraintType.X)
                }
            }
            else -> throw IllegalArgumentException(type.prettyName)
        }
    }
}