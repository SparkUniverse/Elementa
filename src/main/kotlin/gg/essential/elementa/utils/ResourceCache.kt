package gg.essential.elementa.utils

import gg.essential.elementa.components.UIImage
import gg.essential.elementa.components.image.CacheableImage
import gg.essential.elementa.components.image.MSDFComponent
import gg.essential.elementa.components.inspector.Inspector
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

class ResourceCache(val size: Int, private val context: Class<*>) {
    @Deprecated("Does not work properly when used with Java 9 Modules")
    @JvmOverloads
    constructor(size: Int = 50) : this(size, ResourceCache::class.java)

    private val cacheMap = ConcurrentHashMap<String, CacheableImage>()

    fun getUIImage(path: String): CacheableImage {
        if (cacheMap.size > size)
            cacheMap.clear()
        val cachedImage = cacheMap.computeIfAbsent(path) { pth ->
            UIImage(CompletableFuture.supplyAsync {
                ImageIO.read(context.getResourceAsStream(pth))
            })
        }
        return UIImage(CompletableFuture.completedFuture<BufferedImage>(null)).also {
            cachedImage.supply(it)
        }
    }

    fun invalidateAll() {
        cacheMap.clear()
    }

    fun invalidate(path: String): Boolean {
        return cacheMap.remove(path) != null
    }

    fun getMSDFComponent(path: String): MSDFComponent {
        if (cacheMap.size > size)
            cacheMap.clear()
        val cachedImage = cacheMap.computeIfAbsent(path) { pth ->
            MSDFComponent(CompletableFuture.supplyAsync {
                ImageIO.read(context.getResourceAsStream(pth))
            })
        }
        return MSDFComponent(CompletableFuture.completedFuture<BufferedImage>(null)).also {
            cachedImage.supply(it)
        }
    }

    private companion object {
        init {
            Inspector.registerComponentFactory(ResourceCache::class.java)
        }
    }
}