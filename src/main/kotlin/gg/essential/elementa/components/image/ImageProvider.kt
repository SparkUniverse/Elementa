package gg.essential.elementa.components.image

import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.universal.UMatrixStack
import java.awt.Color
import kotlin.math.roundToInt

@JvmDefaultWithCompatibility
interface ImageProvider {
    /**
     * Extracts the image provided by this component with the provided attributes for later rendering.
     *
     * This method is guaranteed to be called from the main thread.
     *
     * Even though this method has a default implementation, it should in all cases be implemented.
     * The default implementation exists only for backwards compatibility.
     */
    fun extract(extractor: ElementaExtractor, x: Int, y: Int, width: Int, height: Int, color: Color) {}

    /**
     * Render the image provided by this component with the provided attributes.
     *
     * This method is guaranteed to be called from the main thread.
     *
     * Even though this method has a default implementation, it should in all cases be implemented.
     * The default implementation exists only for backwards compatibility.
     */
    @Deprecated(
        "`draw`-style rendering is deprecated. Use `extract` instead.",
        replaceWith = ReplaceWith("extractMcScale(extractor, x, y, width, height, color)")
    )
    fun drawImage(matrixStack: UMatrixStack, x: Double, y: Double, width: Double, height: Double, color: Color)
            = matrixStack.runWithGlobalState { @Suppress("DEPRECATION") drawImageCompat(UMatrixStack(), x, y, width, height, color) }

    @Deprecated(UMatrixStack.Compat.DEPRECATED, ReplaceWith("drawImage(matrixStack, x, y, width, height, color)"))
    fun drawImage(x: Double, y: Double, width: Double, height: Double, color: Color): Unit =
        @Suppress("DEPRECATION")
        drawImage(UMatrixStack.Compat.get(), x, y, width, height, color)

    fun drawImageCompat(matrixStack: UMatrixStack, x: Double, y: Double, width: Double, height: Double, color: Color): Unit =
        UMatrixStack.Compat.runLegacyMethod(matrixStack) { @Suppress("DEPRECATION") drawImage(x, y, width, height, color) }
}

fun ImageProvider.extractMcScale(extractor: ElementaExtractor, x: Float, y: Float, width: Float, height: Float, color: Color) =
    extract(
        extractor,
        (x * extractor.guiScale).roundToInt(),
        (y * extractor.guiScale).roundToInt(),
        (width * extractor.guiScale).roundToInt(),
        (height * extractor.guiScale).roundToInt(),
        color,
    )
