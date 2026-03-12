package gg.essential.elementa.unstable.state.v2.collections

import gg.essential.elementa.unstable.state.v2.collections.TrackedList.Change
import gg.essential.elementa.unstable.state.v2.collections.TrackedList.Change.Companion.estimate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TrackedListTest {
    private infix fun <T> Int.add(value: T): Change<T> =
        TrackedList.Add(IndexedValue(this, value))
    private infix fun <T> Int.del(value: T): Change<T> =
        TrackedList.Remove(IndexedValue(this, value))
    private fun <T> clear(oldElements: List<T>): Change<T> =
        TrackedList.Clear(oldElements)

    @Test
    fun testUnchanged() {
        assertEquals(listOf(), estimate<Int>(listOf(), listOf()))
        assertEquals(listOf(), estimate(listOf(1), listOf(1)))
        assertEquals(listOf(), estimate(listOf(1, 2), listOf(1, 2)))
        assertEquals(listOf(), estimate(listOf(1, 2, 3), listOf(1, 2, 3)))
    }

    @Test
    fun testClear() {
        assertEquals(listOf(clear(listOf(1))), estimate(listOf(1), listOf()))
        assertEquals(listOf(clear(listOf(1, 2))), estimate(listOf(1, 2), listOf()))
        assertEquals(listOf(clear(listOf(1, 2, 3))), estimate(listOf(1, 2, 3), listOf()))
    }

    @Test
    fun testAdd() {
        assertEquals(listOf(0 add 1), estimate(listOf(), listOf(1)))

        assertEquals(listOf(0 add 1, 1 add 2), estimate(listOf(), listOf(1, 2)))
        assertEquals(listOf(0 add 1, 1 add 2, 2 add 3), estimate(listOf(), listOf(1, 2, 3)))

        assertEquals(listOf(1 add 2), estimate(listOf(1), listOf(1, 2)))
        assertEquals(listOf(1 add 2, 2 add 3), estimate(listOf(1), listOf(1, 2, 3)))

        assertEquals(listOf(0 add 1), estimate(listOf(2), listOf(1, 2)))
        assertEquals(listOf(0 add 1, 2 add 3), estimate(listOf(2), listOf(1, 2, 3)))

        assertEquals(listOf(0 add 1, 1 add 2), estimate(listOf(3), listOf(1, 2, 3)))

        assertEquals(listOf(2 add 3), estimate(listOf(1, 2), listOf(1, 2, 3)))
        assertEquals(listOf(1 add 2), estimate(listOf(1, 3), listOf(1, 2, 3)))
        assertEquals(listOf(0 add 1), estimate(listOf(2, 3), listOf(1, 2, 3)))
    }

    @Test
    fun testRemove() {
        assertEquals(listOf(1 del 2), estimate(listOf(1, 2), listOf(1)))
        assertEquals(listOf(0 del 1), estimate(listOf(1, 2), listOf(2)))
        assertEquals(listOf(0 del 1), estimate(listOf(1, 2, 3), listOf(2, 3)))
        assertEquals(listOf(1 del 2), estimate(listOf(1, 2, 3), listOf(1, 3)))
        assertEquals(listOf(2 del 3), estimate(listOf(1, 2, 3), listOf(1, 2)))
        assertEquals(listOf(1 del 2, 1 del 3), estimate(listOf(1, 2, 3), listOf(1)))
        assertEquals(listOf(0 del 1, 1 del 3), estimate(listOf(1, 2, 3), listOf(2)))
        assertEquals(listOf(0 del 1, 0 del 2), estimate(listOf(1, 2, 3), listOf(3)))
    }

    @Test
    fun testReplace() {
        assertEquals(listOf(0 del 1, 0 add 9), estimate(listOf(1), listOf(9)))
        assertEquals(listOf(0 del 1, 0 add 9), estimate(listOf(1, 2), listOf(9, 2)))
        assertEquals(listOf(1 del 2, 1 add 9), estimate(listOf(1, 2), listOf(1, 9)))
        assertEquals(listOf(0 del 1, 0 add 8, 1 del 2, 1 add 9), estimate(listOf(1, 2), listOf(8, 9)))
        assertEquals(listOf(0 del 1, 0 add 9), estimate(listOf(1, 2, 3), listOf(9, 2, 3)))
        assertEquals(listOf(1 del 2, 1 add 9), estimate(listOf(1, 2, 3), listOf(1, 9, 3)))
        assertEquals(listOf(2 del 3, 2 add 9), estimate(listOf(1, 2, 3), listOf(1, 2, 9)))
    }

    // Note: Unlike above, the exact output sequence of the following tests is not guaranteed (sometimes there are even
    //       multiple optimal sequences), so we only test the correctness of the sequence, not the exact values.
    private fun <T> checkEstimate(old: List<T>, new: List<T>) {
        val changes = estimate(old, new)
        val actual = old.toMutableList()
        for (change in changes) {
            when (change) {
                is TrackedList.Clear -> actual.clear()
                is TrackedList.Add -> actual.add(change.element.index, change.element.value)
                is TrackedList.Remove -> assertEquals(actual.removeAt(change.element.index), change.element.value)
            }
        }
        assertEquals(new, actual, "Invalid changes $changes")
    }

    @Test
    fun testCombinations() {
        checkEstimate(listOf(1, 2, 3), listOf(1, 9, 3, 4))
        checkEstimate(listOf(1, 2, 3), listOf(1, 9))
        checkEstimate(listOf(1, 2, 3), listOf(2, 3, 4))
    }
}
