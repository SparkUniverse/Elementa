package gg.essential.elementa.unstable.state.v2

import gg.essential.elementa.state.v2.ReferenceHolder
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
