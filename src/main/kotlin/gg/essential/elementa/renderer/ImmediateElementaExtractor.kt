package gg.essential.elementa.renderer

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UResolution
import gg.essential.universal.render.UGpuFormat
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuTexture
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UBufferBuilder
import gg.essential.universal.vertex.UVertexConsumer
import java.awt.Color

/**
 * This implementation of [ElementaExtractor] which directly draws to the global render target.
 *
 * Its intended use case is for components which should support both [UIComponent.extractComponent] and
 * [UIComponent.draw] for backwards compatibility, but for which both implementations essentially do the same thing.
 * Such components can then implement only [UIComponent.extractComponent] and forward [UIComponent.draw] to
 * [UIComponent.extractComponent] using an instance of this class as the extractor.
 *
 * Note that while this class will usually produce identical results as the legacy code, not all of its methods are
 * strictly identical.
 * This may be important for cases where strict backwards compatibility is required, e.g. where callers may have
 * previously relied on setting up certain global OpenGL state before calling `draw`. Such side-effect behavior is not
 * re-produced by this class. For such cases, both legacy and new implementation should be kept separately.
 * Only [ElementaExtractor.fill] is guaranteed to be exactly equal to [UIBlock.drawBlock].
 */
class ImmediateElementaExtractor(val matrixStack: UMatrixStack) : ElementaExtractor {
    override val version: ElementaVersion
        get() = ElementaVersion.V11
    override val guiScale: Float = UResolution.scaleFactor.toFloat()
    private val invScale: Double = 1.0 / guiScale

    // Not yet implemented because restoring isn't trivial, and doesn't really fit into the use-case of this class anyway
    override fun pushScissor(x1: Int, y1: Int, x2: Int, y2: Int) = TODO("Not yet implemented")
    override fun pushScissorRaw(x1: Int, y1: Int, x2: Int, y2: Int) = TODO("Not yet implemented")
    override fun popScissor() = TODO("Not yet implemented")
    override fun isVisible(x1: Int, y1: Int, x2: Int, y2: Int) = true

    override fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Color) {
        // Note: We're explicitly not early returning on color.alpha == 0 here to honor the class contract.
        UIBlock.drawBlock(matrixStack, color, x1 * invScale, y1 * invScale, x2 * invScale, y2 * invScale)
    }

    override fun blit(
        x1: Int, y1: Int, x2: Int, y2: Int,
        u1: Float, v1: Float, u2: Float, v2: Float,
        texture: UGpuTextureView, sampler: UGpuSampler,
        textureContentImmutable: Boolean, premultipliedAlpha: Boolean,
        color: Color,
    ) {
        if (premultipliedAlpha) {
            if (color.rgb == 0) return
        } else {
            if (color.alpha == 0) return
        }

        val pipeline = if (premultipliedAlpha) PIPELINE_TEXTURE_PREMULTIPLIED_ALPHA else PIPELINE_TEXTURE

        val bufferBuilder = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR)
        bufferBuilder.pos(matrixStack, x1 * invScale, y2 * invScale, 0.0).tex(u1.toDouble(), v2.toDouble()).color(color).endVertex()
        bufferBuilder.pos(matrixStack, x2 * invScale, y2 * invScale, 0.0).tex(u2.toDouble(), v2.toDouble()).color(color).endVertex()
        bufferBuilder.pos(matrixStack, x2 * invScale, y1 * invScale, 0.0).tex(u2.toDouble(), v1.toDouble()).color(color).endVertex()
        bufferBuilder.pos(matrixStack, x1 * invScale, y1 * invScale, 0.0).tex(u1.toDouble(), v1.toDouble()).color(color).endVertex()
        bufferBuilder.build()?.drawAndClose(pipeline) {
            texture(0, texture, sampler)
        }
    }

    override fun custom(
        x1: Int, y1: Int, x2: Int, y2: Int,
        pipeline: URenderPipeline,
        textures: List<Pair<UGpuTextureView, UGpuSampler>>,
        vertices: Int,
        build: (UVertexConsumer, Int, Int) -> Unit,
    ) {
        val drawMode = pipeline.drawMode
        val format = pipeline.commonVertexFormat ?: throw UnsupportedOperationException("Only pipelines with CommonVertexFormats are supported.")
        val bufferBuilder = UBufferBuilder.create(drawMode, format)
        build(bufferBuilder, 0, 0)

        matrixStack.push()
        matrixStack.scale(invScale, invScale, 1.0)
        matrixStack.runWithGlobalState {
            bufferBuilder.build()?.drawAndClose(pipeline) {
                for ((i, attachment) in textures.withIndex()) {
                    val (textureView, sampler) = attachment
                    texture(i, textureView, sampler)
                }
            }
        }
        matrixStack.pop()
    }

    override fun <T> special(
        x1: Int, y1: Int, x2: Int, y2: Int,
        factory: SpecialRenderer.Factory<T>,
        args: T,
    ) {
        val w = x2 - x1
        val h = y2 - y1
        if (w <= 0 || h <= 0) return

        val device = UGraphics.getDevice()
        val texture = device.createTexture(
            null,
            UGpuTexture.Usage.COPY_DST + UGpuTexture.Usage.TEXTURE_BINDING + UGpuTexture.Usage.RENDER_ATTACHMENT,
            UGpuFormat.DEFAULT_RGBA,
            w,
            h,
            1,
        )
        val textureView = device.createTextureView(texture, 0, 1)
        try {
            device.clearColor(texture, 0f, 0f, 0f, 0f)

            factory.create().use { renderer ->
                renderer.render(textureView, listOf(
                    SpecialRenderer.Instance(
                        0, 0, w, h,
                        0, 0, w, h,
                        args,
                    )
                ))
            }

            blit(x1, y1, x2, y2, 0f, 1f, 1f, 0f, textureView, SAMPLER_NEAREST, false, true, Color.WHITE)
        } finally {
            textureView.close()
            texture.close()
        }
    }

    override fun <T> pushPostProcessing(factory: PostProcessingRenderer.Factory<T>, args: T) =
        throw UnsupportedOperationException()
    override fun popPostProcessing(factory: PostProcessingRenderer.Factory<*>) =
        throw UnsupportedOperationException()

    private companion object {
        private val PIPELINE_TEXTURE = URenderPipeline.builderWithDefaultShader(
            "elementa:immediate/texture",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR,
        ).apply {
            blendState = BlendState.ALPHA
            depthTest = URenderPipeline.DepthTest.Always
        }.build()

        private val PIPELINE_TEXTURE_PREMULTIPLIED_ALPHA = URenderPipeline.builderWithDefaultShader(
            "elementa:immediate/texture_premultiplied_alpha",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR,
        ).apply {
            blendState = BlendState.PREMULTIPLIED_ALPHA
            depthTest = URenderPipeline.DepthTest.Always
        }.build()

        private val SAMPLER_NEAREST = UGpuSampler(
            UGpuSampler.AddressMode.CLAMP_TO_EDGE,
            UGpuSampler.AddressMode.CLAMP_TO_EDGE,
            UGpuSampler.FilterMode.NEAREST,
            UGpuSampler.FilterMode.NEAREST,
            false,
        )
    }
}