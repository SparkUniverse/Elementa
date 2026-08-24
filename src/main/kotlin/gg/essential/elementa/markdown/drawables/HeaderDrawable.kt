package gg.essential.elementa.markdown.drawables

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.markdown.DrawState
import gg.essential.elementa.markdown.MarkdownComponent
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.fillMcScaleXYWH
import gg.essential.universal.UMatrixStack

class HeaderDrawable(
    md: MarkdownComponent,
    private val level: Int,
    private val paragraph: ParagraphDrawable
) : Drawable(md) {
    override val children: List<Drawable> get() = listOf(paragraph)
    internal val id = paragraph.textDrawables.joinToString(separator = " ") { it.plainText() }

    var dividerWidth: Double? = null

    init {
        paragraph.parent = this
    }

    private val headerConfig = when (level) {
        1 -> config.headerConfig.level1
        2 -> config.headerConfig.level2
        3 -> config.headerConfig.level3
        4 -> config.headerConfig.level4
        5 -> config.headerConfig.level5
        6 -> config.headerConfig.level6
        else -> throw IllegalStateException()
    }

    init {
        paragraph.headerConfig = headerConfig
        trim(paragraph)
    }

    override fun layoutImpl(x: Float, y: Float, width: Float): Layout {
        val spaceBefore = if (insertSpaceBefore) headerConfig.verticalSpaceBefore else 0f
        val spaceAfter = if (insertSpaceAfter) headerConfig.verticalSpaceAfter else 0f
        paragraph.layout(x, y + spaceBefore, width)

        val height = spaceBefore + paragraph.height + spaceAfter + if (headerConfig.hasDivider) {
            headerConfig.spaceBeforeDivider + headerConfig.dividerWidth
        } else 0f

        return Layout(
            x,
            y,
            width,
            height,
            Margin(0f, spaceBefore, 0f, spaceAfter)
        )
    }

    override fun draw(matrixStack: UMatrixStack, state: DrawState) {
        paragraph.drawCompat(matrixStack, state)

        if (headerConfig.hasDivider) {
            val y = layout.bottom - layout.margin.bottom - headerConfig.dividerWidth
            UIBlock.drawBlockSized(
                matrixStack,
                headerConfig.dividerColor, 
                (x + state.xShift).toDouble(),
                (y + state.yShift).toDouble(),
                dividerWidth ?: width.toDouble(),
                headerConfig.dividerWidth.toDouble()
            )
        }
    }

    override fun extract(extractor: ElementaExtractor, state: DrawState) {
        paragraph.extract(extractor, state)

        if (headerConfig.hasDivider) {
            val y = layout.bottom - layout.margin.bottom - headerConfig.dividerWidth
            extractor.fillMcScaleXYWH(
                x + state.xShift,
                y + state.yShift,
                dividerWidth?.toFloat() ?: width,
                headerConfig.dividerWidth,
                headerConfig.dividerColor,
            )
        }
    }

    override fun cursorAt(mouseX: Float, mouseY: Float, dragged: Boolean, mouseButton: Int) = paragraph.cursorAt(mouseX, mouseY, dragged, mouseButton)
    override fun cursorAtStart() = paragraph.cursorAtStart()
    override fun cursorAtEnd() = paragraph.cursorAtEnd()

    override fun selectedText(asMarkdown: Boolean): String {
        if (!hasSelectedText())
            return ""

        val text = paragraph.selectedText(asMarkdown)
        return if (asMarkdown) {
            "#".repeat(level) + " $text"
        } else text
    }
}
