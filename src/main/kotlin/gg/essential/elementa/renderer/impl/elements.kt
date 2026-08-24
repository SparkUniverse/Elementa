package gg.essential.elementa.renderer.impl

import gg.essential.elementa.renderer.PostProcessingRenderer
import gg.essential.elementa.renderer.SpecialRenderer
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.vertex.UVertexConsumer
import java.awt.Color

internal sealed interface Element {
    val bounds: Rect
}

internal class ColoredElement(
    override val bounds: Rect,
    val color: Color,
) : Element {
    constructor(
        rect: Rect,
        scissor: Rect,
        color: Color,
    ) : this(rect.intersection(scissor), color)
}

internal class TexturedElement(
    val rect: Rect,
    val scissor: Rect,
    val textureView: UGpuTextureView,
    val sampler: UGpuSampler,
    val textureContentImmutable: Boolean,
    val premultipliedAlpha: Boolean,
    val color: Color,
    val u1: Float,
    val v1: Float,
    val u2: Float,
    val v2: Float,
) : Element {
    override val bounds: Rect
        get() = rect.intersection(scissor)
}

internal class CustomElement(
    val rect: Rect,
    val scissor: Rect,
    val pipeline: URenderPipeline,
    val textures: List<Pair<UGpuTextureView, UGpuSampler>>,
    val vertices: Int,
    val build: (UVertexConsumer, Int, Int) -> Unit,
) : Element {
    override val bounds: Rect
        get() = rect.intersection(scissor)

    val scissorOrNull: Rect?
        get() = if (scissor.contains(rect)) null else scissor
}

internal class SpecialElement<T>(
    val rect: Rect,
    val scissor: Rect,
    val factory: SpecialRenderer.Factory<T>,
    val args: T,
) : Element {
    override val bounds: Rect
        get() = rect.intersection(scissor)
}

internal class PostProcessingElement<T>(
    val rect: Rect,
    val scissor: Rect,
    val factory: PostProcessingRenderer.Factory<T>,
    val args: T,
    val inner: List<Element>,
) : Element {
    override val bounds: Rect
        get() = rect.intersection(scissor)

    fun copyWith(rect: Rect, inner: List<Element>): PostProcessingElement<T> =
        PostProcessingElement(rect, scissor, factory, args, inner)
}

internal fun computeBounds(elements: List<Element>): Rect {
    var maxBounds = Rect(0, 0, 0, 0)
    for (element in elements) {
        maxBounds = maxBounds.union(element.bounds)
    }
    return maxBounds
}
