package gg.essential.elementa.unstable.common

import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.State
import gg.essential.elementa.unstable.state.v2.collections.TrackedList

typealias AddListener<T> = (index: Int, element: T) -> Unit
typealias SetListener<T> = (index: Int, element: T, oldElement: T) -> Unit
typealias RemoveListener<T> = AddListener<T>
typealias ClearListener<T> = (list: List<T>) -> Unit

@Deprecated("Using StateV1 is discouraged, use StateV2 instead", ReplaceWith("mutableListState(state)", "gg.essential.elementa.unstable.state.v2.ListKt.mutableListState"))
class ListState<T>(initialList: MutableList<T> = mutableListOf()) : BasicState<MutableList<T>>(initialList) {
    private val addListeners = Listeners<AddListener<T>>()
    private val setListeners = Listeners<SetListener<T>>()
    private val removeListeners = Listeners<RemoveListener<T>>()
    private val clearListeners = Listeners<ClearListener<T>>()

    fun onAdd(action: AddListener<T>) = apply {
        addListeners.add(action)
    }

    fun onSet(action: SetListener<T>) = apply {
        setListeners.add(action)
    }

    fun onRemove(action: RemoveListener<T>) = apply {
        removeListeners.add(action)
    }

    fun onClear(action: ClearListener<T>) = apply {
        clearListeners.add(action)
    }

    fun add(element: T) = apply {
        add(get().size, element)
    }

    fun add(index: Int, element: T) = apply {
        get().add(index, element)
        addListeners.forEach { it(index, element) }
    }

    fun set(index: Int, element: T) = apply {
        get().also { list ->
            val oldValue = list[index]
            if (element == oldValue)
                return@also
            list[index] = element
            setListeners.forEach { it(index, element, oldValue) }
        }
    }

    fun remove(element: T) = apply {
        removeAt(get().indexOf(element))
    }

    fun removeAt(index: Int) = apply {
        get().also { list ->
            val element = list.removeAt(index)
            removeListeners.forEach { it(index, element) }
        }
    }

    fun clear() = apply {
        val list = get()
        val values = list.toList()
        list.clear()
        clearListeners.forEach { it(values) }
    }

    fun onElementAddedOrPresent(action: (element: T) -> Unit) = apply {
        onElementAdded(action)
        get().forEach(action)
    }

    fun onElementAdded(action: (element: T) -> Unit) = apply {
        onAdd { _, element ->
            action(element)
        }

        onSet { _, element, _ ->
            action(element)
        }
    }

    fun onElementRemoved(action: (element: T) -> Unit) = apply {
        onSet { _, _, oldElement ->
            action(oldElement)
        }

        onRemove { _, element ->
            action(element)
        }

        onClear {
            it.forEach(action)
        }
    }

    fun onElementAddedOrRemoved(action: (element: T) -> Unit) = apply {
        onElementAdded(action)
        onElementRemoved(action)
    }

    fun onElementAddedOrRemovedOrPresent(action: (element: T) -> Unit) = apply {
        onElementAddedOrRemoved(action)
        get().forEach(action)
    }

    operator fun contains(element: T) = element in get()

    fun <U> reduce(mapper: (List<T>) -> U) = MappedListState(this, mapper)

    companion object {
        fun <T> from(state: State<List<T>>): ListState<T> {
            val listState = ListState<T>()
            state.onSetValueAndNow { newList ->
                for (change in TrackedList.Change.estimate(listState.get(), newList)) {
                    when (change) {
                        is TrackedList.Clear -> listState.clear()
                        is TrackedList.Add -> listState.add(change.element.index, change.element.value)
                        is TrackedList.Remove -> listState.removeAt(change.element.index)
                    }
                }
            }
            return listState
        }
    }
}

fun <T, U> ListState<T>.mapList(mapper: (List<T>) -> List<U>): ListState<U> = ListState.from(reduce(mapper))

// TODO: all of these are quite inefficient, might make sense to implement some as efficient primitives instead

fun <T> ListState<T>.filter(filter: (T) -> Boolean) = mapList { it.filter(filter) }

fun <T, U> ListState<T>.map(mapper: (T) -> U) = mapList { it.map(mapper) }

fun <T, U, V> ListState<T>.zip(otherState: State<U>, transform: (T, U) -> V) =
    ListState.from(reduce { it.toList() }.zip(otherState).map { (list, other) -> list.map { transform(it, other) } })

fun <T, U, V> ListState<T>.zip(otherList: ListState<U>, transform: (T, U) -> V) =
    ListState.from(reduce { it.toList() }.zip(otherList.reduce { it.toList() }).map { (a, b) -> a.zip(b, transform) })

fun <T, U> ListState<T>.mapNotNull(mapper: (T) -> U?) = mapList { it.mapNotNull(mapper) }

fun <T> ListState<T?>.filterNotNull() = mapList { it.filterNotNull() }

inline fun <reified T> ListState<*>.filterIsInstance() = mapList { it.filterIsInstance<T>() }

@Deprecated("Using StateV1 is discouraged, use StateV2 instead", ReplaceWith("state.map", "gg.essential.elementa.unstable.state.v2.combinators.StateKt.map"))
class MappedListState<T, U>(state: ListState<T>, mapper: (List<T>) -> U) : BasicState<U>(mapper(state.get())) {
    init {
        state.onAdd { _, _ ->
            set(mapper(state.get()))
        }

        state.onSet { _, _, _ ->
            set(mapper(state.get()))
        }

        state.onRemove { _, _ ->
            set(mapper(state.get()))
        }

        state.onClear {
            set(mapper(emptyList()))
        }
    }
}

/** A mutable list of listeners that is safe to extend while being iterated. */
private class Listeners<T> {
    private val active = mutableListOf<T>()
    private val new = mutableListOf<T>()

    fun add(listener: T) {
        new.add(listener)
    }

    fun forEach(caller: (T) -> Unit) {
        if (new.isNotEmpty()) {
            active.addAll(new)
            new.clear()
        }
        active.forEach(caller)
    }
}
