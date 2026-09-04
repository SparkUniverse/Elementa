package gg.essential.elementa.renderer.impl

import gg.essential.universal.UMatrixStack
import gg.essential.universal.vertex.UVertexConsumer

// Note: Cannot use Kotlin's implementation `by` delegation, cause we need the methods to return `this`, not `inner`
internal open class DelegatingVertexConsumer(val inner: UVertexConsumer) : UVertexConsumer {
    override fun color(red: Int, green: Int, blue: Int, alpha: Int): UVertexConsumer = apply { inner.color(red, green, blue, alpha) }
    override fun endVertex(): UVertexConsumer = apply { inner.endVertex() }
    override fun light(u: Int, v: Int): UVertexConsumer = apply { inner.light(u, v) }
    override fun norm(stack: UMatrixStack, x: Float, y: Float, z: Float): UVertexConsumer = apply { inner.norm(stack, x, y, z) }
    override fun overlay(u: Int, v: Int): UVertexConsumer = apply { inner.overlay(u, v) }
    override fun pos(stack: UMatrixStack, x: Double, y: Double, z: Double): UVertexConsumer = apply { inner.pos(stack, x, y, z) }
    override fun tex(u: Double, v: Double): UVertexConsumer = apply { inner.tex(u, v) }
}
