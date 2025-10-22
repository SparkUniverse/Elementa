package gg.essential.elementa.unstable.state.v2

import gg.essential.elementa.unstable.state.v2.collections.MutableTrackedList
import gg.essential.elementa.unstable.state.v2.collections.MutableTrackedSet
import gg.essential.elementa.unstable.state.v2.collections.TrackedList
import gg.essential.elementa.unstable.state.v2.combinators.map
import gg.essential.elementa.unstable.state.v2.combinators.zip

fun <T> ListState<T>.toSet(): SetState<T> {
    val count = mutableMapOf<T, Int>()
    return mapChange({ list ->
        for (element in list) {
            count.compute(element) { _, c -> (c ?: 0) + 1 }
        }
        MutableTrackedSet(list.toMutableSet())
    }, { set, change ->
        when (change) {
            is TrackedList.Add -> {
                if (count.compute(change.element.value) { _, c -> (c ?: 0) + 1 } == 1) {
                    set.add(change.element.value)
                } else {
                    set
                }
            }
            is TrackedList.Remove -> {
                if (count.compute(change.element.value) { _, c -> (c!! - 1).takeUnless { it == 0 } } == null) {
                    set.remove(change.element.value)
                } else {
                    set
                }
            }
            is TrackedList.Clear -> set.clear()
        }
    })
}

// mapList { it.filter(filter) }
fun <T> ListState<T>.filter(filter: (T) -> Boolean): ListState<T> {
    val indices = mutableListOf<Int>()
    return mapChange({ list ->
        MutableTrackedList(mutableListOf<T>().also { filteredList ->
            for (elem in list) {
                if (filter(elem)) {
                    indices.add(filteredList.size)
                    filteredList.add(elem)
                } else {
                    indices.add(-1)
                }
            }
        })
    }) { list, change ->
        when (change) {
            is TrackedList.Add -> {
                if (filter(change.element.value)) {
                    val mappedIndex = if (change.element.index == indices.size) {
                        // Fast path, add to end
                        list.size
                    } else {
                        // Slow path, to find the index of the newly added element, we need to find the index
                        // of the previous (non-filtered) element
                        var mappedIndex = 0
                        for (i in (0 until change.element.index).reversed()) {
                            val index = indices[i]
                            if (index != -1) {
                                mappedIndex = index + 1
                                break
                            }
                        }
                        // And then also increment the index of all elements that are after it
                        for (i in change.element.index .. indices.lastIndex) {
                            val index = indices[i]
                            if (index != -1) {
                                indices[i] = index + 1
                            }
                        }
                        mappedIndex
                    }
                    indices.add(change.element.index, mappedIndex)
                    list.add(mappedIndex, change.element.value)
                } else {
                    indices.add(change.element.index, -1)
                    list
                }
            }
            is TrackedList.Remove -> {
                val mappedIndex = indices.removeAt(change.element.index)
                if (mappedIndex != -1) {
                    for (i in change.element.index .. indices.lastIndex) {
                        val index = indices[i]
                        if (index != -1) {
                            indices[i] = index - 1
                        }
                    }
                    list.removeAt(mappedIndex)
                } else {
                    list
                }
            }
            is TrackedList.Clear -> {
                indices.clear()
                list.clear()
            }
        }
    }
}

// mapList { it.map(mapper) }
fun <T, U> ListState<T>.mapEach(mapper: (T) -> U): ListState<U> =
    mapChange({ MutableTrackedList(it.mapTo(mutableListOf(), mapper)) }) { list, change ->
        when (change) {
            is TrackedList.Add -> list.add(change.element.index, mapper(change.element.value))
            is TrackedList.Remove -> list.removeAt(change.element.index)
            is TrackedList.Clear -> list.clear()
        }
    }


// TODO: all of these are based on mapList and as such are quite inefficient, might make sense to implement some as efficient primitives instead

fun <T, U> ListState<T>.mapList(mapper: (List<T>) -> List<U>): ListState<U> =
    map(mapper).toListState()

fun <T, U, V> ListState<T>.zipWithEachElement(otherState: State<U>, transform: (T, U) -> V) =
    zip(otherState) { list, other -> list.map { transform(it, other) } }.toListState()

fun <T, U, V> ListState<T>.zipElements(otherList: ListState<U>, transform: (T, U) -> V) =
    zip(otherList) { a, b -> a.zip(b, transform) }.toListState()

fun <T, U> ListState<T>.mapEachNotNull(mapper: (T) -> U?) = mapList { it.mapNotNull(mapper) }

fun <T> ListState<T?>.filterNotNull() = mapList { it.filterNotNull() }

inline fun <reified U> ListState<*>.filterIsInstance(): ListState<U> = map { it.filterIsInstance<U>() }.toListState()

fun <T, U> ListState<T>.flatMap(block: (T) -> Iterable<U>) = mapList { it.flatMap(block) }

fun <T> ListState<T>.isEmpty() = map { it.isEmpty() }

fun <T> ListState<T>.isNotEmpty() = map { it.isNotEmpty() }
