package gg.essential.elementa.renderer

import gg.essential.universal.render.UGpuTextureView

/**
 * Applies post-processing effects to a subset of rendered elements.
 *
 * @see ElementaExtractor.pushPostProcessing
 */
interface PostProcessingRenderer<T> : AutoCloseable {
    /**
     * Applies the post-processing effect to all given [instances], reading from original rendered elements from the
     * [source] texture and placing the post-processed result in the [destination] texture.
     *
     * Note that this renderer must only draw within the bounds given by
     * [Instance.dstX]/[Instance.dstY]/[Instance.width]/[Instance.height] as there may already be other content in the
     * [destination] texture that must not be overwritten.
     *
     * The renderer may assume that the [destination] texture has been cleared within all bounds given by [instances].
     */
    fun render(destination: UGpuTextureView, source: UGpuTextureView, instances: List<Instance<T>>)

    interface Factory<T> {
        fun create(): PostProcessingRenderer<T>
    }
    class Instance<T>(
        // Bounds in unscaled MC space (same as what [ElementaExtractor] uses)
        val x: Int, val y: Int, val width: Int, val height: Int,
        // Corresponding position in [destination] texture
        val dstX: Int, val dstY: Int,
        // Corresponding position in [source] texture
        val srcX: Int, val srcY: Int,
        val args: T,
    )
}
