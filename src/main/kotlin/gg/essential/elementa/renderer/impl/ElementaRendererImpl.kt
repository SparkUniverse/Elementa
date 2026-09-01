package gg.essential.elementa.renderer.impl

import gg.essential.elementa.renderer.ElementaRenderState
import gg.essential.elementa.renderer.PostProcessingRenderer
import gg.essential.elementa.renderer.SpecialRenderer
import gg.essential.elementa.utils.NEAREST
import gg.essential.elementa.utils.elementaDev
import gg.essential.universal.render.UGpuDevice
import gg.essential.universal.render.UGpuFormat
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuTexture
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPassDescriptor
import java.awt.Color
import kotlin.random.Random
import kotlin.use

internal class ElementaRendererImpl(
    private val device: UGpuDevice,
) : AutoCloseable {

    private val maxAtlasSize = device.info.limits.maxTextureSize(UGpuFormat.DEFAULT_RGBA)

    private val temporaryTexturesCache = TemporaryTexturesCache(
        device,
        UGpuTexture.Usage.COPY_DST + UGpuTexture.Usage.TEXTURE_BINDING + UGpuTexture.Usage.RENDER_ATTACHMENT,
        UGpuFormat.DEFAULT_RGBA,
    )
    private val specialRendererCache = SpecialRendererCache()
    private val postProcessingRendererCache = PostProcessingRendererCache()
    private val textureAtlasCache = TextureAtlasCache(device, maxAtlasSize)

    fun renderToTexture(
        destination: UGpuTextureView,
        renderArea: Rect,
        viewport: Rect,
        state: ElementaRenderState,
    ) {
        val allElements = buildFlattenedListOfElementLists(state)

        bakeTextureAtlas(allElements, state.guiScale)
        bakeSpecialElements(allElements)
        bakePostProcessingEffectElements(allElements)

        renderElements(
            allElements.first().asSequence(),
            destination,
            renderArea,
            viewport,
        )

        temporaryTexturesCache.endFrame()
        if (shake && shakeRandom.nextInt(10) == 0) temporaryTexturesCache.endFrame()
        specialRendererCache.endFrame()
        postProcessingRendererCache.endFrame()
    }

    private data class Id(val indexOfList: Int, val indexInList: Int)

    /**
     * Flattens the tree created by [PostProcessingElement]s.
     * The result is a list of lists, where each inner list is one logical render pass.
     */
    private fun buildFlattenedListOfElementLists(state: ElementaRenderState) = buildList {
        fun visit(list: MutableList<Element>) {
            add(list)
            for ((i, element) in list.withIndex()) {
                if (element is PostProcessingElement<*>) {
                    val innerList = element.inner.toMutableList()
                    list[i] = element.copyWith(element.rect, innerList)
                    visit(innerList)
                }
            }
        }
        visit(state.elements.toMutableList())
    }

    private fun bakeTextureAtlas(
        allElements: List<MutableList<Element>>,
        guiScale: Float,
    ) {
        val cacheableElements = mutableListOf<Pair<Id, TexturedElement>>()
        for ((indexOfList, list) in allElements.withIndex()) {
            for ((indexInList, element) in list.withIndex()) {
                if (element is TexturedElement && element.isCacheable(guiScale)) {
                    cacheableElements.add(Pair(Id(indexOfList, indexInList), element))
                }
            }
        }

        val cachedElements = textureAtlasCache.provide(cacheableElements.asSequence().map { it.second })
        for ((i, idAndElement) in cacheableElements.withIndex()) {
            val (id, _) = idAndElement
            allElements[id.indexOfList][id.indexInList] = cachedElements[i]
        }
    }

    private fun bakeSpecialElements(allElements: List<MutableList<Element>>) {
        val specialElements = mutableListOf<Pair<Id, SpecialElement<*>>>()
        for ((indexOfList, list) in allElements.withIndex()) {
            for ((indexInList, element) in list.withIndex()) {
                if (element is SpecialElement<*>) {
                    specialElements.add(Pair(Id(indexOfList, indexInList), element))
                }
            }
        }

        for ((factory, elements) in specialElements.groupBy { it.second.factory }) {
            @Suppress("UNCHECKED_CAST") // safe because of above `groupBy`
            fun <T> bakeSpecialElementsUnchecked(allElements: List<MutableList<Element>>, factory: SpecialRenderer.Factory<T>, elements: List<Pair<Id, SpecialElement<*>>>) =
                bakeSpecialElements(allElements, factory, elements as List<Pair<Id, SpecialElement<T>>>)
            bakeSpecialElementsUnchecked(allElements, factory, elements)
        }
    }

    private fun <T> bakeSpecialElements(allElements: List<MutableList<Element>>, factory: SpecialRenderer.Factory<T>, elements: List<Pair<Id, SpecialElement<T>>>) {
        val renderer = specialRendererCache.provide(factory)
        val packings = if (renderer.supportsScissor) {
            packMany(elements.mapIndexed { i, (_, e) -> PackTexture(i, e.bounds.w, e.bounds.h) }, maxAtlasSize)
        } else if (renderer.onlyDrawsInBounds) {
            packMany(elements.mapIndexed { i, (_, e) -> PackTexture(i, e.rect.w, e.rect.h) }, maxAtlasSize)
        } else {
            elements.mapIndexed { i, (_, e) -> trivialPacking(PackTexture(i, e.bounds.w, e.bounds.h)) }
        }
        for (packing in shake(packings)) {
            val textureView = temporaryTexturesCache.provide(packing.atlasWidth, packing.atlasHeight)
            device.clearColor(textureView.texture, 0f, 0f, 0f, 0f)

            val instances = mutableListOf<SpecialRenderer.Instance<T>>()
            for (packingEntry in packing.entries) {
                val (id, element) = elements[packingEntry.id]
                val rect = element.rect
                val scissor = element.scissor
                val bounds = if (renderer.supportsScissor || !renderer.onlyDrawsInBounds) rect.intersection(scissor) else rect

                instances.add(SpecialRenderer.Instance(
                    packingEntry.x + rect.x - bounds.x, packingEntry.y + rect.y - bounds.y, rect.w, rect.h,
                    packingEntry.x + scissor.x - bounds.x, packingEntry.y + scissor.y - bounds.y, scissor.w, scissor.h,
                    element.args,
                ))

                val u = packingEntry.x / textureView.texture.width.toFloat()
                val v = packingEntry.y / textureView.texture.height.toFloat()
                val uWidth = bounds.w / textureView.texture.width.toFloat()
                val vHeight = bounds.h / textureView.texture.height.toFloat()
                allElements[id.indexOfList][id.indexInList] = TexturedElement(
                    bounds, element.bounds,
                    textureView, UGpuSampler.NEAREST, false, true, Color.WHITE,
                    u, 1f - v, u + uWidth, 1f - v - vHeight,
                )
            }
            renderer.render(textureView, instances)
        }
    }

    private fun bakePostProcessingEffectElements(allElements: List<MutableList<Element>>) {
        val nestedPasses = mutableListOf<MutableList<Pair<Id, PostProcessingElement<*>>>>()
        fun visit(list: List<Element>, depth: Int) {
            val indexOfList = allElements.indexOfFirst { it === list }
            for ((indexInList, element) in list.withIndex()) {
                if (element is PostProcessingElement<*>) {
                    if (nestedPasses.lastIndex < depth) nestedPasses.add(mutableListOf())
                    nestedPasses[depth].add(Pair(Id(indexOfList, indexInList), element))
                    visit(element.inner, depth + 1)
                }
            }
        }
        visit(allElements.first(), 0)

        for (postProcessingElements in nestedPasses.asReversed()) {
            val packings = packMany(postProcessingElements.mapIndexed { i, (_, element) -> PackTexture(i, element.bounds.w, element.bounds.h) }, maxAtlasSize)
            for (packing in shake(packings)) {
                bakePostProcessingEffectElements(allElements, postProcessingElements, packing)
            }
        }
    }

    private fun bakePostProcessingEffectElements(
        allElements: List<MutableList<Element>>,
        postProcessingElements: List<Pair<Id, PostProcessingElement<*>>>,
        packing: Packing,
    ) {
        val rawTextureView = temporaryTexturesCache.provide(packing.atlasWidth, packing.atlasHeight)

        renderElements(
            packing.entries.asSequence().flatMap { (index, x, y) ->
                val (_, element) = postProcessingElements[index]
                val bounds = element.bounds
                val offset = Pos(x, y) - bounds.xy
                element.inner.asSequence().map { it.offset(offset) }
            },
            rawTextureView,
            Rect.xywh(0, 0, rawTextureView.texture.width, rawTextureView.texture.height),
            Rect.xywh(0, 0, rawTextureView.texture.width, rawTextureView.texture.height),
        )

        val outTextureView = temporaryTexturesCache.provide(packing.atlasWidth, packing.atlasHeight)
        device.clearColor(outTextureView.texture, 0f, 0f, 0f, 0f)

        val byFactory = packing.entries.groupBy({ (index, _, _) ->
            postProcessingElements[index].second.factory
        }, { (index, x, y) ->
            val (_, element) = postProcessingElements[index]
            val bounds = element.bounds

            PostProcessingRenderer.Instance(
                bounds.x, bounds.y, bounds.w, bounds.h,
                x, y,
                x, y,
                element.args,
            )
        })
        for ((factory, instancesForFactory) in byFactory) {
            val renderer = postProcessingRendererCache.provide(factory)
            @Suppress("UNCHECKED_CAST")
            fun <T> renderUnchecked(renderer: PostProcessingRenderer<T>, instances: List<PostProcessingRenderer.Instance<*>>) {
                renderer.render(outTextureView, rawTextureView, instances as List<PostProcessingRenderer.Instance<T>>)
            }
            renderUnchecked(renderer, instancesForFactory)
        }

        for ((index, x, y) in packing.entries) {
            val (id, element) = postProcessingElements[index]
            val bounds = element.bounds

            val u = x / outTextureView.texture.width.toFloat()
            val v = y / outTextureView.texture.height.toFloat()
            val uWidth = bounds.w / outTextureView.texture.width.toFloat()
            val vHeight = bounds.h / outTextureView.texture.height.toFloat()
            allElements[id.indexOfList][id.indexInList] = TexturedElement(
                bounds, bounds,
                outTextureView, UGpuSampler.NEAREST, false, true, Color.WHITE,
                u, 1f - v, u + uWidth, 1f - v - vHeight,
            )
        }
    }

    private fun Element.offset(offset: Pos): Element = when (this) {
        is ColoredElement -> ColoredElement(bounds + offset, color)
        is CustomElement -> CustomElement(rect + offset, scissor + offset, pipeline, textures, vertices) { builder, offsetX, offsetY ->
            build(OffsetVertexConsumer(builder, offset.x, offset.y), offsetX + offset.x, offsetY + offset.y)
        }
        is TexturedElement -> TexturedElement(rect + offset, scissor + offset, textureView, sampler, textureContentImmutable, premultipliedAlpha, color, u1, v1, u2, v2)
        is SpecialElement<*>,
        is PostProcessingElement<*> ->
            throw AssertionError("Should have already been baked into a TexturedElement")
    }

    private fun renderElements(
        elements: Sequence<Element>,
        renderTarget: UGpuTextureView,
        renderArea: Rect,
        viewport: Rect,
    ) {
        val layers = sortIntoLayers(elements)

        val descriptor = URenderPassDescriptor { "Elementa GUI" }
            .withColorAttachment(renderTarget, URenderPassDescriptor.ClearColor(0f, 0f, 0f, 0f))
            .withRenderArea(URenderPassDescriptor.RenderArea(renderArea.x, renderTarget.texture.height - renderArea.y - renderArea.h, renderArea.w, renderArea.h))

        val renderTargetRect = Rect(0, 0, renderTarget.texture.width, renderTarget.texture.height)
        val projectionRect = renderTargetRect - renderArea.xy + viewport.xy
        val projectionMatrix = with(projectionRect) {
            floatArrayOf(
                2f/w, 0f,    0f,   0f,
                0f,   -2f/h, 0f,   0f,
                0f,   0f,    1f,   0f,
                -(x1+x2)/w.toFloat(), (y1+y2)/h.toFloat(), 0f, 1f,
            )
        }

        device.createRenderPass(descriptor).use { renderPass ->
            renderPass.projectionMatrix(projectionMatrix)
            renderLayers(device, renderPass, renderTarget, renderArea, viewport, layers)
        }
    }

    // When in dev mode, we'll apply a random offset to special and post-processing renderer targets to ensure
    // they work reliably and don't e.g. accidentally rely on rendering to 0/0.
    private val shake = System.getProperty("elementa.dev.renderer.shake", elementaDev.toString()).toBoolean()
    private val shakeRandom = Random(0L)
    private fun shake(packings: List<Packing>): List<Packing> = if (!shake) packings else packings.map { shake(it) }
    private fun shake(packing: Packing): Packing {
        if (!shake) return packing

        val extraWidth = shakeRandom.nextInt(10).coerceAtMost(maxAtlasSize - packing.atlasWidth)
        val extraHeight = shakeRandom.nextInt(10).coerceAtMost(maxAtlasSize - packing.atlasHeight)
        val extraX = if (extraWidth > 0) shakeRandom.nextInt(extraWidth) else 0
        val extraY = if (extraHeight > 0) shakeRandom.nextInt(extraHeight) else 0
        return Packing(
            packing.atlasWidth + extraWidth,
            packing.atlasHeight + extraHeight,
            packing.entries.map { PackingEntry(it.id, it.x + extraX, it.y + extraY) }
        )
    }

    override fun close() {
        textureAtlasCache.close()
        postProcessingRendererCache.close()
        specialRendererCache.close()
        temporaryTexturesCache.close()
    }
}
