package gg.essential.elementa.unstable.state.v2.combinators

import gg.essential.elementa.unstable.state.v2.MutableState
import gg.essential.elementa.unstable.state.v2.State

infix fun State<Boolean>.and(other: State<Boolean>) =
    zip(other) { a, b -> a && b }

infix fun State<Boolean>.or(other: State<Boolean>) =
    zip(other) { a, b -> a || b }

operator fun State<Boolean>.not() = map { !it }

operator fun MutableState<Boolean>.not() = bimap({ !it }, { !it })
