package gg.essential.elementa.renderer.impl

import gg.essential.universal.vertex.UVertexConsumer

internal class CountingVertexConsumer(inner: UVertexConsumer, val maxCount: Int) : DelegatingVertexConsumer(inner) {
    var count = 0

    override fun endVertex(): UVertexConsumer {
        count++
        if (count > maxCount) {
            throw IllegalStateException("Element has specified a max vertex count of $maxCount, which has now been exceeded.")
        }
        return super.endVertex()
    }
}
