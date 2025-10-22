@file:Suppress("DEPRECATION")
package gg.essential.elementa.components.image

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIImage
import gg.essential.elementa.components.Window
import gg.essential.elementa.font.FontRenderer.Companion.doffsetUniform
import gg.essential.elementa.font.FontRenderer.Companion.fgColorUniform
import gg.essential.elementa.font.FontRenderer.Companion.hintAmountUniform
import gg.essential.elementa.font.FontRenderer.Companion.samplerUniform
import gg.essential.elementa.font.FontRenderer.Companion.sdfTexel
import gg.essential.elementa.font.FontRenderer.Companion.shader
import gg.essential.elementa.font.FontRenderer.Companion.subpixelAmountUniform
import gg.essential.elementa.utils.ResourceCache
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.utils.ReleasedDynamicTexture
import org.lwjgl.opengl.GL11
import java.awt.image.BufferedImage
import java.io.File
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import javax.imageio.ImageIO

@Deprecated("Not well maintained. Does not currently support 1.21.5+ at all.")
open class MSDFComponent constructor(
    private val imageFuture: CompletableFuture<BufferedImage>
) : UIComponent(), CacheableImage {
    private var texture: ReleasedDynamicTexture? = null

    private val waiting = ConcurrentLinkedQueue<CacheableImage>()
    var imageWidth = 1f
    var imageHeight = 1f
    var destroy = true
    val isLoaded: Boolean
        get() = texture != null

    init {
        imageFuture.thenAcceptAsync {
            if (it == null) {
                destroy = false
                return@thenAcceptAsync
            }
            imageWidth = it.width.toFloat()
            imageHeight = it.height.toFloat()
            imageFuture.obtrudeValue(null)

            Window.enqueueRenderOperation {
                texture = UGraphics.getTexture(it)
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


    override fun draw(matrixStack: UMatrixStack) {
        beforeDrawCompat(matrixStack)

        val x = this.getLeft().toDouble()
        val y = this.getTop().toDouble()
        val width = this.getWidth().toDouble()
        val height = this.getHeight().toDouble()
        val color = this.getColor()

        if (color.alpha == 0) {
            return super.draw(matrixStack)
        }
        val tex = texture ?: return super.draw(matrixStack)
        while (waiting.isEmpty().not())
            waiting.poll().applyTexture(texture)

        initShaders()
        UGraphics.enableBlend()
        UGraphics.tryBlendFuncSeparate(
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA
        )
        shader.bind()

        samplerUniform.setValue(tex.dynamicGlId)
        UGraphics.configureTexture(tex.dynamicGlId) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        }
        sdfTexel.setValue(1f / 128, 1f / 128)
        doffsetUniform.setValue((3.5f / height).toFloat())

        val current = getColor()
        subpixelAmountUniform.setValue(0f)
        hintAmountUniform.setValue(0f)


        val textureTop = 0.0
        val textureBottom = 1.0
        val textureLeft = (0).toDouble()
        val textureRight = (1).toDouble()

        fgColorUniform.setValue(
            current.red / 255F,
            current.green / 255F,
            current.blue / 255F,
            1f
        )
        val worldRenderer = UGraphics.getFromTessellator()
        worldRenderer.beginWithActiveShader(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE)
        val doubleX = x.toDouble()
        val doubleY = y.toDouble()
        worldRenderer.pos(matrixStack, doubleX, doubleY + height, 0.0).tex(textureLeft, textureBottom).endVertex()
        worldRenderer.pos(matrixStack, doubleX + width, doubleY + height, 0.0).tex(textureRight, textureBottom)
            .endVertex()
        worldRenderer.pos(matrixStack, doubleX + width, doubleY, 0.0).tex(textureRight, textureTop).endVertex()
        worldRenderer.pos(matrixStack, doubleX, doubleY, 0.0).tex(textureLeft, textureTop).endVertex()
        worldRenderer.drawDirect()

        shader.unbind()
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

    companion object {

        @JvmStatic
        fun ofFile(file: File): MSDFComponent {
            return MSDFComponent(CompletableFuture.supplyAsync { ImageIO.read(file) })
        }

        @JvmStatic
        fun ofURL(url: URL): MSDFComponent {
            return MSDFComponent(CompletableFuture.supplyAsync { UIImage.get(url) })
        }

        @JvmStatic
        fun ofURL(url: URL, cache: ImageCache): MSDFComponent {
            return MSDFComponent(CompletableFuture.supplyAsync {
                return@supplyAsync cache[url] ?: UIImage.get(url).also {
                    cache[url] = it
                }
            })
        }

        @JvmStatic
        fun ofResource(path: String): MSDFComponent {
            return MSDFComponent(CompletableFuture.supplyAsync {
                ImageIO.read(this::class.java.getResourceAsStream(path))
            })
        }

        @JvmStatic
        fun ofResourceCached(path: String): MSDFComponent {
            return ofResourceCached(path, UIImage.defaultResourceCache)
        }

        @JvmStatic
        fun ofResourceCached(path: String, resourceCache: ResourceCache): MSDFComponent {
            return resourceCache.getMSDFComponent(path)
        }

        fun areShadersInitialized() = gg.essential.elementa.font.FontRenderer.areShadersInitialized()
        fun initShaders() = gg.essential.elementa.font.FontRenderer.initShaders()
    }


}
