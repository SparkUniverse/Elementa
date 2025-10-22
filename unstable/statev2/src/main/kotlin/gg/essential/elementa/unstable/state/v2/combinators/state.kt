package gg.essential.elementa.unstable.state.v2.combinators

import gg.essential.elementa.unstable.state.v2.MutableState
import gg.essential.elementa.unstable.state.v2.State
import gg.essential.elementa.unstable.state.v2.memo

/** Maps this state into a new state */
fun <T, U> State<T>.map(mapper: (T) -> U): State<U> {
    return memo { mapper(get()) }
}

/** Maps this mutable state into a new mutable state. */
fun <T, U> MutableState<T>.bimap(map: (T) -> U, unmap: (U) -> T): MutableState<U> {
    return object : MutableState<U>, State<U> by this.map(map) {
        override fun set(mapper: (U) -> U) {
            this@bimap.set { unmap(mapper(map(it))) }
        }
    }
}

/** Zips this state with another state */
fun <T, U> State<T>.zip(other: State<U>): State<Pair<T, U>> = zip(other, ::Pair)

/** Zips this state with another state using [mapper] */
fun <T, U, V> State<T>.zip(other: State<U>, mapper: (T, U) -> V): State<V> {
    return memo { mapper(this@zip(), other()) }
}
