package gg.essential.elementa.renderer

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.renderer.impl.Rect
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.vertex.UVertexConsumer
import java.awt.Color

internal class ScissorExtractingElementaExtractor(
    val screenSize: Rect,
    override val guiScale: Float,
) : ElementaExtractor {
    override val version: ElementaVersion
        get() = ElementaVersion.V11

    val scissorStack = mutableListOf(screenSize)

    override fun pushScissor(x1: Int, y1: Int, x2: Int, y2: Int) {
        scissorStack.add(Rect.ltrbChecked(x1, y1, x2, y2).intersection(scissorStack.lastOrNull() ?: screenSize))
    }

    override fun pushScissorRaw(x1: Int, y1: Int, x2: Int, y2: Int) {
        val rect = Rect.ltrbChecked(x1, y1, x2, y2)
        require(rect in screenSize) { "$rect is out of bounds $screenSize" }
        scissorStack.add(rect)
    }

    override fun popScissor() {
        check(scissorStack.size > 1) { "Unbalanced push/popScissor" }
        scissorStack.removeLast()
    }

    override fun isVisible(x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        return scissorStack.last().intersects(Rect(x1, y1, x2, y2))
    }

    override fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Color) {
    }

    override fun blit(x1: Int, y1: Int, x2: Int, y2: Int, u1: Float, v1: Float, u2: Float, v2: Float, texture: UGpuTextureView, sampler: UGpuSampler, textureContentImmutable: Boolean, premultipliedAlpha: Boolean, color: Color) {
    }

    override fun custom(x1: Int, y1: Int, x2: Int, y2: Int, pipeline: URenderPipeline, textures: List<Pair<UGpuTextureView, UGpuSampler>>, vertices: Int, build: (UVertexConsumer, Int, Int) -> Unit) {
    }

    override fun <T> special(x1: Int, y1: Int, x2: Int, y2: Int, factory: SpecialRenderer.Factory<T>, args: T) {
    }

    override fun <T> pushPostProcessing(factory: PostProcessingRenderer.Factory<T>, args: T) {
    }

    override fun popPostProcessing(factory: PostProcessingRenderer.Factory<*>) {
    }
}
