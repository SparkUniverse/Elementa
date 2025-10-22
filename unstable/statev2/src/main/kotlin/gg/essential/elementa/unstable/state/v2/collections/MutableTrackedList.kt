package gg.essential.elementa.unstable.state.v2.collections

import kotlin.collections.AbstractList

/**
 * An immutable List type that remembers the changes that have been applied to it, allowing one to very cheaply obtain
 * a "diff" between its current and an older version via [getChangesSince].
 *
 * To maintain good performance, the implementation assumes that the standard use case involves only a single chain
 * of changes and that older lists are only ever compared to newer list, not read from directly.
 * For this standard use case, it maintains performance characteristics similar to the regular [mutableListOf] in terms
 * of memory and runtime.
 *
 * Non-standard use cases are supported but will generally have performance of `O(n+m)` where `n` is the size of the
 * latest list and `m` is the amount of changes that have happened between this list and the latest list (i.e. only the
 * latest list contains the full array of values, previous lists only contain a change and their successor list).
 */
class MutableTrackedList<E> private constructor(
    /** Counter increased with every change. Used to quickly determine which of two lists is older. */
    private val generation: Int,
    realList: MutableList<E>,
) : AbstractList<E>(), TrackedList<E> {

    private var maybeRealList: MutableList<E>? = realList
    private val realList: MutableList<E>
        get() = maybeRealList ?: computeRealList()

    private var nextList: MutableTrackedList<E>? = null
    private var nextDiff: Diff<E>? = null

    /** Computes the real list for this list from the next list(s). */
    private fun computeRealList(): MutableList<E> {
        val generations = generateSequence(this) { if (it.maybeRealList != null) null else it.nextList }.toList()
        val list = generations.last().realList.toMutableList()
        for (i in generations.indices.reversed()) {
            generations[i].nextDiff?.revert(list)
        }
        maybeRealList = list
        return list
    }

    /** Creates a child list based on this list with the given diff. */
    private fun fork(
        diff: Diff<E>,
        child: MutableTrackedList<E> = MutableTrackedList(generation + 1, realList),
    ): MutableTrackedList<E> {

        // Relinquish ownership of our real list, it now belongs to the child
        maybeRealList = null

        // We only want to update our next pointer if we don't yet have one, otherwise we risk changing the result of
        // future diff calls (compared to what they previously returned).
        if (nextList == null) {
            nextList = child
            nextDiff = diff
        }

        // Finally, apply the diff
        diff.apply(child.realList)

        return child
    }

    override fun getChangesSince(other: TrackedList<E>): Sequence<TrackedList.Change<E>> {
        return if (other is MutableTrackedList) {
            getChangesSince(other)
        } else {
            TrackedList.Change.estimate(other, this).asSequence()
        }
    }

    fun getChangesSince(other: MutableTrackedList<E>): Sequence<TrackedList.Change<E>> {
        // Trivial case: no changes
        if (other == this) {
            return emptySequence()
        }

        // Fast path: single diff only
        if (other.nextList == this) {
            return other.nextDiff!!.asChangeSequence()
        }

        if (other.generation < this.generation) {
            // Regular diff
            val generations = generateSequence(other) { if (it == this) null else it.nextList }.toMutableList()
            if (generations.removeLast() != this) return TrackedList.Change.estimate(other, this).asSequence()
            return generations.asSequence().flatMap { it.nextDiff!!.asChangeSequence() }
        } else {
            // Reverse diff
            val generations = generateSequence(this) { if (it == other) null else it.nextList }.toMutableList()
            if (generations.removeLast() != other) return TrackedList.Change.estimate(other, this).asSequence()
            return generations.asReversed().asSequence().flatMap { it.nextDiff!!.asInverseChangeSequence() }
        }
    }

    constructor(mutableList: MutableList<E> = mutableListOf()) : this(0, mutableList)

    override val size: Int
        get() = realList.size

    override fun get(index: Int): E = realList[index]

    fun set(index: Int, element: E) =
        fork(Diff.Multiple(listOf(Diff.Removal(index, realList[index]), Diff.Addition(index, element))))

    fun add(element: E) = add(size, element)
    fun add(index: Int, element: E) = fork(Diff.Addition(index, element))
    fun addAll(elements: Collection<E>) = addAll(size, elements)
    fun addAll(index: Int, elements: Collection<E>) = fork(Diff.Multiple(elements.mapIndexed { i, e -> Diff.Addition(index + i, e) }))

    fun clear(): MutableTrackedList<E> = fork(Diff.Clear(realList), MutableTrackedList(generation + 1, mutableListOf()))

    fun remove(element: E): MutableTrackedList<E> {
        val index = indexOf(element)
        return if (index == -1) this else fork(Diff.Removal(index, element))
    }
    fun removeAt(index: Int) = fork(Diff.Removal(index, this[index]))
    fun removeAll(elements: Collection<E>): MutableTrackedList<E> {
        val diffs = elements.mapNotNull { element ->
            val index = indexOf(element)
            if (index == -1) null else Diff.Removal(index, element)
        }.sortedBy { -it.index }
        return if (diffs.isEmpty()) this else fork(Diff.Multiple(diffs))
    }
    fun retainAll(elements: Collection<E>): MutableTrackedList<E> {
        val diffs = realList.mapIndexedNotNull { index, element ->
            if (element in elements) null else Diff.Removal(index, element)
        }.reversed()
        return if (diffs.isEmpty()) this else fork(Diff.Multiple(diffs))
    }

    fun applyChanges(changes: List<TrackedList.Change<E>>): MutableTrackedList<E> {
        if (changes.isEmpty()) return this
        return fork(changes.map {
            when (it) {
                is TrackedList.Add -> Diff.Addition(it.element.index, it.element.value)
                is TrackedList.Remove -> Diff.Removal(it.element.index, it.element.value)
                is TrackedList.Clear -> Diff.Clear(it.oldElements.toList())
            }
        }.let { it.singleOrNull() ?: Diff.Multiple(it) })
    }

    private sealed interface Diff<E> {
        fun apply(list: MutableList<E>)
        fun revert(list: MutableList<E>)
        fun asChangeSequence(): Sequence<TrackedList.Change<E>>
        fun asInverseChangeSequence(): Sequence<TrackedList.Change<E>>

        data class Addition<E>(val index: Int, val element: E) : Diff<E> {
            override fun apply(list: MutableList<E>) {
                list.add(index, element)
            }

            override fun revert(list: MutableList<E>) {
                list.removeAt(index)
            }

            override fun asChangeSequence(): Sequence<TrackedList.Change<E>> =
                sequenceOf(TrackedList.Add(IndexedValue(index, element)))

            override fun asInverseChangeSequence(): Sequence<TrackedList.Change<E>> =
                sequenceOf(TrackedList.Remove(IndexedValue(index, element)))
        }

        data class Removal<E>(val index: Int, val element: E) : Diff<E> {
            override fun apply(list: MutableList<E>) {
                list.removeAt(index)
            }

            override fun revert(list: MutableList<E>) {
                list.add(index, element)
            }

            override fun asChangeSequence(): Sequence<TrackedList.Change<E>> =
                sequenceOf(TrackedList.Remove(IndexedValue(index, element)))

            override fun asInverseChangeSequence(): Sequence<TrackedList.Change<E>> =
                sequenceOf(TrackedList.Add(IndexedValue(index, element)))
        }

        data class Clear<E>(val oldList: List<E>) : Diff<E> {
            override fun apply(list: MutableList<E>) {
                list.clear()
            }

            override fun revert(list: MutableList<E>) {
                list.addAll(oldList)
            }

            override fun asChangeSequence(): Sequence<TrackedList.Change<E>> =
                sequenceOf(TrackedList.Clear(oldList))

            override fun asInverseChangeSequence(): Sequence<TrackedList.Change<E>> =
                oldList.withIndex().asSequence().map { TrackedList.Add(it) }
        }

        data class Multiple<E>(val diffs: List<Diff<E>>) : Diff<E> {
            override fun revert(list: MutableList<E>) {
                for (i in diffs.indices.reversed()) {
                    diffs[i].revert(list)
                }
            }

            override fun apply(list: MutableList<E>) {
                for (change in diffs) {
                    change.apply(list)
                }
            }

            override fun asChangeSequence(): Sequence<TrackedList.Change<E>> =
                diffs.asSequence().flatMap { it.asChangeSequence() }

            override fun asInverseChangeSequence(): Sequence<TrackedList.Change<E>> =
                diffs.asReversed().asSequence().flatMap { it.asInverseChangeSequence() }
        }
    }
}
