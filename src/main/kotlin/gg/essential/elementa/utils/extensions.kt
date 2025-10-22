package gg.essential.elementa.utils

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.Window
import gg.essential.universal.UGraphics.CommonVertexFormats
import gg.essential.universal.UResolution
import gg.essential.universal.shader.BlendState
import gg.essential.universal.shader.UShader
import java.awt.Color
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sign

fun Float.guiHint(roundDown: Boolean) = UIComponent.guiHint(this, roundDown)
fun Double.guiHint(roundDown: Boolean) = UIComponent.guiHint(this, roundDown)

fun Float.roundToRealPixels(): Float {
    val factor = UResolution.scaleFactor.toFloat()
    return round(this * factor).let { if (it == 0f && abs(this) > 0.001f) sign(this) else it } / factor
}
fun Double.roundToRealPixels(): Double {
    val factor = UResolution.scaleFactor
    return round(this * factor).let { if (it == 0.0 && abs(this) > 0.001) sign(this) else it } / factor
}

fun Color.withAlpha(alpha: Int) = Color(this.red, this.green, this.blue, alpha)
fun Color.withAlpha(alpha: Float) = Color(this.red, this.green, this.blue, (alpha * 255).toInt())
fun Color.invisible() = withAlpha(0)

operator fun Color.component1() = this.red
operator fun Color.component2() = this.green
operator fun Color.component3() = this.blue
operator fun Color.component4() = this.alpha

@Deprecated("Stops working in 1.21.5, see UGraphics.Globals")
@Suppress("DEPRECATION")
internal fun UShader.Companion.readFromLegacyShader(vertName: String, fragName: String, blendState: BlendState) =
    fromLegacyShader(readElementaShaderSource(vertName, "vsh"), readElementaShaderSource(fragName, "fsh"), blendState)

internal fun readElementaShaderSource(name: String, ext: String) =
    Window::class.java.getResource("/shaders/$name.$ext").readText()
