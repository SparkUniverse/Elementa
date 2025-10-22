package gg.essential.elementa.components

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.toConstraint
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.utils.readElementaShaderSource
import gg.essential.elementa.utils.readFromLegacyShader
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.shader.Float2Uniform
import gg.essential.universal.shader.FloatUniform
import gg.essential.universal.shader.UShader
import gg.essential.universal.vertex.UBufferBuilder
import java.awt.Color

/**
 * Simple component that uses shaders to draw a circle. This component
 * takes a radius instead of a height/width!
 *
 * @param radius circle radius
 * @param color circle color
 * @param steps unused, kept for backwards compatibility
 */
class UICircle @JvmOverloads constructor(radius: Float = 0f, color: Color = Color.WHITE, var steps: Int = 0) :
    UIComponent() {
    init {
        setColor(color.toConstraint())
        setRadius(radius.pixels())
    }

    override fun getLeft(): Float {
        return constraints.getX() - getRadius()
    }

    override fun getTop(): Float {
        return constraints.getY() - getRadius()
    }

    override fun getWidth(): Float {
        return getRadius() * 2
    }

    override fun getHeight(): Float {
        return getRadius() * 2
    }

    override fun isPositionCenter(): Boolean {
        return true
    }

    override fun draw(matrixStack: UMatrixStack) {
        beforeDraw(matrixStack)

        val x = constraints.getX()
        val y = constraints.getY()
        val r = getRadius()

        val color = getColor()
        if (color.alpha == 0) return super.draw(matrixStack)

        drawCircle(matrixStack, x, y, r, color)

        super.draw(matrixStack)
    }

    companion object {
        private lateinit var shader: UShader
        private lateinit var shaderRadiusUniform: FloatUniform
        private lateinit var shaderCenterPositionUniform: Float2Uniform

        private val PIPELINE = URenderPipeline.builderWithLegacyShader(
            "elementa:circle",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
            readElementaShaderSource("rect", "vsh"),
            readElementaShaderSource("circle", "fsh"),
        ).apply {
            @Suppress("DEPRECATION")
            blendState = BlendState.NORMAL
            depthTest = URenderPipeline.DepthTest.Always // see UIBlock.PIPELINE
        }.build()

        private val PIPELINE2 = URenderPipeline.builderWithLegacyShader(
            "elementa:circle",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
            readElementaShaderSource("rect", "vsh"),
            readElementaShaderSource("circle", "fsh"),
        ).apply {
            blendState = BlendState.ALPHA
            depthTest = URenderPipeline.DepthTest.Always // see UIBlock.PIPELINE
        }.build()

        fun initShaders() {
            if (URenderPipeline.isRequired) return
            if (::shader.isInitialized)
                return

            @Suppress("DEPRECATION")
            shader = UShader.readFromLegacyShader("rect", "circle", BlendState.NORMAL)
            if (!shader.usable) {
                println("Failed to load Elementa UICircle shader")
                return
            }
            shaderRadiusUniform = shader.getFloatUniform("u_Radius")
            shaderCenterPositionUniform = shader.getFloat2Uniform("u_CenterPos")
        }

        @Deprecated(
            UMatrixStack.Compat.DEPRECATED,
            ReplaceWith("drawCircle(matrixStack, centerX, centerY, radius, color)"),
        )
        fun drawCircle(centerX: Float, centerY: Float, radius: Float, color: Color) =
            drawCircle(UMatrixStack(), centerX, centerY, radius, color)

        fun drawCircle(matrixStack: UMatrixStack, centerX: Float, centerY: Float, radius: Float, color: Color) {
            if (!URenderPipeline.isRequired && !ElementaVersion.atLeastV9Active) {
                @Suppress("DEPRECATION")
                return drawCircleLegacy(matrixStack, centerX, centerY, radius, color)
            }

            val bufferBuilder = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)
            UIBlock.drawBlock(
                bufferBuilder,
                matrixStack,
                color,
                (centerX - radius).toDouble(),
                (centerY - radius).toDouble(),
                (centerX + radius).toDouble(),
                (centerY + radius).toDouble()
            )
            bufferBuilder.build()?.drawAndClose(if (ElementaVersion.atLeastV10Active) PIPELINE2 else PIPELINE) {
                uniform("u_Radius", radius)
                uniform("u_CenterPos", centerX, centerY)
            }
        }

        @Deprecated("Stops working in 1.21.5")
        @Suppress("DEPRECATION")
        private fun drawCircleLegacy(matrixStack: UMatrixStack, centerX: Float, centerY: Float, radius: Float, color: Color) {
            if (!::shader.isInitialized || !shader.usable)
                return

            shader.bind()
            shaderRadiusUniform.setValue(radius)
            shaderCenterPositionUniform.setValue(centerX, centerY)

            UIBlock.drawBlockWithActiveShader(
                matrixStack,
                color,
                (centerX - radius).toDouble(),
                (centerY - radius).toDouble(),
                (centerX + radius).toDouble(),
                (centerY + radius).toDouble()
            )

            shader.unbind()
        }
    }
}
