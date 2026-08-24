package gg.essential.elementa.markdown.selection

import gg.essential.elementa.markdown.DrawState
import gg.essential.elementa.markdown.drawables.Drawable
import gg.essential.elementa.markdown.drawables.ImageDrawable
import gg.essential.elementa.markdown.drawables.TextDrawable
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.universal.UMatrixStack

class Selection private constructor(val start: Cursor<*>, val end: Cursor<*>) {
    val drawables = mutableListOf<Drawable>()

    init {
        if (start.target == end.target) {
            drawables.add(start.target)
            val (cursorStart, cursorEnd) = if (start is TextCursor) {
                start.offset to (end as TextCursor).offset
            } else -1 to -1

            setSelected(start.target, cursorStart, cursorEnd, selected = true)
        } else {
            // Configure selection area for the starting target
            val (cursorStart, cursorEnd) = if (start is TextCursor) {
                start.offset to start.target.plainText().length
            } else -1 to -1
            setSelected(start.target, cursorStart, cursorEnd, selected = true)

            // We now have to iterate the entire markdown tree structure.
            var currentTarget: Drawable? = start.target

            loop@ while (currentTarget != null) {
                drawables.add(currentTarget)
                currentTarget = nextTarget(currentTarget)

                when (currentTarget) {
                    null -> throw IllegalStateException()
                    end.target -> {
                        drawables.add(currentTarget)
                        val (cursorStart, cursorEnd) = if (end is TextCursor) {
                            0 to end.offset
                        } else -1 to -1
                        setSelected(currentTarget, cursorStart, cursorEnd, selected = true)
                        break@loop
                    }
                    else -> {
                        val (cursorStart, cursorEnd) = if (currentTarget is TextDrawable) {
                            0 to currentTarget.plainText().length
                        } else -1 to -1
                        setSelected(currentTarget, cursorStart, cursorEnd, selected = true)
                    }
                }
            }
        }
    }

    @Deprecated(UMatrixStack.Compat.DEPRECATED, ReplaceWith("draw(matrixStack, state)"))
    fun draw(state: DrawState) = draw(UMatrixStack(), state)

    fun draw(matrixStack: UMatrixStack, state: DrawState) {
        start.draw(matrixStack, state)
        end.draw(matrixStack, state)
    }

    fun extract(extractor: ElementaExtractor, state: DrawState) {
        start.extract(extractor, state)
        end.extract(extractor, state)
    }

    fun remove() {
        drawables.forEach {
            setSelected(it, -1, -1, selected = false)
        }
    }

    private fun nextTarget(drawable: Drawable): Drawable? {
        var nextTarget: Drawable? = drawable.next
        while (nextTarget != null && nextTarget !is TextDrawable && nextTarget !is ImageDrawable)
            nextTarget = nextTarget.next

        if (nextTarget is TextDrawable || nextTarget is ImageDrawable)
            return nextTarget

        var nextContainer: Drawable = drawable.parent ?: return null
        while (nextContainer.next == null) {
            if (nextContainer.parent == null)
                return null
            nextContainer = nextContainer.parent!!
        }

        return firstTargetChild(nextContainer.next!!)
    }

    private fun firstTargetChild(drawable: Drawable): Drawable? {
        if (drawable is TextDrawable || drawable is ImageDrawable)
            return drawable

        if (drawable.children.isEmpty())
            return null

        return firstTargetChild(drawable.children.first())
    }

    private fun setSelected(
        drawable: Drawable,
        start: Int,
        end: Int,
        selected: Boolean
    ) {
        when (drawable) {
            is TextDrawable -> {
                drawable.selectionStart = start
                drawable.selectionEnd = end
            }
            is ImageDrawable -> drawable.selected = selected
            else -> throw IllegalArgumentException()
        }
    }

    companion object {
        fun fromCursors(first: Cursor<*>, second: Cursor<*>): Selection {
            return if (first <= second) Selection(first, second) else Selection(second, first)
        }
    }
}
