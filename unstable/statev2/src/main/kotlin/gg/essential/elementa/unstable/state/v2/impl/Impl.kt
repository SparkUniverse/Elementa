package gg.essential.elementa.unstable.state.v2.impl

import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.elementa.unstable.state.v2.MutableState
import gg.essential.elementa.unstable.state.v2.Observer
import gg.essential.elementa.unstable.state.v2.ReferenceHolderImpl
import gg.essential.elementa.unstable.state.v2.State
import gg.essential.elementa.unstable.state.v2.mutableStateOf

internal interface Impl {
    fun <T> mutableState(value: T): MutableState<T>
    fun <T> memo(func: Observer.() -> T): State<T>
    fun effect(referenceHolder: ReferenceHolder, func: Observer.() -> Unit): () -> Unit

    fun <T> derivedState(
        initialValue: T,
        builder: (owner: ReferenceHolder, derivedState: MutableState<T>) -> Unit,
    ): State<T> =
        object : State<T> {
            val referenceHolder = ReferenceHolderImpl() // keep this alive for at least as long as the returned state
            val derivedState = mutableStateOf(initialValue)
            init {
                builder(referenceHolder, derivedState)
            }

            override fun Observer.get(): T = derivedState()
        }
}
