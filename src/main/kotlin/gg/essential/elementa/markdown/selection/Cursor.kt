package gg.essential.elementa.markdown.selection

import gg.essential.elementa.markdown.DrawState
import gg.essential.elementa.markdown.MarkdownComponent
import gg.essential.elementa.markdown.drawables.Drawable
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.ImmediateElementaExtractor
import gg.essential.elementa.renderer.fillMcScaleXYWH
import gg.essential.universal.UMatrixStack
import java.awt.Color

abstract class Cursor<T : Drawable>(val target: T) {
    protected open val xBase = target.x
    protected open val yBase = target.y
    protected val height = target.height.toDouble()
    protected val width = height / 9.0

    @Deprecated(UMatrixStack.Compat.DEPRECATED, ReplaceWith("draw(matrixStack, state)"))
    @Suppress("DEPRECATION")
    fun draw(state: DrawState) = draw(UMatrixStack(), state)

    @Deprecated("`draw`-style rendering is deprecated. Use `extract` instead.")
    fun draw(matrixStack: UMatrixStack, state: DrawState) {
        extract(ImmediateElementaExtractor(matrixStack), state)
    }

    fun extract(extractor: ElementaExtractor, state: DrawState) {
        if (!MarkdownComponent.DEBUG)
            return
        extractor.fillMcScaleXYWH(
            xBase + state.xShift,
            yBase + state.yShift,
            width.toFloat(),
            height.toFloat(),
            Color.RED,
        )
    }

    abstract operator fun compareTo(other: Cursor<*>): Int
}
