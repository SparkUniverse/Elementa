package gg.essential.elementa.renderer.impl

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

internal class Packing(
    val atlasWidth: Int,
    val atlasHeight: Int,
    val entries: List<PackingEntry>,
)
internal data class PackTexture(val id: Int, val w: Int, val h: Int)
internal data class PackingEntry(val id: Int, val x: Int, val y: Int)

internal fun trivialPacking(texture: PackTexture): Packing {
    return Packing(texture.w, texture.h, listOf(PackingEntry(texture.id, 0, 0)))
}

internal fun packMany(
    textures: List<PackTexture>,
    maxAtlasSize: Int,
    discardStep: Int = 16,
): List<Packing> {
    when (textures.size) {
        0 -> return emptyList()
        1 -> return listOf(trivialPacking(textures.first()))
    }

    val remainingTextures = textures.toMutableList()
    remainingTextures.sortWith(packTextureSorting)

    val singlePacking = pack(remainingTextures, maxAtlasSize, discardStep, guessInitialSize(remainingTextures, maxAtlasSize))
    if (singlePacking != null) {
        return listOf(singlePacking)
    }

    val packings = mutableListOf<Packing>()
    while (remainingTextures.isNotEmpty()) {
        // TODO could probably substantially reduce wastage here by binary searching for smallest possible size,
        //  but probably overkill
        val packing = packWithSize(remainingTextures, maxAtlasSize, maxAtlasSize, failFast = false)
        packings.add(packing)
        remainingTextures.removeIf { t -> packing.entries.any { it.id == t.id } }
    }
    return packings
}

private val packTextureSorting: Comparator<PackTexture> =
    compareByDescending<PackTexture> { it.w }
        .thenByDescending { it.h }

private fun guessInitialSize(textures: List<PackTexture>, maxAtlasSize: Int): Int {
    val area = textures.sumOf { it.w * it.h }
    val maxSide = textures.maxOf { max(it.w, it.h) }
    return max(sqrt(area.toFloat()).toInt(), maxSide).nextPowerOfTwo().coerceAtMost(maxAtlasSize)
}

// Packing algorithm very much based on https://github.com/TeamHypersomnia/rectpack2D#algorithm
internal fun pack(
    textures: List<PackTexture>,
    maxAtlasSize: Int,
    discardStep: Int = 16,
    initialSize: Int = 512,
): Packing? {

    var bestPacking: Packing? = packWithSizeOrNull(textures, initialSize, initialSize)

    var squareSize = initialSize
    while (bestPacking == null) {
        squareSize *= 2
        if (squareSize > maxAtlasSize) {
            return null
        }
        bestPacking = packWithSizeOrNull(textures, squareSize, squareSize)
    }

    var step = -squareSize / 2
    if (squareSize > initialSize) step /= 2
    while (abs(step) >= discardStep) {
        squareSize += step
        val packing = packWithSizeOrNull(textures, squareSize, squareSize)
        step = if (packing == null) {
            abs(step / 2)
        } else {
            -abs(step / 2)
        }
        bestPacking = packing ?: bestPacking
    }

    bestPacking!!
    var width = bestPacking.atlasWidth
    var height = bestPacking.atlasHeight

    step = -width / 2
    while (abs(step) >= discardStep) {
        width += step
        val packing = packWithSizeOrNull(textures, width, height)
        step = if (packing == null) {
            abs(step / 2)
        } else {
            -abs(step / 2)
        }
        bestPacking = packing ?: bestPacking
    }

    bestPacking!!
    width = bestPacking.atlasWidth
    height = bestPacking.atlasHeight

    step = -height / 2
    while (abs(step) >= discardStep) {
        height += step
        val packing = packWithSizeOrNull(textures, width, height)
        step = if (packing == null) {
            abs(step / 2)
        } else {
            -abs(step / 2)
        }
        bestPacking = packing ?: bestPacking
    }

    return bestPacking
}

private fun packWithSizeOrNull(textures: List<PackTexture>, atlasWidth: Int, atlasHeight: Int): Packing? {
    return packWithSize(textures, atlasWidth, atlasHeight, failFast = true)
        .takeUnless { it.entries.size < textures.size }
}

private fun packWithSize(
    textures: List<PackTexture>,
    atlasWidth: Int,
    atlasHeight: Int,
    failFast: Boolean,
): Packing {
    val packedTextures = mutableListOf<PackingEntry>()
    data class XYWH(val x: Int, val y: Int, val w: Int, val h: Int)
    val freeRects = mutableListOf(XYWH(0, 0, atlasWidth, atlasHeight))
    textures@for (texture in textures) {
        // Search backwards through all free rects, so we try smaller ones first
        for (i in freeRects.lastIndex downTo 0) {
            val freeRect = freeRects[i]

            val remainingW = freeRect.w - texture.w
            val remainingH = freeRect.h - texture.h

            if (remainingW < 0 || remainingH < 0) {
                continue // doesn't fit, try next one
            }

            // Texture fits into this free rect, place it
            packedTextures.add(PackingEntry(texture.id, freeRect.x, freeRect.y))

            // Fits, remove the free rect
            // (by swapping with the last one so we don't need to shift the entire array)
            freeRects[i] = freeRects.last()
            freeRects.removeLast()

            when {
                // Texture fills entire freeRect, nothing remains
                remainingW == 0 && remainingH == 0 -> {}
                // Texture fill entire width, add remaining height as new free rect
                remainingW == 0 ->
                    freeRects.add(XYWH(freeRect.x, freeRect.y + texture.h, freeRect.w, remainingH))
                // Texture fill entire height, add remaining width as new free rect
                remainingH == 0 ->
                    freeRects.add(XYWH(freeRect.x + texture.w, freeRect.y, remainingW, freeRect.h))
                // Texture fills neither width nor height, add remaining space as two free rects
                else -> {
                    // Prefer one tiny and one huge free rect, assumption being that less space is wasted that way.
                    // Insert tiny one last so it is tried first for subsequent loops
                    if (remainingW > remainingH) {
                        // Large rect to the right of the texture
                        freeRects.add(XYWH(freeRect.x + texture.w, freeRect.y, remainingW, freeRect.h))
                        // Small rect directly below the texture
                        freeRects.add(XYWH(freeRect.x, freeRect.y + texture.h, texture.w, remainingH))
                    } else {
                        // Large rect below the texture
                        freeRects.add(XYWH(freeRect.x, freeRect.y + texture.h, freeRect.w, remainingH))
                        // Small rect directly to the right of the texture
                        freeRects.add(XYWH(freeRect.x + texture.w, freeRect.y, remainingW, texture.h))
                    }
                }
            }

            continue@textures // placed successfully, continue to the next texture
        }

        if (failFast) {
            break // no fitting free space found, give up
        }
    }
    return Packing(atlasWidth, atlasHeight, packedTextures)
}

private fun Int.nextPowerOfTwo(): Int {
    return 1 shl (32 - countLeadingZeroBits())
}
