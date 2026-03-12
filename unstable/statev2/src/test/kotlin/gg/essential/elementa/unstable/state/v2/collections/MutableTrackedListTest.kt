package gg.essential.elementa.unstable.state.v2.collections

import gg.essential.elementa.unstable.state.v2.collections.TrackedList.Change
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MutableTrackedListTest {
    private infix fun <T> Int.add(value: T): Change<T> =
        TrackedList.Add(IndexedValue(this, value))
    private infix fun <T> Int.del(value: T): Change<T> =
        TrackedList.Remove(IndexedValue(this, value))
    private fun <T> clear(vararg oldElements: T): Change<T> =
        TrackedList.Clear(oldElements.toList())

    @Test
    fun testChanges() {
        val le = MutableTrackedList<Int>()
        assertEquals(listOf(), le.getChangesSince(le).toList())
        val l0 = le.add(0)
        assertEquals(listOf(0 add 0), l0.getChangesSince(le).toList())
        assertEquals(listOf(0 del 0), le.getChangesSince(l0).toList())
        assertEquals(listOf(), l0.getChangesSince(l0).toList())
        val l1 = l0.add(10)
        assertEquals(listOf(), l1.getChangesSince(l1).toList())
        assertEquals(listOf(1 add 10), l1.getChangesSince(l0).toList())
        assertEquals(listOf(1 del 10), l0.getChangesSince(l1).toList())
        val l2 = l1.add(20)
        assertEquals(listOf(), l2.getChangesSince(l2).toList())
        assertEquals(listOf(2 add 20), l2.getChangesSince(l1).toList())
        assertEquals(listOf(2 del 20), l1.getChangesSince(l2).toList())
        assertEquals(listOf(1 add 10, 2 add 20), l2.getChangesSince(l0).toList())
        assertEquals(listOf(2 del 20, 1 del 10), l0.getChangesSince(l2).toList())
        assertEquals(listOf(0 add 0, 1 add 10, 2 add 20), l2.getChangesSince(le).toList())
        assertEquals(listOf(2 del 20, 1 del 10, 0 del 0), le.getChangesSince(l2).toList())
    }

    @Test
    fun testUnrelated() {
        val a0 = MutableTrackedList<Int>()
        val b0 = MutableTrackedList<Int>()
        assertEquals(listOf(), a0.getChangesSince(b0).toList())
        val a1 = a0.add(10)
        val b1 = b0.add(100)
        assertEquals(listOf(0 add 10), a1.getChangesSince(b0).toList())
        assertEquals(listOf(clear(10)), b0.getChangesSince(a1).toList())
        assertEquals(listOf(0 del 100, 0 add 10), a1.getChangesSince(b1).toList())
        assertEquals(listOf(0 del 10, 0 add 100), b1.getChangesSince(a1).toList())
    }
}
