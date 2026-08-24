package gg.essential.elementa

import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import gg.essential.elementa.font.FontProvider
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.ImmediateElementaExtractor
import gg.essential.elementa.renderer.SpecialRenderer
import gg.essential.elementa.utils.roundToRealPixels
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.font.UFontRenderer
import java.awt.Color
import kotlin.math.roundToInt

class VanillaFontRenderer : FontProvider {
    override var cachedValue: FontProvider = this
    override var recalculate: Boolean = false
    override var constrainTo: UIComponent? = null

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {
    }

    override fun getStringWidth(string: String, pointSize: Float): Float =
        UGraphics.getStringWidth(string).toFloat()

    override fun getStringHeight(string: String, pointSize: Float): Float =
        UGraphics.getFontHeight().toFloat()

    override fun extract(
        extractor: ElementaExtractor,
        string: String,
        color: Color,
        x: Int,
        y: Int,
        scale: Float,
        shadow: Boolean,
        shadowColor: Color?
    ) {
        // Fast-path for legacy-style rendering
        if (extractor is ImmediateElementaExtractor) {
            @Suppress("DEPRECATION")
            drawString(
                extractor.matrixStack,
                string,
                color,
                x / extractor.guiScale,
                y / extractor.guiScale,
                10f,
                scale / extractor.guiScale,
                shadow,
                shadowColor
            )
            return
        }

        val width = getStringWidth(string, 0f) * scale
        val height = getStringHeight(string, 0f) * scale
        extractor.special(
            x,
            y,
            x + width.roundToInt(),
            y + height.roundToInt(),
            FontSpecialRendererFactory,
            FontRendererArgs(
                string,
                color.rgb,
                shadow,
                shadowColor?.rgb,
                scale,
            )
        )
    }

    @Deprecated(
        "`draw`-style rendering is deprecated. Use `extract` instead.",
        replaceWith = ReplaceWith("extractMcScale(extractor, string, color, x, y, originalPointSize / 10 * scale, shadow, shadowColor)")
    )
    override fun drawString(
        matrixStack: UMatrixStack,
        string: String,
        color: Color,
        x: Float,
        y: Float,
        originalPointSize: Float,
        scale: Float,
        shadow: Boolean,
        shadowColor: Color?
    ) {
        val scaledX = x.roundToRealPixels() / scale
        val scaledY = y.roundToRealPixels() / scale

        matrixStack.scale(scale, scale, 1f)
        if (shadowColor == null || !shadow) {
            UGraphics.drawString(matrixStack, string, scaledX, scaledY, color.rgb, shadow)
        } else {
            UGraphics.drawString(matrixStack, string, scaledX, scaledY, color.rgb, shadowColor.rgb)
        }
        matrixStack.scale(1 / scale, 1 / scale, 1f)
    }

    override fun getBaseLineHeight(): Float {
        return BASE_CHAR_HEIGHT
    }

    override fun getShadowHeight(): Float {
        return SHADOW_HEIGHT;
    }

    override fun getBelowLineHeight(): Float {
        return BELOW_LINE_HEIGHT;
    }

    companion object {
        /** Most (English) capital letters have this height, so this is what we use to center "the line". */
        internal const val BASE_CHAR_HEIGHT = 7f

        /**
         * Some letters have a few extra pixels below the visually centered line (gjpqy).
         * To accommodate these, we need to add extra height at the bottom and the top (to keep the original line
         * centered). This needs special consideration because the font renderer does not consider it, so we need to
         * adjust the position we give to it accordingly.
         * Additionally, adding the space on top make top-alignment difficult, whereas not adding it makes centering
         * difficult, so we use a simple heuristic to determine which one it is we're most likely looking for and then
         * either add just the bottom one or the top one as well.
         */
        internal const val BELOW_LINE_HEIGHT = 1f

        /** Extra height if shadows are enabled. */
        const val SHADOW_HEIGHT = 1f
    }
}

private class FontRendererArgs(
    val text: String,
    val color: Int,
    val shadow: Boolean,
    val shadowColor: Int?,
    val scale: Float,
)
private object FontSpecialRendererFactory : SpecialRenderer.Factory<FontRendererArgs> {
    override fun create(): SpecialRenderer<FontRendererArgs> =
        FontSpecialRenderer()
}
private class FontSpecialRenderer : SpecialRenderer<FontRendererArgs> {
    private val renderer = UFontRenderer()

    override val supportsScissor: Boolean
        get() = false
    override val onlyDrawsInBounds: Boolean
        get() = true

    override fun render(destination: UGpuTextureView, instances: List<SpecialRenderer.Instance<FontRendererArgs>>) {
        renderer.render(destination, instances.map {
            UFontRenderer.Text(it.dstX, it.dstY, it.args.scale, it.args.text, it.args.color, it.args.shadow, it.args.shadowColor)
        })
    }

    override fun close() {
        renderer.close()
    }
}
