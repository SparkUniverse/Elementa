package gg.essential.elementa.unstable.common.constraints

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.constraints.WidthConstraint
import gg.essential.elementa.constraints.resolution.ConstraintVisitor

/**
 * Note: All items are assumed to be same width
 */
@Deprecated("Apply FlowLayoutController on container instead.")
class SpacedCramSiblingConstraint(
    private val minSeparation: WidthConstraint,
    private val margin: WidthConstraint,
    private val verticalSeparation: WidthConstraint? = null,
) :
    SiblingConstraint() {
    override var cachedValue = 0f
    override var recalculate = true
    override var constrainTo: UIComponent? = null

    override fun getXPositionImpl(component: UIComponent): Float {
        val index = component.parent.children.indexOf(component)

        val marginPixels = margin.getWidth(component).toInt()
        val minSeparationPixels = minSeparation.getWidth(component).toInt()
        val totalWidth = component.parent.getWidth() - marginPixels * 2
        val itemWidth = component.getWidth()
        val itemsPerRow = ((totalWidth + minSeparationPixels) / (itemWidth + minSeparationPixels)).toInt()
        if (itemsPerRow <= 1) {
            return component.parent.getLeft() + ((totalWidth - itemWidth) / 2f)
        }
        if (index == 0) {
            return component.parent.getLeft() + marginPixels
        }
        val itemSep = (totalWidth - itemsPerRow * itemWidth) / (itemsPerRow - 1)
        val sibling = component.parent.children[index - 1]
        if (sibling.getRight() + component.getWidth() + minSeparationPixels <= component.parent.getRight() + floatErrorMargin) {
            return sibling.getRight() + itemSep
        }

        return component.parent.getLeft() + marginPixels
    }

    override fun getYPositionImpl(component: UIComponent): Float {
        val index = component.parent.children.indexOf(component)

        val marginPixels = margin.getWidth(component).toInt()
        if (index == 0) {
            return component.parent.getTop() + marginPixels
        }

        val minSeparationPixels = minSeparation.getWidth(component).toInt()
        val totalWidth = component.parent.getWidth() - marginPixels * 2
        val itemWidth = component.getWidth()
        val itemsPerRow = ((totalWidth + minSeparationPixels) / (itemWidth + minSeparationPixels)).toInt()
        val sibling = component.parent.children[index - 1]
        if (itemsPerRow <= 1) {
            return sibling.getBottom() + minSeparationPixels
        }
        val itemSep = (totalWidth - itemsPerRow * itemWidth) / (itemsPerRow - 1)

        if (sibling.getRight() + component.getWidth() + minSeparationPixels <= component.parent.getRight() + floatErrorMargin) {
            return sibling.getTop()
        }
        val verticalSep = verticalSeparation?.getWidth(component.parent)
            ?: itemSep.coerceAtLeast(minSeparationPixels.toFloat())
        return getLowestPoint(sibling, component.parent, index) + verticalSep
    }

    // This allows ChildBasedSizeConstraint to function for the parent height by emitting negative padding for
    // items that are layed out in-line.
    // Note: For simplicity this assumes all items are the same size, both horizontally (as the constraint as a whole
    //       already assumes) but also vertically.
    //       It also does not support margin as that's just unnecessary complexity (just add a wrapper if you need it).
    override fun getVerticalPadding(component: UIComponent): Float {
        val index = component.parent.children.indexOf(component)
        if (index == 0) {
            return 0f
        }

        val minSeparationPixels = minSeparation.getWidth(component).toInt()
        val totalWidth = component.parent.getWidth()
        val itemWidth = component.getWidth()
        val itemsPerRow = ((totalWidth + minSeparationPixels) / (itemWidth + minSeparationPixels)).toInt()
        if (itemsPerRow <= 1) {
            return minSeparationPixels.toFloat()
        }
        val itemSep = (totalWidth - itemsPerRow * itemWidth) / (itemsPerRow - 1)
        if (index % itemsPerRow == 0) {
            return verticalSeparation?.getWidth(component.parent)
                ?: itemSep.coerceAtLeast(minSeparationPixels.toFloat())
        }
        return -component.getHeight()
    }

    override fun to(component: UIComponent) = apply {
        throw UnsupportedOperationException("Constraint.to(UIComponent) is not available in this context!")
    }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {
        val indexInParent = visitor.component.let { it.parent.children.indexOf(it) }

        when (type) {
            ConstraintType.X -> {
                if (indexInParent == 0) {
                    visitor.visitParent(ConstraintType.X)
                    return
                }

                visitor.visitSelf(ConstraintType.WIDTH)
                visitor.visitSibling(ConstraintType.X, indexInParent - 1)
                visitor.visitSibling(ConstraintType.WIDTH, indexInParent - 1)
                visitor.visitParent(ConstraintType.WIDTH)
                visitor.visitParent(ConstraintType.X)
            }
            ConstraintType.Y -> {
                if (indexInParent == 0) {
                    visitor.visitParent(ConstraintType.Y)
                    return
                }

                visitor.visitSelf(ConstraintType.WIDTH)
                visitor.visitSibling(ConstraintType.X, indexInParent - 1)
                visitor.visitSibling(ConstraintType.WIDTH, indexInParent - 1)
                visitor.visitParent(ConstraintType.WIDTH)
                visitor.visitParent(ConstraintType.X)
            }
            else -> throw IllegalArgumentException(type.prettyName)
        }
    }

    companion object {
        private const val floatErrorMargin = 0.001f
    }
}
