package gg.essential.elementa.renderer

import gg.essential.universal.render.UGpuTextureView

/**
 * Renders a special custom GUI component to the supplied texture.
 *
 * @see ElementaExtractor.special
 */
interface SpecialRenderer<T> : AutoCloseable {
    /**
     * Indicates that this renderer will respect the `scissor*` properties of [Instance] and will not draw outside of
     * them.
     *
     * This is generally desirable as it allows the gui renderer to pack instance more compactly.
     *
     * If this is not supported, the gui renderer will usually (provided [onlyDrawsInBounds] is `true`) ask this special
     * renderer to draw the whole component, but then later only copy parts of the resulting texture to the screen.
     * Alternatively it may also ask this renderer to render an instance at a negative position, or with a width that's
     * larger than the output texture; such that the output texture only contains what it actually needs (i.e. it may be
     * using the viewport as a scissor).
     * Which of these strategies it uses is up to the gui renderer implementation and should not be relied upon by this
     * renderer.
     */
    val supportsScissor: Boolean

    /**
     * Whether this renderer can guarantee that it will only draw within the bounds given by
     * [Instance.dstX]/[Instance.dstY]/[Instance.width]/[Instance.height].
     *
     * This is highly desirable as it allows the gui renderer to pack multiple instance into a single output texture,
     * and in turn allows this special renderer to render them all at once.
     *
     * If this is not supported, a dedicated output texture and special renderer will be created for each instance, and
     * [render] will only ever be called with a single [Instance].
     */
    val onlyDrawsInBounds: Boolean

    /**
     * Renders all given instances of this renderer's element type to the [destination] texture.
     *
     * Note that if [onlyDrawsInBounds] is `true`, this renderer must only draw within the bounds given by
     * [Instance.dstX]/[Instance.dstY]/[Instance.width]/[Instance.height] as there may already be other content in the
     * [destination] texture that must not be overwritten.
     *
     * The renderer may assume that the [destination] texture has been cleared within all bounds given by [instances].
     */
    fun render(destination: UGpuTextureView, instances: List<Instance<T>>)

    interface Factory<T> {
        fun create(): SpecialRenderer<T>
    }
    class Instance<T>(
        val dstX: Int, val dstY: Int,
        val width: Int, val height: Int,
        val scissorX: Int, val scissorY: Int,
        val scissorWidth: Int, val scissorHeight: Int,
        val args: T,
    )
}
