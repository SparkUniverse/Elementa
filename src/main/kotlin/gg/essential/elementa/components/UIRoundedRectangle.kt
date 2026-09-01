package gg.essential.elementa.components

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.fillXYWH
import gg.essential.elementa.utils.readElementaShaderSource
import gg.essential.elementa.utils.readFromLegacyShader
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.shader.Float4Uniform
import gg.essential.universal.shader.FloatUniform
import gg.essential.universal.shader.UShader
import gg.essential.universal.vertex.UBufferBuilder
import java.awt.Color
import kotlin.math.roundToInt

/**
 * Alternative to [UIBlock] with rounded corners.
 *
 * @param radius corner radius.
 */
open class UIRoundedRectangle(radius: Float) : UIComponent() {
    init {
        setRadius(radius.pixels())
    }

    override fun extractComponent(extractor: ElementaExtractor) {
        extractRoundedRectangle(extractor, getLeft(), getTop(), getRight(), getBottom(), getRadius(), getColor())
    }

    @Deprecated(
        "`draw`-style rendering is deprecated. Override `extractComponent` instead. Call `extract` to extract this component, its effects, and its children.",
        replaceWith = ReplaceWith("extract(extractor)")
    )
    override fun draw(matrixStack: UMatrixStack) {
        beforeDrawCompat(matrixStack)

        val radius = getRadius()

        val color = getColor()
        if (color.alpha != 0)
            drawRoundedRectangle(matrixStack, getLeft(), getTop(), getRight(), getBottom(), radius, color)

        @Suppress("DEPRECATION")
        super.draw(matrixStack)
    }

    companion object {
        private lateinit var shader: UShader
        private lateinit var shaderRadiusUniform: FloatUniform
        private lateinit var shaderInnerRectUniform: Float4Uniform

        private val PIPELINE = URenderPipeline.builderWithLegacyShader(
            "elementa:rounded_rectangle",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
            readElementaShaderSource("rect", "vsh"),
            readElementaShaderSource("rounded_rect", "fsh"),
        ).apply {
            @Suppress("DEPRECATION")
            blendState = BlendState.NORMAL
            depthTest = URenderPipeline.DepthTest.Always // see UIBlock.PIPELINE
        }.build()

        private val PIPELINE2 = URenderPipeline.builderWithLegacyShader(
            "elementa:rounded_rectangle",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
            readElementaShaderSource("rect", "vsh"),
            readElementaShaderSource("rounded_rect", "fsh"),
        ).apply {
            blendState = BlendState.ALPHA
            depthTest = URenderPipeline.DepthTest.Always // see UIBlock.PIPELINE
        }.build()

        fun initShaders() {
            if (URenderPipeline.isRequired) return
            if (::shader.isInitialized)
                return

            @Suppress("DEPRECATION")
            shader = UShader.readFromLegacyShader("rect", "rounded_rect", BlendState.NORMAL)
            if (!shader.usable) {
                println("Failed to load Elementa UIRoundedRectangle shader")
                return
            }
            shaderRadiusUniform = shader.getFloatUniform("u_Radius")
            shaderInnerRectUniform = shader.getFloat4Uniform("u_InnerRect")
        }

        @Deprecated(
            UMatrixStack.Compat.DEPRECATED,
            ReplaceWith("drawRoundedRectangle(matrixStack, left, top, right, bottom, radius, color)"),
        )
        fun drawRoundedRectangle(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Color) =
            drawRoundedRectangle(UMatrixStack(), left, top, right, bottom, radius, color)

        /**
         * Draws a rounded rectangle
         */
        fun drawRoundedRectangle(matrixStack: UMatrixStack, left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Color) {
            if (!URenderPipeline.isRequired && !ElementaVersion.atLeastV9Active) {
                @Suppress("DEPRECATION")
                return drawRoundedRectangleLegacy(matrixStack, left, top, right, bottom, radius, color)
            }

            val bufferBuilder = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)
            UIBlock.drawBlock(bufferBuilder, matrixStack, color, left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble())
            bufferBuilder.build()?.drawAndClose(if (ElementaVersion.atLeastV10Active) PIPELINE2 else PIPELINE) {
                uniform("u_Radius", radius)
                uniform("u_InnerRect", left + radius, top + radius, right - radius, bottom - radius)
            }
        }

        @Deprecated("Stops working in 1.21.5")
        @Suppress("DEPRECATION")
        private fun drawRoundedRectangleLegacy(matrixStack: UMatrixStack, left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Color) {
            if (!::shader.isInitialized || !shader.usable)
                return

            shader.bind()
            shaderRadiusUniform.setValue(radius)
            shaderInnerRectUniform.setValue(left + radius, top + radius, right - radius, bottom - radius)

            UIBlock.drawBlockWithActiveShader(matrixStack, color, left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble())

            shader.unbind()
        }

        fun extractRoundedRectangle(extractor: ElementaExtractor, left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Color) {
            if (color.alpha == 0) return
            extractRoundedRectanglePixelSpace(
                extractor,
                (left * extractor.guiScale).roundToInt(),
                (top * extractor.guiScale).roundToInt(),
                (right * extractor.guiScale).roundToInt(),
                (bottom * extractor.guiScale).roundToInt(),
                radius * extractor.guiScale,
                color,
            )
        }

        private fun extractRoundedRectanglePixelSpace(
            extractor: ElementaExtractor,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            radius: Float,
            color: Color
        ) {
            val width = right - left
            val height = bottom - top
            val desiredRadius = radius.roundToInt()
            val cornerWidth = desiredRadius.coerceAtMost(width / 2)
            val cornerHeight = desiredRadius.coerceAtMost(height / 2)
            val centerWidth = (width - cornerWidth * 2)
            val centerHeight = (height - cornerHeight * 2)

            // Left and right quads
            if (cornerWidth > 0 && centerHeight > 0) {
                extractor.fillXYWH(left, top + cornerHeight, cornerWidth, centerHeight, color)
                extractor.fillXYWH(right - cornerWidth, top + cornerHeight, cornerWidth, centerHeight, color)
            }
            // Center quad (includes top and bottom)
            if (centerWidth > 0) {
                extractor.fillXYWH(left + cornerWidth, top, centerWidth, height, color)
            }
            // Corners (top-left, top-right, bottom-left, bottom-right)
            if (cornerWidth > 0 && cornerHeight > 0) {
                fun corner(x1: Int, y1: Int, centerX: Float, centerY: Float) =
                    UICircle.extractCirclePixelSpace(extractor, x1, y1, x1 + cornerWidth, y1 + cornerHeight, centerX, centerY, radius, color)
                corner(left, top, left + radius, top + radius)
                corner(right - cornerWidth, top, right - radius, top + radius)
                corner(left, bottom - cornerHeight, left + radius, bottom - radius)
                corner(right - cornerWidth, bottom - cornerHeight, right - radius, bottom - radius)
            }
        }
    }
}
