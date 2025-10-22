package gg.essential.elementa.unstable.state.v2.collections

/**
 * An immutable Set type that remembers the changes that have been applied to it, allowing one to very cheaply obtain
 * a "diff" between its current and an older version via [getChangesSince].
 *
 * To maintain good performance, the implementation assumes that the standard use case involves only a single chain
 * of changes and that older sets are only ever compared to newer sets, not read from directly.
 * For this standard use case, it maintains performance characteristics similar to the regular [mutableSetOf] in terms
 * of memory and runtime.
 *
 * Non-standard use cases are supported but will generally have performance of `O(n+m)` where `n` is the size of the
 * latest set and `m` is the amount of changes that have happened between this set and the latest set (i.e. only the
 * latest set contains the full array of values, previous sets only contain a change and their successor set).
 *
 * In the standard use case, the iteration order for the latest version matches insertion order. For all other use cases
 * and versions, iteration order is undefined.
 */
class MutableTrackedSet<E> private constructor(
    /** Counter increased with every change. Used to quickly determine which of two sets is older. */
    private val generation: Int,
    realSet: MutableSet<E>,
) : AbstractSet<E>(), TrackedSet<E> {

    private var maybeRealSet: MutableSet<E>? = realSet
    private val realSet: MutableSet<E>
        get() = maybeRealSet ?: computeRealSet()

    private var nextSet: MutableTrackedSet<E>? = null
    private var nextDiff: Diff<E>? = null

    /** Computes the real set for this set from the next set(s). */
    private fun computeRealSet(): MutableSet<E> {
        val generations = generateSequence(this) { if (it.maybeRealSet != null) null else it.nextSet }.toList()
        val set = generations.last().realSet.toMutableSet()
        for (i in generations.indices.reversed()) {
            generations[i].nextDiff?.revert(set)
        }
        maybeRealSet = set
        return set
    }

    /** Creates a child set based on this set with the given diff. */
    private fun fork(
        diff: Diff<E>,
        child: MutableTrackedSet<E> = MutableTrackedSet(generation + 1, realSet),
    ): MutableTrackedSet<E> {

        // Relinquish ownership of our real set, it now belongs to the child
        maybeRealSet = null

        // We only want to update our next pointer if we don't yet have one, otherwise we risk changing the result of
        // future diff calls (compared to what they previously returned).
        if (nextSet == null) {
            nextSet = child
            nextDiff = diff
        }

        // Finally, apply the diff
        diff.apply(child.realSet)

        return child
    }

    override fun getChangesSince(other: TrackedSet<E>): Sequence<TrackedSet.Change<E>> {
        return if (other is MutableTrackedSet) {
            getChangesSince(other)
        } else {
            TrackedSet.Change.estimate(other, this).asSequence()
        }
    }

    fun getChangesSince(other: MutableTrackedSet<E>): Sequence<TrackedSet.Change<E>> {
        // Trivial case: no changes
        if (other == this) {
            return emptySequence()
        }

        // Fast path: single diff only
        if (other.nextSet == this) {
            return other.nextDiff!!.asChangeSequence()
        }

        if (other.generation < this.generation) {
            // Regular diff
            val generations = generateSequence(other) { if (it == this) null else it.nextSet }.toMutableList()
            if (generations.removeLast() != this) return TrackedSet.Change.estimate(other, this).asSequence()
            return generations.asSequence().flatMap { it.nextDiff!!.asChangeSequence() }
        } else {
            // Reverse diff
            val generations = generateSequence(this) { if (it == other) null else it.nextSet }.toMutableList()
            if (generations.removeLast() != other) return TrackedSet.Change.estimate(other, this).asSequence()
            return generations.asReversed().asSequence().flatMap { it.nextDiff!!.asInverseChangeSequence() }
        }
    }

    constructor(mutableSet: MutableSet<E> = mutableSetOf()) : this(0, mutableSet)

    override val size: Int
        get() = realSet.size

    override fun iterator(): Iterator<E> = realSet.iterator()
    override fun contains(element: E): Boolean = realSet.contains(element)

    fun add(element: E) = if (element in this) this else fork(Diff.Addition(element))
    fun addAll(elements: Collection<E>): MutableTrackedSet<E> {
        val diffs = elements.mapNotNull { element ->
            if (element in this) null else Diff.Addition(element)
        }
        return if (diffs.isEmpty()) this else fork(Diff.Multiple(diffs))
    }

    fun clear(): MutableTrackedSet<E> = fork(Diff.Clear(realSet), MutableTrackedSet(generation + 1, mutableSetOf()))

    fun remove(element: E) = if (element in this) fork(Diff.Removal(element)) else this
    fun removeAll(elements: Collection<E>): MutableTrackedSet<E> {
        val diffs = elements.mapNotNull { element ->
            if (element in this) Diff.Removal(element) else null
        }
        return if (diffs.isEmpty()) this else fork(Diff.Multiple(diffs))
    }
    fun retainAll(elements: Collection<E>): MutableTrackedSet<E> {
        val diffs = realSet.mapNotNull { element ->
            if (element in elements) null else Diff.Removal(element)
        }
        return if (diffs.isEmpty()) this else fork(Diff.Multiple(diffs))
    }

    fun applyChanges(changes: List<TrackedSet.Change<E>>): MutableTrackedSet<E> {
        if (changes.isEmpty()) return this
        return fork(changes.map {
            when (it) {
                is TrackedSet.Add -> Diff.Addition(it.element)
                is TrackedSet.Remove -> Diff.Removal(it.element)
                is TrackedSet.Clear -> Diff.Clear(it.oldElements.toSet())
            }
        }.let { it.singleOrNull() ?: Diff.Multiple(it) })
    }

    private sealed interface Diff<E> {
        fun apply(set: MutableSet<E>)
        fun revert(set: MutableSet<E>)
        fun asChangeSequence(): Sequence<TrackedSet.Change<E>>
        fun asInverseChangeSequence(): Sequence<TrackedSet.Change<E>>

        data class Addition<E>(val element: E) : Diff<E> {
            override fun apply(set: MutableSet<E>) {
                set.add(element)
            }

            override fun revert(set: MutableSet<E>) {
                set.remove(element)
            }

            override fun asChangeSequence(): Sequence<TrackedSet.Change<E>> =
                sequenceOf(TrackedSet.Add(element))

            override fun asInverseChangeSequence(): Sequence<TrackedSet.Change<E>> =
                sequenceOf(TrackedSet.Remove(element))
        }

        data class Removal<E>(val element: E) : Diff<E> {
            override fun apply(set: MutableSet<E>) {
                set.remove(element)
            }

            override fun revert(set: MutableSet<E>) {
                set.add(element)
            }

            override fun asChangeSequence(): Sequence<TrackedSet.Change<E>> =
                sequenceOf(TrackedSet.Remove(element))

            override fun asInverseChangeSequence(): Sequence<TrackedSet.Change<E>> =
                sequenceOf(TrackedSet.Add(element))
        }

        data class Clear<E>(val oldSet: Set<E>) : Diff<E> {
            override fun apply(set: MutableSet<E>) {
                set.clear()
            }

            override fun revert(set: MutableSet<E>) {
                set.addAll(oldSet)
            }

            override fun asChangeSequence(): Sequence<TrackedSet.Change<E>> =
                sequenceOf(TrackedSet.Clear(oldSet))

            override fun asInverseChangeSequence(): Sequence<TrackedSet.Change<E>> =
                oldSet.asSequence().map { TrackedSet.Add(it) }
        }

        data class Multiple<E>(val diffs: List<Diff<E>>) : Diff<E> {
            override fun revert(set: MutableSet<E>) {
                for (i in diffs.indices.reversed()) {
                    diffs[i].revert(set)
                }
            }

            override fun apply(set: MutableSet<E>) {
                for (change in diffs) {
                    change.apply(set)
                }
            }

            override fun asChangeSequence(): Sequence<TrackedSet.Change<E>> =
                diffs.asSequence().flatMap { it.asChangeSequence() }

            override fun asInverseChangeSequence(): Sequence<TrackedSet.Change<E>> =
                diffs.asReversed().asSequence().flatMap { it.asInverseChangeSequence() }
        }
    }
}
