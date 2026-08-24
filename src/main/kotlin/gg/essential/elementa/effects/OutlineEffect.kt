package gg.essential.elementa.effects

import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.ImmediateElementaExtractor
import gg.essential.elementa.renderer.fillMcScale
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.MappedState
import gg.essential.elementa.state.State
import gg.essential.universal.UMatrixStack
import java.awt.Color

/**
 * Draws a basic, rectangular outline of the specified [color] and [width] around
 * this component. The outline will be drawn before this component's children are drawn,
 * so all children will render above the outline.
 */
class OutlineEffect @JvmOverloads constructor(
    color: State<Color>,
    width: State<Float>,
    var drawAfterChildren: Boolean = false,
    var drawInsideChildren: Boolean = false,
    sides: Set<Side> = setOf(Side.Left, Side.Top, Side.Right, Side.Bottom)
) : Effect() {
    @JvmOverloads constructor(
        color: Color,
        width: Float,
        drawAfterChildren: Boolean = false,
        drawInsideChildren: Boolean = false,
        sides: Set<Side> = setOf(Side.Left, Side.Top, Side.Right, Side.Bottom)
    ) : this(BasicState(color), BasicState(width), drawAfterChildren, drawInsideChildren, sides)

    private var hasLeft = Side.Left in sides
    private var hasTop = Side.Top in sides
    private var hasRight = Side.Right in sides
    private var hasBottom = Side.Bottom in sides

    private val colorState: MappedState<Color, Color> = color.map { it }
    private val widthState: MappedState<Float, Float> = width.map { it }

    var color: Color
        get() = colorState.get()
        set(value) {
            colorState.set(value)
        }

    var width: Float
        get() = widthState.get()
        set(value) {
            widthState.set(value)
        }

    fun bindColor(state: State<Color>) = apply {
        colorState.rebind(state)
    }

    fun bindWidth(state: State<Float>) = apply {
        widthState.rebind(state)
    }

    var sides = sides
        set(value) {
            field = value
            hasLeft = Side.Left in sides
            hasTop = Side.Top in sides
            hasRight = Side.Right in sides
            hasBottom = Side.Bottom in sides
        }

    fun addSide(side: Side) = apply {
        sides = sides + side
    }

    fun removeSide(side: Side) = apply {
        sides = sides - side
    }

    override fun extractBeforeChildren(extractor: ElementaExtractor) {
        if (!drawAfterChildren)
            drawOutline(extractor)
    }

    override fun extractAfter(extractor: ElementaExtractor) {
        if (drawAfterChildren)
            drawOutline(extractor)
    }

    @Deprecated(
        "`draw`-style rendering is deprecated. Use `extract` instead.",
        replaceWith = ReplaceWith("extractBeforeChildren(extractor)")
    )
    override fun beforeChildrenDraw(matrixStack: UMatrixStack) {
        if (!drawAfterChildren)
            drawOutline(ImmediateElementaExtractor(matrixStack))
    }

    @Deprecated(
        "`draw`-style rendering is deprecated. Use `extract` instead.",
        replaceWith = ReplaceWith("extractAfter(extractor)")
    )
    override fun afterDraw(matrixStack: UMatrixStack) {
        if (drawAfterChildren)
            drawOutline(ImmediateElementaExtractor(matrixStack))
    }

    private fun drawOutline(extractor: ElementaExtractor) {
        val color = colorState.get()
        val width = widthState.get()

        val left = boundComponent.getLeft()
        val right = boundComponent.getRight()
        val top = boundComponent.getTop()
        val bottom = boundComponent.getBottom()

        val leftBounds = if (drawInsideChildren) {
            left to (left + width)
        } else (left - width) to left

        val topBounds = if (drawInsideChildren) {
            top to (top + width)
        } else (top - width) to top

        val rightBounds = if (drawInsideChildren) {
            (right - width) to right
        } else right to (right + width)

        val bottomBounds = if (drawInsideChildren) {
            (bottom - width) to bottom
        } else bottom to (bottom + width)

        // Left outline block
        if (hasLeft)
            extractor.fillMcScale(leftBounds.first, top, leftBounds.second, bottom, color)

        // Top outline block
        if (hasTop)
            extractor.fillMcScale(left, topBounds.first, right, topBounds.second, color)

        // Right outline block
        if (hasRight)
            extractor.fillMcScale(rightBounds.first, top, rightBounds.second, bottom, color)

        // Bottom outline block
        if (hasBottom)
            extractor.fillMcScale(left, bottomBounds.first, right, bottomBounds.second, color)

        if (!drawInsideChildren) {
            // Top left square
            if (hasLeft && hasTop)
                extractor.fillMcScale(leftBounds.first, topBounds.first, left, top, color)

            // Top right square
            if (hasRight && hasTop)
                extractor.fillMcScale(right, topBounds.first, rightBounds.second, top, color)

            // Bottom right square
            if (hasRight && hasBottom)
                extractor.fillMcScale(right, bottom, rightBounds.second, bottomBounds.second, color)

            // Bottom left square
            if (hasBottom && hasLeft)
                extractor.fillMcScale(leftBounds.first, bottom, left, bottomBounds.second, color)
        }
    }

    enum class Side {
        Left,
        Top,
        Right,
        Bottom,
    }
}
