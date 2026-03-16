package gg.essential.elementa.markdown.drawables

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.dsl.width
import gg.essential.elementa.markdown.DrawState
import gg.essential.elementa.markdown.HeaderLevelConfig
import gg.essential.elementa.markdown.MarkdownComponent
import gg.essential.elementa.markdown.selection.Cursor
import gg.essential.elementa.markdown.selection.ImageCursor
import gg.essential.elementa.markdown.selection.TextCursor
import gg.essential.elementa.utils.withAlpha
import gg.essential.universal.UDesktop
import gg.essential.universal.UMatrixStack
import java.awt.Color
import java.net.URI
import java.net.URISyntaxException
import kotlin.math.abs
import kotlin.math.floor

class ParagraphDrawable(
    md: MarkdownComponent,
    private val originalDrawables: DrawableList,
) : Drawable(md) {
    @Deprecated("Use children instead", ReplaceWith("children"))
    val drawables = DrawableList(md, originalDrawables)

    override val children: List<Drawable>
        get() = drawables
    val textDrawables: List<TextDrawable>
        get() = children.filterIsInstance<TextDrawable>()

    // The width of the longest TextDrawable line after lines are split
    var maxTextLineWidth = 0f
        private set

    // Used by HeaderDrawable
    internal var headerConfig: HeaderLevelConfig? = null
        set(value) {
            field = value
            scaleModifier = value?.textScale ?: scaleModifier
            textDrawables.forEach {
                it.headerConfig = value
            }
        }

    var scaleModifier = 1f
        set(value) {
            field = value
            drawables.filterIsInstance<TextDrawable>().forEach {
                it.scaleModifier = value
            }
        }

    init {
        originalDrawables.parent = this
        drawables.parent = this
    }

    override fun layoutImpl(x: Float, y: Float, width: Float): Layout {
        val marginTop = if (insertSpaceBefore) config.paragraphConfig.spaceBefore else 0f
        val marginBottom = if (insertSpaceAfter) config.paragraphConfig.spaceAfter else 0f

        // We need to build a new drawable list, as text drawables may be split
        // into two or more during layout.
        val newDrawables = mutableListOf<Drawable>()

        var currX = x
        var currY = y + marginTop
        var widthRemaining = width
        val centered = config.paragraphConfig.centered

        // Used to trim text components which are at the start of the line
        // or after a soft break so we don't render extra spaces
        var trimNextText = true

        // These are used for centered text. When we render centered markdown,
        // we layout all of our text drawables as normal, and center them after.
        // These lists help keep track of which drawables are on their own lines.
        val lines = mutableListOf<List<Drawable>>()
        val currentLine = mutableListOf<Drawable>()
        var maxLineHeight = Float.MIN_VALUE

        fun gotoNextLine() {
            currX = x
            currY += maxLineHeight + config.paragraphConfig.spaceBetweenLines

            if (maxLineHeight > 9f) {
                for (drawable in currentLine)
                    drawable.y += (maxLineHeight - drawable.height) / 2f
            }

            maxLineHeight = Float.MIN_VALUE

            widthRemaining = width
            lines.add(currentLine.toList())
            currentLine.clear()
            trimNextText = true
        }

        fun layout(drawable: Drawable, width: Float) {
            val newWidth = if (trimNextText && drawable is TextDrawable) {
                // We don't want spaces at the start of a drawable if it is the
                // first drawable in the line.
                drawable.ensureTrimmed()
                drawable.width()
            } else width

            drawable.layout(currX, currY, newWidth).also {
                if (it.height > maxLineHeight)
                    maxLineHeight = it.height
                widthRemaining -= it.width
                currX += it.width
            }
            trimNextText = false
            currentLine.add(drawable)
            newDrawables.add(drawable)
        }

        for ((index, text) in originalDrawables.withIndex()) {
            if (text is SoftBreakDrawable || text is HardBreakDrawable) {
                if (config.paragraphConfig.softBreakIsNewline || text is HardBreakDrawable) {
                    gotoNextLine()
                } else {
                    val previousStyle = (newDrawables.lastOrNull { it is TextDrawable } as? TextDrawable)?.let {
                        it.style.copy(isCode = false)
                    } ?: TextDrawable.Style.EMPTY
                    val newText = TextDrawable(md, " ", previousStyle)

                    // Do this before laying out newText, so that newText isn't in the
                    // newDrawables list yet
                    if (newDrawables.isNotEmpty() && index != originalDrawables.lastIndex) {
                        val previous = newDrawables.last()
                        val next = originalDrawables[index + 1]
                        if (previous is TextDrawable && next is TextDrawable && previous.style == next.style) {
                            // Link the two texts together, as a soft break (when not
                            // treated as a new line) should not interrupt a link
                            val linkedTexts = TextDrawable.LinkedTexts.merge(previous.linkedTexts, next.linkedTexts)
                            linkedTexts.linkText(previous)
                            linkedTexts.linkText(newText)
                            linkedTexts.linkText(next)
                            previous.linkedTexts = linkedTexts
                            newText.linkedTexts = linkedTexts
                            next.linkedTexts = linkedTexts
                        }
                    }
                    layout(newText, newText.width())
                    if (widthRemaining <= 0)
                        gotoNextLine()
                    trimNextText = true
                }
                continue
            }

            if (text is ImageDrawable) {
                if (currentLine.isNotEmpty()) gotoNextLine()
                layout(text, width)
                gotoNextLine()
                continue
            }

            if (text !is TextDrawable)
                TODO()

            var target: TextDrawable = text

            while (true) {
                val targetWidth = target.width()
                if (targetWidth <= widthRemaining) {
                    // We can just layout this text drawable inline, next to the last one
                    layout(target, targetWidth)
                    if (widthRemaining <= 0)
                        gotoNextLine()
                    break
                }

                val splitResult = target.split(widthRemaining)
                if (splitResult != null) {
                    // We successfully split the text component up. Draw the
                    // first part on this line, and deal with the second part
                    // during the next loop iteration
                    layout(splitResult.first, targetWidth)
                    gotoNextLine()
                    target = splitResult.second
                    continue
                }

                // If we can't split the text in a way that doesn't break
                // a word, we'll just draw the whole thing on the next line.
                // Only need to advance onto the next line, if there is
                // actually something on this one. Otherwise we're already
                // good to take the whole line.
                if (currentLine.isNotEmpty()) {
                    gotoNextLine()
                }

                // Before we do that though, we have to make sure that its
                // width isn't greater than the width of the entire component.
                // If it is, we need to split it on the overall width and
                // continue this splitting loop

                if (targetWidth > width) {
                    val splitResult2 = target.split(width)

                    if (splitResult2 == null) {
                        // Edge case where the width of the MarkdownComponent is
                        // probably very small, and we can't split it on a word
                        // boundary. In this case we opt to split again, breaking
                        // words if we have to. We run split twice here, but as
                        // this is a rare edge case, it's not a problem.
                        val splitResult3 = target.split(width, breakWords = true) ?: throw IllegalStateException(
                            "MarkdownComponent's width (${md.getWidth()}) is too small to render its content"
                        )

                        layout(splitResult3.first, splitResult3.first.width())
                        gotoNextLine()
                        target = splitResult3.second
                        continue
                    }

                    // We've split the component based on the overall width. We'll
                    // draw the first part on this line, and the second part on the
                    // next line during the next loop iteration.
                    layout(splitResult2.first, splitResult2.first.width())
                    gotoNextLine()
                    target = splitResult2.second
                    continue
                }

                // We can draw the target on the next line
                layout(target, targetWidth)
                break
            }
        }

        // We can have extra drawables in the current line that didn't get handled
        // by the last iteration of the loop
        if (currentLine.isNotEmpty()) gotoNextLine()

        // There isn't a next line, so this space shouldn't be there
        if (lines.isNotEmpty()) currY -= config.paragraphConfig.spaceBetweenLines

        if (centered) {
            // Offset each text component by half of the space at the end of each line
            for (line in lines) {
                val totalWidth = line.sumOf {
                    (it as? TextDrawable)?.width()?.toDouble() ?: it.width.toDouble()
                }.toFloat()
                val shift = (width - totalWidth) / 2f
                for (text in line) {
                    text.x += shift
                }
            }
        }

        maxTextLineWidth = lines.maxOfOrNull { line ->
            line.sumOf { (it as? TextDrawable)?.width()?.toDouble() ?: it.width.toDouble() }.toFloat()
        } ?: 0f

        newDrawables.forEach {
            if (it is TextDrawable)
                it.scaleModifier = scaleModifier
        }

        drawables.setDrawables(newDrawables)

        val height = currY - y + if (insertSpaceAfter) config.paragraphConfig.spaceAfter else 0f

        return Layout(
            x,
            y,
            width,
            height,
            Margin(0f, marginTop, 0f, marginBottom)
        )
    }

    override fun draw(matrixStack: UMatrixStack, state: DrawState) {
        drawables.forEach { it.drawCompat(matrixStack, state) }

        // TODO: Remove
        if (MarkdownComponent.DEBUG) {
            UIBlock.drawBlockSized(
                matrixStack,
                rc,
                layout.elementLeft.toDouble() + state.xShift,
                layout.elementTop.toDouble() + state.yShift,
                layout.elementWidth.toDouble(),
                layout.elementHeight.toDouble()
            )
        }
    }

    override fun cursorAt(mouseX: Float, mouseY: Float, dragged: Boolean, mouseButton: Int): Cursor<*> {
        // Account for padding between lines
        // TODO: Don't account for this padding for the first and last lines?
        val linePadding = config.paragraphConfig.spaceBetweenLines / 2f

        fun yRange(d: Drawable) = (d.y - linePadding)..(d.y + d.height + linePadding)

        // Step 1: Get to the correct row

        val firstInRow = drawables.firstOrNull {
            mouseY in yRange(it)
        }

        // Ensure that the mouseY position actually falls within this drawable.
        // If not, we'll just select either the start of end of the component,
        // depending on the mouseY position
        if (firstInRow == null) {
            if (mouseY < drawables.first().y - linePadding) {
                // The position occurs before this paragraph, so we just
                // select the start of this paragraph
                return cursorAtStart()
            }

            // The mouse isn't in this drawable, and it isn't before this
            // drawable, so it must be after this drawable
            return cursorAtEnd()
        }

        // Step 2: Get to the correct text drawable

        if (mouseX < firstInRow.x) {
            // Because we iterate text drawables top to bottom, left to right,
            // if the mouseX is left of the text start, we can just select the
            // start of the current component. We don't have to walk the text
            // siblings (using text.previous) because firstTextInRow is the
            // first text component which has an acceptable y-range.
            return firstInRow.cursorAtStart()
        }

        // We've selected a drawable based on the y position, now we must do
        // the same thing in the x direction. This time, though, we need to
        // be careful to always check the y range of the next drawable. If
        // mouseY ever falls outside of the drawable y range, then the mouse
        // is to the right of this paragraph drawable, and we'll select the
        // drawable which we are currently on

        var currentDrawable: Drawable = firstInRow

        while (mouseX > currentDrawable.x + currentDrawable.width && currentDrawable.next != null) {
            var nextDrawable = currentDrawable.next!!

            while (nextDrawable !is TextDrawable && nextDrawable !is ImageDrawable && nextDrawable.next != null) {
                nextDrawable = nextDrawable.next!!
            }

            if (nextDrawable !is TextDrawable && nextDrawable !is ImageDrawable) {
                // currentText is the last text in this paragraph, so we'll just
                // select its end
                return currentDrawable.cursorAtEnd()
            }

            if (mouseY !in yRange(nextDrawable)) {
                // As mentioned above, the mouse is to the right of this paragraph
                // component
                return currentDrawable.cursorAtEnd()
            }

            currentDrawable = nextDrawable
        }

        // Step 3: If the hovered drawable is an image, we return early

        if (currentDrawable is ImageDrawable)
            return ImageCursor(currentDrawable)

        if (currentDrawable !is TextDrawable)
            TODO()

        // Step 4: If the current text is linked, open it (only if we're not dragging though)
        // TODO: Confirmation modal somehow?

        currentDrawable.style.linkLocation?.takeIf { !dragged && mouseButton == 0 }?.let { linkLocation ->
            if (md.fireLinkClickEvent(MarkdownComponent.LinkClickEvent(linkLocation))) {
                try {
                    UDesktop.browse(URI(linkLocation))
                } catch (e: URISyntaxException) {
                    // Ignored, if the link is invalid we just do nothing
                }
            }
        }

        // Step 5: Get the string offset position in the current text

        fun textWidth(offset: Int) =
            currentDrawable.formattedText.substring(0, offset).width(currentDrawable.scaleModifier)

        var offset = currentDrawable.style.numFormattingChars
        var cachedWidth = 0f

        // Iterate from left to right in the text component until we find a good
        // offset based on the text width
        while (offset < currentDrawable.formattedText.length) {
            offset++
            val newWidth = textWidth(offset)

            if (currentDrawable.x + newWidth > mouseX) {
                // We've passed mouseX, but now we have to consider which offset
                // is closer to mouseX: `offset` or `offset - 1`. We check that
                // here and use the closest offset

                val oldDist = abs(mouseX - currentDrawable.x - cachedWidth)
                val newDist = abs(newWidth - (mouseX - currentDrawable.x))

                if (oldDist < newDist) {
                    // The old offset was better
                    offset--
                }

                return TextCursor(currentDrawable, offset - currentDrawable.style.numFormattingChars)
            }

            cachedWidth = newWidth
        }

        return currentDrawable.cursorAtEnd()
    }

    override fun cursorAtStart() = drawables.first { it is TextDrawable || it is ImageDrawable }.cursorAtStart()
    override fun cursorAtEnd() = drawables.last { it is TextDrawable || it is ImageDrawable }.cursorAtEnd()

    override fun selectedText(asMarkdown: Boolean): String {
        return drawables.filter {
            it is TextDrawable || it is ImageDrawable
        }.joinToString(separator = "") { it.selectedText(asMarkdown) }
    }

    private val rc = randomColor().withAlpha(100)

    private fun randomColor(): Color {
        return Color(randomComponent(), randomComponent(), randomComponent())
    }

    private fun randomComponent(): Int = floor(Math.random() * 256f).toInt()
}
