package gg.essential.elementa.renderer.impl

import gg.essential.elementa.components.UIBlock
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.SharedIndexBuffers
import gg.essential.universal.render.UGpuBuffer
import gg.essential.universal.render.UGpuDevice
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPass
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.vertex.UBufferBuilder
import java.util.WeakHashMap
import kotlin.use

internal fun renderLayers(
    device: UGpuDevice,
    renderPass: URenderPass,
    renderTarget: UGpuTextureView,
    renderArea: Rect,
    viewport: Rect,
    layers: List<MutableList<Element>>,
) {
    var batchBuilder: BatchBuilder? = null
    for (layer in layers) {
        layer.sortWith(::compareRenderOrder)
        for (element in layer) {
            if (batchBuilder == null) {
                batchBuilder = createBatchBuilder(element)
            }
            if (!batchBuilder.accept(element)) {
                batchBuilder.render(device, renderPass, renderTarget, renderArea, viewport)
                batchBuilder = createBatchBuilder(element)
                batchBuilder.accept(element).also { assert(it) }
            }
        }
    }
    batchBuilder?.render(device, renderPass, renderTarget, renderArea, viewport)
}

/**
 * Sorts elements such that those which can share a draw call are grouped together.
 */
private fun compareRenderOrder(a: Element, b: Element): Int {
    fun typeOrdering(element: Element) = when (element) {
        is ColoredElement -> 0
        is TexturedElement -> 1
        is CustomElement -> 2
        is SpecialElement<*>,
        is PostProcessingElement<*> ->
            throw AssertionError("should have been replaced by TexturedElement")
    }
    (typeOrdering(a) - typeOrdering(b)).let { if (it != 0) return it }

    when (a) {
        is ColoredElement -> {}
        is TexturedElement -> {
            b as TexturedElement
            compareArbitrary(a.premultipliedAlpha, b.premultipliedAlpha).let { if (it != 0) return it }
            compareArbitrary(a.textureView, b.textureView).let { if (it != 0) return it }
            compareArbitrary(a.sampler, b.sampler).let { if (it != 0) return it }
        }
        is CustomElement -> {
            b as CustomElement
            compareArbitrary(a.pipeline, b.pipeline).let { if (it != 0) return it }
            (a.textures.size - b.textures.size).let { if (it != 0) return it }
            for ((i, aTex) in a.textures.withIndex()) {
                val bTex = b.textures[i]
                compareArbitrary(aTex.first, bTex.first).let { if (it != 0) return it }
                compareArbitrary(aTex.second, bTex.second).let { if (it != 0) return it }
            }
            compareValues(a.scissorOrNull, b.scissorOrNull).let { if (it != 0) return it }
        }
        is SpecialElement<*>,
        is PostProcessingElement<*> ->
            throw AssertionError("should have been replaced by TexturedElement")
    }

    return 0
}

private var nextArbitraryOrdering = 0L
private val arbitraryOrdering = WeakHashMap<Any, Long>()
private fun <T> compareArbitrary(a: T, b: T): Int {
    if (a === b) return 0
    val aHashCode = a.hashCode()
    val bHashCode = b.hashCode()
    if (aHashCode != bHashCode) return aHashCode - bHashCode
    if (a == b) return 0
    // hashCode collision, need to fall back to explicitly storing an ordering
    synchronized(arbitraryOrdering) {
        val aOrder = arbitraryOrdering.getOrPut(a) { nextArbitraryOrdering++ }
        val bOrder = arbitraryOrdering.getOrPut(b) { nextArbitraryOrdering++ }
        return if (aOrder < bOrder) -1 else 1
    }
}

/**
 * Accumulates a batch of [Element] that can all be rendered via a single draw call.
 */
internal interface BatchBuilder {
    /**
     * Adds the given element to this batch if eligible.
     */
    fun accept(element: Element): Boolean

    /**
     * Consumes the builder and issues a corresponding draw call.
     */
    fun render(device: UGpuDevice, renderPass: URenderPass, renderTarget: UGpuTextureView, renderArea: Rect, viewport: Rect)
}

internal fun createBatchBuilder(element: Element): BatchBuilder {
    return when (element) {
        is ColoredElement -> ColoredBatchBuilder()
        is TexturedElement -> TexturedBatchBuilder(element.textureView, element.sampler, element.premultipliedAlpha)
        is CustomElement -> CustomBatchBuilder(element.pipeline, element.scissorOrNull, element.textures)
        is SpecialElement<*>,
        is PostProcessingElement<*> ->
            throw AssertionError("should have been replaced by TexturedElement")
    }
}

private class ColoredBatchBuilder : BatchBuilder {
    val bufferBuilder = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)
    var quads = 0

    override fun accept(element: Element): Boolean {
        if (element !is ColoredElement) return false

        val b = element.bounds
        UIBlock.drawBlock(bufferBuilder, UMatrixStack.UNIT, element.color, b.x1.toDouble(), b.y1.toDouble(), b.x2.toDouble(), b.y2.toDouble())
        quads++

        return true
    }

    override fun render(device: UGpuDevice, renderPass: URenderPass, renderTarget: UGpuTextureView, renderArea: Rect, viewport: Rect) {
        val gpuBuffer = bufferBuilder.build()!!.use { builtBuffer ->
            device.createBuffer(UGpuBuffer.Usage.VERTEX, builtBuffer.toByteBuffer())
        }
        val (indexBuffer, indexType) = SharedIndexBuffers.quads(quads * 4)

        renderPass.pipeline(PIPELINE_COLOR)
        renderPass.vertexBuffer(0, gpuBuffer.slice())
        renderPass.indexBuffer(indexBuffer, indexType)
        renderPass.drawIndexed(quads * 6)
        gpuBuffer.close()
    }
}

private class TexturedBatchBuilder(
    val textureView: UGpuTextureView,
    val sampler: UGpuSampler,
    val premultipliedAlpha: Boolean,
) : BatchBuilder {
    val bufferBuilder = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR)
    var quads = 0

    override fun accept(element: Element): Boolean {
        if (element !is TexturedElement) return false
        if (element.textureView != textureView) return false
        if (element.sampler != sampler) return false
        if (element.premultipliedAlpha != premultipliedAlpha) return false

        val rect = element.rect
        val b = element.bounds

        var u1 = element.u1.toDouble()
        var v1 = element.v1.toDouble()
        var u2 = element.u2.toDouble()
        var v2 = element.v2.toDouble()
        if (rect != b) {
            val uWidth = u2 - u1
            val vHeight = v2 - v1
            if (rect.x1 != b.x1) u1 += (b.x1 - rect.x1) / rect.w.toFloat() * uWidth
            if (rect.y1 != b.y1) v1 += (b.y1 - rect.y1) / rect.h.toFloat() * vHeight
            if (rect.x2 != b.x2) u2 += (b.x2 - rect.x2) / rect.w.toFloat() * uWidth
            if (rect.y2 != b.y2) v2 += (b.y2 - rect.y2) / rect.h.toFloat() * vHeight
        }

        bufferBuilder.pos(UMatrixStack.UNIT, b.x1.toDouble(), b.y2.toDouble(), 0.0).tex(u1, v2).color(element.color).endVertex()
        bufferBuilder.pos(UMatrixStack.UNIT, b.x2.toDouble(), b.y2.toDouble(), 0.0).tex(u2, v2).color(element.color).endVertex()
        bufferBuilder.pos(UMatrixStack.UNIT, b.x2.toDouble(), b.y1.toDouble(), 0.0).tex(u2, v1).color(element.color).endVertex()
        bufferBuilder.pos(UMatrixStack.UNIT, b.x1.toDouble(), b.y1.toDouble(), 0.0).tex(u1, v1).color(element.color).endVertex()

        quads++
        return true
    }

    override fun render(device: UGpuDevice, renderPass: URenderPass, renderTarget: UGpuTextureView, renderArea: Rect, viewport: Rect) {
        val gpuBuffer = bufferBuilder.build()!!.use { builtBuffer ->
            device.createBuffer(UGpuBuffer.Usage.VERTEX, builtBuffer.toByteBuffer())
        }
        val (indexBuffer, indexType) = SharedIndexBuffers.quads(quads * 4)

        renderPass.pipeline(if (premultipliedAlpha) PIPELINE_TEXTURE_PREMULTIPLIED_ALPHA else PIPELINE_TEXTURE)
        renderPass.vertexBuffer(0, gpuBuffer.slice())
        renderPass.indexBuffer(indexBuffer, indexType)
        renderPass.texture("Sampler0", textureView, sampler)
        renderPass.drawIndexed(quads * 6)
        gpuBuffer.close()
    }
}

private class CustomBatchBuilder(
    val pipeline: URenderPipeline,
    val scissor: Rect?,
    val textures: List<Pair<UGpuTextureView, UGpuSampler>>,
) : BatchBuilder {
    val drawMode = pipeline.drawMode
    val format = pipeline.commonVertexFormat ?: throw UnsupportedOperationException("Only pipelines with CommonVertexFormats are supported.")
    val bufferBuilder = UBufferBuilder.create(drawMode, format)
    var vertexCount = 0

    override fun accept(element: Element): Boolean {
        if (element !is CustomElement) return false
        if (element.pipeline != pipeline) return false
        if (element.textures != textures) return false
        if (element.scissorOrNull != scissor) return false

        val vertexConsumer = CountingVertexConsumer(bufferBuilder, element.vertices)
        element.build(vertexConsumer, 0, 0)
        vertexCount += vertexConsumer.count

        return true
    }

    override fun render(device: UGpuDevice, renderPass: URenderPass, renderTarget: UGpuTextureView, renderArea: Rect, viewport: Rect) {
        val gpuBuffer = bufferBuilder.build()!!.use { builtBuffer ->
            device.createBuffer(UGpuBuffer.Usage.VERTEX, builtBuffer.toByteBuffer())
        }

        fun URenderPass.scissor(rect: Rect) {
            val r = rect.intersection(renderArea)
            scissor(r.x, renderTarget.texture.height - r.h - r.y, r.w, r.h)
        }

        val (indexBuffer, indexCount) = @Suppress("DEPRECATION") when (drawMode) {
            UGraphics.DrawMode.LINES -> TODO()
            UGraphics.DrawMode.LINE_STRIP -> throw UnsupportedOperationException()
            UGraphics.DrawMode.TRIANGLES -> null to vertexCount
            UGraphics.DrawMode.TRIANGLE_STRIP -> TODO()
            UGraphics.DrawMode.TRIANGLE_FAN -> SharedIndexBuffers.triangleFan(vertexCount) to (vertexCount - 2) * 3
            UGraphics.DrawMode.QUADS -> SharedIndexBuffers.quads(vertexCount) to vertexCount / 4 * 6
        }

        if (scissor != null) {
            renderPass.scissor(Rect.xywh(renderArea.x - viewport.x + scissor.x, renderArea.y - viewport.y + scissor.y, scissor.w, scissor.h))
        }
        renderPass.pipeline(pipeline)
        for ((i, attachment) in textures.withIndex()) {
            val (textureView, sampler) = attachment
            renderPass.texture("Sampler$i", textureView, sampler)
        }
        renderPass.vertexBuffer(0, gpuBuffer.slice())
        if (indexBuffer != null) {
            val (buffer, type) = indexBuffer
            renderPass.indexBuffer(buffer, type)
            renderPass.drawIndexed(indexCount)
        } else {
            renderPass.draw(vertexCount)
        }
        gpuBuffer.close()

        if (scissor != null) {
            renderPass.scissor(renderArea)
        }
    }
}
