package gg.essential.elementa.renderer.impl

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.ElementaRenderState
import gg.essential.elementa.renderer.PostProcessingRenderer
import gg.essential.elementa.renderer.SpecialRenderer
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.vertex.UVertexConsumer
import java.awt.Color
import kotlin.math.roundToInt

internal class ElementaExtractorImpl(
    val screenSize: Rect,
    override val guiScale: Float,
) : ElementaExtractor {
    override val version: ElementaVersion
        get() = ElementaVersion.V11

    private val scissorStack = mutableListOf(screenSize)
    private val postProcessingStack = mutableListOf<Pair<PostProcessingElement<*>, MutableList<Element>>>()
    private var elements = mutableListOf<Element>()

    fun finish(): ElementaRenderState {
        check(scissorStack.size == 1) { "Unbalanced push/popScissor" }
        check(postProcessingStack.isEmpty()) { "Unbalanced push/popPostProcessing" }

        return ElementaRenderState(screenSize.w, screenSize.h, guiScale, elements)
    }

    override fun pushScissor(x1: Int, y1: Int, x2: Int, y2: Int) {
        scissorStack.add(Rect.ltrbChecked(x1, y1, x2, y2).intersection(scissorStack.lastOrNull() ?: screenSize))
    }

    override fun pushScissorRaw(x1: Int, y1: Int, x2: Int, y2: Int) {
        val rect = Rect.ltrbChecked(x1, y1, x2, y2)
        require(rect in screenSize) { "$rect is out of bounds $screenSize"}
        scissorStack.add(rect)
    }

    override fun popScissor() {
        check(scissorStack.size > 1) { "Unbalanced push/popScissor" }
        scissorStack.removeLast()
    }

    override fun isVisible(x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        return scissorStack.last().intersects(Rect(x1, y1, x2, y2))
    }

    private fun addElement(element: Element) {
        if (element.bounds.isEmpty()) return
        elements.add(element)
    }

    override fun fill(
        x1: Int, y1: Int, x2: Int, y2: Int,
        color: Color,
    ) = addElement(
        ColoredElement(
            Rect.ltrbChecked(x1, y1, x2, y2),
            scissorStack.last(),
            color,
        )
    )

    override fun blit(
        x1: Int, y1: Int, x2: Int, y2: Int,
        u1: Float, v1: Float, u2: Float, v2: Float,
        texture: UGpuTextureView, sampler: UGpuSampler,
        textureContentImmutable: Boolean, premultipliedAlpha: Boolean,
        color: Color,
    ) = addElement(
        TexturedElement(
            Rect.ltrbChecked(x1, y1, x2, y2),
            scissorStack.last(),
            texture,
            sampler,
            textureContentImmutable,
            premultipliedAlpha,
            color,
            u1, v1, u2, v2,
        )
    )

    override fun custom(
        x1: Int, y1: Int, x2: Int, y2: Int,
        pipeline: URenderPipeline,
        textures: List<Pair<UGpuTextureView, UGpuSampler>>,
        vertices: Int,
        build: (UVertexConsumer, Int, Int) -> Unit,
    ) = addElement(
        CustomElement(
            Rect.ltrbChecked(x1, y1, x2, y2),
            scissorStack.last(),
            pipeline,
            textures,
            vertices,
            build,
        )
    )

    override fun <T> special(
        x1: Int, y1: Int, x2: Int, y2: Int,
        factory: SpecialRenderer.Factory<T>,
        args: T,
    ) = addElement(
        SpecialElement(
            Rect.ltrbChecked(x1, y1, x2, y2),
            scissorStack.last(),
            factory,
            args,
        )
    )

    override fun <T> pushPostProcessing(factory: PostProcessingRenderer.Factory<T>, args: T) {
        val element = PostProcessingElement(
            Rect(0, 0, 0, 0), // proper value filled by [popPostProcessing]
            scissorStack.last(),
            factory,
            args,
            emptyList(), // proper value filled by [popPostProcessing]
        )
        postProcessingStack.add(Pair(element, elements))
        elements = mutableListOf()
    }

    override fun popPostProcessing(factory: PostProcessingRenderer.Factory<*>) {
        val (element, orgElements) = postProcessingStack.removeLast()
        check(element.factory === factory) { "Attempted to pop $factory but top-most was ${element.factory}" }
        val innerElements = elements
        elements = orgElements
        addElement(element.copyWith(computeBounds(innerElements), innerElements))
    }
}

internal fun ElementaExtractor.isVisible(component: UIComponent): Boolean =
    isVisible(
        (component.getLeft() * guiScale).roundToInt(),
        (component.getTop() * guiScale).roundToInt(),
        (component.getRight() * guiScale).roundToInt(),
        (component.getBottom() * guiScale).roundToInt(),
    )
