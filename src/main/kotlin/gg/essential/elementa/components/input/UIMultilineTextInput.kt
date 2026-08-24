package gg.essential.elementa.components.input

import gg.essential.elementa.constraints.HeightConstraint
import gg.essential.elementa.dsl.coerceAtMost
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.dsl.width
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.utils.splitStringToWidthTruncated
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import java.awt.Color

class UIMultilineTextInput @JvmOverloads constructor(
    placeholder: String = "",
    shadow: Boolean = true,
    selectionBackgroundColor: Color = Color.WHITE,
    selectionForegroundColor: Color = Color(64, 139, 229),
    allowInactiveSelection: Boolean = false,
    inactiveSelectionBackgroundColor: Color = Color(176, 176, 176),
    inactiveSelectionForegroundColor: Color = Color.WHITE,
    cursorColor: Color = Color.WHITE
) : AbstractTextInput(
    placeholder,
    shadow,
    selectionBackgroundColor,
    selectionForegroundColor,
    allowInactiveSelection,
    inactiveSelectionBackgroundColor,
    inactiveSelectionForegroundColor,
    cursorColor
) {
    private var maxHeight: HeightConstraint? = null

    fun setMaxHeight(maxHeight: HeightConstraint) = apply {
        this.maxHeight = maxHeight
    }

    fun setMaxLines(maxLines: Int) = apply {
        this.maxHeight = (lineHeight * maxLines).pixels()
    }

    override fun getText() = textualLines.joinToString("\n") { it.text }

    override fun textToLines(text: String): List<String> {
        return text.split('\n')
    }

    override fun scrollIntoView(pos: LinePosition) {
        val visualPos = pos.toVisualPos()

        val visualLineOffset = visualPos.line * -lineHeight

        if (targetVerticalScrollingOffset < visualLineOffset) {
            targetVerticalScrollingOffset = visualLineOffset
        } else if (visualLineOffset - lineHeight < targetVerticalScrollingOffset - getHeight()) {
            targetVerticalScrollingOffset += visualLineOffset - lineHeight - (targetVerticalScrollingOffset - getHeight())
        }
    }

    override fun recalculateDimensions() {
        if (maxHeight == null)
            return

        setHeight((lineHeight * visualLines.size).pixels().coerceAtMost(maxHeight!!))
    }

    override fun onEnterPressed() {
        if (UKeyboard.isShiftKeyDown()) {
            commitTextAddition("\n")
            updateAction(getText())
        } else {
            activateAction(getText())
        }
    }

    override fun extractComponent(extractor: ElementaExtractor) {
        val textScale = getTextScale()
        if (!active && !hasText()) {
            val textToDraw = splitStringToWidthTruncated(placeholder, getWidth(), textScale, 1)[0]
            extractUnselectedText(extractor, textToDraw, getLeft(), 0)
            super.extractComponent(extractor)
            return
        }

        if (hasSelection()) {
            cursorComponent.hide(instantly = true)
        } else if (active) {
            cursorComponent.unhide()
            val (cursorPosX, cursorPosY) = cursor.toScreenPos()
            cursorComponent.setX((cursorPosX ).pixels())
            cursorComponent.setY((cursorPosY ).pixels())
        }

        val (selectionStart, selectionEnd) = getSelection()

        for ((i, visualLine) in visualLines.withIndex()) {
            val topOffset = (lineHeight * i * getTextScale()) + verticalScrollingOffset
            if (topOffset < -lineHeight * getTextScale() || topOffset > getHeight() + lineHeight * getTextScale())
                continue

            if (!hasSelection() || i < selectionStart.line || i > selectionEnd.line) {
                extractUnselectedText(extractor, visualLine.text, getLeft(), i)
            } else {
                val startText = when {
                    i == selectionStart.line && selectionStart.column > 0 -> {
                        visualLine.text.substring(0, selectionStart.column)
                    }
                    else -> ""
                }

                val selectedText = when {
                    selectionStart.line == selectionEnd.line -> visualLine.text.substring(
                        selectionStart.column,
                        selectionEnd.column
                    )
                    i > selectionStart.line && i < selectionEnd.line -> visualLine.text
                    i == selectionStart.line -> visualLine.text.substring(selectionStart.column)
                    i == selectionEnd.line -> visualLine.text.substring(0, selectionEnd.column)
                    else -> ""
                }

                val endText = when {
                    i == selectionEnd.line && selectionEnd.column < visualLines[i].length -> {
                        visualLine.text.substring(selectionEnd.column)
                    }
                    else -> ""
                }

                val startTextWidth = startText.width(textScale)
                val selectedTextWidth = selectedText.width(textScale)

                val newlinePadding = if (i < selectionEnd.line) ' '.width(textScale) else 0f

                if (startText.isNotEmpty())
                    extractUnselectedText(extractor, startText, getLeft(), i)

                if (selectedText.isNotEmpty() || newlinePadding != 0f) {
                    extractSelectedText(
                        extractor,
                        selectedText,
                        getLeft() + startTextWidth,
                        getLeft() + startTextWidth + selectedTextWidth + newlinePadding,
                        i
                    )
                }

                if (endText.isNotEmpty())
                    extractUnselectedText(extractor, endText, getLeft() + startTextWidth + selectedTextWidth, i)
            }
        }

        super.extractComponent(extractor)
    }

    @Deprecated(
        "`draw`-style rendering is deprecated. Override `extractComponent` instead. Call `extract` to extract this component, its effects, and its children.",
        replaceWith = ReplaceWith("extract(extractor)")
    )
    @Suppress("DEPRECATION")
    override fun draw(matrixStack: UMatrixStack) {
        beforeDraw(matrixStack)

        val textScale = getTextScale()
        if (!active && !hasText()) {
            val textToDraw = splitStringToWidthTruncated(placeholder, getWidth(), textScale, 1)[0]
            drawUnselectedText(matrixStack, textToDraw, getLeft(), 0)
            return super.draw(matrixStack)
        }

        if (hasSelection()) {
            cursorComponent.hide(instantly = true)
        } else if (active) {
            cursorComponent.unhide()
            val (cursorPosX, cursorPosY) = cursor.toScreenPos()
            cursorComponent.setX((cursorPosX ).pixels())
            cursorComponent.setY((cursorPosY ).pixels())
        }

        val (selectionStart, selectionEnd) = getSelection()

        for ((i, visualLine) in visualLines.withIndex()) {
            val topOffset = (lineHeight * i * getTextScale()) + verticalScrollingOffset
            if (topOffset < -lineHeight * getTextScale() || topOffset > getHeight() + lineHeight * getTextScale())
                continue

            if (!hasSelection() || i < selectionStart.line || i > selectionEnd.line) {
                drawUnselectedText(matrixStack, visualLine.text, getLeft(), i)
            } else {
                val startText = when {
                    i == selectionStart.line && selectionStart.column > 0 -> {
                        visualLine.text.substring(0, selectionStart.column)
                    }
                    else -> ""
                }

                val selectedText = when {
                    selectionStart.line == selectionEnd.line -> visualLine.text.substring(
                        selectionStart.column,
                        selectionEnd.column
                    )
                    i > selectionStart.line && i < selectionEnd.line -> visualLine.text
                    i == selectionStart.line -> visualLine.text.substring(selectionStart.column)
                    i == selectionEnd.line -> visualLine.text.substring(0, selectionEnd.column)
                    else -> ""
                }

                val endText = when {
                    i == selectionEnd.line && selectionEnd.column < visualLines[i].length -> {
                        visualLine.text.substring(selectionEnd.column)
                    }
                    else -> ""
                }

                val startTextWidth = startText.width(textScale)
                val selectedTextWidth = selectedText.width(textScale)

                val newlinePadding = if (i < selectionEnd.line) ' '.width(textScale) else 0f

                if (startText.isNotEmpty())
                    drawUnselectedText(matrixStack, startText, getLeft(), i)

                if (selectedText.isNotEmpty() || newlinePadding != 0f) {
                    drawSelectedText(
                        matrixStack,
                        selectedText,
                        getLeft() + startTextWidth,
                        getLeft() + startTextWidth + selectedTextWidth + newlinePadding,
                        i
                    )
                }

                if (endText.isNotEmpty())
                    drawUnselectedText(matrixStack, endText, getLeft() + startTextWidth + selectedTextWidth, i)
            }
        }

        super.draw(matrixStack, )
    }

    override fun screenPosToVisualPos(x: Float, y: Float): LinePosition {
        val realY = y - verticalScrollingOffset

        if (realY <= 0)
            return LinePosition(0, 0, isVisual = true)

        val line = (realY / (lineHeight * getTextScale())).toInt()
        if (line > visualLines.lastIndex)
            return LinePosition(visualLines.lastIndex, visualLines.last().text.length, isVisual = true)

        val text = visualLines[line].text
        var column = 0
        var currWidth = 0f

        if (x <= 0)
            return LinePosition(line, 0, isVisual = true)
        if (x >= getWidth())
            return LinePosition(line, visualLines[line].text.length, isVisual = true)

        for (char in text.toCharArray()) {
            val charWidth = char.width(getTextScale())
            if (currWidth + (charWidth / 2) >= x)
                return LinePosition(line, column, isVisual = true)

            currWidth += charWidth
            column++
        }

        return LinePosition(line, column, isVisual = true)
    }
}
