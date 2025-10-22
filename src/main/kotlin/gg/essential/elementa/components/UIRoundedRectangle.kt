package gg.essential.elementa.components

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.pixels
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

/**
 * Alternative to [UIBlock] with rounded corners.
 *
 * @param radius corner radius.
 */
open class UIRoundedRectangle(radius: Float) : UIComponent() {
    init {
        setRadius(radius.pixels())
    }

    override fun draw(matrixStack: UMatrixStack) {
        beforeDrawCompat(matrixStack)

        val radius = getRadius()

        val color = getColor()
        if (color.alpha != 0)
            drawRoundedRectangle(matrixStack, getLeft(), getTop(), getRight(), getBottom(), radius, color)

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
    }
}
