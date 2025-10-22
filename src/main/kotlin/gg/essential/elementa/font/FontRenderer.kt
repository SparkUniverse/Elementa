package gg.essential.elementa.font

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import gg.essential.elementa.font.data.Font
import gg.essential.elementa.font.data.Glyph
import gg.essential.elementa.utils.readFromLegacyShader
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UResolution
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.shader.Float2Uniform
import gg.essential.universal.shader.Float4Uniform
import gg.essential.universal.shader.FloatUniform
import gg.essential.universal.shader.SamplerUniform
import gg.essential.universal.shader.UShader
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * [MSDF](https://github.com/Chlumsky/msdfgen) Font Renderer
 */
@Deprecated("Not well maintained. Does not currently support 1.21.5+ or ElementaVersion.V9 at all.")
@Suppress("DEPRECATION")
class FontRenderer(
    private val regularFont: Font,
    private val boldFont: Font = regularFont
) : FontProvider {
    private var underline: Boolean = false
    private var strikethrough: Boolean = false
    private var bold: Boolean = false
    private var italics: Boolean = false
    private var obfuscated: Boolean = false
    private var textColor: Color? = null
    private var shadowColor: Color? = null
    private var drawingShadow = false
    private var activeFont: Font = regularFont
    override var cachedValue: FontProvider = this
    override var recalculate: Boolean = false
    override var constrainTo: UIComponent? = null

    override fun getStringWidth(string: String, pointSize: Float): Float {
        return getStringInformation(string, pointSize).first
    }

    override fun getStringHeight(string: String, pointSize: Float): Float {
        return getStringInformation(string, pointSize).second
    }

    private fun getStringInformation(string: String, pointSize: Float): Pair<Float, Float> {
        var width = 0f
        var height = 0f
        var currentPointSize = pointSize * 1.3623059867f

        var i = 0
        while (i < string.length) {
            val char = string[i]

            // parse formatting codes
            if (char == '\u00a7' && i + 1 < string.length) {
                val j = ("0123456789abcdefklm" +
                    "nor").indexOf(string[i + 1])
                if (j == 17) {
//                    currentPointSize = pointSize * 1.1081f //Adjust bold being smaller
                    activeFont = boldFont
                } else {
                    currentPointSize = pointSize
                    activeFont = regularFont
                }
                i += 2
                continue
            }

            val glyph = activeFont.fontInfo.glyphs[char.code]
            if (glyph == null) {
                i++
                continue
            }
            val planeBounds = glyph.planeBounds

            if (planeBounds != null) {
                height = max((planeBounds.top - planeBounds.bottom) * currentPointSize, height)
            }

            width += (glyph.advance * currentPointSize)
            i++
        }
        return Pair(width, height)
    }

    fun getLineHeight(pointSize: Float): Float {
        return activeFont.fontInfo.metrics.lineHeight * pointSize
    }

    /**
     * Changes font context. Use 1 for regular and 2 for bold
     */
    private fun switchFont(type: Int) {
        val tmp = activeFont
        when (type) {
            1 -> {
                activeFont = regularFont
            }
            2 -> {
                activeFont = boldFont
            }
        }

        samplerUniform.setValue(activeFont.getTexture().dynamicGlId)

        if (activeFont != tmp) { //Font context switch
            UGraphics.configureTexture(activeFont.getTexture().dynamicGlId) {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            }
            sdfTexel.setValue(1f / activeFont.fontInfo.atlas.width, 1f / activeFont.fontInfo.atlas.height)
        }
    }

    override fun drawString(
        matrixStack: UMatrixStack,
        string: String,
        color: Color,
        x: Float,
        y: Float,
        originalPointSize: Float,
        scale: Float,
        shadow: Boolean,
        shadowColor: Color?
    ) {
        if (ElementaVersion.atLeastV9Active) {
            return
        }
        val effectiveSize = originalPointSize * scale * 1.3623059867f
        val adjustedY = y - effectiveSize / 5
        if (shadow) {
            drawingShadow = true
            val effectiveShadow: Color? = shadowColor
            var baseColor = color.rgb

            if (effectiveShadow == null) {
                if (baseColor and -67108864 == 0) {
                    baseColor = baseColor or -16777216
                }
                baseColor = baseColor and 0xFCFCFC shr 2 or (baseColor and -16777216)
            }
            this.shadowColor = Color(baseColor)
            val shadowOffset = effectiveSize / 10
            matrixStack.translate(shadowOffset, shadowOffset, 0f)
            drawStringNow(matrixStack, string, Color(baseColor), x, adjustedY, effectiveSize)
            matrixStack.translate(-shadowOffset, -shadowOffset, 0f)
        }
        drawingShadow = false
        drawStringNow(matrixStack, string, color, x, adjustedY, effectiveSize)
    }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {

    }

    override fun getBaseLineHeight(): Float {
        return regularFont.fontInfo.atlas.baseCharHeight
    }

    override fun getShadowHeight(): Float {
        return regularFont.fontInfo.atlas.shadowHeight
    }

    override fun getBelowLineHeight(): Float {
        return regularFont.fontInfo.atlas.belowLineHeight
    }
    private fun refreshColor(pointSize: Float) {
        val current = if (drawingShadow) shadowColor else textColor
        val amt = Color.RGBtoHSB(current!!.red, current.green, current.blue, null)[2]
        hintAmountUniform.setValue(0f)
//        subpixelAmountUniform.setValue(0f)

//        if(pointSize < 7) {
//            subpixelAmountUniform.setValue(amt)
//        } else {
//            subpixelAmountUniform.setValue(0f)
//        }
    }

    private fun drawStringNow(matrixStack: UMatrixStack, string: String, color: Color, x: Float, y: Float, originalPointSize: Float) {
        if (!areShadersInitialized() || !shader.usable)
            return

        var currentPointSize = originalPointSize

        shader.bind()
        switchFont(1)
        //shadowColorUniform.setValue(Vector4f(0.1f, 0.1f, 0.1f, 1f))

        doffsetUniform.setValue(3.5f / currentPointSize)


        val guiScale = UResolution.scaleFactor.toFloat()

        //Reset
        obfuscated = false
        bold = false
        italics = false
        strikethrough = false
        underline = false
        textColor = color

        refreshColor(originalPointSize)

        var currentX = x
        var i = 0
        while (i < string.length) {
            //for (i in string.indices) {
            //string.forEach { char ->
            val char = string[i]

            // parse formatting codes
            if (char == '\u00a7' && i + 1 < string.length) {
                val j = "0123456789abcdefklmnor".indexOf(string[i + 1])
                when {
                    j < 16 -> {
                        switchFont(1)
                        obfuscated = false
                        bold = false
                        italics = false
                        strikethrough = false
                        underline = false
                        if (j < 0) {
                            textColor = colors[15]
                            shadowColor = colors[31]
                        } else {
                            textColor = colors[j]
                            shadowColor = colors[j + 16]
                        }
                        currentPointSize = originalPointSize
                        doffsetUniform.setValue(3.5f / currentPointSize)
                        refreshColor(originalPointSize)
                    }
                    j == 16 -> obfuscated = true
                    j == 17 -> {
                        switchFont(2)
//                        currentPointSize = originalPointSize * 1.1081f //Adjust bold being smaller
                        doffsetUniform.setValue(3.5f / currentPointSize)
                        bold = true
                    }
                    j == 18 -> strikethrough = true
                    j == 19 -> underline = true
                    j == 20 -> italics = true
                    else -> {
                        currentPointSize = originalPointSize
                        switchFont(1)
                        doffsetUniform.setValue(3.5f / currentPointSize)
                        obfuscated = false
                        bold = false
                        italics = false
                        strikethrough = false
                        underline = false
                        textColor = color
                        shadowColor
                        refreshColor(originalPointSize)
                    }
                }
                i += 2
                continue
            }


            var glyph = activeFont.fontInfo.glyphs[char.code]
            if (glyph == null) {
                i++
                continue
            }

            if (obfuscated && char != ' ') {
                val advance = glyph.advance

                for (iter in 1..100) { //100 tries max

                    val tmp = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".random()
                    if (advance == activeFont.fontInfo.glyphs[tmp.code]?.advance ?: continue) {
                        glyph = activeFont.fontInfo.glyphs[tmp.code]
                        break
                    }
                }
            }
            if (glyph == null) {
                i++
                continue
            }
            val planeBounds = glyph.planeBounds

            if (planeBounds != null) {
                val metrics = activeFont.fontInfo.metrics
                val baseline = y + ((metrics.lineHeight + metrics.descender) * currentPointSize)

                val hintedBaseline = floor(baseline * guiScale) / guiScale

                val width = (planeBounds.right - planeBounds.left) * currentPointSize
                val height = (planeBounds.top - planeBounds.bottom) * currentPointSize

                val hintedHeight = ceil(height * guiScale) / guiScale
                val hintedY = hintedBaseline - ceil(planeBounds.top * currentPointSize * guiScale) / guiScale

                drawGlyph(
                    matrixStack,
                    glyph,
                    color,
                    currentX,
                    hintedY,
                    width,
                    hintedHeight
                )
            }

            currentX += (glyph.advance * currentPointSize)
            i++
        }

        shader.unbind()
        activeFont = boldFont
    }


    private fun drawGlyph(matrixStack: UMatrixStack, glyph: Glyph, color: Color, x: Float, y: Float, width: Float, height: Float) {
        val atlasBounds = glyph.atlasBounds ?: return
        val atlas = activeFont.fontInfo.atlas
        val textureTop = 1.0 - (atlasBounds.top / atlas.height)
        val textureBottom = 1.0 - (atlasBounds.bottom / atlas.height)
        val textureLeft = (atlasBounds.left / atlas.width).toDouble()
        val textureRight = (atlasBounds.right / atlas.width).toDouble()

        val drawColor = if (drawingShadow) (shadowColor ?: color) else (textColor ?: color)
        fgColorUniform.setValue(
            drawColor.red / 255f,
            drawColor.green / 255f,
            drawColor.blue / 255f,
            1f
        )
        val worldRenderer = UGraphics.getFromTessellator()
        worldRenderer.beginWithActiveShader(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE)
        val doubleX = x.toDouble()
        val doubleY = y.toDouble()
        worldRenderer.pos(matrixStack, doubleX, doubleY + height, 0.0).tex(textureLeft, textureBottom).endVertex()
        worldRenderer.pos(matrixStack, doubleX + width, doubleY + height, 0.0).tex(textureRight, textureBottom).endVertex()
        worldRenderer.pos(matrixStack, doubleX + width, doubleY, 0.0).tex(textureRight, textureTop).endVertex()
        worldRenderer.pos(matrixStack, doubleX, doubleY, 0.0).tex(textureLeft, textureTop).endVertex()
        worldRenderer.drawDirect()

//        if (underline) {
//            TODO()
//        }
//        if (bold) {
//            TODO()
//        }
//        if (italics) {
//            TODO()
//        }
    }

    companion object {
        // trusting these are right... thank you minecraft wiki
        private val colors: Map<Int, Color> = mapOf(
            0 to Color.BLACK,
            1 to Color(0, 0, 170),
            2 to Color(0, 170, 0),
            3 to Color(0, 170, 170),
            4 to Color(170, 0, 0),
            5 to Color(170, 0, 170),
            6 to Color(255, 170, 0),
            7 to Color(170, 170, 170),
            8 to Color(85, 85, 85),
            9 to Color(85, 85, 255),
            10 to Color(85, 255, 85),
            11 to Color(85, 255, 255),
            12 to Color(255, 85, 85),
            13 to Color(255, 85, 255),
            14 to Color(255, 255, 85),
            15 to Color(255, 255, 255),
            // shadows for (i - 16)
            16 to Color.BLACK,
            17 to Color(0, 0, 42),
            18 to Color(0, 42, 0),
            19 to Color(0, 42, 42),
            20 to Color(42, 0, 0),
            21 to Color(42, 0, 42),
            22 to Color(42, 42, 0),
            23 to Color(42, 42, 42),
            24 to Color(21, 21, 21),
            25 to Color(21, 21, 63),
            26 to Color(21, 63, 21),
            27 to Color(21, 63, 63),
            28 to Color(63, 21, 21),
            29 to Color(63, 21, 63),
            30 to Color(63, 63, 21),
            31 to Color(63, 63, 63)
        )

        internal lateinit var shader: UShader
        internal lateinit var samplerUniform: SamplerUniform
        internal lateinit var doffsetUniform: FloatUniform
        internal lateinit var hintAmountUniform: FloatUniform
        internal lateinit var subpixelAmountUniform: FloatUniform
        internal lateinit var sdfTexel: Float2Uniform
        internal lateinit var fgColorUniform: Float4Uniform
        //internal lateinit var shadowColorUniform: Float4Uniform

        fun areShadersInitialized() = ::shader.isInitialized

        fun initShaders() {
            if (URenderPipeline.isRequired) return
            if (areShadersInitialized())
                return

            @Suppress("DEPRECATION")
            shader = UShader.readFromLegacyShader("font", "font", BlendState.NORMAL)
            if (!shader.usable) {
                println("Failed to load Elementa font shader")
                return
            }
            samplerUniform = shader.getSamplerUniform("msdf")
            doffsetUniform = shader.getFloatUniform("doffset")
            hintAmountUniform = shader.getFloatUniform("hint_amount")
            // subpixelAmountUniform = shader.getFloatUniform("subpixel_amount")
            sdfTexel = shader.getFloat2Uniform("sdf_texel")
            fgColorUniform = shader.getFloat4Uniform("fgColor")
            //shadowColorUniform = shader.getFloat4Uniform("shadowColor")
        }
    }
}