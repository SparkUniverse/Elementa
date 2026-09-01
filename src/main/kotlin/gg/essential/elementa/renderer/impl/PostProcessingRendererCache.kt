package gg.essential.elementa.renderer.impl

import gg.essential.elementa.renderer.PostProcessingRenderer

internal class PostProcessingRendererCache : AutoCloseable {
    private class Entry<T> : AutoCloseable {
        val available = mutableListOf<PostProcessingRenderer<T>>()
        val used = mutableListOf<PostProcessingRenderer<T>>()

        fun endFrame() {
            // Clean up unused instances
            available.forEach { it.close() }
            available.clear()

            // Move used instances to be available again for the next frame
            available.addAll(used)
            used.clear()
        }

        override fun close() {
            available.forEach { it.close() }
            available.clear()
            used.forEach { it.close() }
            used.clear()
        }
    }
    private val entries = mutableMapOf<PostProcessingRenderer.Factory<*>, Entry<*>>()

    fun <T> provide(factory: PostProcessingRenderer.Factory<T>): PostProcessingRenderer<T> {
        @Suppress("UNCHECKED_CAST")
        val cache = entries.getOrPut(factory) { Entry<T>() } as Entry<T>
        val instance = cache.available.removeLastOrNull() ?: factory.create()
        cache.used.add(instance)
        return instance
    }

    fun endFrame() {
        entries.values.removeIf { entry ->
            entry.endFrame()
            entry.available.isEmpty()
        }
    }

    override fun close() {
        entries.values.forEach { it.close() }
        entries.clear()
    }
}
