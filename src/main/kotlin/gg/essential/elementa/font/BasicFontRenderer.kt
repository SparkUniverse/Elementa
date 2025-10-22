package gg.essential.elementa.font

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import gg.essential.elementa.font.data.Font
import gg.essential.elementa.font.data.Glyph
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UBufferBuilder
import gg.essential.universal.vertex.UVertexConsumer
import java.awt.Color
import kotlin.math.max

class BasicFontRenderer(
    private val regularFont: Font
) : FontProvider {

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
        var width = 0f
        var height = 0f

        /*
            10 point font is the default used in Elementa.
            Adjust the point size based on this font's size.
         */
        val currentPointSize = pointSize / 10 * regularFont.fontInfo.atlas.size

        var i = 0
        while (i < string.length) {
            val char = string[i]

            //Ignore formatting codes for purpose of calculating string dimensions
            if (char == '\u00a7' && i + 1 < string.length) {
                //not handled by this font renderer
                i += 2
                continue
            }

            val glyph = regularFont.fontInfo.glyphs[char.code]
            if (glyph?.atlasBounds == null) {
                i++
                continue
            }
            val planeBounds = glyph.planeBounds

            if (planeBounds != null) {
                height = max((planeBounds.top - planeBounds.bottom) * currentPointSize, height)
            }

            //The last character should not have the whitespace to the right of it
            //Added to the width. Instead, we only add the width of the character
            val lastCorrection = if (i < string.length - 1) 0 else 1

            //The texture atlas is used here because in the context of this implementation of the font renderer
            //we do not need or want the full precision the msdf font renderer exports in. Instead, we care about
            //calculating width based on the texture pixels
            width += (((glyph.atlasBounds.right - glyph.atlasBounds.left - lastCorrection) / regularFont.fontInfo.atlas.size) * currentPointSize)


            i++
        }
        return Pair(width, height)
    }

    fun getLineHeight(pointSize: Float): Float {
        return regularFont.fontInfo.metrics.lineHeight * pointSize
    }

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
        /*
            10 point font is the default used in Elementa.
            Adjust the point size based on this font's size.
         */
        val scaledPointSize = originalPointSize / 10 * regularFont.fontInfo.atlas.size

        /*
            Moved one pixel up so that the main body of the text is in
            the top left of the component. This change keeps text location
            in the same location as the vanilla font renderer relative to
            a UIText component.
         */
        if (shadow) {
            drawStringNow(
                matrixStack,
                string,
                shadowColor ?: Color(
                    ((color.rgb and 16579836).shr(2)).or((color.rgb).and(-16777216))
                ),
                x + 1,
                y,
                scaledPointSize * scale
            )
        }
        drawStringNow(
            matrixStack,
            string,
            color,
            x,
            y - 1,
            scaledPointSize * scale
        )
    }

    override fun getBaseLineHeight(): Float {
        return regularFont.fontInfo.atlas.baseCharHeight
    }

    override fun getShadowHeight(): Float {
        return regularFont.fontInfo.atlas.shadowHeight
    }

    override fun getBelowLineHeight(): Float {
        return regularFont.fontInfo.atlas.belowLineHeight
    }

    private fun drawStringNow(
        matrixStack: UMatrixStack,
        string: String,
        color: Color,
        x: Float,
        y: Float,
        originalPointSize: Float
    ) {
        if (URenderPipeline.isRequired || ElementaVersion.atLeastV9Active) {
            val bufferBuilder = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR)
            drawStringNow(bufferBuilder, matrixStack, string, color, x, y, originalPointSize)
            bufferBuilder.build()?.drawAndClose(if (ElementaVersion.atLeastV10Active) PIPELINE2 else PIPELINE) {
                texture(0, regularFont.getTexture().dynamicGlId)
            }
        } else {
            UGraphics.bindTexture(0, regularFont.getTexture().dynamicGlId)
            val bufferBuilder = UGraphics.getFromTessellator()
            @Suppress("DEPRECATION")
            bufferBuilder.beginWithDefaultShader(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR)
            drawStringNow(bufferBuilder.asUVertexConsumer(), matrixStack, string, color, x, y, originalPointSize)
            bufferBuilder.drawDirect()
        }
    }

    private fun drawStringNow(
        vertexConsumer: UVertexConsumer,
        matrixStack: UMatrixStack,
        string: String,
        color: Color,
        x: Float,
        y: Float,
        originalPointSize: Float
    ) {
        var currentX = x
        var i = 0
        while (i < string.length) {
            val char = string[i]

            // Ignore color code characters in this font renderer
            if (char == '\u00a7' && i + 1 < string.length) {
                i += 2
                continue
            }


            val glyph = regularFont.fontInfo.glyphs[char.code]
            if (glyph == null) {
                i++
                continue
            }

            val planeBounds = glyph.planeBounds

            if (planeBounds != null) {
                val width = (planeBounds.right - planeBounds.left) * originalPointSize
                val height = (planeBounds.top - planeBounds.bottom) * originalPointSize

                drawGlyph(
                    vertexConsumer,
                    matrixStack,
                    glyph,
                    color,
                    currentX,
                    y + planeBounds.bottom * originalPointSize,
                    width,
                    height
                )
            }

            //The texture atlas is used here because in the context of this implementation of the font renderer
            //we do not need or want the full precision the msdf font renderer exports in. Instead, we care about
            //calculating width based on the texture pixels
            if (glyph.atlasBounds != null) {
                currentX += (((glyph.atlasBounds.right - glyph.atlasBounds.left) / regularFont.fontInfo.atlas.size) * originalPointSize)
            } else {
                currentX += (glyph.advance) * originalPointSize
            }
            i++
        }

    }


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
        val atlas = regularFont.fontInfo.atlas
        val textureTop = 1.0 - ((atlasBounds.top) / atlas.height)
        val textureBottom = 1.0 - ((atlasBounds.bottom) / atlas.height)
        val textureLeft = (atlasBounds.left / atlas.width).toDouble()
        val textureRight = (atlasBounds.right / atlas.width).toDouble()

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
        private val PIPELINE = URenderPipeline.builderWithDefaultShader("elementa:basic_font", UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR).apply {
            @Suppress("DEPRECATION")
            blendState = BlendState.NORMAL.copy(srcAlpha = BlendState.Param.ONE, dstAlpha = BlendState.Param.ZERO)
        }.build()
        private val PIPELINE2 = URenderPipeline.builderWithDefaultShader("elementa:basic_font", UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR).apply {
            blendState = BlendState.ALPHA
        }.build()
    }
}