package gg.essential.elementa.font

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import gg.essential.elementa.font.data.Font
import gg.essential.elementa.font.data.FontInfo
import gg.essential.elementa.font.data.Glyph
import gg.essential.elementa.font.data.shrinkGlyphsByHalfAPixel
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UBufferBuilder
import gg.essential.universal.vertex.UVertexConsumer
import java.awt.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BasicFontRenderer(
    regularFont: Font
) : FontProvider {
    private val regularFontInfo = regularFont.fontInfo.shrinkGlyphsByHalfAPixel()
    private val regularFontTexture by lazy { regularFont.getTexture() }

    /* Required by Elementa but unused for this type of constraint */
    override var cachedValue: FontProvider = this
    override var recalculate: Boolean = false
    override var constrainTo: UIComponent? = null


    override fun getStringWidth(string: String, pointSize: Float): Float {
        return getStringDimensions(string, pointSize).first
    }

    override fun getStringHeight(string: String, pointSize: Float): Float {
        return getStringDimensions(string, pointSize).second
    }

    private fun getStringDimensions(string: String, pointSize: Float): Pair<Float, Float> {
        var currentX = 0f
        var top = Float.NEGATIVE_INFINITY
        var bottom = Float.POSITIVE_INFINITY

        /*
            10 point font is the default used in Elementa.
            Adjust the point size based on this font's size.
         */
        val currentPointSize = pointSize / 10 * regularFontInfo.atlas.size

        var i = 0
        while (i < string.length) {
            val char = string[i]

            //Ignore formatting codes for purpose of calculating string dimensions
            if (char == '\u00a7' && i + 1 < string.length) {
                //not handled by this font renderer
                i += 2
                continue
            }

            val glyph = regularFontInfo.glyphs[char.code]
            if (glyph == null) {
                i++
                continue
            }

            val planeBounds = glyph.planeBounds

            if (planeBounds != null) {
                top = max(top, planeBounds.t)
                bottom = min(bottom, planeBounds.b)
            }

            currentX += computeAdvance(regularFontInfo, glyph)

            i++
        }

        // undo letter spacing after final letter
        currentX -= 1 / regularFontInfo.atlas.size

        val width = currentX.coerceAtLeast(0f)
        val height = if (top.isInfinite() || bottom.isInfinite()) 0f else top - bottom
        return Pair(width * currentPointSize, height * currentPointSize)
    }

    fun getLineHeight(pointSize: Float): Float {
        return regularFontInfo.metrics.lineHeight * pointSize
    }

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
        val pointSize = 10 * scale / extractor.guiScale
        val w = (getStringWidth(string, pointSize) * extractor.guiScale).roundToInt()
        val h = (getStringHeight(string, pointSize) * extractor.guiScale).roundToInt()
        val textures = listOf(regularFontTexture.gpuTextureView to UGpuSampler(
            UGpuSampler.AddressMode.CLAMP_TO_EDGE,
            UGpuSampler.AddressMode.CLAMP_TO_EDGE,
            UGpuSampler.FilterMode.NEAREST,
            UGpuSampler.FilterMode.NEAREST,
            false,
        ))
        val vertices = string.length * 4 * (if (shadow) 2 else 1)

        extractor.custom(x, y, x + w, y + h, PIPELINE2, textures, vertices) { buffer, _, _ ->
            drawString(buffer, UMatrixStack.UNIT, string, color, x.toFloat(), y.toFloat(), scale, shadow, shadowColor)
        }
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
        if (URenderPipeline.isRequired || ElementaVersion.atLeastV9Active) {
            val bufferBuilder = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR)
            drawString(bufferBuilder, matrixStack, string, color, x, y, originalPointSize / 10 * scale, shadow, shadowColor)
            bufferBuilder.build()?.drawAndClose(if (ElementaVersion.atLeastV10Active) PIPELINE2 else PIPELINE) {
                texture(0, regularFontTexture.gpuTextureView, UGpuSampler(
                    UGpuSampler.AddressMode.CLAMP_TO_EDGE,
                    UGpuSampler.AddressMode.CLAMP_TO_EDGE,
                    UGpuSampler.FilterMode.NEAREST,
                    UGpuSampler.FilterMode.NEAREST,
                    false,
                ))
            }
        } else {
            UGraphics.bindTexture(0, regularFontTexture.dynamicGlId)
            val bufferBuilder = UGraphics.getFromTessellator()
            @Suppress("DEPRECATION")
            bufferBuilder.beginWithDefaultShader(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR)
            drawString(bufferBuilder.asUVertexConsumer(), matrixStack, string, color, x, y, originalPointSize / 10 * scale, shadow, shadowColor)
            bufferBuilder.drawDirect()
        }
    }

    private fun drawString(
        vertexConsumer: UVertexConsumer,
        matrixStack: UMatrixStack,
        string: String,
        color: Color,
        x: Float,
        y: Float,
        scale: Float,
        shadow: Boolean,
        shadowColor: Color?
    ) {
        if (shadow) {
            drawStringNow(
                vertexConsumer,
                matrixStack,
                string,
                shadowColor ?: Color(
                    ((color.rgb and 16579836).shr(2)).or((color.rgb).and(-16777216))
                ),
                x + 1,
                y + 1,
                scale,
            )
        }
        drawStringNow(
            vertexConsumer,
            matrixStack,
            string,
            color,
            x,
            y,
            scale,
        )
    }

    override fun getBaseLineHeight(): Float {
        return regularFontInfo.atlas.baseCharHeight
    }

    override fun getShadowHeight(): Float {
        return regularFontInfo.atlas.shadowHeight
    }

    override fun getBelowLineHeight(): Float {
        return regularFontInfo.atlas.belowLineHeight
    }

    private fun drawStringNow(
        vertexConsumer: UVertexConsumer,
        matrixStack: UMatrixStack,
        string: String,
        color: Color,
        x: Float,
        y: Float,
        scale: Float,
    ) {
        val scaledPointSize = scale * regularFontInfo.atlas.size

        var currentX = x
        var i = 0
        while (i < string.length) {
            val char = string[i]

            // Ignore color code characters in this font renderer
            if (char == '\u00a7' && i + 1 < string.length) {
                i += 2
                continue
            }


            val glyph = regularFontInfo.glyphs[char.code]
            if (glyph == null) {
                i++
                continue
            }

            val planeBounds = glyph.planeBounds

            if (planeBounds != null) {
                val width = (planeBounds.r - planeBounds.l) * scaledPointSize
                val height = (planeBounds.t - planeBounds.b) * scaledPointSize

                drawGlyph(
                    vertexConsumer,
                    matrixStack,
                    glyph,
                    color,
                    currentX,
                    y + regularFontInfo.atlas.baseCharHeight * scale - planeBounds.t * scaledPointSize,
                    width,
                    height
                )
            }

            currentX += computeAdvance(regularFontInfo, glyph) * scaledPointSize
            i++
        }

    }

    // Letter spacing for many fonts is like 1.25px, so we ignore font-provided advance values, and instead derive
    // ones directly based on the actual size of the glyph.
    private fun computeAdvance(fontInfo: FontInfo, glyph: Glyph): Float =
        if (glyph.atlasBounds != null) (glyph.atlasBounds.r - glyph.atlasBounds.l + 1) / fontInfo.atlas.size
        // For empty glyphs (like ` `), we use the font-provided value but round it to pixels so we don't end up with
        // sub-pixel positions. We don't use `roundToRealPixels`, so the value stays scale-independent.
        else (glyph.advance * fontInfo.atlas.size).roundToInt() / fontInfo.atlas.size

    private fun drawGlyph(
        worldRenderer: UVertexConsumer,
        matrixStack: UMatrixStack,
        glyph: Glyph,
        color: Color,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        val atlasBounds = glyph.atlasBounds ?: return
        val atlas = regularFontInfo.atlas
        val textureTop = 1.0 - ((atlasBounds.t) / atlas.height)
        val textureBottom = 1.0 - ((atlasBounds.b) / atlas.height)
        val textureLeft = (atlasBounds.l / atlas.width).toDouble()
        val textureRight = (atlasBounds.r / atlas.width).toDouble()

        val doubleX = x.toDouble()
        val doubleY = y.toDouble()
        worldRenderer.pos(matrixStack, doubleX, doubleY + height, 0.0).tex(textureLeft, textureBottom).color(
            color.red,
            color.green,
            color.blue,
            255
        ).endVertex()
        worldRenderer.pos(matrixStack, doubleX + width, doubleY + height, 0.0).tex(textureRight, textureBottom).color(
            color.red,
            color.green,
            color.blue,
            255
        ).endVertex()
        worldRenderer.pos(matrixStack, doubleX + width, doubleY, 0.0).tex(textureRight, textureTop).color(
            color.red,
            color.green,
            color.blue,
            255
        ).endVertex()
        worldRenderer.pos(matrixStack, doubleX, doubleY, 0.0).tex(textureLeft, textureTop).color(
            color.red,
            color.green,
            color.blue,
            255
        ).endVertex()
    }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {

    }

    private companion object {
        private val PIPELINE = URenderPipeline.builderWithDefaultShader("elementa:basic_font", UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR).apply {
            @Suppress("DEPRECATION")
            blendState = BlendState.NORMAL.copy(srcAlpha = BlendState.Param.ONE, dstAlpha = BlendState.Param.ZERO)
        }.build()
        private val PIPELINE2 = URenderPipeline.builderWithDefaultShader("elementa:basic_font", UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR).apply {
            blendState = BlendState.ALPHA
        }.build()
    }
}
