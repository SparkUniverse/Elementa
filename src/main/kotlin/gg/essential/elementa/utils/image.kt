package gg.essential.elementa.utils

import gg.essential.elementa.ElementaVersion
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.utils.ReleasedDynamicTexture
import gg.essential.universal.vertex.UBufferBuilder
import gg.essential.universal.vertex.UVertexConsumer
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.withSign

internal fun drawTexture(
    matrixStack: UMatrixStack,
    texture: ReleasedDynamicTexture,
    color: Color,
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    textureMinFilter: Int = GL11.GL_NEAREST,
    textureMagFilter: Int = GL11.GL_NEAREST
) {
    matrixStack.push()

    matrixStack.scale(1f, 1f, 50f)
    val glId = texture.dynamicGlId
    val red = color.red.toFloat() / 255f
    val green = color.green.toFloat() / 255f
    val blue = color.blue.toFloat() / 255f
    val alpha = color.alpha.toFloat() / 255f
    UGraphics.configureTexture(glId) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureMinFilter)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, textureMagFilter)
    }

    fun drawTexturedQuad(vertexConsumer: UVertexConsumer) {
        vertexConsumer.pos(matrixStack, x, y + height, 0.0).tex(0.0, 1.0).color(red, green, blue, alpha).endVertex()
        vertexConsumer.pos(matrixStack, x + width, y + height, 0.0).tex(1.0, 1.0).color(red, green, blue, alpha).endVertex()
        vertexConsumer.pos(matrixStack, x + width, y, 0.0).tex(1.0, 0.0).color(red, green, blue, alpha).endVertex()
        vertexConsumer.pos(matrixStack, x, y, 0.0).tex(0.0, 0.0).color(red, green, blue, alpha).endVertex()
    }

    if (URenderPipeline.isRequired || ElementaVersion.atLeastV9Active) {
        val bufferBuilder = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR)
        drawTexturedQuad(bufferBuilder)
        bufferBuilder.build()?.drawAndClose(if (ElementaVersion.atLeastV10Active) TEXTURED_QUAD_PIPELINE2 else TEXTURED_QUAD_PIPELINE) {
            texture(0, glId)
        }
    } else {
        @Suppress("DEPRECATION")
        UGraphics.enableBlend()
        UGraphics.enableAlpha()
        UGraphics.bindTexture(0, glId)
        val worldRenderer = UGraphics.getFromTessellator()
        @Suppress("DEPRECATION")
        worldRenderer.beginWithDefaultShader(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR)
        drawTexturedQuad(worldRenderer.asUVertexConsumer())
        worldRenderer.drawDirect()
    }

    matrixStack.pop()
}

private val TEXTURED_QUAD_PIPELINE = URenderPipeline.builderWithDefaultShader(
    "elementa:textured_quad",
    UGraphics.DrawMode.QUADS,
    UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR,
).apply {
    @Suppress("DEPRECATION")
    blendState = BlendState.NORMAL
}.build()

private val TEXTURED_QUAD_PIPELINE2 = URenderPipeline.builderWithDefaultShader(
    "elementa:textured_quad",
    UGraphics.DrawMode.QUADS,
    UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR,
).apply {
    blendState = BlendState.ALPHA
}.build()

fun decodeBlurHash(blurHash: String?, width: Int, height: Int, punch: Float = 1f): BufferedImage? {
    if (blurHash == null || blurHash.length < 6) return null

    val numCompEnc = decode83(blurHash, 0, 1)
    val numCompX = (numCompEnc % 9) + 1
    val numCompY = (numCompEnc / 9) + 1

    if (blurHash.length != 4 + 2 * numCompX * numCompY) return null

    val maxAcEnc = decode83(blurHash, 1, 2)
    val maxAc = (maxAcEnc + 1) / 166f
    val colors = Array(numCompX * numCompY) { i ->
        if (i == 0) {
            val colorEnc = decode83(blurHash, 2, 6)
            decodeDc(colorEnc)
        } else {
            val from = 4 + i * 2
            val colorEnc = decode83(blurHash, from, from + 2)
            decodeAc(colorEnc, maxAc * punch)
        }
    }

    return composeBitmap(width, height, numCompX, numCompY, colors)
}

private fun decode83(str: String, from: Int = 0, to: Int = str.length): Int {
    var result = 0
    for (i in from until to) {
        val index = charMap[str[i]] ?: -1

        if (index != -1) {
            result = result * 83 + index
        }
    }
    return result
}

private fun decodeDc(colorEnc: Int): FloatArray {
    val r = colorEnc shr 16
    val g = (colorEnc shr 8) and 255
    val b = colorEnc and 255
    return floatArrayOf(srgbToLinear(r), srgbToLinear(g), srgbToLinear(b))
}

private fun srgbToLinear(colorEnc: Int): Float {
    val v = colorEnc / 255f
    return if (v <= 0.04045f) {
        (v / 12.92f)
    } else {
        ((v + 0.055f) / 1.055f).pow(2.4f)
    }
}

private fun decodeAc(value: Int, maxAc: Float): FloatArray {
    val r = value / (19 * 19)
    val g = (value / 19) % 19
    val b = value % 19
    return floatArrayOf(
        signedPow2((r - 9) / 9.0f) * maxAc,
        signedPow2((g - 9) / 9.0f) * maxAc,
        signedPow2((b - 9) / 9.0f) * maxAc
    )
}

private fun signedPow2(value: Float) = value.pow(2f).withSign(value)

private fun composeBitmap(
    width: Int, height: Int,
    numCompX: Int, numCompY: Int,
    colors: Array<FloatArray>
): BufferedImage {
    val bitmap = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var r = 0f
            var g = 0f
            var b = 0f
            for (j in 0 until numCompY) {
                for (i in 0 until numCompX) {
                    val basis = (cos(PI * x * i / width) * cos(PI * y * j / height)).toFloat()
                    val color = colors[j * numCompX + i]
                    r += color[0] * basis
                    g += color[1] * basis
                    b += color[2] * basis
                }
            }
            bitmap.setRGB(x, y, ((255 shl 24) + (linearToSrgb(r) shl 16) + (linearToSrgb(g) shl 8) + linearToSrgb(b)))
        }
    }
    return bitmap
}

private fun linearToSrgb(value: Float): Int {
    val v = value.coerceIn(0f, 1f)
    return if (v <= 0.0031308f) {
        (v * 12.92f * 255f + 0.5f).toInt()
    } else {
        ((1.055f * v.pow(1 / 2.4f) - 0.055f) * 255 + 0.5f).toInt()
    }
}

private val charMap = listOf(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G',
    'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
    'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
    'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '#', '$', '%', '*', '+', ',',
    '-', '.', ':', ';', '=', '?', '@', '[', ']', '^', '_', '{', '|', '}', '~'
).mapIndexed { i, c -> c to i }.toMap()
