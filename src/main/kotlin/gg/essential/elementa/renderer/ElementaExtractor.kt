package gg.essential.elementa.renderer

import gg.essential.elementa.ElementaVersion
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.vertex.UVertexConsumer
import java.awt.Color
import kotlin.math.roundToInt

interface ElementaExtractor {
    val version: ElementaVersion

    val guiScale: Float

    fun pushScissor(x1: Int, y1: Int, x2: Int, y2: Int)
    fun pushScissorRaw(x1: Int, y1: Int, x2: Int, y2: Int)
    fun popScissor()
    fun isVisible(x1: Int, y1: Int, x2: Int, y2: Int): Boolean

    fun fill(
        x1: Int, y1: Int, x2: Int, y2: Int,
        color: Color,
    )
    fun blit(
        x1: Int, y1: Int, x2: Int, y2: Int,
        u1: Float, v1: Float, u2: Float, v2: Float,
        texture: UGpuTextureView, sampler: UGpuSampler,
        /**
         * Whether the content of the given [texture] may change between frames.
         * If this is `true`, the gui renderer may assume that the content cannot change, and may perform optimizations
         * such as dynamically generating a texture atlas containing it rather than reading from the actual texture
         * each frame.
         * Note: For one-off textures that are only used once for a single frame, so technically don't change between
         * frames, it may make sense to nevertheless pass `false` here, so the gui renderer doesn't unnecessarily copy
         * it around.
         */
        textureContentImmutable: Boolean,
        /**
         * Whether the alpha component of [texture] has already been pre-multiplied into its RGB components.
         * This will typically be the case if the texture is the result of a previous render operation; it will
         * typically not be the case when e.g. directly loaded from a PNG file.
         */
        premultipliedAlpha: Boolean,
        /** Multiplied component-wise onto every pixel of [texture] after sampling and before any alpha blending. */
        color: Color,
    )
    fun custom(
        x1: Int, y1: Int, x2: Int, y2: Int,
        pipeline: URenderPipeline,
        textures: List<Pair<UGpuTextureView, UGpuSampler>>,
        /**
         * Specifies the size of the buffer to be allocated.
         * This is an upper bound. It is an error for [build] to emit more vertices than specified here. It is however
         * fine for [build] to emit fewer vertices than specified, but that will waste resources, so should be avoided.
         */
        vertices: Int,
        /**
         * Should emit the vertices for this component to the given [UVertexConsumer].
         * The position passed to [UVertexConsumer.pos] should be in unscaled MC space (same as [x1]/[y1]/[x2]/[y2]).
         *
         * The renderer may, under the hood, modify the position you pass before it gets to your vertex shader.
         * If you're passing positions in other vertex format elements that you then want to compare with the main
         * position in your shader, you'll need to modify those positions accordingly by adding the `offsetX`/`offsetY`
         * offsets to them as well.
         */
        build: (UVertexConsumer, offXset: Int, offsetY: Int) -> Unit,
    )

    /**
     * Uses a [SpecialRenderer] to draw something custom to the given region of the screen.
     *
     * Note that this is substantially more expensive than [custom] because it cannot be part of the same
     * [gg.essential.universal.render.URenderPass] as the regular [fill]/[blit]/[custom] elements, and intermediate
     * textures will need to be allocated for it.
     * As such, it should be avoided if possible.
     * Its main use-case is to interface with third-party rendering code (e.g. drawing a Minecraft entity on screen).
     *
     * The same factory should ideally be re-used between different calls to this method.
     * This allows the renderer to process multiple instances at once and for it to be re-used across frames.
     * To pass data to the renderer which is specific to this call, use the generic [args] parameter.
     */
    fun <T> special(
        x1: Int, y1: Int, x2: Int, y2: Int,
        factory: SpecialRenderer.Factory<T>,
        args: T,
    )

    /**
     * Pushes a [PostProcessingRenderer] to be applied to everything rendered until the corresponding
     * [popPostProcessing].
     *
     * The same factory should ideally be re-used between different calls to this method.
     * This allows the renderer to process multiple instances at once and for it to be re-used across frames.
     * To pass data to the renderer which is specific to this call, use the generic [args] parameter.
     */
    fun <T> pushPostProcessing(factory: PostProcessingRenderer.Factory<T>, args: T)

    /**
     * Ends the top-most post-processing effect.
     *
     * The [factory] passed must exactly match what was supplied to [pushPostProcessing].
     */
    fun popPostProcessing(factory: PostProcessingRenderer.Factory<*>)
}

fun ElementaExtractor.fillMcScale(x1: Float, y1: Float, x2: Float, y2: Float, color: Color) =
    fill((x1 * guiScale).roundToInt(), (y1 * guiScale).roundToInt(), (x2 * guiScale).roundToInt(), (y2 * guiScale).roundToInt(), color)

fun ElementaExtractor.fillXYWH(x: Int, y: Int, w: Int, h: Int, color: Color) =
    fill(x, y, x + w, y + h, color)

fun ElementaExtractor.fillMcScaleXYWH(x: Float, y: Float, w: Float, h: Float, color: Color) =
    fillXYWH((x * guiScale).roundToInt(), (y * guiScale).roundToInt(), (w * guiScale).roundToInt(), (h * guiScale).roundToInt(), color)
