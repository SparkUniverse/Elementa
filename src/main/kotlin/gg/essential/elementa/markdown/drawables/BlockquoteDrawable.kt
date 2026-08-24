package gg.essential.elementa.markdown.drawables

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.markdown.DrawState
import gg.essential.elementa.markdown.MarkdownComponent
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.fillMcScaleXYWH
import gg.essential.universal.UMatrixStack

class BlockquoteDrawable(md: MarkdownComponent, val drawables: DrawableList) : Drawable(md) {
    private var dividerHeight: Float = -1f
    override val children: List<Drawable> get() = drawables

    var maxTextLineWidth = 0f
        private set

    init {
        drawables.parent = this
    }

    override fun layoutImpl(x: Float, y: Float, width: Float): Layout {
        val config = config.blockquoteConfig

        // Horizontal padding due to the quote bar, which will shift the drawables to the right
        val padding = config.spaceBeforeDivider + config.dividerWidth + config.spaceAfterDivider

        var currY = y
        currY += if (insertSpaceBefore) config.spaceBeforeBlockquote else 0f
        val dividerStart = currY
        currY += config.dividerPaddingTop

        trim(drawables)

        drawables.forEach {
            // Layout our children taking into account the quote bar padding
            currY += it.layout(x + padding, currY, width - padding).height
        }

        currY += config.dividerPaddingBottom
        dividerHeight = currY - dividerStart
        if (insertSpaceAfter)
            currY += config.spaceAfterBlockquote

        val height = currY - y

        maxTextLineWidth = drawables.maxOfOrNull { drawable ->
            (drawable as? ParagraphDrawable)?.maxTextLineWidth?.plus(padding) ?: 0f
        } ?: 0f

        return Layout(
            x,
            y,
            width,
            height,
            Margin(0f, config.spaceBeforeBlockquote, 0f, config.spaceAfterBlockquote)
        )
    }

    override fun draw(matrixStack: UMatrixStack, state: DrawState) {
        UIBlock.drawBlockSized(
            matrixStack,
            config.blockquoteConfig.dividerColor,
            x + state.xShift + config.blockquoteConfig.spaceBeforeDivider.toDouble(),
            y + state.yShift + config.blockquoteConfig.spaceBeforeBlockquote.toDouble(),
            config.blockquoteConfig.dividerWidth.toDouble(),
            dividerHeight.toDouble()
        )

        drawables.forEach { it.drawCompat(matrixStack, state) }
    }

    override fun extract(extractor: ElementaExtractor, state: DrawState) {
        extractor.fillMcScaleXYWH(
            x + state.xShift + config.blockquoteConfig.spaceBeforeDivider,
            y + state.yShift + config.blockquoteConfig.spaceBeforeBlockquote,
            config.blockquoteConfig.dividerWidth,
            dividerHeight,
            config.blockquoteConfig.dividerColor,
        )

        drawables.forEach { it.extract(extractor, state) }
    }

    override fun cursorAt(mouseX: Float, mouseY: Float, dragged: Boolean, mouseButton: Int) = drawables.cursorAt(mouseX, mouseY, dragged, mouseButton)
    override fun cursorAtStart() = drawables.cursorAtStart()
    override fun cursorAtEnd() = drawables.cursorAtEnd()

    override fun selectedText(asMarkdown: Boolean): String {
        if (!hasSelectedText())
            return ""

        val text = drawables.selectedText(asMarkdown)
        return if (asMarkdown) {
            text.lines().joinToString(separator = "\n") { "> $it" }
        } else text
    }
}
