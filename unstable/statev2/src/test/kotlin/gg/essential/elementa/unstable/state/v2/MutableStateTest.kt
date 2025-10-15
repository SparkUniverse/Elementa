package gg.essential.elementa.unstable.state.v2

import kotlin.test.Test
import kotlin.test.assertEquals

class MutableStateTest {
    @Test
    fun testMutableState() {
        val state = mutableStateOf(0)
        assertEquals(0, state.getUntracked())
        state.set(1)
        assertEquals(1, state.getUntracked())
        state.set { it + 1 }
        assertEquals(2, state.getUntracked())
    }
}
