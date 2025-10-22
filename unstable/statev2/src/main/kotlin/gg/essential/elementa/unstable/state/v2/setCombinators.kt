package gg.essential.elementa.unstable.state.v2

import gg.essential.elementa.unstable.state.v2.collections.MutableTrackedList
import gg.essential.elementa.unstable.state.v2.collections.MutableTrackedSet
import gg.essential.elementa.unstable.state.v2.collections.TrackedSet
import gg.essential.elementa.unstable.state.v2.combinators.map
import gg.essential.elementa.unstable.state.v2.combinators.zip

fun <T> SetState<T>.toList(): ListState<T> {
    return mapChange({ MutableTrackedList(it.toMutableList()) }, { list, change ->
        when (change) {
            is TrackedSet.Add -> list.add(change.element)
            is TrackedSet.Remove -> list.remove(change.element)
            is TrackedSet.Clear -> list.clear()
        }
    })
}

fun <T> SetState<T>.filter(filter: (T) -> Boolean): SetState<T> =
    mapChange({ MutableTrackedSet(it.filterTo(mutableSetOf(), filter)) }) { set, change ->
        when (change) {
            is TrackedSet.Add -> {
                if (filter(change.element)) {
                    set.add(change.element)
                } else {
                    set
                }
            }
            is TrackedSet.Remove -> set.remove(change.element)
            is TrackedSet.Clear -> set.clear()
        }
    }

fun <T, U> SetState<T>.mapEach(mapper: (T) -> U): SetState<U> {
    val mappedValues = mutableMapOf<T, U>()
    val mappedCount = mutableMapOf<U, Int>()
    return mapChange({ set ->
        MutableTrackedSet(set.mapTo(mutableSetOf()) { value ->
            mapper(value).also { mappedValue ->
                mappedValues[value] = mappedValue
                mappedCount.compute(mappedValue) { _, i -> (i ?: 0) + 1}
            }
        })
    }) { list, change ->
        when (change) {
            is TrackedSet.Add -> {
                val mappedValue = mapper(change.element)
                mappedValues[change.element] = mappedValue
                mappedCount.compute(mappedValue) { _, i -> (i ?: 0) + 1 }
                list.add(mappedValue)
            }
            is TrackedSet.Remove -> {
                val mappedValue = mappedValues.remove(change.element)!!
                if (mappedCount.computeIfPresent(mappedValue) { _, i -> (i - 1).takeIf { i > 0 } } == null) {
                    list.remove(mappedValue)
                } else {
                    list
                }
            }
            is TrackedSet.Clear -> {
                mappedValues.clear()
                mappedCount.clear()
                list.clear()
            }
        }
    }
}

// TODO: all of these are based on mapSet and as such are quite inefficient, might make sense to implement some as efficient primitives instead

fun <T, U> SetState<T>.mapSet(mapper: (Set<T>) -> Set<U>): SetState<U> =
    map(mapper).toSetState()

fun <T, U, V> SetState<T>.zipWithEachElement(otherState: State<U>, transform: (T, U) -> V) =
    zip(otherState) { set, other -> set.mapTo(mutableSetOf()) { transform(it, other) } }.toSetState()

fun <T, U : Any> SetState<T>.mapEachNotNull(mapper: (T) -> U?) = mapSet { it.mapNotNullTo(mutableSetOf(), mapper) }

fun <T : Any> SetState<T?>.filterNotNull() = mapSet { it.filterNotNullTo(mutableSetOf()) }

inline fun <reified U> SetState<*>.filterIsInstance(): SetState<U> = map { it.filterIsInstanceTo<U, MutableSet<U>>(mutableSetOf()) }.toSetState()
