package gg.essential.elementa.unstable.state.v2

import gg.essential.elementa.state.v2.ReferenceHolder
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class StateSchedulerTest {
    @Test
    fun test() {
        val startTime = Instant.EPOCH.plusMillis(2)
        val currentTime = mutableStateOf(startTime)
        val scheduler = StateScheduler(currentTime)

        val completed = Array(100000) { false }
        val effects = completed.indices.map { i ->
            effect(ReferenceHolder.Weak) {
                val targetTime = Instant.EPOCH.plusMillis(i.toLong())
                val reachedTarget = with(scheduler) { observe(targetTime) }
                // Only time the effect should run is when we reached the target time and during setup.
                if (reachedTarget) {
                    completed[i] = true
                    assertEquals(if (targetTime > startTime) targetTime else startTime, currentTime.getUntracked())
                } else {
                    assertEquals(startTime, currentTime.getUntracked())
                }
            }
        }

        assertEquals(true, completed[0])
        assertEquals(true, completed[1])
        assertEquals(true, completed[2])
        assertEquals(false, completed[3])

        for (i in completed.indices) {
            currentTime.set(startTime.plusMillis(i.toLong()))
        }

        effects.forEach { it() }

        assert(completed.all { it })
    }
}
