package gg.essential.elementa.renderer

import gg.essential.elementa.renderer.impl.ElementaRendererImpl
import gg.essential.elementa.renderer.impl.Rect
import gg.essential.universal.UGraphics
import gg.essential.universal.render.UGpuTextureView

class ElementaRenderer : AutoCloseable {
    private val impl = ElementaRendererImpl(UGraphics.getDevice())
    private var closed = false

    fun renderToTexture(
        destination: UGpuTextureView,
        destinationX: Int, destinationY: Int,
        sourceX: Int, sourceY: Int,
        width: Int, height: Int,
        state: ElementaRenderState,
    ) {
        val textureSize = Rect.xywh(0, 0, destination.texture.width, destination.texture.height)
        val renderArea = Rect.xywhChecked(destinationX, destinationY, width, height)
        val viewportArea = Rect.xywhChecked(sourceX, sourceY, width, height)

        check(!closed) { "ElementaRenderer has already been closed." }
        require(destination.mipLevels == 1) { "Rendering with mipLevels > 1 is not yet supported" }
        require(destination.baseMipLevel == 0) { "Rendering to mipLevel != 0 is not yet supported" }
        require(textureSize.contains(renderArea)) { "Destination $renderArea is out of bounds for texture $textureSize" }

        impl.renderToTexture(
            destination,
            renderArea,
            viewportArea,
            state,
        )
    }

    override fun close() {
        check(!closed) { "ElementaRenderer has already been closed." }
        closed = true
        impl.close()
    }
}
