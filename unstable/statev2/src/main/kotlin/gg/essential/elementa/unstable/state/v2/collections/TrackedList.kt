package gg.essential.elementa.unstable.state.v2.collections

/**
 * An immutable List type that remembers the changes that have been applied to construct it, allowing one to very
 * cheaply obtain a "diff" between it and one of the lists it has been constructed from.
 *
 * The exact meaning of "very cheaply" may differ depending on the specific implementation but should never be worse
 * than `O(n+m)` where `n` is the size of both lists and `m` is the amount of changes that have happened between
 * two lists. In the best case it should just be `O(m)`.
 *
 * If two unrelated tracked lists are compared with each other, the result will usually just equal [Change.estimate],
 * which takes `O(n)`.
 *
 * Beware that even though lists of this type appear to be immutable, they are not guaranteed to be internally immutable
 * (for performance reasons) and as such are not generally thread-safe.
 */
interface TrackedList<out E> : List<E> {
    /** Returns changes one would have to apply to [other] to obtain `this`. */
    fun getChangesSince(other: TrackedList<@UnsafeVariance E>): Sequence<Change<E>>

    data class Add<E>(val element: IndexedValue<E>) : Change<E>
    data class Remove<E>(val element: IndexedValue<E>) : Change<E>
    data class Clear<E>(val oldElements: List<E>) : Change<E>

    sealed interface Change<out E> {
        companion object {
            /**
             * Estimates the changes one would have to apply to [oldList] to obtain [newList].
             *
             * Note that while the estimate is correct (i.e. the changes will result in [newList]), it is not
             * necessarily minimal (i.e. there may be a shorter list of changes that would also result in [newList]),
             * nor is it accurate (i.e. even if both arguments are [MutableTrackedList]s, the returned changes may
             * differ from how those lists were actually created).
             *
             * The result is however minimal if only one of additions, removals, or updates (`set`) were applied between
             * [oldList] and [newList]. It may also be minimal if a mix of these was applied but no guarantees are made
             * in that case.
             */
            fun <E> estimate(oldList: List<E>, newList: List<E>): List<Change<E>> {
                return if (newList.isEmpty()) {
                    if (oldList.isEmpty()) {
                        emptyList()
                    } else {
                        listOf(Clear(oldList))
                    }
                } else {
                    val changes = mutableListOf<Change<E>>()

                    var oldIndex = 0
                    var newIndex = 0

                    while (oldIndex <= oldList.lastIndex && newIndex <= newList.lastIndex) {
                        val oldValue = oldList[oldIndex]
                        val newValue = newList[newIndex]
                        if (oldValue == newValue) {
                            oldIndex++
                            newIndex++
                            continue
                        }
                        if (newList.size == oldList.size) {
                            changes.add(Remove(IndexedValue(newIndex, oldValue)))
                            changes.add(Add(IndexedValue(newIndex, newValue)))
                            oldIndex++
                            newIndex++
                        } else if (newList.size - newIndex > oldList.size - oldIndex) {
                            changes.add(Add(IndexedValue(newIndex, newValue)))
                            newIndex++
                        } else {
                            changes.add(Remove(IndexedValue(newIndex, oldValue)))
                            oldIndex++
                        }
                    }

                    while (newIndex <= newList.lastIndex) {
                        changes.add(Add(IndexedValue(newIndex, newList[newIndex])))
                        newIndex++
                    }

                    while (oldIndex <= oldList.lastIndex) {
                        changes.add(Remove(IndexedValue(newIndex, oldList[oldIndex])))
                        oldIndex++
                    }

                    changes
                }
            }
        }
    }
}