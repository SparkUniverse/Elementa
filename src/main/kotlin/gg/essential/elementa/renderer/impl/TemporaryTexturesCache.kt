package gg.essential.elementa.renderer.impl

import gg.essential.universal.render.UGpuDevice
import gg.essential.universal.render.UGpuFormat
import gg.essential.universal.render.UGpuTexture
import gg.essential.universal.render.UGpuTextureView

internal class TemporaryTexturesCache(
    val device: UGpuDevice,
    val usage: UGpuTexture.Usage,
    val format: UGpuFormat,
) : AutoCloseable {
    val available = mutableListOf<UGpuTextureView>() // always sorted by area
    val used = mutableListOf<UGpuTextureView>()

    fun provide(width: Int, height: Int): UGpuTextureView {
        val area = width * height

        // Use binary search to quickly skip over all textures which are definitely too small
        var i = -available.binarySearch { if (it.texture.width * it.texture.height < area) -1 else 1 } - 1
        while (i < available.size) {
            val textureView = available[i]
            val texture = textureView.texture
            // Give up if we can't find a texture that's at most twice as big as what we need
            if (texture.width * texture.height > area * 2) {
                break
            }
            if (texture.width >= width && texture.height >= height) {
                available.removeAt(i)
                used.add(textureView)
                return textureView
            }
            i++
        }

        val texture = device.createTexture(
            null,
            usage,
            format,
            width,
            height,
            1,
        )
        val textureView = device.createTextureView(texture, 0, 1)
        used.add(textureView)
        return textureView
    }

    fun endFrame() {
        // Clean up unused instances
        available.forEach { it.close(); it.texture.close() }
        available.clear()

        // Move used instances to be available again for the next frame
        available.addAll(used)
        available.sortBy { it.texture.width * it.texture.height }
        used.clear()
    }

    override fun close() {
        available.forEach { it.close(); it.texture.close() }
        available.clear()
        used.forEach { it.close(); it.texture.close() }
        used.clear()
    }
}