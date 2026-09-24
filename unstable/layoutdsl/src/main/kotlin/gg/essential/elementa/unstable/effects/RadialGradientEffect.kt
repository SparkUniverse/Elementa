package gg.essential.elementa.unstable.effects

import gg.essential.elementa.effects.Effect
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.SpecialRenderer
import gg.essential.elementa.unstable.state.v2.State
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.SharedIndexBuffers
import gg.essential.universal.render.UGpuBuffer
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPassDescriptor
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UBufferBuilder
import org.intellij.lang.annotations.Language
import java.awt.Color
import kotlin.math.roundToInt
import kotlin.use

/**
 * Draws a radial gradient behind the bound component.
 */
class RadialGradientEffect(
    private val insideColor: State<Color>,
    private val outsideColor: State<Color>,
    private val center: State<Pair<Float, Float>>,
    private val radius: State<Float>,
) : Effect() {

    override fun extractBeforeChildren(extractor: ElementaExtractor) {
        val insideColor = this.insideColor.getUntracked()
        val outsideColor = this.outsideColor.getUntracked()
        val center = this.center.getUntracked()
        val radius = this.radius.getUntracked()
        val x1 = (boundComponent.getLeft() * extractor.guiScale).roundToInt()
        val x2 = (boundComponent.getRight() * extractor.guiScale).roundToInt()
        val y1 = (boundComponent.getTop() * extractor.guiScale).roundToInt()
        val y2 = (boundComponent.getBottom() * extractor.guiScale).roundToInt()
        val aspect = boundComponent.getWidth() / boundComponent.getHeight()
        extractor.special(x1, y1, x2, y2, RendererFactory(), RenderState(insideColor, outsideColor, center, radius, aspect))
    }

    private data class RenderState(
        val insideColor: Color,
        val outsideColor: Color,
        val center: Pair<Float, Float>,
        val radius: Float,
        val aspect: Float,
    )

    private class RendererFactory: SpecialRenderer.Factory<RenderState> {
        override fun create(): SpecialRenderer<RenderState> = Renderer()
    }

    private class Renderer: SpecialRenderer<RenderState> {
        override val supportsScissor: Boolean
            get() = false
        override val onlyDrawsInBounds: Boolean
            get() = true

        override fun render(destination: UGpuTextureView, instances: List<SpecialRenderer.Instance<RenderState>>) {
            val device = UGraphics.getDevice()

            device.createRenderPass(
                URenderPassDescriptor { "RadialGradientEffect" }
                    .withColorAttachment(destination)
            ).use { renderPass ->
                renderPass.pipeline(PIPELINE)

                val w = destination.texture.width
                val h = destination.texture.height

                renderPass.projectionMatrix(floatArrayOf(
                    2f/w, 0f,    0f,   0f,
                    0f,   -2f/h, 0f,   0f,
                    0f,   0f,    1f,   0f,
                    -1f,  1f,    0f,   1f,
                ))

                for (instance in instances) {
                    val x = instance.dstX
                    val y = instance.dstY
                    val x1 = x.toDouble()
                    val x2 = (x + instance.width).toDouble()
                    val y1 = y.toDouble()
                    val y2 = (y + instance.height).toDouble()

                    val state = instance.args
                    val builder = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE)

                    val (indexBuffer, indexType) = SharedIndexBuffers.quads(4)
                    builder.pos(UMatrixStack.UNIT, x2, y1, 0.0).tex(1.0, 0.0).endVertex()
                    builder.pos(UMatrixStack.UNIT, x1, y1, 0.0).tex(0.0, 0.0).endVertex()
                    builder.pos(UMatrixStack.UNIT, x1, y2, 0.0).tex(0.0, 1.0).endVertex()
                    builder.pos(UMatrixStack.UNIT, x2, y2, 0.0).tex(1.0, 1.0).endVertex()

                    builder.build()!!.use { device.createBuffer(UGpuBuffer.Usage.VERTEX, it.toByteBuffer()) }
                        .use { vertexBuffer ->
                            renderPass.vertexBuffer(0, vertexBuffer.slice())
                            renderPass.uniform("center", state.center.first, state.center.second)
                            renderPass.uniform("radius", state.radius)
                            renderPass.uniform("aspect", state.aspect)
                            val insideColor = state.insideColor
                            val outsideColor = state.outsideColor
                            renderPass.uniform("insideColor", insideColor.red / 255f, insideColor.green / 255f, insideColor.blue / 255f, insideColor.alpha / 255f)
                            renderPass.uniform("outsideColor", outsideColor.red / 255f, outsideColor.green / 255f, outsideColor.blue / 255f, outsideColor.alpha / 255f)
                            renderPass.indexBuffer(indexBuffer, indexType)
                            renderPass.drawIndexed(6)
                        }
                }
            }
        }

        override fun close() {
        }
    }

    companion object {
        @Language("GLSL")
        private val vertSource = """
            varying vec2 f_TexCoord;
            
            void main() {
                f_TexCoord = gl_MultiTexCoord0.st;
                gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
            }
        """.trimIndent()

        @Language("GLSL")
        private val fragSource = """
            varying vec2 f_TexCoord;
            
            uniform vec2 center;
            uniform float radius;
            uniform float aspect;
            uniform vec4 insideColor;
            uniform vec4 outsideColor;
            
            void main() {
                vec2 position = f_TexCoord - center;
            
                position.x *= aspect;
            
                float distance = length(position);
                float weight = smoothstep(0.0, radius, distance);
                
                // Generate four pseudo-random values in range [-0.5; 0.5] for the current fragment coords, based on
                // Vlachos 2016, "Advanced VR Rendering"
                vec4 noise = vec4(dot(vec2(171.0, 231.0), gl_FragCoord.xy));
                noise = fract(noise / vec4(103.0, 71.0, 97.0, 127.0)) - 0.5;

                // Apply dithering, i.e. randomly offset all the values within a color band, so there are no harsh
                // edges between different bands after quantization.
                gl_FragColor = mix(insideColor, outsideColor, weight) + noise / 255.0;
            }
        """.trimIndent()

        private val PIPELINE = URenderPipeline.builderWithLegacyShader(
            "elementa:radial_gradient_effect",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_TEXTURE,
            vertSource,
            fragSource,
        ).apply {
            blendState = BlendState.ALPHA
        }.build()
    }
}
