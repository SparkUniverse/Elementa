package gg.essential.elementa.font

import gg.essential.elementa.constraints.SuperConstraint
import gg.essential.universal.UMatrixStack
import java.awt.Color

@JvmDefaultWithCompatibility
interface FontProvider : SuperConstraint<FontProvider> {
    fun getStringWidth(string: String, pointSize: Float): Float

    fun getStringHeight(string: String, pointSize: Float): Float

    // Note: Even though this method has a default implementation, it should in all cases be implemented.
    //       The default implementation exists only for backwards compatibility.
    fun drawString(
        matrixStack: UMatrixStack,
        string: String,
        color: Color,
        x: Float,
        y: Float,
        originalPointSize: Float, //Unused for MC font
        scale: Float,
        shadow: Boolean = true,
        shadowColor: Color? = null
    ): Unit = matrixStack.runWithGlobalState {
        @Suppress("DEPRECATION")
        drawString(string, color, x, y, originalPointSize, scale, shadow, shadowColor)
    }

    @Deprecated(UMatrixStack.Compat.DEPRECATED, ReplaceWith("drawString(matrixStack, string, color, x, y, originalPointSize, scale, shadow, shadowColor)"))
    fun drawString(
        string: String,
        color: Color,
        x: Float,
        y: Float,
        originalPointSize: Float,
        scale: Float,
        shadow: Boolean = true,
        shadowColor: Color? = null
    ): Unit = drawString(
        UMatrixStack(),
        string,
        color,
        x,
        y,
        originalPointSize,
        scale,
        shadow,
        shadowColor
    )

    fun getBaseLineHeight(): Float

    fun getShadowHeight(): Float

    fun getBelowLineHeight(): Float
}
