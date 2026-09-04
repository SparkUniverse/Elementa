package gg.essential.elementa.components.image

import gg.essential.elementa.components.UIImage
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.utils.drawTexture
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.utils.ReleasedDynamicTexture
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object DefaultFailureImage : ImageProvider {

    private var loadingImage: BufferedImage? = ImageIO.read(this::class.java.getResourceAsStream("/textures/failure.png"))
    private lateinit var loadingTexture: ReleasedDynamicTexture

    override fun extract(extractor: ElementaExtractor, x: Int, y: Int, width: Int, height: Int, color: Color) {
        if (!::loadingTexture.isInitialized) {
            loadingTexture = UGraphics.getTexture(loadingImage!!)
            loadingImage = null
        }

        val sampler = UGpuSampler(
            UGpuSampler.AddressMode.CLAMP_TO_EDGE,
            UGpuSampler.AddressMode.CLAMP_TO_EDGE,
            UGpuSampler.FilterMode.LINEAR,
            UGpuSampler.FilterMode.LINEAR,
            true,
        )
        extractor.blit(x, y, x + width, y + height, 0f, 0f, 1f, 1f, loadingTexture.gpuTextureView, sampler, true, false, color)
    }

    @Deprecated(
        "`draw`-style rendering is deprecated. Use `extract` instead.",
        replaceWith = ReplaceWith("extractMcScale(extractor, x, y, width, height, color)")
    )
    override fun drawImage(
        matrixStack: UMatrixStack,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        color: Color,
    ) {
        if (!::loadingTexture.isInitialized) {
            loadingTexture = UGraphics.getTexture(loadingImage!!)
            loadingImage = null
        }

        drawTexture(
            matrixStack,
            loadingTexture,
            color,
            x,
            y,
            width,
            height,
            textureMinFilter = UIImage.TextureScalingMode.LINEAR,
            textureMagFilter = UIImage.TextureScalingMode.LINEAR,
        )
    }
}
