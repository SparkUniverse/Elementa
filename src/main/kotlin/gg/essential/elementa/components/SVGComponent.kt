package gg.essential.elementa.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.image.ImageProvider
import gg.essential.elementa.svg.SVGParser
import gg.essential.elementa.svg.data.SVG
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import java.awt.Color

/**
 * Component with native SVG rendering for high-res icons.
 *
 * Note that Elementa's SVG Renderer is quite primitive and only supports
 * basic SVGs. To ensure compatibility, we recommend using icons from
 * [Tabler](https://github.com/tabler/tabler-icons)
 */
//#if MC>=11700
//$$ @Deprecated("Not currently supported on GL3 Core / 1.17+, needs updating")
//#endif
class SVGComponent(private var svg: SVG) : UIComponent(), ImageProvider {
    private var vboID = -1
    private lateinit var vboData: List<VBOData>
    private var needsReload = false

    fun setSVG(svg: SVG) {
        this.svg = svg
        needsReload = true
    }

    override fun drawImage(matrixStack: UMatrixStack, x: Double, y: Double, width: Double, height: Double, color: Color) {
        if (UGraphics.isCoreProfile()) {
            // TODO heavily relies on legacy gl, at least need to use per-vertex color and convert lines/points to tris
            return
        }

        if (!::vboData.isInitialized || needsReload) {
            generateVBOData()
            needsReload = false
        }

        val xScale = svg.width?.let { width / it } ?: 1.0
        val yScale = svg.height?.let { height / it } ?: 1.0
        val strokeWidth = (xScale * svg.strokeWidth).toFloat().coerceAtMost(10f)

        matrixStack.push()

        @Suppress("DEPRECATION")
        UGraphics.enableBlend()
        @Suppress("DEPRECATION")
        UGraphics.disableTexture2D()

        GL11.glColor4f(color.red / 255f, color.green / 255f, color.blue / 255f, 1f)
        matrixStack.translate(x, y, 0.0)
        matrixStack.scale(xScale, yScale, 0.0)

        GL11.glPointSize(strokeWidth)
        GL11.glLineWidth(strokeWidth)
        GL11.glEnable(GL11.GL_LINE_SMOOTH)
        GL11.glEnable(GL11.GL_POINT_SMOOTH)

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID)
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY)
        GL11.glVertexPointer(2, GL11.GL_FLOAT, 0, 0)

        matrixStack.runWithGlobalState { vboData.forEach { (drawType, startIndex, vertexCount, drawPoints) ->
            GL11.glDrawArrays(drawType, startIndex, vertexCount)

            if (drawPoints && svg.roundLineJoins) {
                GL11.glDrawArrays(GL11.GL_POINTS, startIndex, vertexCount)
            }
        } }

        GL11.glDisable(GL11.GL_LINE_SMOOTH)
        GL11.glDisable(GL11.GL_POINT_SMOOTH)
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)

        @Suppress("DEPRECATION")
        UGraphics.enableTexture2D()

        matrixStack.pop()
    }

    override fun draw(matrixStack: UMatrixStack) {
        beforeDraw(matrixStack)

        val x = this.getLeft().toDouble()
        val y = this.getTop().toDouble()
        val width = this.getWidth().toDouble()
        val height = this.getHeight().toDouble()
        val color = this.getColor()

        if (color.alpha == 0) {
            return super.draw(matrixStack)
        }

        drawImage(matrixStack, x, y, width, height, color)

        super.draw(matrixStack)
    }

    private fun generateVBOData() {
        vboID = GL15.glGenBuffers()

        val totalVertexCount = svg.elements.sumOf { it.getVertexCount() }
        val vertexBuffer = BufferUtils.createFloatBuffer(totalVertexCount * 2)

        var currPos = 0
        vboData = svg.elements.map { el ->
            val vertexCount = el.getVertexCount()

            VBOData(el.createBuffer(vertexBuffer), currPos, vertexCount, el.drawSmoothPoints())
                .also { currPos += vertexCount }
        }

        vertexBuffer.rewind()

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_STATIC_DRAW)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
    }

    fun finalize() {
        val vboID = vboID
        if (vboID != -1) {
            Window.enqueueRenderOperation {
                GL15.glDeleteBuffers(vboID)
            }
        }
    }

    companion object {
        fun ofResource(resourcePath: String): SVGComponent = SVGComponent(SVGParser.parseFromResource(resourcePath))

        private data class VBOData(val drawType: Int, val startIndex: Int, val count: Int, val drawPoints: Boolean)
    }
}
