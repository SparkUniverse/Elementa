package gg.essential.elementa.unstable.effects

import gg.essential.elementa.effects.Effect
import gg.essential.elementa.unstable.state.v2.State
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UBufferBuilder
import org.intellij.lang.annotations.Language
import java.awt.Color

/**
 * Draws a gradient (smooth color transition) behind the bound component.
 *
 * Unlike [gg.essential.elementa.components.GradientComponent], this effect also applies dithering to the gradient to
 * mitigate color banding artifacts.
 *
 * Note: The behavior of non-axis-aligned gradients (e.g. more than two colors, or diagonal) is currently undefined.
 */
class GradientEffect(
    private val topLeft: State<Color>,
    private val topRight: State<Color>,
    private val bottomLeft: State<Color>,
    private val bottomRight: State<Color>,
) : Effect() {
    override fun beforeChildrenDraw(matrixStack: UMatrixStack) {
        val topLeft = this.topLeft.get()
        val topRight = this.topRight.get()
        val bottomLeft = this.bottomLeft.get()
        val bottomRight = this.bottomRight.get()

        val dither = topLeft != topRight || topLeft != bottomLeft || bottomLeft != bottomRight

        val buffer = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)

        val x1 = boundComponent.getLeft().toDouble()
        val x2 = boundComponent.getRight().toDouble()
        val y1 = boundComponent.getTop().toDouble()
        val y2 = boundComponent.getBottom().toDouble()

        buffer.pos(matrixStack, x2, y1, 0.0).color(topRight).endVertex()
        buffer.pos(matrixStack, x1, y1, 0.0).color(topLeft).endVertex()
        buffer.pos(matrixStack, x1, y2, 0.0).color(bottomLeft).endVertex()
        buffer.pos(matrixStack, x2, y2, 0.0).color(bottomRight).endVertex()

        buffer.build()?.drawAndClose(if (dither) PIPELINE_DITHERED else PIPELINE_FLAT)
    }

    companion object {
        @Language("GLSL")
        private val vertSource = """
            varying vec4 vColor;
            
            void main() {
                gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
                vColor = gl_Color;
            }
        """.trimIndent()

        @Language("GLSL")
        private val fragSource = """
            varying vec4 vColor;
            
            void main() {
                // Generate four pseudo-random values in range [-0.5; 0.5] for the current fragment coords, based on
                // Vlachos 2016, "Advanced VR Rendering"
                vec4 noise = vec4(dot(vec2(171.0, 231.0), gl_FragCoord.xy));
                noise = fract(noise / vec4(103.0, 71.0, 97.0, 127.0)) - 0.5;
            
                // Apply dithering, i.e. randomly offset all the values within a color band, so there are no harsh
                // edges between different bands after quantization.
                gl_FragColor = vColor + noise / 255.0;
            }
        """.trimIndent()

        private val PIPELINE_DITHERED = URenderPipeline.builderWithLegacyShader(
            "elementa:gradient_effect/dithered",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
            vertSource,
            fragSource,
        ).apply {
            blendState = BlendState.ALPHA
            depthTest = URenderPipeline.DepthTest.Always
        }.build()

        private val PIPELINE_FLAT = URenderPipeline.builderWithDefaultShader(
            "elementa:gradient_effect/flat",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
        ).apply {
            blendState = BlendState.ALPHA
            depthTest = URenderPipeline.DepthTest.Always
        }.build()
    }
}
