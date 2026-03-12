package gg.essential.elementa.unstable.state.v2

import gg.essential.elementa.state.v2.ReferenceHolder
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoTest {
    @Test
    fun testEmptyMemo() {
        var ran = 0
        val state = memo {
            ran++
            "value"
        }
        assertEquals(0, ran, "memo should be lazy")
        assertEquals("value", state.getUntracked())
        assertEquals(1, ran, "memo should only be evaluated once")
        assertEquals("value", state.getUntracked())
        assertEquals(1, ran, "memo result should be memoized")
    }

    @Test
    fun testSimpleMemo() {
        val state = mutableStateOf(0)

        var ran = 0
        val memoState = memo {
            ran++
            state() + 1
        }
        assertEquals(0, ran, "memo should be lazy")
        assertEquals(1, memoState.getUntracked())
        assertEquals(1, ran, "memo should only be evaluated once")
        assertEquals(1, memoState.getUntracked())
        assertEquals(1, ran, "memo result should be memoized")

        state.set(1)
        // Ideally we wouldn't run the computation if no one is subscribed, current implementation does though
        // assertEquals(1, ran, "memo should not be updated if no one is subscribed")
        assertEquals(2, memoState.getUntracked())
        assertEquals(2, ran, "memo should only be evaluated once")
        assertEquals(2, memoState.getUntracked())
        assertEquals(2, ran, "memo result should be memoized")

        state.set(2)
        // Ideally we wouldn't run the computation if no one is subscribed, current implementation does though
        // assertEquals(2, ran, "memo should not be updated if no one is subscribed")
        assertEquals(3, memoState.getUntracked())
        assertEquals(3, ran, "memo should only be evaluated once")
        assertEquals(3, memoState.getUntracked())
        assertEquals(3, ran, "memo result should be memoized")
    }

    @Test
    fun testSimpleMemoWithEffect() {
        val state = mutableStateOf(Pair(0, false))
        val memoState = memo { state().first }

        var expecting: Int?

        expecting = 0
        val unregister = effect(ReferenceHolder.Weak) {
            assertEquals(expecting, memoState())
            expecting = null // we're only expecting a single call per change
        }
        assertEquals(expecting, null, "Effect should have ran")

        expecting = 1
        state.set(Pair(1, false))
        assertEquals(expecting, null, "Effect should have ran")

        expecting = null // effect shouldn't run because the memo should produce the same result as before
        state.set(Pair(1, true))

        expecting = 2
        state.set(Pair(2, false))
        assertEquals(expecting, null, "Effect should have ran")

        unregister()

        expecting = null
        state.set(Pair(3, false))
        state.set(Pair(4, false))
        state.set(Pair(5, false))
        assertEquals(5, memoState.getUntracked())
    }

    @Test
    fun testMemoWithEffectAndDiamondDependency() {
        val state = mutableStateOf(0)
        val a = memo { state() + 1 }
        val b = memo { state() + 2 }
        val c = memo {
            val aVal = a()
            val bVal = b()
            assert(aVal + 1 == bVal)
            aVal + bVal
        }

        var expecting: Int?

        expecting = 3
        val unregister = effect(ReferenceHolder.Weak) {
            assertEquals(expecting, c())
            expecting = null // we're only expecting a single call per change
        }
        assertEquals(expecting, null, "Effect should have ran")

        expecting = 5
        state.set(1)
        assertEquals(expecting, null, "Effect should have ran")

        expecting = 7
        state.set(2)
        assertEquals(expecting, null, "Effect should have ran")

        unregister()

        expecting = null
        state.set(3)
        state.set(4)
        state.set(5)
        assertEquals(13, c.getUntracked())
        assertEquals(7, b.getUntracked())
        assertEquals(6, a.getUntracked())
    }

    @Test
    fun testMemoWithDelayedDependency() {
        val state = mutableStateOf(0)

        val memo = memo { lazy { state() } }

        var ran = 0
        lateinit var lazyValue: Lazy<Int>
        val unregister = effect(ReferenceHolder.Weak) {
            ran++
            lazyValue = memo()
        }
        assertEquals(1, ran)

        assertEquals(0, lazyValue.value)
        assertEquals(1, ran, "Merely observing shouldn't change the result of the memo")

        state.set(1)
        assertEquals(2, ran, "Effect should have ran")

        state.set(2)
        assertEquals(2, ran, "Effect should not have ran, we haven't re-subscribed")

        assertEquals(2, lazyValue.value)

        state.set(3)
        assertEquals(3, ran, "Effect should have ran")

        unregister()

        assertEquals(3, lazyValue.value)

        state.set(4)
        assertEquals(3, ran, "Effect should not have ran, it had been unregistered")
    }
}
