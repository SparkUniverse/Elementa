package gg.essential.elementa.markdown.drawables

import gg.essential.elementa.dsl.width
import gg.essential.elementa.markdown.DrawState
import gg.essential.elementa.markdown.MarkdownComponent
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.universal.UMatrixStack

class ListDrawable(
    md: MarkdownComponent,
    private val drawables: DrawableList,
    private val isOrdered: Boolean,
    /**
     * A "loose" list is a list in which any of its list items are
     * separated by blank lines, or if any item contains two block
     * elements with a blank line between them. A loose list has more
     * separation between list elements than a tight list does.
     *
     * Reference: https://spec.commonmark.org/0.28/#tight
     */
    private var isLoose: Boolean
) : Drawable(md) {
    private val listItems = mutableListOf<ListEntry>()
    override val children: List<Drawable> get() = listItems

    private val elementSpacing: Float
        get() = if (isLoose) {
            config.listConfig.elementSpacingLoose
        } else config.listConfig.elementSpacingTight

    /**
     * The indentation of this list in any parent lists. This is set
     * below in the layoutImpl method
     */
    private var indentLevel = 0

    // The width of the longest list entry, including the indentation, symbol width, and space after symbol
    var maxTextLineWidth = 0f
        private set

    init {
        trim(drawables)
        drawables.parent = this
    }

    override fun layoutImpl(x: Float, y: Float, width: Float): Layout {
        listItems.clear()

        val marginTop = if (insertSpaceBefore) config.listConfig.spaceBeforeList else 0f
        val marginBottom = if (insertSpaceAfter) config.listConfig.spaceAfterList else 0f
        var currY = y + marginTop
        val spaceAfterSymbol = config.listConfig.spaceBeforeText
        val indentation = config.listConfig.indentation

        var orderedListShift = 0

        fun symbol(index: Int): String {
            return if (isOrdered) {
                "${index + 1 - orderedListShift}."
            } else {
                val symbols = config.listConfig.unorderedSymbols
                symbols[indentLevel % symbols.length].toString()
            }
        }

        // Get the maximum width of all list item symbols to align
        // them with each other. Unordered lists have the same symbol
        // at each level, however ordered lists are variable width.
        val symbolWidth = if (isOrdered) {
            val dotWidth = '.'.width()
            drawables.indices.filter {
                drawables[it] !is ListDrawable
            }.maxOf {
                it.toString().width() + dotWidth
            }
        } else {
            val symbols = config.listConfig.unorderedSymbols
            symbols[indentLevel % symbols.length].width()
        }

        var index = 0

        fun addItem(drawable: Drawable) {
            val item = ListEntry(
                md,
                symbol(index),
                symbolWidth,
                spaceAfterSymbol,
                drawable
            )
            listItems.add(item)
            currY += item.layout(x + indentation, currY, width - indentation).height
            currY += elementSpacing
        }

        for (drawable in drawables) {
            if (drawable is ListDrawable)
                drawable.indentLevel = indentLevel + 1
            addItem(drawable)
            index++
        }

        maxTextLineWidth = drawables.maxOfOrNull { drawable ->
            drawable.children.filterIsInstance<ParagraphDrawable>().maxOfOrNull {
                indentation + symbolWidth + spaceAfterSymbol + it.maxTextLineWidth
            } ?: 0f
        } ?: 0f

        currY -= elementSpacing
        currY += marginBottom

        val height = currY - y

        return Layout(
            x,
            y,
            width,
            height,
            Margin(0f, marginTop, 0f, marginBottom)
        )
    }

    override fun draw(matrixStack: UMatrixStack, state: DrawState) {
        listItems.forEach { it.drawCompat(matrixStack, state) }
    }

    override fun extract(extractor: ElementaExtractor, state: DrawState) {
        listItems.forEach { it.extract(extractor, state) }
    }

    override fun cursorAt(mouseX: Float, mouseY: Float, dragged: Boolean, mouseButton: Int) = drawables.cursorAt(mouseX, mouseY, dragged, mouseButton)
    override fun cursorAtStart() = drawables.cursorAtStart()
    override fun cursorAtEnd() = drawables.cursorAtEnd()

    override fun selectedText(asMarkdown: Boolean) = listItems.joinToString(separator = "\n") {
        it.selectedText(asMarkdown)
    }

    // A mostly organized and ready-to-render list item
    inner class ListEntry(
        md: MarkdownComponent,
        private val symbol: String,
        private val symbolWidth: Float,
        private val symbolPaddingRight: Float,
        val drawable: Drawable
    ) : Drawable(md) {
        private val actualSymbolWidth = symbol.width()
        override val children: List<Drawable> get() = listOf(drawable)

        init {
            trim(drawable)

            // trim any space around list elements
            if (drawable is DrawableList) {
                for ((index, item) in drawable.withIndex()) {
                    if (item is ListDrawable) {
                        if (index != 0)
                            trim(drawable[index - 1])
                        if (index != drawable.lastIndex)
                            trim(drawable[index + 1])
                    }
                }
            }
        }

        override fun layoutImpl(x: Float, y: Float, width: Float): Layout {
            val nonDrawableSpace = symbolWidth + symbolPaddingRight
            drawable.layout(x + nonDrawableSpace, y, width - nonDrawableSpace)
            return Layout(x, y, width, drawable.height)
        }

        override fun draw(matrixStack: UMatrixStack, state: DrawState) {
            val newX = x + symbolWidth - actualSymbolWidth
            if (drawable !is ListDrawable)
                TextDrawable.drawString(matrixStack, config, md.getFontProvider(), symbol, newX + state.xShift, y + state.yShift)
            drawable.drawCompat(matrixStack, state)
        }

        override fun extract(extractor: ElementaExtractor, state: DrawState) {
            val newX = x + symbolWidth - actualSymbolWidth
            if (drawable !is ListDrawable)
                TextDrawable.extractString(extractor, config, md.getFontProvider(), symbol, newX + state.xShift, y + state.yShift, 1f)
            drawable.extract(extractor, state)
        }

        override fun cursorAt(mouseX: Float, mouseY: Float, dragged: Boolean, mouseButton: Int) =
            drawable.cursorAt(mouseX, mouseY, dragged, mouseButton)

        override fun cursorAtStart() = drawable.cursorAtStart()
        override fun cursorAtEnd() = drawable.cursorAtEnd()

        override fun selectedText(asMarkdown: Boolean): String {
            if (!hasSelectedText())
                return ""

            val text = drawable.selectedText(asMarkdown)

            return buildString {
                repeat(indentLevel) {
                    append("  ")
                }
                if (asMarkdown) {
                    append(symbol)
                    append(' ')
                }
                append(text)
            }
        }
    }
}
