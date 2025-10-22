package gg.essential.elementa.components

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.toConstraint
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UBufferBuilder
import org.lwjgl.opengl.GL11
import java.awt.Color

// feeling cute, might delete this class later
//   (he did not delete the class later)

/**
 * Component for drawing arbitrary shapes.
 */
@Deprecated("Currently only supports convex polygons. Use with care! Or better, create a dedicated component for your use case.")
open class UIShape @JvmOverloads constructor(color: Color = Color.WHITE) : UIComponent() {
    private var vertices = mutableListOf<UIPoint>()
    @Deprecated("Only supports GL_POLYGON on 1.17+, implemented as TRIANGEL_FAN.")
    var drawMode = GL11.GL_POLYGON

    init {
        setColor(color.toConstraint())
    }

    fun addVertex(point: UIPoint) = apply {
        this.parent.addChild(point)
        vertices.add(point)
    }

    fun addVertices(vararg points: UIPoint) = apply {
        parent.addChildren(*points)
        vertices.addAll(points)
    }

    fun getVertices() = vertices

    override fun draw(matrixStack: UMatrixStack) {
        beforeDrawCompat(matrixStack)

        val color = this.getColor()
        if (color.alpha == 0) return super.draw(matrixStack)

        if (URenderPipeline.isRequired || ElementaVersion.atLeastV9Active) {
            draw(matrixStack, color)
        } else {
            @Suppress("DEPRECATION")
            drawLegacy(matrixStack, color)
        }

        super.draw(matrixStack)
    }

    private fun draw(matrixStack: UMatrixStack, color: Color) {
        val bufferBuilder = UBufferBuilder.create(UGraphics.DrawMode.TRIANGLE_FAN, UGraphics.CommonVertexFormats.POSITION_COLOR)
        vertices.forEach {
            bufferBuilder
                .pos(matrixStack, it.absoluteX.toDouble(), it.absoluteY.toDouble(), 0.0)
                .color(color.red, color.green, color.blue, color.alpha)
                .endVertex()
        }
        bufferBuilder.build()?.drawAndClose(if (ElementaVersion.atLeastV10Active) PIPELINE2 else PIPELINE)
    }

    @Deprecated("Stops working in 1.21.5, see UGraphics.Globals")
    @Suppress("DEPRECATION")
    private fun drawLegacy(matrixStack: UMatrixStack, color: Color) {
        UGraphics.enableBlend()
        UGraphics.disableTexture2D()
        val red = color.red.toFloat() / 255f
        val green = color.green.toFloat() / 255f
        val blue = color.blue.toFloat() / 255f
        val alpha = color.alpha.toFloat() / 255f

        val worldRenderer = UGraphics.getFromTessellator()
        UGraphics.tryBlendFuncSeparate(770, 771, 1, 0)

        if (UGraphics.isCoreProfile()) {
            worldRenderer.beginWithDefaultShader(UGraphics.DrawMode.TRIANGLE_FAN, UGraphics.CommonVertexFormats.POSITION_COLOR)
        } else {
            worldRenderer.begin(drawMode, UGraphics.CommonVertexFormats.POSITION_COLOR)
        }
        vertices.forEach {
            worldRenderer
                .pos(matrixStack, it.absoluteX.toDouble(), it.absoluteY.toDouble(), 0.0)
                .color(red, green, blue, alpha)
                .endVertex()
        }
        worldRenderer.drawDirect()

        UGraphics.enableTexture2D()
        UGraphics.disableBlend()
    }

    private companion object {
        private val PIPELINE = URenderPipeline.builderWithDefaultShader("elementa:shape", UGraphics.DrawMode.TRIANGLE_FAN, UGraphics.CommonVertexFormats.POSITION_COLOR).apply {
            @Suppress("DEPRECATION")
            blendState = BlendState.NORMAL.copy(srcAlpha = BlendState.Param.ONE, dstAlpha = BlendState.Param.ZERO)
        }.build()
        private val PIPELINE2 = URenderPipeline.builderWithDefaultShader("elementa:shape", UGraphics.DrawMode.TRIANGLE_FAN, UGraphics.CommonVertexFormats.POSITION_COLOR).apply {
            blendState = BlendState.ALPHA
        }.build()
    }
}
