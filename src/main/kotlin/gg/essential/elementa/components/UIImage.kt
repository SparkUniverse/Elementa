package gg.essential.elementa.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.image.*
import gg.essential.elementa.components.inspector.Inspector
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.utils.ResourceCache
import gg.essential.elementa.utils.drawTexture
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.utils.ReleasedDynamicTexture
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Component for drawing arbitrary images from [BufferedImage].
 *
 * There are companion functions available to get [UIImage]s from other sources,
 * such as URLs: [Companion.ofURL], [Companion.ofFile] and [Companion.ofResource].
 */
open class UIImage @JvmOverloads constructor(
    private val imageFuture: CompletableFuture<BufferedImage>,
    private val loadingImage: ImageProvider = DefaultLoadingImage,
    private val failureImage: ImageProvider = DefaultFailureImage,
) : UIComponent(), ImageProvider, CacheableImage {
    private var texture: ReleasedDynamicTexture? = null

    private val waiting = ConcurrentLinkedQueue<CacheableImage>()
    var imageWidth = 1f
    var imageHeight = 1f
    var destroy = true
    val isLoaded: Boolean
        get() = texture != null
    var textureMinFilter = TextureScalingMode.NEAREST
    var textureMagFilter = TextureScalingMode.NEAREST

    init {
        imageFuture.exceptionally {
            it.printStackTrace()
            return@exceptionally null
        }.thenAcceptAsync {
            if (it == null) {
                destroy = false
                return@thenAcceptAsync
            }
            imageWidth = it.width.toFloat()
            imageHeight = it.height.toFloat()
            imageFuture.obtrudeValue(null)

            // In versions before 1.15, we make the bufferedImage.getRGB call without the upload in the
            // constructor since that takes most of the CPU time and we upload the actual texture during the
            // first call to uploadTexture or getGlTextureId
            // Same for 1.15+ actually, except that it is not getRGB but serialization to byte[] (so we can re-parse it
            // as a NativeImage) which is slow.
            val texture = UGraphics.getTexture(it)
            Window.enqueueRenderOperation {
                texture?.uploadTexture()
                this.texture = texture
                while (waiting.isEmpty().not())
                    waiting.poll().applyTexture(texture)
            }
        }
    }

    @Deprecated(
        "Please provide a completable future instead",
        ReplaceWith("CompletableFuture.supplyAsync(imageFunction)", "java.util.concurrent.CompletableFuture"),
        level = DeprecationLevel.ERROR
    )
    constructor(imageFunction: () -> BufferedImage) : this(CompletableFuture.supplyAsync(imageFunction))

    override fun extractComponent(extractor: ElementaExtractor) {
        val color = getColor()
        if (color.alpha == 0) return

        extract(
            extractor,
            (getLeft() * extractor.guiScale).roundToInt(),
            (getTop() * extractor.guiScale).roundToInt(),
            (getWidth() * extractor.guiScale).roundToInt(),
            (getHeight() * extractor.guiScale).roundToInt(),
            color,
        )
    }

    override fun extract(extractor: ElementaExtractor, x: Int, y: Int, width: Int, height: Int, color: Color) {
        val texture = texture
        when {
            texture != null -> {
                val sampler = UGpuSampler(
                    UGpuSampler.AddressMode.CLAMP_TO_EDGE,
                    UGpuSampler.AddressMode.CLAMP_TO_EDGE,
                    textureMinFilter.ucMode,
                    textureMagFilter.ucMode,
                    textureMinFilter.useMipmaps,
                )
                extractor.blit(x, y, x + width, y + height, 0f, 0f, 1f, 1f, texture.gpuTextureView, sampler, true, false, color)
            }
            imageFuture.isCompletedExceptionally -> failureImage.extract(extractor, x, y, width, height, color)
            else -> loadingImage.extract(extractor, x, y, width, height, color)
        }
    }

    @Deprecated(
        "`draw`-style rendering is deprecated. Use `extract` instead.",
        replaceWith = ReplaceWith("extractMcScale(extractor, x, y, width, height, color)")
    )
    override fun drawImage(matrixStack: UMatrixStack, x: Double, y: Double, width: Double, height: Double, color: Color) {
        when {
            texture != null -> drawTexture(matrixStack, texture!!, color, x, y, width, height, textureMinFilter, textureMagFilter)
            imageFuture.isCompletedExceptionally -> failureImage.drawImageCompat(matrixStack, x, y, width, height, color)
            else -> loadingImage.drawImageCompat(matrixStack, x, y, width, height, color)
        }
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

        @Suppress("DEPRECATION")
        drawImage(matrixStack, x, y, width, height, color)

        @Suppress("DEPRECATION")
        super.draw(matrixStack)
    }

    override fun supply(image: CacheableImage) {
        if (texture != null) {
            image.applyTexture(texture)
            return
        }
        waiting.add(image)
    }

    override fun applyTexture(texture: ReleasedDynamicTexture?) {
        this.texture = texture
        while (waiting.isEmpty().not())
            waiting.poll().applyTexture(texture)
    }

    enum class TextureScalingMode(internal val glMode: Int, internal val ucMode: UGpuSampler.FilterMode, internal val useMipmaps: Boolean) {
        NEAREST(GL11.GL_NEAREST, UGpuSampler.FilterMode.NEAREST, false),
        LINEAR(GL11.GL_LINEAR, UGpuSampler.FilterMode.LINEAR, false),
        NEAREST_MIPMAP_NEAREST(GL11.GL_NEAREST_MIPMAP_NEAREST, UGpuSampler.FilterMode.NEAREST, true),
        LINEAR_MIPMAP_NEAREST(GL11.GL_LINEAR_MIPMAP_NEAREST, UGpuSampler.FilterMode.LINEAR, true),
        NEAREST_MIPMAP_LINEAR(GL11.GL_NEAREST_MIPMAP_LINEAR, UGpuSampler.FilterMode.NEAREST, true),
        LINEAR_MIPMAP_LINEAR(GL11.GL_LINEAR_MIPMAP_LINEAR, UGpuSampler.FilterMode.LINEAR, true)
    }

    companion object {

        val defaultResourceCache = ResourceCache(50)

        init {
            Inspector.registerComponentFactory(Companion::class.java)
        }

        @JvmStatic
        fun ofFile(file: File): UIImage {
            return UIImage(CompletableFuture.supplyAsync { ImageIO.read(file) })
        }

        @JvmStatic
        fun ofURL(url: URL): UIImage {
            return UIImage(CompletableFuture.supplyAsync { get(url) })
        }

        @JvmStatic
        fun ofURL(url: URL, cache: ImageCache): UIImage {
            return UIImage(CompletableFuture.supplyAsync {
                return@supplyAsync cache[url] ?: get(url).also {
                    cache[url] = it
                }
            })
        }

        @JvmStatic
        fun ofResource(path: String): UIImage {
            return UIImage(CompletableFuture.supplyAsync {
                ImageIO.read(this::class.java.getResourceAsStream(path))
            })
        }

        @JvmStatic
        fun ofResourceCached(path: String): UIImage {
            return ofResourceCached(path, defaultResourceCache)
        }

        @JvmStatic
        fun ofResourceCached(path: String, resourceCache: ResourceCache): UIImage {
            return resourceCache.getUIImage(path) as UIImage
        }

        @JvmStatic
        fun get(url: URL): BufferedImage {
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.useCaches = true
            connection.addRequestProperty("User-Agent", "Mozilla/4.76 (Elementa)")
            connection.doOutput = true

            return ImageIO.read(connection.inputStream)
        }
    }
}
