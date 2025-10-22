package gg.essential.elementa.unstable.state.v2.collections

/**
 * An immutable Set type that remembers the changes that have been applied to construct it, allowing one to very
 * cheaply obtain a "diff" between it and one of the sets it has been constructed from.
 *
 * The exact meaning of "very cheaply" may differ depending on the specific implementation but should never be worse
 * than `O(n+m)` where `n` is the size of both sets and `m` is the amount of changes that have happened between
 * two sets. In the best case it should just be `O(m)`.
 *
 * If two unrelated tracked sets are compared with each other, the result will usually just equal [Change.estimate],
 * which takes `O(n)`.
 *
 * Beware that even though sets of this type appear to be immutable, they are not guaranteed to be internally immutable
 * (for performance reasons) and as such are not generally thread-safe.
 */
interface TrackedSet<out E> : Set<E> {
    /** Returns changes one would have to apply to [other] to obtain `this`. */
    fun getChangesSince(other: TrackedSet<@UnsafeVariance E>): Sequence<Change<E>>

    data class Add<E>(val element: E) : Change<E>
    data class Remove<E>(val element: E) : Change<E>
    data class Clear<E>(val oldElements: Set<E>) : Change<E>

    sealed interface Change<out E> {
        companion object {
            /**
             * Estimates the changes one would have to apply to [oldSet] to obtain [newSet].
             */
            fun <E> estimate(oldSet: Set<E>, newSet: Set<E>): List<Change<E>> {
                return if (newSet.isEmpty()) {
                    if (oldSet.isEmpty()) {
                        emptyList()
                    } else {
                        listOf(Clear(oldSet))
                    }
                } else {
                    val changes = mutableListOf<Change<E>>()

                    for (newValue in newSet) {
                        if (newValue !in oldSet) {
                            changes.add(Add(newValue))
                        }
                    }
                    for (oldValue in oldSet) {
                        if (oldValue !in newSet) {
                            changes.add(Remove(oldValue))
                        }
                    }

                    changes
                }
            }
        }
    }
}