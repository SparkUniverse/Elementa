package gg.essential.elementa.unstable.state.v2

import gg.essential.elementa.state.v2.ReferenceHolder
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class EffectTest {
    @Test
    fun testEmptyEffect() {
        var ran = 0
        effect(ReferenceHolder.Weak) {
            ran++
        }
        assertEquals(1, ran)
    }

    @Test
    fun testSimpleEffect() {
        val state = mutableStateOf(0)

        var expecting: Int?

        expecting = 0
        val unregister = effect(ReferenceHolder.Weak) {
            assertEquals(expecting, state())
            expecting = null // we're only expecting a single call per change
        }
        assertEquals(expecting, null, "Effect should have ran")

        expecting = 1
        state.set(1)
        assertEquals(expecting, null, "Effect should have ran")

        expecting = 2
        state.set(2)
        assertEquals(expecting, null, "Effect should have ran")

        unregister()

        expecting = null
        state.set(3)
        state.set(4)
        state.set(5)
    }

    @Test
    fun testEffectWithMultipleAndChangingDependencies() {
        val aState = mutableStateOf(0)
        val bState = mutableStateOf(2)
        val outerState = mutableStateOf(aState)

        var expecting: Int?

        expecting = 0
        val unregister = effect(ReferenceHolder.Weak) {
            assertEquals(expecting, outerState()())
            expecting = null // we're only expecting a single call per change
        }
        assertEquals(expecting, null, "Effect should have ran")

        expecting = 1
        aState.set(1)
        assertEquals(expecting, null, "Effect should have ran")

        expecting = 2
        outerState.set(bState)
        assertEquals(expecting, null, "Effect should have ran")

        // Should no longer depend on aState
        expecting = null
        aState.set(42)

        expecting = 3
        bState.set(3)
        assertEquals(expecting, null, "Effect should have ran")

        unregister()

        expecting = null
        outerState.set(aState)
        aState.set(123)
        bState.set(123)
    }

    @Test
    fun testEffectWithDelayedDependency() {
        val state = mutableStateOf(0)

        var ran = 0
        lateinit var observer: Observer
        val unregister = effect(ReferenceHolder.Weak) {
            ran++
            observer = this@effect
        }
        assertEquals(1, ran)

        with(observer) { assertEquals(0, state()) }

        state.set(1)
        assertEquals(2, ran, "Effect should have ran")

        state.set(2)
        assertEquals(2, ran, "Effect should not have ran, we haven't re-subscribed")

        with(observer) { assertEquals(2, state()) }

        state.set(3)
        assertEquals(3, ran, "Effect should have ran")

        unregister()

        with(observer) { assertEquals(3, state()) }

        state.set(4)
        assertEquals(3, ran, "Effect should not have ran, it had been unregistered")
    }

    @Test
    fun testEffectChangingItsOwnDependencies() {
        val state = mutableStateOf(1)

        val seenValues = mutableListOf<Int>()

        val unregister = effect(ReferenceHolder.Weak) {
            val value = state()
            seenValues.add(value)

            state.set(2)
        }

        assertEquals(listOf(1, 2), seenValues)

        unregister()
    }

    @Test
    fun testEffectGarbageCollection() {
        val state = mutableStateOf(0)

        // Testing whether effects are garbage collected properly isn't exactly easy due to Java not providing
        // any way to **force** garbage collections. So we'll just create effects until it has no choice.
        var createdEffects = 0
        var liveEffects = 0

        // Hold on to effects for a while so the JVM can't just optimize away the whole `effect` call
        val heldEffects = mutableListOf<Any>()

        val startTime = TimeSource.Monotonic.markNow()
        while (true) {
            createdEffects++
            heldEffects.add(effect(ReferenceHolder.Weak) {
                state()
                liveEffects++
            })

            if (heldEffects.size > 100) {
                heldEffects.removeFirst() // notably explicitly not invoking the unregister function here
                System.gc() // beg the JVM to do GC, there's go guarantee this works
            }

            liveEffects = 0
            state.set { it + 1 }
            assert(liveEffects <= createdEffects)
            if (liveEffects < createdEffects) {
                println("Finished after creating $createdEffects effects, with only $liveEffects still live")
                return
            }

            val timeout = 5.seconds
            if (startTime.elapsedNow() > timeout) {
                throw AssertionError("Created $createdEffects effects over $timeout but not one was GCed")
            }
        }
    }

    @Test
    fun testEffectThrowingException() {
        val state = mutableStateOf(0)
        // We'll also throw in a `memo` to check that its internal state isn't corrupted either
        val derived = memo { state() }

        class TestException : RuntimeException()

        var assert1: ((Int?) -> Unit)? = { assertEquals(0, it) }
        var throw1: TestException? = null
        val unregister1 = effect(ReferenceHolder.Weak) {
            assert1.let { func ->
                assertNotNull(func, "Unexpected invocation of first effect!")
                func(derived())
            }
            assert1 = null

            throw1?.let { throw it }
        }

        var assert2: ((Int?) -> Unit)? = { assertEquals(0, it) }
        var throw2: TestException? = null
        val unregister2 = effect(ReferenceHolder.Weak) {
            assert2.let { func ->
                assertNotNull(func, "Unexpected invocation of second effect!")
                func(derived())
            }
            assert2 = null

            throw2?.let { throw it }
        }

        // Ensure setup is correct
        assert1 = { assertEquals(1, it) }
        assert2 = { assertEquals(1, it) }
        state.set(1)
        assertNull(assert1, "First effect was not invoked")
        assertNull(assert2, "Second effect was not invoked")
        assertEquals(1, derived.getUntracked())

        // Have the first effect throw
        assert1 = { assertEquals(2, it) }
        assert2 = { assertEquals(2, it) }
        throw1 = TestException()
        assertEquals(throw1, assertThrows<TestException> {
            state.set(2)
        })
        throw1 = null
        assertNull(assert1, "First effect was not invoked")
        assertNull(assert2, "Second effect was not invoked")
        assertEquals(2, derived.getUntracked())

        // Ensure future updates are unaffected
        assert1 = { assertEquals(3, it) }
        assert2 = { assertEquals(3, it) }
        state.set(3)
        assertNull(assert1, "First effect was not invoked")
        assertNull(assert2, "Second effect was not invoked")
        assertEquals(3, derived.getUntracked())

        // Have the second effect throw
        assert1 = { assertEquals(4, it) }
        assert2 = { assertEquals(4, it) }
        throw2 = TestException()
        assertEquals(throw2, assertThrows<TestException> {
            state.set(4)
        })
        throw2 = null
        assertNull(assert1, "First effect was not invoked")
        assertNull(assert2, "Second effect was not invoked")
        assertEquals(4, derived.getUntracked())

        // Ensure future updates are unaffected
        assert1 = { assertEquals(5, it) }
        assert2 = { assertEquals(5, it) }
        state.set(5)
        assertNull(assert1, "First effect was not invoked")
        assertNull(assert2, "Second effect was not invoked")
        assertEquals(5, derived.getUntracked())

        // Have both effects throw
        assert1 = { assertEquals(6, it) }
        assert2 = { assertEquals(6, it) }
        throw1 = TestException()
        throw2 = TestException()
        val e = assertThrows<TestException> {
            state.set(6)
        }
        assertEquals(throw1, e)
        assertEquals(throw2, throw1.suppressedExceptions[0])
        throw1 = null
        throw2 = null
        assertNull(assert1, "First effect was not invoked")
        assertNull(assert2, "Second effect was not invoked")
        assertEquals(6, derived.getUntracked())

        // Ensure future updates are unaffected
        assert1 = { assertEquals(7, it) }
        assert2 = { assertEquals(7, it) }
        state.set(7)
        assertNull(assert1, "First effect was not invoked")
        assertNull(assert2, "Second effect was not invoked")
        assertEquals(7, derived.getUntracked())

        unregister1()
        unregister2()
    }
}
