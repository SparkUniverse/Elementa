package gg.essential.elementa.markdown.drawables

import gg.essential.elementa.markdown.DrawState
import gg.essential.elementa.markdown.MarkdownComponent
import gg.essential.elementa.markdown.MarkdownConfig
import gg.essential.elementa.markdown.selection.Cursor
import gg.essential.elementa.markdown.selection.TextCursor
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.universal.UMatrixStack

/**
 * Represents a list of drawables.
 *
 * Most markdown drawables accepts a DrawableList, which it
 * will display with additional "effects" (such as a vertical
 * bar in BlockquoteDrawable). This class nicely delegates
 * certain behavior to its children, such as layout and
 * selection.
 */
class DrawableList(
    md: MarkdownComponent,
    drawables: List<Drawable>
) : Drawable(md), List<Drawable> {
    private lateinit var drawables: List<Drawable>
    override val children: List<Drawable> get() = drawables

    init {
        setDrawables(drawables)
    }

    fun setDrawables(newDrawables: List<Drawable>) {
        drawables = newDrawables
        drawables.forEach { it.parent = this }
        trim(this)

        forEachIndexed { index, drawable ->
            if (index > 0)
                drawable.previous = this[index - 1]
            if (index != lastIndex)
                drawable.next = this[index + 1]
        }
    }

    override fun layoutImpl(x: Float, y: Float, width: Float): Layout {
        var currY = y
        forEach {
            currY += it.layout(x, currY, width).height
        }
        val height = currY - y
        return Layout(x, y, width, height)
    }

    override fun cursorAt(mouseX: Float, mouseY: Float, dragged: Boolean, mouseButton: Int): Cursor<*> {
        // Used for positioning the cursor in-between drawables if no
        // drawable is being directly hovered
        var closestDrawable: Drawable? = null
        var closestDistance = Float.MAX_VALUE
        var direction: Direction? = null

        for (drawable in drawables) {
            if (drawable.isHovered(mouseX, mouseY)) {
                return drawable.cursorAt(mouseX, mouseY, dragged, mouseButton)
            } else {
                if (mouseY < drawable.y) {
                    if (drawable.y - mouseY < closestDistance) {
                        direction = Direction.Up
                        closestDistance = drawable.y - mouseY
                        closestDrawable = drawable
                    }
                } else if (mouseY > drawable.y + drawable.height) {
                    if (drawable.y + drawable.height - mouseY < closestDistance) {
                        direction = Direction.Down
                        closestDistance = mouseY - (drawable.y + drawable.height)
                        closestDrawable = drawable
                    }
                } else {
                    // The drawable is hovered vertically, but not horizontally
                    closestDistance = 0f
                    closestDrawable = drawable
                    direction = if (mouseX < drawable.x) {
                        Direction.Left
                    } else Direction.Right
                    break
                }
            }
        }

        if (closestDrawable == null || closestDistance == Float.MAX_VALUE || direction == null)
            TODO()

        return when (direction) {
            Direction.Up -> closestDrawable.cursorAtStart()
            Direction.Down -> closestDrawable.cursorAtEnd()
            Direction.Left, Direction.Right ->
                closestDrawable.cursorAt(mouseX, mouseY, dragged, mouseButton)
        }
    }

    override fun cursorAtStart() = drawables.first().cursorAtStart()

    override fun cursorAtEnd() = drawables.last().cursorAtEnd()

    override fun draw(matrixStack: UMatrixStack, state: DrawState) {
        forEach { it.drawCompat(matrixStack, state) }
    }

    override fun extract(extractor: ElementaExtractor, state: DrawState) {
        forEach { it.extract(extractor, state) }
    }

    override fun selectedText(asMarkdown: Boolean): String {
        return filter {
            it.hasSelectedText()
        }.joinToString(separator = "\n\n") {
            it.selectedText(asMarkdown)
        }
    }

    override val size get() = drawables.size
    override fun contains(element: Drawable) = element in drawables
    override fun containsAll(elements: Collection<Drawable>) = drawables.containsAll(elements)
    override fun get(index: Int) = drawables[index]
    override fun indexOf(element: Drawable) = drawables.indexOf(element)
    override fun isEmpty() = drawables.isEmpty()
    override fun iterator() = drawables.iterator()
    override fun lastIndexOf(element: Drawable) = drawables.lastIndexOf(element)
    override fun listIterator() = drawables.listIterator()
    override fun listIterator(index: Int) = drawables.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int) = drawables.subList(fromIndex, toIndex)

    enum class Direction {
        Up,
        Down,
        Left,
        Right
    }
}
