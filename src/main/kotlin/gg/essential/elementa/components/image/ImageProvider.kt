package gg.essential.elementa.components.image

import gg.essential.universal.UMatrixStack
import java.awt.Color

@JvmDefaultWithCompatibility
interface ImageProvider {
    /**
     * Render the image provided by this component with the provided attributes.
     *
     * This method is guaranteed to be called from the main thread.
     *
     * Even though this method has a default implementation, it should in all cases be implemented.
     * The default implementation exists only for backwards compatibility.
     */
    fun drawImage(matrixStack: UMatrixStack, x: Double, y: Double, width: Double, height: Double, color: Color)
            = matrixStack.runWithGlobalState { @Suppress("DEPRECATION") drawImageCompat(UMatrixStack(), x, y, width, height, color) }

    @Deprecated(UMatrixStack.Compat.DEPRECATED, ReplaceWith("drawImage(matrixStack, x, y, width, height, color)"))
    fun drawImage(x: Double, y: Double, width: Double, height: Double, color: Color): Unit =
        drawImage(UMatrixStack.Compat.get(), x, y, width, height, color)

    fun drawImageCompat(matrixStack: UMatrixStack, x: Double, y: Double, width: Double, height: Double, color: Color): Unit =
        UMatrixStack.Compat.runLegacyMethod(matrixStack) { @Suppress("DEPRECATION") drawImage(x, y, width, height, color) }
}
