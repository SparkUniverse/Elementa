package gg.essential.elementa.renderer.impl

// TODO this is roughly O(n^2), could probably improve that to at least O(n*log(n))
/**
 * Sorts elements into layers, such that the draw order of stuff in each layer can be freely reordered without
 * affecting the result.
 * In practise that means that any time two elements try to draw to overlapping regions of the screen, the latter
 * element is put in a higher layer, so it is guaranteed to draw after the former one.
 *
 * There's one optimization / special case to this: If both elements are plain color elements, we can put them in
 * the same layer even if they overlap, since we don't ever need to reorder color elements, so the order in which
 * color elements are drawn is preserved even within a single layer.
 */
internal fun sortIntoLayers(elements: Sequence<Element>): List<MutableList<Element>> {
    val layers = mutableListOf<MutableList<Element>>()
    for (element in elements) {
        val bounds = element.bounds
        var targetLayer = -1
        for (layer in layers.indices.reversed()) {
            var intersects = false
            var needsNewLayer = false
            for (other in layers[layer]) {
                if (other.bounds.intersects(bounds)) {
                    intersects = true
                    if (element !is ColoredElement || other !is ColoredElement) {
                        needsNewLayer = true
                    }
                }
            }
            if (intersects) {
                targetLayer = if (needsNewLayer) layer + 1 else layer
                break
            }
        }
        if (targetLayer == -1) {
            targetLayer = 0
        }
        if (targetLayer > layers.lastIndex) {
            layers.add(mutableListOf())
        }
        layers[targetLayer].add(element)
    }
    return layers
}
