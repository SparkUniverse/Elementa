package gg.essential.elementa.components

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.MappedState
import gg.essential.elementa.state.State
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UBufferBuilder
import org.lwjgl.opengl.GL11
import java.awt.Color

/**
 * Variant of [UIBlock] with two colours that fade into each other in a
 * gradient pattern.
 */
open class GradientComponent constructor(
    startColor: State<Color>,
    endColor: State<Color>,
    direction: State<GradientDirection>
) : UIBlock(Color(0, 0, 0, 0)) {

    @JvmOverloads constructor(
        startColor: Color = Color.WHITE,
        endColor: Color = Color.WHITE,
        direction: GradientDirection = GradientDirection.TOP_TO_BOTTOM
    ): this(BasicState(startColor), BasicState(endColor), BasicState(direction))

    private val startColorState: MappedState<Color, Color> = startColor.map { it }
    private val endColorState: MappedState<Color, Color> = endColor.map{ it }
    private val directionState: MappedState<GradientDirection, GradientDirection> = direction.map { it }

    fun getStartColor(): Color = startColorState.get()
    fun setStartColor(startColor: Color) = apply {
        startColorState.set(startColor)
    }
    fun bindStartColor(newStartColorState: State<Color>) = apply {
        startColorState.rebind(newStartColorState)
    }

    fun getEndColor(): Color = endColorState.get()
    fun setEndColor(endColor: Color) = apply {
        endColorState.set(endColor)
    }
    fun bindEndColor(newEndColorState: State<Color>) = apply {
        endColorState.rebind(newEndColorState)
    }

    fun getDirection(): GradientDirection = directionState.get()
    fun setDirection(direction: GradientDirection) = apply {
        directionState.set(direction)
    }
    fun bindDirection(newDirectionState: State<GradientDirection>) = apply {
        directionState.rebind(newDirectionState)
    }

    override fun drawBlock(matrixStack: UMatrixStack, x: Double, y: Double, x2: Double, y2: Double) {
        drawGradientBlock(
            matrixStack,
            x,
            y,
            x2,
            y2,
            startColorState.get(),
            endColorState.get(),
            directionState.get()
        )
    }

    enum class GradientDirection {
        TOP_TO_BOTTOM,
        BOTTOM_TO_TOP,
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT;

        fun getGradientColors(startColor: Color, endColor: Color): GradientColors = when (this) {
            TOP_TO_BOTTOM -> GradientColors(startColor, startColor, endColor, endColor)
            BOTTOM_TO_TOP -> GradientColors(endColor, endColor, startColor, startColor)
            LEFT_TO_RIGHT -> GradientColors(startColor, endColor, startColor, endColor)
            RIGHT_TO_LEFT -> GradientColors(endColor, startColor, endColor, startColor)
        }
    }

    data class GradientColors(val topLeft: Color, val topRight: Color, val bottomLeft: Color, val bottomRight: Color)

    companion object {
        @Deprecated(
            "This method does not allow for gradients to be rendered at sub-pixel positions. Use the Double variant instead and do not cast to Int.",
            ReplaceWith("drawGradientBlock(x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble(), startColor, endColor, direction)")
        )
        @Suppress("DEPRECATION")
        fun drawGradientBlock(
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int,
            startColor: Color,
            endColor: Color,
            direction: GradientDirection
        ): Unit = drawGradientBlock(x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble(), startColor, endColor, direction)

        @Deprecated(
            UMatrixStack.Compat.DEPRECATED,
            ReplaceWith("drawGradientBlock(matrixStack, x1, y1, x2, y2, startColor, endColor, direction)"),
        )
        fun drawGradientBlock(
            x1: Double,
            y1: Double,
            x2: Double,
            y2: Double,
            startColor: Color,
            endColor: Color,
            direction: GradientDirection
        ) = drawGradientBlock(UMatrixStack(), x1, y1, x2, y2, startColor, endColor, direction)

        /**
         * Draw a rectangle with a gradient effect.
         */
        fun drawGradientBlock(
            matrixStack: UMatrixStack,
            x1: Double,
            y1: Double,
            x2: Double,
            y2: Double,
            startColor: Color,
            endColor: Color,
            direction: GradientDirection
        ) {
            if (!URenderPipeline.isRequired && !ElementaVersion.atLeastV9Active) {
                @Suppress("DEPRECATION")
                return drawGradientBlockLegacy(matrixStack, x1, y1, x2, y2, startColor, endColor, direction)
            }

            val colors = direction.getGradientColors(startColor, endColor)
            val bufferBuilder = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)
            bufferBuilder.pos(matrixStack, x2, y1, 0.0).color(colors.topRight).endVertex()
            bufferBuilder.pos(matrixStack, x1, y1, 0.0).color(colors.topLeft).endVertex()
            bufferBuilder.pos(matrixStack, x1, y2, 0.0).color(colors.bottomLeft).endVertex()
            bufferBuilder.pos(matrixStack, x2, y2, 0.0).color(colors.bottomRight).endVertex()
            bufferBuilder.build()?.drawAndClose(if (ElementaVersion.atLeastV10Active) PIPELINE2 else PIPELINE)
        }

        @Deprecated("Stops working in 1.21.5")
        @Suppress("DEPRECATION")
        private fun drawGradientBlockLegacy(
            matrixStack: UMatrixStack,
            x1: Double, y1: Double, x2: Double, y2: Double,
            startColor: Color, endColor: Color, direction: GradientDirection
        ) {
            UGraphics.disableAlpha()
            UGraphics.enableBlend()
            UGraphics.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
            UGraphics.shadeModel(GL11.GL_SMOOTH)

            val colours = direction.getGradientColors(startColor, endColor)
            val tessellator = UGraphics.getFromTessellator()
            tessellator.beginWithDefaultShader(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)
            tessellator.pos(matrixStack, x2, y1, 0.0).color(colours.topRight).endVertex()
            tessellator.pos(matrixStack, x1, y1, 0.0).color(colours.topLeft).endVertex()
            tessellator.pos(matrixStack, x1, y2, 0.0).color(colours.bottomLeft).endVertex()
            tessellator.pos(matrixStack, x2, y2, 0.0).color(colours.bottomRight).endVertex()
            tessellator.drawDirect()

            UGraphics.shadeModel(GL11.GL_FLAT)
            UGraphics.disableBlend()
            UGraphics.enableAlpha()
        }

        private val PIPELINE = URenderPipeline.builderWithDefaultShader("elementa:gradient_block", UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR).apply {
            @Suppress("DEPRECATION")
            blendState = BlendState.NORMAL.copy(srcAlpha = BlendState.Param.ONE, dstAlpha = BlendState.Param.ZERO)
        }.build()

        private val PIPELINE2 = URenderPipeline.builderWithDefaultShader("elementa:gradient_block", UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR).apply {
            blendState = BlendState.ALPHA
        }.build()
    }
}