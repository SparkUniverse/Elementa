package gg.essential.elementa.components

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.toConstraint
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.utils.readElementaShaderSource
import gg.essential.elementa.utils.readFromLegacyShader
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.UGpuFormat
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuTexture
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.shader.Float2Uniform
import gg.essential.universal.shader.FloatUniform
import gg.essential.universal.shader.UShader
import gg.essential.universal.vertex.UBufferBuilder
import org.intellij.lang.annotations.Language
import java.awt.Color
import kotlin.math.roundToInt

/**
 * Simple component that uses shaders to draw a circle. This component
 * takes a radius instead of a height/width!
 *
 * @param radius circle radius
 * @param color circle color
 * @param steps unused, kept for backwards compatibility
 */
class UICircle @JvmOverloads constructor(radius: Float = 0f, color: Color = Color.WHITE, var steps: Int = 0) :
    UIComponent() {
    init {
        setColor(color.toConstraint())
        setRadius(radius.pixels())
    }

    override fun getLeft(): Float {
        return constraints.getX() - getRadius()
    }

    override fun getTop(): Float {
        return constraints.getY() - getRadius()
    }

    override fun getWidth(): Float {
        return getRadius() * 2
    }

    override fun getHeight(): Float {
        return getRadius() * 2
    }

    override fun isPositionCenter(): Boolean {
        return true
    }

    override fun extractComponent(extractor: ElementaExtractor) {
        extractCircle(
            extractor,
            constraints.getX(),
            constraints.getY(),
            getRadius(),
            getColor(),
        )
    }

    @Deprecated(
        "`draw`-style rendering is deprecated. Override `extractComponent` instead. Call `extract` to extract this component, its effects, and its children.",
        replaceWith = ReplaceWith("extract(extractor)")
    )
    @Suppress("DEPRECATION")
    override fun draw(matrixStack: UMatrixStack) {
        beforeDraw(matrixStack)

        val x = constraints.getX()
        val y = constraints.getY()
        val r = getRadius()

        val color = getColor()
        if (color.alpha == 0) return super.draw(matrixStack)

        drawCircle(matrixStack, x, y, r, color)

        super.draw(matrixStack)
    }

    companion object {
        private lateinit var shader: UShader
        private lateinit var shaderRadiusUniform: FloatUniform
        private lateinit var shaderCenterPositionUniform: Float2Uniform

        private val PIPELINE = URenderPipeline.builderWithLegacyShader(
            "elementa:circle",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
            readElementaShaderSource("rect", "vsh"),
            readElementaShaderSource("circle", "fsh"),
        ).apply {
            @Suppress("DEPRECATION")
            blendState = BlendState.NORMAL
            depthTest = URenderPipeline.DepthTest.Always // see UIBlock.PIPELINE
        }.build()

        private val PIPELINE2 = URenderPipeline.builderWithLegacyShader(
            "elementa:circle",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
            readElementaShaderSource("rect", "vsh"),
            readElementaShaderSource("circle", "fsh"),
        ).apply {
            blendState = BlendState.ALPHA
            depthTest = URenderPipeline.DepthTest.Always // see UIBlock.PIPELINE
        }.build()

        fun initShaders() {
            if (URenderPipeline.isRequired) return
            if (::shader.isInitialized)
                return

            @Suppress("DEPRECATION")
            shader = UShader.readFromLegacyShader("rect", "circle", BlendState.NORMAL)
            if (!shader.usable) {
                println("Failed to load Elementa UICircle shader")
                return
            }
            shaderRadiusUniform = shader.getFloatUniform("u_Radius")
            shaderCenterPositionUniform = shader.getFloat2Uniform("u_CenterPos")
        }

        @Deprecated(
            UMatrixStack.Compat.DEPRECATED,
            ReplaceWith("drawCircle(matrixStack, centerX, centerY, radius, color)"),
        )
        fun drawCircle(centerX: Float, centerY: Float, radius: Float, color: Color) =
            drawCircle(UMatrixStack(), centerX, centerY, radius, color)

        fun drawCircle(matrixStack: UMatrixStack, centerX: Float, centerY: Float, radius: Float, color: Color) {
            if (!URenderPipeline.isRequired && !ElementaVersion.atLeastV9Active) {
                @Suppress("DEPRECATION")
                return drawCircleLegacy(matrixStack, centerX, centerY, radius, color)
            }

            val bufferBuilder = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)
            UIBlock.drawBlock(
                bufferBuilder,
                matrixStack,
                color,
                (centerX - radius).toDouble(),
                (centerY - radius).toDouble(),
                (centerX + radius).toDouble(),
                (centerY + radius).toDouble()
            )
            bufferBuilder.build()?.drawAndClose(if (ElementaVersion.atLeastV10Active) PIPELINE2 else PIPELINE) {
                uniform("u_Radius", radius)
                uniform("u_CenterPos", centerX, centerY)
            }
        }

        @Deprecated("Stops working in 1.21.5")
        @Suppress("DEPRECATION")
        private fun drawCircleLegacy(matrixStack: UMatrixStack, centerX: Float, centerY: Float, radius: Float, color: Color) {
            if (!::shader.isInitialized || !shader.usable)
                return

            shader.bind()
            shaderRadiusUniform.setValue(radius)
            shaderCenterPositionUniform.setValue(centerX, centerY)

            UIBlock.drawBlockWithActiveShader(
                matrixStack,
                color,
                (centerX - radius).toDouble(),
                (centerY - radius).toDouble(),
                (centerX + radius).toDouble(),
                (centerY + radius).toDouble()
            )

            shader.unbind()
        }

        @Language("GLSL")
        private val vertSource = """
            varying vec2 vPos;
            varying float vRadius;
            varying vec2 vCenter;
            varying vec4 vColor;
            
            void main() {
                gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * vec4(gl_Vertex.xy, 0.0, 1.0);
                vPos = gl_Vertex.xy;
                vRadius = gl_Vertex.z;
                vCenter = gl_MultiTexCoord0.st;
                vColor = gl_Color;
            }
        """.trimIndent()

        @Language("GLSL")
        private val fragSource = """
            varying vec2 vPos;
            varying float vRadius;
            varying vec2 vCenter;
            varying vec4 vColor;
            
            void main() {
                float dist = length(vPos - vCenter) - vRadius;
                float alpha = clamp(1.0 - dist, 0.0, 1.0);
                gl_FragColor = vColor * alpha;
            }
        """.trimIndent()

        private val PIPELINE3 = URenderPipeline.builderWithLegacyShader(
            "elementa:circle",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR,
            vertSource,
            fragSource,
        ).apply {
            blendState = BlendState.ALPHA
        }.build()

        fun extractCircle(extractor: ElementaExtractor, centerX: Float, centerY: Float, radius: Float, color: Color) {
            if (color.alpha == 0) return
            if (radius <= 0f) return

            extractCirclePixelSpace(
                extractor,
                ((centerX - radius) * extractor.guiScale).roundToInt(),
                ((centerY - radius) * extractor.guiScale).roundToInt(),
                ((centerX + radius) * extractor.guiScale).roundToInt(),
                ((centerY + radius) * extractor.guiScale).roundToInt(),
                centerX * extractor.guiScale,
                centerY * extractor.guiScale,
                radius * extractor.guiScale,
                color
            )
        }

        // Also used to draw partial circles by UIRoundedRectangle
        internal fun extractCirclePixelSpace(
            extractor: ElementaExtractor,
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int,
            centerX: Float,
            centerY: Float,
            radius: Float,
            color: Color
        ) {
            // We're smuggling to the shader the radius via z (proper z is restored in the vertex shader) and the
            // center via uv. This allows us to do some nice anti-aliasing in the shader.
            val z = radius
            val u = centerX
            val v = centerY

            extractor.custom(
                x1, y1, x2, y2,
                PIPELINE3,
                emptyList(),
                4,
            ) { builder, offsetX, offsetY ->
                fun vert(x: Int, y: Int) {
                    builder
                        .pos(UMatrixStack.UNIT, x.toDouble(), y.toDouble(), z.toDouble())
                        .tex((u + offsetX).toDouble(), (v + offsetY).toDouble())
                        .color(color)
                        .endVertex()
                }
                vert(x1, y2)
                vert(x2, y2)
                vert(x2, y1)
                vert(x1, y1)
            }
        }
    }
}
