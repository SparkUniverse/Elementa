package gg.essential.elementa.unstable.state.v2

import gg.essential.elementa.unstable.state.v2.collections.MutableTrackedList
import gg.essential.elementa.unstable.state.v2.collections.TrackedList

typealias ListState<T> = State<TrackedList<T>>
typealias MutableListState<T> = MutableState<MutableTrackedList<T>>

fun <T> State<List<T>>.toListState(): ListState<T> {
    var oldList = MutableTrackedList<T>()
    return memo {
        val newList = get()
        oldList.applyChanges(TrackedList.Change.estimate(oldList, newList)).also { oldList = it }
    }
}

fun <T, U> ListState<T>.mapChanges(init: (TrackedList<T>) -> U, update: (old: U, changes: Sequence<TrackedList.Change<T>>) -> U): State<U> {
    var trackedList: TrackedList<T>? = null
    var trackedValue: U? = null
    return memo {
        val newList = get()
        val oldList = trackedList
        val newValue =
            if (oldList == null) {
                init(newList)
            } else {
                @Suppress("UNCHECKED_CAST")
                update(trackedValue as U, newList.getChangesSince(oldList))
            }

        trackedList = newList
        trackedValue = newValue

        newValue
    }
}

fun <T, U> ListState<T>.mapChange(init: (TrackedList<T>) -> U, update: (old: U, change: TrackedList.Change<T>) -> U): State<U> =
    mapChanges(init) { old, changes -> changes.fold(old, update) }

fun <T> listStateOf(vararg elements: T): ListState<T> =
    stateOf(MutableTrackedList(mutableListOf(*elements)))

fun <T> mutableListStateOf(vararg elements: T): MutableListState<T> =
    mutableStateOf(MutableTrackedList(mutableListOf(*elements)))

fun <T> MutableListState<T>.set(index: Int, element: T) = set { it.set(index, element) }
fun <T> MutableListState<T>.setAll(newList: List<T>) = set { it.applyChanges(TrackedList.Change.estimate(it, newList)) }
fun <T> MutableListState<T>.add(element: T) = set { it.add(element) }
fun <T> MutableListState<T>.add(index: Int, element: T) = set { it.add(index, element) }
fun <T> MutableListState<T>.addAll(elements: List<T>) = set { it.addAll(elements) }
fun <T> MutableListState<T>.remove(element: T) = set { it.remove(element) }
fun <T> MutableListState<T>.removeAt(index: Int) = set { it.removeAt(index) }
fun <T> MutableListState<T>.removeAll(predicate: (T) -> Boolean) = set { it.removeAll(it.filter(predicate)) }
fun <T> MutableListState<T>.clear() = set { it.clear() }
