package gg.essential.elementa.unstable.state.v2

fun <T> State<State<T>>.flatten() = memo { this@flatten()() }
