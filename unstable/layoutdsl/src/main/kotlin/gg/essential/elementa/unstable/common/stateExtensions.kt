package gg.essential.elementa.unstable.common

import gg.essential.elementa.state.State
import gg.essential.elementa.state.v2.ReferenceHolder

fun <T> State<T>.onSetValueAndNow(listener: (T) -> Unit) = onSetValue(listener).also { listener(get()) }

@Deprecated("See `State.onSetValue`. Use `stateBy`/`effect` instead.")
fun <T> gg.essential.elementa.unstable.state.v2.State<T>.onSetValueAndNow(owner: ReferenceHolder, listener: (T) -> Unit) =
    onSetValue(owner, listener).also { listener(get()) }

operator fun State<Boolean>.not() = map { !it }