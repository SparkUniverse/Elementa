package gg.essential.elementa.renderer.impl

import gg.essential.elementa.utils.NEAREST
import gg.essential.universal.render.UGpuDevice
import gg.essential.universal.render.UGpuFormat
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuTexture
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPassDescriptor
import java.awt.Color
import java.util.WeakHashMap
import kotlin.math.min
import kotlin.math.roundToInt

internal class TextureAtlasCache(
    val device: UGpuDevice,
    val maxAtlasSize: Int,
) : AutoCloseable {
    private var textureAtlas: TextureAtlas = TextureAtlas.Empty
    private var newLastFrame = emptySet<CacheKey>()

    fun provide(elements: Sequence<TexturedElement>): List<TexturedElement> {
        val result = mutableListOf<TexturedElement>()
        val newThisFrame = mutableSetOf<CacheKey>()
        for (element in elements) {
            val fromAtlas = textureAtlas.get(element)
            if (fromAtlas != null) {
                result.add(fromAtlas)
                continue
            }

            val key = CacheKey(element)
            if (key !in newLastFrame) {
                newThisFrame.add(key)
                result.add(element)
                continue
            }

            regenerateAtlas(elements)
            return provide(elements)
        }
        newLastFrame = newThisFrame
        return result
    }

    private fun regenerateAtlas(elements: Sequence<TexturedElement>) {
        val oldAtlas = textureAtlas
        textureAtlas = renderTextureAtlas(
            elements.distinctBy { CacheKey(it) }.toList(),
            maxAtlasSize,
            device,
            oldAtlas,
        )
        oldAtlas.close()
    }

    override fun close() {
        textureAtlas.close()
        newLastFrame = emptySet()
    }
}

private data class CacheKey(
    val textureView: UGpuTextureView,
    val sampler: UGpuSampler,
    val u1: Float,
    val v1: Float,
    val u2: Float,
    val v2: Float,
) {
    constructor(element: TexturedElement) : this(
        element.textureView,
        element.sampler,
        element.u1,
        element.v1,
        element.u2,
        element.v2,
    )
}

internal interface TextureAtlas : AutoCloseable {
    fun get(element: TexturedElement): TexturedElement?

    object Empty : TextureAtlas {
        override fun get(element: TexturedElement): TexturedElement? = null
        override fun close() = Unit
    }

    companion object {
        fun of(list: List<TextureAtlas>) = list.singleOrNull() ?: object : TextureAtlas {
            override fun get(element: TexturedElement): TexturedElement? = list.firstNotNullOfOrNull { it.get(element) }
            override fun close() = list.forEach { it.close() }
        }
    }
}

private class TextureAtlasImpl(
    val map: Map<CacheKey, Entry>,
    val textureView: UGpuTextureView,
) : TextureAtlas {
    class Entry(
        val u1: Float,
        val v1: Float,
        val u2: Float,
        val v2: Float,
    )

    override fun get(element: TexturedElement): TexturedElement? {
        val entry = map[CacheKey(element)] ?: return null
        return TexturedElement(
            element.rect,
            element.scissor,
            textureView,
            UGpuSampler.NEAREST,
            // not technically true since we may re-cycle the atlas texture,
            // but by that point this element will no longer be used either
            textureContentImmutable = true,
            // we already multiply with alpha where applicable when creating the atlas, so the whole atlas is already
            // pre-multiplied
            premultipliedAlpha = true,
            color = if (!element.premultipliedAlpha) {
                // The provided color is meant to be multiplied with the source texture before the resulting alpha is
                // multiplied with the resulting rgb components.
                // Our atlas already has the texture alpha multiplied with the texture rgb though.
                // So we need to convert the provided color into pre-multiplied form as well, so it composes correctly.
                //<editor-fold desc="Proof">
                // Drawing element without atlas (color multiplier followed by conventional alpha blending):
                //   shaderOutRed   = textureRed * colorRed
                //   shaderOutAlpha = textureAlpha * colorAlpha
                //   resultRed   = shaderOutRed * shaderOutAlpha
                //               = (textureRed * colorRed) * (textureAlpha * colorAlpha)
                //   resultAlpha = shaderOutAlpha
                //               = textureAlpha * colorAlpha
                //
                // Drawing into atlas (color multiplier (hard-coded to WHITE) followed by conventional alpha blending):
                //   shaderOutRed   = textureRed * colorRed
                //                  = textureRed
                //   shaderOutAlpha = textureAlpha * colorAlpha
                //                  = textureAlpha
                //   atlasRed   = shaderOutRed * shaderOutAlpha
                //              = textureRed * textureAlpha
                //   atlasAlpha = shaderOutAlpha
                //              = textureAlpha
                // Drawing element from atlas (premultiplied alpha blending):
                //   ourColorRed   = colorRed * colorAlpha
                //   ourColorAlpha = colorAlpha
                //   shaderOutRed   = atlasRed * ourColorRed
                //                  = (textureRed * textureAlpha) * (colorRed * colorAlpha)
                //   shaderOutAlpha = atlasAlpha * ourColorAlpha
                //                  = textureAlpha * colorAlpha
                //   resultRed   = shaderOutRed
                //               = (textureRed * textureAlpha) * (colorRed * colorAlpha)
                //               = textureRed * textureAlpha * colorRed * colorAlpha
                //               = (textureRed * colorRed) * (textureAlpha * colorAlpha)
                //   resultAlpha = shaderOutAlpha
                //               = textureAlpha * colorAlpha
                //   q.e.d.
                //</editor-fold>
                with(element.color) {
                    Color(red * alpha / 256, green * alpha / 256, blue * alpha / 256, alpha)
                }
            } else {
                // No change necessary, both texture and atlas contain the exact same content, so it doesn't matter
                // whether we draw from the texture or from the atlas, the color is the same in either case.
                element.color
            },
            entry.u1,
            entry.v1,
            entry.u2,
            entry.v2,
        )
    }

    override fun close() {
        textureView.close()
        textureView.texture.close()
    }
}

private fun renderTextureAtlas(
    elements: List<TexturedElement>,
    maxAtlasSize: Int,
    device: UGpuDevice,
    existingAtlas: TextureAtlas,
): TextureAtlas {
    val packings = packMany(elements.mapIndexed { index, element ->
        if (element.canCopySource()) {
            PackTexture(index, element.textureView.texture.width, element.textureView.texture.height)
        } else {
            PackTexture(index, element.rect.w, element.rect.h)
        }
    }, maxAtlasSize)

    return packings
        .map { renderTextureAtlas(elements, it, device, existingAtlas) }
        .let { TextureAtlas.of(it) }
}

private fun renderTextureAtlas(
    elements: List<TexturedElement>,
    packing: Packing,
    device: UGpuDevice,
    existingAtlas: TextureAtlas,
): TextureAtlas {
    val atlasSize = Rect.xywh(0, 0, packing.atlasWidth, packing.atlasHeight)
    val atlasView = device.createTextureView(device.createTexture(
        null,
        UGpuTexture.Usage.RENDER_ATTACHMENT + UGpuTexture.Usage.TEXTURE_BINDING,
        UGpuFormat.DEFAULT_RGBA,
        atlasSize.w,
        atlasSize.h,
        1,
    ), 0, 1)

    val copyJobs = mutableListOf<Element>()
    val atlasMap = mutableMapOf<CacheKey, TextureAtlasImpl.Entry>()

    for ((id, packX, packY) in packing.entries) {
        val element = elements[id]
        val canCopySource = element.canCopySource()
        val width = if (canCopySource) element.textureView.texture.width else element.rect.w
        val height = if (canCopySource) element.textureView.texture.height else element.rect.h

        atlasMap[CacheKey(element)] = TextureAtlasImpl.Entry(
            packX.toFloat() / atlasSize.w,
            1 - packY.toFloat() / atlasSize.h,
            (packX + width).toFloat() / atlasSize.w,
            1 - (packY + height).toFloat() / atlasSize.h,
        )

        // We prefer copying from an existing atlas, as that allows us to do a single draw for multiple textures
        val copySrc = existingAtlas.get(element) ?: element
        copyJobs.add(TexturedElement(
            Rect.xywh(packX, packY, width, height),
            atlasSize,
            copySrc.textureView,
            copySrc.sampler,
            copySrc.textureContentImmutable,
            copySrc.premultipliedAlpha,
            // Use white so we get an exact copy of the source in the atlas, rather than a tinted version.
            // Proper color is later applied when rendering from the atlas.
            Color.WHITE,
            copySrc.u1,
            copySrc.v1,
            copySrc.u2,
            copySrc.v2,
        ))
    }

    val descriptor = URenderPassDescriptor { "Elementa GUI Texture Atlas" }
        .withColorAttachment(atlasView, URenderPassDescriptor.ClearColor(0f, 0f, 0f, 0f))
    device.createRenderPass(descriptor).use { renderPass ->
        renderPass.projectionMatrix(with(atlasSize) {
            floatArrayOf(
                2f/w, 0f,    0f,   0f,
                0f,   -2f/h, 0f,   0f,
                0f,   0f,    1f,   0f,
                -1f,  1f,    0f,   1f,
            )
        })
        renderLayers(device, renderPass, atlasView, atlasSize, atlasSize, listOf(copyJobs))
    }

    return TextureAtlasImpl(atlasMap, atlasView)
}

internal fun TexturedElement.isCacheable(guiScale: Float): Boolean {
    if (!textureContentImmutable) return false
    val canCopySource = canCopySource()
    val maxCacheableSize = min(128 * if (canCopySource) 1 else guiScale.roundToInt(), 512)
    val width = if (canCopySource) textureView.texture.width else rect.w
    val height = if (canCopySource) textureView.texture.height else rect.h
    return width <= maxCacheableSize && height <= maxCacheableSize
}

// For trivial elements, we can make a copy of the source texture,
// for non-trivial ones we have to cache the output instead.
private fun TexturedElement.canCopySource(): Boolean {
    if (sampler != UGpuSampler.NEAREST) return false
    if (textureView.baseMipLevel != 0) return false
    if (textureView.mipLevels != 1) return false
    // TODO currently we only deal with blits that copy the entire source texture
    //  that could probably be relaxed to all pixel-aligned blits,
    //  just requires a more complex check and more complex size computation at `canCopySource` call sites
    fun Float.isTrivial(): Boolean = this == 0f || this == 1f
    if (!u1.isTrivial() || !v1.isTrivial() || !u2.isTrivial() || !v2.isTrivial()) return false
    // The source size being bigger than the output size doesn't literally mean we cannot copy,
    // it just doesn't make much sense to copy the source when the output is smaller.
    if (textureView.texture.width > rect.w) return false
    if (textureView.texture.height > rect.h) return false
    return true
}
