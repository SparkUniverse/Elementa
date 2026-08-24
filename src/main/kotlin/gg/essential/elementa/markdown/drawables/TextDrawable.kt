package gg.essential.elementa.markdown.drawables

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.dsl.width
import gg.essential.elementa.font.FontProvider
import gg.essential.elementa.font.extractMcScale
import gg.essential.elementa.markdown.DrawState
import gg.essential.elementa.markdown.HeaderLevelConfig
import gg.essential.elementa.markdown.MarkdownComponent
import gg.essential.elementa.markdown.MarkdownConfig
import gg.essential.elementa.markdown.selection.TextCursor
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.ImmediateElementaExtractor
import gg.essential.elementa.renderer.fillMcScaleXYWH
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UMouse
import java.awt.Color

class TextDrawable(
    md: MarkdownComponent,
    text: String,
    val style: Style
) : Drawable(md) {
    // Used by HeaderDrawable
    var scaleModifier = 1f
    // FIXME: Should probably be handled by [Style] at some point.
    internal var headerConfig: HeaderLevelConfig? = null
        set(value) {
            field = value
            scaleModifier = value?.textScale ?: scaleModifier
        }

    var formattedText: String = style.formattingSymbols + text
        private set

    // Used by the selection API to tell this Drawable how it is
    // selected. These do not consider style characters
    var selectionStart = -1
    var selectionEnd = -1

    // Used to store the Text classes to render between beforeDraw and draw
    private var texts = mutableListOf<Text>()

    // Stores whether or not this component is hovered
    private var isHovered = false

    // Populated with any text drawables which used to be part of this
    // drawable, but were split with a call to split(). Used to show
    // hovered links across newline boundaries
    var linkedTexts: LinkedTexts? = null

    fun plainText() = formattedText.drop(style.numFormattingChars)

    fun ensureTrimmed() {
        // TODO: We shouldn't mutate formattedText here, because this is used
        // conditionally based on the position this text drawable _happens_ to
        // be rendered in its parent ParagraphDrawable. This may change if the
        // MarkdownComponent re-layouts, which can happen at any time.

        val styleChars = style.numFormattingChars
        formattedText = formattedText.substring(0, styleChars) +
                formattedText.substring(styleChars, formattedText.length).trimStart()
    }

    fun width() = formattedText.width(scaleModifier) + if (style.isCode) {
        config.inlineCodeConfig.let {
            (it.outlineWidth + it.horizontalPadding) * 2f
        }
    } else 0f

    // Returns null if this drawable cannot be split in a way that doesn't
    // break a word. This means that the drawable should just be drawn on
    // the next line
    fun split(maxWidth: Float, breakWords: Boolean = false): Pair<TextDrawable, TextDrawable>? {
        val styleChars = style.numFormattingChars
        val plainText = plainText()

        if (plainText.length <= 1) {
            return null
        }

        var splitPoint = formattedText.indices.drop(styleChars).firstOrNull {
            formattedText.substring(0, it + 1).width(scaleModifier) > maxWidth
        } ?: throw IllegalStateException("TextDrawable#split called when it should not have been called")

        splitPoint -= styleChars

        if (!breakWords) {
            while (splitPoint > 0 && plainText[splitPoint] != ' ') {
                splitPoint--
            }

            if (splitPoint == 0) {
                return null
            }
        }

        if (splitPoint <= 0) {
            splitPoint = 1
        }

        val first = TextDrawable(md, plainText.substring(0, splitPoint).trimEnd(), style)
        val second = TextDrawable(md, plainText.substring(splitPoint, plainText.length), style)

        val linkedTexts = this.linkedTexts?.also {
            // We are splitting this text drawable, so in effect this
            // drawable no longer "exists", because it isn't relevant.
            // Therefore, we remove it from this linked text group
            it.unlinkText(this)
        } ?: LinkedTexts()

        linkedTexts.linkText(first)
        linkedTexts.linkText(second)

        first.linkedTexts = linkedTexts
        second.linkedTexts = linkedTexts

        first.scaleModifier = scaleModifier
        second.scaleModifier = scaleModifier

        return first to second
    }

    override fun layoutImpl(x: Float, y: Float, width: Float): Layout {
        return Layout(
            x,
            y,
            width,
            9f * scaleModifier + if (style.isCode) {
                (config.inlineCodeConfig.let { it.verticalPadding + it.outlineWidth }) * 2f
            } else 0f
        )
    }

    override fun beforeDraw(state: DrawState) {
        texts.clear()

        if (selectionStart == -1 && selectionEnd == -1) {
            texts.add(Text(formattedText, x, y, false))
        } else if (selectionStart == -1 || selectionEnd == -1) {
            throw IllegalStateException()
        } else {
            val start = selectionStart + style.numFormattingChars
            val end = selectionEnd + style.numFormattingChars

            val formatChars = formattedText.substring(0, style.numFormattingChars)

            val nextX = if (selectionStart > 0) {
                texts.add(
                    Text(
                        formattedText.substring(0, start),
                        x,
                        y,
                        false
                    )
                )
                x + formattedText.substring(0, start).width(scaleModifier)
            } else x

            val selectedString = formatChars + formattedText.substring(start, end)
            texts.add(
                Text(
                    selectedString,
                    nextX,
                    y,
                    true
                )
            )

            if (end < formattedText.length) {
                texts.add(
                    Text(
                        formatChars + formattedText.substring(end),
                        nextX + selectedString.width(scaleModifier),
                        y,
                        false
                    )
                )
            }
        }

        val mouseX = UMouse.Scaled.x - state.xShift
        val mouseY = UMouse.Scaled.y - state.yShift
        isHovered = if (style.linkLocation != null) {
            isHovered(mouseX.toFloat(), mouseY.toFloat())
        } else false
    }

    override fun draw(matrixStack: UMatrixStack, state: DrawState) {
        extract(ImmediateElementaExtractor(matrixStack), state)
    }

    override fun extract(extractor: ElementaExtractor, state: DrawState) {
        val hovered = isHovered || (linkedTexts?.isHovered() ?: false)

        if (style.isCode) {
            val x1 = x + state.xShift
            val y1 = y + state.yShift - 1f
            val x2 = x1 + width
            val y2 = y1 + height + config.inlineCodeConfig.verticalPadding * 2 - 1f
            val outlineWidth = config.inlineCodeConfig.outlineWidth

            UIRoundedRectangle.extractRoundedRectangle(
                extractor,
                x1,
                y1,
                x2,
                y2,
                config.inlineCodeConfig.cornerRadius,
                config.inlineCodeConfig.outlineColor
            )

            UIRoundedRectangle.extractRoundedRectangle(
                extractor,
                x1 + outlineWidth,
                y1 + outlineWidth,
                x2 - outlineWidth,
                y2 - outlineWidth,
                config.inlineCodeConfig.cornerRadius,
                config.inlineCodeConfig.backgroundColor
            )
        }

        val xShift = state.xShift + if (style.isCode) config.inlineCodeConfig.horizontalPadding else 0f
        val yShift = state.yShift + if (style.isCode) config.inlineCodeConfig.verticalPadding else 0f

        texts.forEach {
            extractString(
                extractor,
                config,
                md.getFontProvider(),
                it.string,
                it.x + xShift,
                it.y + yShift,
                scaleModifier,
                it.selected,
                style.linkLocation != null,
                hovered,
                headerConfig
            )
        }
    }

    data class Text(
        val string: String,
        val x: Float,
        val y: Float,
        val selected: Boolean
    )

    // TextDrawable mouse selection is managed by ParagraphDrawable#select
    override fun cursorAt(mouseX: Float, mouseY: Float, dragged: Boolean, mouseButton: Int) = throw IllegalStateException("never called")

    override fun cursorAtStart() = TextCursor(this, 0)
    override fun cursorAtEnd() = TextCursor(this, plainText().length)

    override fun selectedText(asMarkdown: Boolean): String {
        if (selectionStart == -1 || selectionEnd == -1)
            return ""

        val selectedText = plainText().substring(selectionStart, selectionEnd)
        if (!asMarkdown)
            return selectedText

        return "${style.markdownSymbols}$selectedText${style.markdownSymbols}"
    }

    override fun toString() = formattedText

    class LinkedTexts {
        private val texts = mutableSetOf<TextDrawable>()

        fun isHovered() = texts.any { it.isHovered }

        fun linkText(text: TextDrawable) {
            texts.add(text)
        }

        fun unlinkText(text: TextDrawable) {
            texts.remove(text)
        }

        companion object {
            fun merge(linked1: LinkedTexts?, linked2: LinkedTexts?): LinkedTexts {
                return when {
                    linked1 == null && linked2 == null -> LinkedTexts()
                    linked1 == null -> linked2!!
                    linked2 == null -> linked1
                    else -> {
                        linked2.texts.forEach {
                            linked1.linkText(it)
                        }
                        linked1
                    }
                }
            }
        }
    }

    data class Style(
        val isBold: Boolean,
        val isItalic: Boolean,
        val isStrikethrough: Boolean,
        val isUnderline: Boolean,
        val isCode: Boolean,
        val linkLocation: String?
    ) {
        val formattingSymbols = buildString {
            if (isCode)
                return@buildString
            if (isBold)
                append("§l")
            if (isItalic)
                append("§o")
            if (isStrikethrough)
                append("§m")
            if (isUnderline)
                append("§n")
        }

        val markdownSymbols = buildString {
            if (isCode)
                append("`")
            if (isBold)
                append("**")
            if (isItalic)
                append("*")
            if (isStrikethrough)
                append("~~")
            if (isUnderline)
                append("++")
        }

        val numFormattingChars: Int get() = formattingSymbols.length

        companion object {
            val EMPTY = Style(
                isBold = false,
                isItalic = false,
                isStrikethrough = false,
                isUnderline = false,
                isCode = false,
                linkLocation = null
            )
        }
    }

    companion object {
        @Deprecated(
            UMatrixStack.Compat.DEPRECATED,
            ReplaceWith("drawString(matrixStack, config, fontProvider, string, x, y, selected, isLink, isHovered)"),
        )
        fun drawString(
            config: MarkdownConfig,
            fontProvider: FontProvider,
            string: String,
            x: Float,
            y: Float,
            selected: Boolean = false,
            isLink: Boolean = false,
            isHovered: Boolean = false
        ) = drawString(UMatrixStack(), config, fontProvider, string, x, y, selected, isLink, isHovered)

        fun drawString(
            matrixStack: UMatrixStack,
            config: MarkdownConfig,
            fontProvider: FontProvider,
            string: String,
            x: Float,
            y: Float,
            selected: Boolean = false,
            isLink: Boolean = false,
            isHovered: Boolean = false,
            headerConfig: HeaderLevelConfig? = null
        ) {
            if (selected) {
                UIBlock.drawBlockSized(
                    matrixStack,
                    config.textConfig.selectionBackgroundColor,
                    x.toDouble(),
                    y.toDouble(),
                    string.width(1f, fontProvider).toDouble(),
                    9.0
                )
            }

            val foregroundColor = when {
                isLink -> config.textConfig.linkColor.rgb
                selected -> config.textConfig.selectionForegroundColor.rgb
                headerConfig != null -> headerConfig.fontColor.rgb
                else -> config.textConfig.color.rgb
            }

            if (config.textConfig.hasShadow) {
                @Suppress("DEPRECATION")
                fontProvider.drawString(
                    matrixStack,
                    string,
                    Color(foregroundColor),
                    x,
                    y,
                    10f,
                    1f,
                    true,
                    Color(config.textConfig.shadowColor.rgb)
                )
            } else {
                @Suppress("DEPRECATION")
                fontProvider.drawString(
                    matrixStack,
                    string,
                    Color(foregroundColor),
                    x,
                    y,
                    10f,
                    1f,
                    false
                )
            }

            if (isLink && isHovered) {
                if (config.textConfig.hasShadow) {
                    UIBlock.drawBlockSized(
                        matrixStack,
                        config.textConfig.shadowColor,
                        x.toDouble() + 1,
                        y.toDouble() + 9,
                        string.width().toDouble(),
                        1.0,
                    )
                }
                UIBlock.drawBlockSized(
                    matrixStack,
                    config.textConfig.linkColor,
                    x.toDouble(),
                    y.toDouble() + 8,
                    string.width().toDouble(),
                    1.0
                )
            }
        }

        fun extractString(
            extractor: ElementaExtractor,
            config: MarkdownConfig,
            fontProvider: FontProvider,
            string: String,
            x: Float,
            y: Float,
            scale: Float,
            selected: Boolean = false,
            isLink: Boolean = false,
            isHovered: Boolean = false,
            headerConfig: HeaderLevelConfig? = null
        ) {
            if (selected) {
                extractor.fillMcScaleXYWH(
                    x,
                    y,
                    string.width(scale, fontProvider),
                    9f * scale,
                    config.textConfig.selectionBackgroundColor,
                )
            }

            val foregroundColor = when {
                isLink -> config.textConfig.linkColor.rgb
                selected -> config.textConfig.selectionForegroundColor.rgb
                headerConfig != null -> headerConfig.fontColor.rgb
                else -> config.textConfig.color.rgb
            }

            if (config.textConfig.hasShadow) {
                fontProvider.extractMcScale(
                    extractor,
                    string,
                    Color(foregroundColor),
                    x,
                    y,
                    scale,
                    true,
                    Color(config.textConfig.shadowColor.rgb)
                )
            } else {
                fontProvider.extractMcScale(
                    extractor,
                    string,
                    Color(foregroundColor),
                    x,
                    y,
                    scale,
                    false
                )
            }

            if (isLink && isHovered) {
                val width = string.width(scale, fontProvider)
                if (config.textConfig.hasShadow) {
                    extractor.fillMcScaleXYWH(
                        x + 1 * scale,
                        y + 9 * scale,
                        width,
                        1 * scale,
                        config.textConfig.shadowColor,
                    )
                }
                extractor.fillMcScaleXYWH(
                    x,
                    y + 8 * scale,
                    width,
                    1 * scale,
                    config.textConfig.linkColor,
                )
            }
        }
    }
}
