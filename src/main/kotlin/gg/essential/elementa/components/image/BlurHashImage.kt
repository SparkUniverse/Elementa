package gg.essential.elementa.components.image

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIImage
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.utils.decodeBlurHash
import gg.essential.elementa.utils.drawTexture
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuSampler.Companion.invoke
import gg.essential.universal.utils.ReleasedDynamicTexture
import java.awt.Color
import java.io.File
import java.net.URL
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt

open class BlurHashImage(private val hash: String) : UIComponent(), ImageProvider {
    private lateinit var texture: ReleasedDynamicTexture
    private var dimensions = BASE_WIDTH to BASE_HEIGHT

    private fun generateTexture(): ReleasedDynamicTexture {
        return decodeBlurHash(hash, dimensions.first.toInt(), dimensions.second.toInt())?.let {
            UGraphics.getTexture(it)
        } ?: run {
            // We encountered an issue decoding the blur hash, it's probably invalid.
            UGraphics.getEmptyTexture()
        }
    }

    private fun sizeTexture(width: Double, height: Double) {
        if (::texture.isInitialized) {
            if (width > 0 && height > 0) {
                val sizeDifference = abs(dimensions.first * dimensions.second - width * height)

                if (sizeDifference > SIZE_THRESHOLD) {
                    dimensions = width to height
                    texture = generateTexture()
                }
            }
        } else {
            texture = generateTexture()
        }
    }

    override fun extract(extractor: ElementaExtractor, x: Int, y: Int, width: Int, height: Int, color: Color) {
        sizeTexture(width.toDouble(), height.toDouble())
        extractor.blit(
            x, y, x + width, y + height,
            0f, 0f, 1f, 1f,
            texture.gpuTextureView,
            UGpuSampler(
                UGpuSampler.AddressMode.CLAMP_TO_EDGE,
                UGpuSampler.AddressMode.CLAMP_TO_EDGE,
                UGpuSampler.FilterMode.LINEAR,
                UGpuSampler.FilterMode.LINEAR,
                false,
            ),
            textureContentImmutable = true,
            premultipliedAlpha = false,
            color,
        )
    }

    @Deprecated(
        "`draw`-style rendering is deprecated. Use `extract` instead.",
        replaceWith = ReplaceWith("extractMcScale(extractor, x, y, width, height, color)")
    )
    override fun drawImage(matrixStack: UMatrixStack, x: Double, y: Double, width: Double, height: Double, color: Color) {
        sizeTexture(width, height)
        drawTexture(matrixStack, texture, color, x, y, width, height)
    }

    override fun extractComponent(extractor: ElementaExtractor) {
        extract(
            extractor,
            (getLeft() * extractor.guiScale).roundToInt(),
            (getTop() * extractor.guiScale).roundToInt(),
            (getWidth() * extractor.guiScale).roundToInt(),
            (getHeight() * extractor.guiScale).roundToInt(),
            getColor(),
        )
    }

    @Deprecated(
        "`draw`-style rendering is deprecated. Override `extractComponent` instead. Call `extract` to extract this component, its effects, and its children.",
        replaceWith = ReplaceWith("extract(extractor)")
    )
    override fun draw(matrixStack: UMatrixStack) {
        beforeDrawCompat(matrixStack)

        val x = this.getLeft().toDouble()
        val y = this.getTop().toDouble()
        val width = this.getWidth().toDouble()
        val height = this.getHeight().toDouble()
        val color = this.getColor()

        if (color.alpha == 0) {
            @Suppress("DEPRECATION")
            return super.draw(matrixStack)
        }

        drawImageCompat(matrixStack, x, y, width, height, color)

        @Suppress("DEPRECATION")
        super.draw(matrixStack)
    }

    companion object {
        const val BASE_WIDTH = 50.0
        const val BASE_HEIGHT = 50.0
        const val SIZE_THRESHOLD = 2000

        /**
         * Creates a [UIImage] component that will be backed by a [BlurHashImage] until it is fully
         * loaded.
         */
        @JvmStatic
        fun ofFile(hash: String, file: File): UIImage {
            return UIImage(CompletableFuture.supplyAsync { ImageIO.read(file) }, BlurHashImage(hash))
        }

        /**
         * Creates a [UIImage] component that will be backed by a [BlurHashImage] until it is fully
         * loaded.
         */
        @JvmStatic
        fun ofURL(hash: String, url: URL): UIImage {
            return UIImage(CompletableFuture.supplyAsync { UIImage.get(url) }, BlurHashImage(hash))
        }

        /**
         * Creates a [UIImage] component that will be backed by a [BlurHashImage] until it is fully
         * loaded.
         */
        @JvmStatic
        fun ofResource(hash: String, path: String): UIImage {
            return UIImage(CompletableFuture.supplyAsync {
                ImageIO.read(this::class.java.getResourceAsStream(path))
            }, BlurHashImage(hash))
        }
    }
}
