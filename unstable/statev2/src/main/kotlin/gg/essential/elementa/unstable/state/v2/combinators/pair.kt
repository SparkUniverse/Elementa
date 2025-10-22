package gg.essential.elementa.unstable.state.v2.combinators

import gg.essential.elementa.unstable.state.v2.State

operator fun <A, B> State<Pair<A, B>>.component1(): State<A> = this.map { it.first }
operator fun <A, B> State<Pair<A, B>>.component2(): State<B> = this.map { it.second }
