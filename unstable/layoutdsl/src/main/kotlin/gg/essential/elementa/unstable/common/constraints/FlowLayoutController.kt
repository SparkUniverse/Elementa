package gg.essential.elementa.unstable.common.constraints

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.PaddingConstraint
import gg.essential.elementa.constraints.PositionConstraint
import gg.essential.elementa.constraints.WidthConstraint
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.utils.ObservableAddEvent
import gg.essential.elementa.utils.ObservableClearEvent
import gg.essential.elementa.utils.ObservableListEvent
import gg.essential.elementa.utils.ObservableRemoveEvent
import gg.essential.elementa.utils.roundToRealPixels
import gg.essential.elementa.unstable.layoutdsl.Alignment
import gg.essential.elementa.unstable.layoutdsl.Arrangement
import kotlin.math.max

class FlowLayoutController(
    private val component: UIComponent,
    /** Minimum spacing allocated between items in each row. Actual spacing is determined by [itemArrangement]. */
    private val xSpacingMin: Float,
    /** Spacing between rows. */
    private val ySpacing: Float,
    /** Arranges items in a row */
    private val itemArrangement: Arrangement,
    /** Aligns items vertically within a row */
    private val itemAlignment: Alignment,
) {
    /**
     * We use a dummy constraint "around" our layout, so Elementa correctly manages cache invalidation for us.
     * This is required because [FlowLayoutControlledPositionConstraint] only manages invalidation for X/Y values
     * but not for padding (because Elementa's [PaddingConstraint] doesn't do any caching by default), so if neither
     * X nor Y values are queried, but padding values are (e.g. to compute the height of the container), then the layout
     * would only be done once and the same stale values would always be returned thereafter even if the size of
     * children has changed in the mean time.
     */
    private val wrapperConstraint = object : WidthConstraint {
        override var recalculate = true
        override fun getWidthImpl(component: UIComponent): Float {
            layout()
            return 0f
        }
        override var cachedValue: Float = 0f
        override var constrainTo: UIComponent? = null
        override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {}

    }

    class Layout(var x: Float, var y: Float, var yPadding: Float)
    private val cachedLayout = mutableMapOf<UIComponent, Layout>()

    fun getLayout(component: UIComponent): Layout {
        // Recompute layout if required
        wrapperConstraint.getWidth(component)

        return cachedLayout[component]
            ?: error("Component $component's position was not laid out by $this")
    }

    private fun layout() {
        val containerWidth = component.getWidth()

        class Row(val startIndex: Int, override val size: Int, val maxHeight: Float) : AbstractList<Float>() {
            override fun get(index: Int): Float = component.children[startIndex + index].getWidth()
        }
        val rows = sequence {
            val spacing = xSpacingMin.roundToRealPixels()
            var startIndex = 0
            var size = 0
            var currentWidth = -spacing
            var maxHeight = 0f
            for ((index, child) in component.children.withIndex()) {
                val childWidth = child.getWidth()
                if (currentWidth + spacing + childWidth > containerWidth + EPSILON && size > 0) {
                    yield(Row(startIndex, size, maxHeight))
                    startIndex = index
                    size = 0
                    currentWidth = -spacing
                    maxHeight = 0f
                }
                size++
                currentWidth += spacing + childWidth
                maxHeight = max(maxHeight, child.getHeight())
            }
            if (size > 0) {
                yield(Row(startIndex, size, maxHeight))
            }
        }

        var y = 0f
        for ((rowIndex, row) in rows.withIndex()) {
            itemArrangement.arrange(containerWidth, row) { i, x ->
                val child = component.children[row.startIndex + i]
                val height = child.getHeight()
                cachedLayout[child] = Layout(
                    x = x,
                    y = y + itemAlignment.align(row.maxHeight, height),
                    // This allows ChildBasedSizeConstraint to function for the parent height by emitting negative
                    // padding for all but the first item in a row
                    yPadding = (if (i == 0) row.maxHeight + (if (rowIndex == 0) 0f else ySpacing) else 0f) - height,
                )
            }
            y += row.maxHeight + ySpacing
        }
    }

    init {
        component.children.forEach(::applyConstraints)
        component.children.addObserver { _, maybeEvent ->
            @Suppress("UNCHECKED_CAST")
            val event = maybeEvent as? ObservableListEvent<UIComponent> ?: return@addObserver
            wrapperConstraint.recalculate = true
            when (event) {
                is ObservableAddEvent -> applyConstraints(event.element.value)
                is ObservableRemoveEvent -> cachedLayout.remove(event.element.value)
                is ObservableClearEvent -> cachedLayout.clear()
            }
        }
    }

    private fun applyConstraints(child: UIComponent) {
        child.setX(FlowLayoutControlledPositionConstraint())
        child.setY(FlowLayoutControlledPositionConstraint())
    }

    private inner class FlowLayoutControlledPositionConstraint : PositionConstraint, PaddingConstraint {
        override var cachedValue = 0f
        override var recalculate = true
            set(value) {
                field = value
                if (value) {
                    wrapperConstraint.recalculate = true
                }
            }

        override fun getXPositionImpl(component: UIComponent) = component.parent.getLeft() + getLayout(component).x
        override fun getYPositionImpl(component: UIComponent) = component.parent.getTop() + getLayout(component).y
        override fun getHorizontalPadding(component: UIComponent): Float = 0f
        override fun getVerticalPadding(component: UIComponent): Float = getLayout(component).yPadding

        override var constrainTo: UIComponent?
            get() = null
            set(_) = throw UnsupportedOperationException()

        override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {}
    }

    companion object {
        private const val EPSILON = 0.01f
    }
}
