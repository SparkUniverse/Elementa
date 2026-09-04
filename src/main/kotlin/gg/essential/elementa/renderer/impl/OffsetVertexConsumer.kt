package gg.essential.elementa.renderer.impl

import gg.essential.universal.UMatrixStack
import gg.essential.universal.vertex.UVertexConsumer

internal class OffsetVertexConsumer(inner: UVertexConsumer, val offsetX: Int, val offsetY: Int) : DelegatingVertexConsumer(inner) {
    override fun pos(stack: UMatrixStack, x: Double, y: Double, z: Double): UVertexConsumer {
        return super.pos(stack, x + offsetX, y + offsetY, z)
    }
}
