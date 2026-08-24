package gg.essential.elementa.renderer

import gg.essential.elementa.components.Window
import gg.essential.elementa.renderer.impl.Element
import gg.essential.elementa.renderer.impl.computeBounds

/**
 * A snapshot of an Elementa [Window] containing all the state necessary to render it.
 *
 * @see Window.extractRenderState
 * @see ElementaRenderer.renderToTexture
 */
class ElementaRenderState internal constructor(
    val screenWidth: Int,
    val screenHeight: Int,
    internal val guiScale: Float,
    internal val elements: List<Element>
) {
    internal val bounds = computeBounds(elements)
    // Left/top bounds (inclusive)
    val boundsX1 = bounds.x1
    val boundsY1 = bounds.y1
    // Right/bottom bounds (exclusive)
    val boundsX2 = bounds.x2
    val boundsY2 = bounds.y2
}
