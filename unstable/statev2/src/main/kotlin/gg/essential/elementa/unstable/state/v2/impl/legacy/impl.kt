package gg.essential.elementa.unstable.state.v2.impl.legacy


import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.elementa.unstable.state.v2.DelegatingMutableState
import gg.essential.elementa.unstable.state.v2.DelegatingState
import gg.essential.elementa.unstable.state.v2.MutableState
import gg.essential.elementa.unstable.state.v2.Observer
import gg.essential.elementa.unstable.state.v2.ObserverImpl
import gg.essential.elementa.unstable.state.v2.State
import gg.essential.elementa.unstable.state.v2.impl.Impl
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/** Legacy implementation based around `onSetValue` which makes no attempt at being glitch-free. */
internal object LegacyImpl : Impl {
    override fun <T> mutableState(value: T): MutableState<T> = BasicState(value)

    override fun <T> memo(func: Observer.() -> T): State<T> {
        val subscribed = mutableMapOf<State<*>, () -> Unit>()
        val observed = mutableSetOf<State<*>>()
        val scope = LegacyObserverImpl(observed)

        return derivedState(initialValue = func(scope)) { owner, derivedState ->
            fun updateSubscriptions() {
                for (state in observed) {
                    if (state in subscribed) continue

                    subscribed[state] = state.onSetValue(owner) {
                        val newValue = func(scope)
                        updateSubscriptions()
                        derivedState.set(newValue)
                    }
                }

                subscribed.entries.removeAll { (state, unregister) ->
                    if (state !in observed) {
                        unregister()
                        true
                    } else {
                        false
                    }
                }

                observed.clear()
            }
            updateSubscriptions()
        }
    }

    override fun effect(referenceHolder: ReferenceHolder, func: Observer.() -> Unit): () -> Unit {
        var disposed = false
        val release = referenceHolder.holdOnto(memo {
            if (disposed) return@memo
            func()
        })
        return {
            disposed = true
            release()
        }
    }

    override fun <T> stateDelegatingTo(state: State<T>): DelegatingState<T> = DelegatingStateImpl(state)

    override fun <T> mutableStateDelegatingTo(state: MutableState<T>): DelegatingMutableState<T> = DelegatingMutableStateImpl(state)

    override fun <T> derivedState(
        initialValue: T,
        builder: (owner: ReferenceHolder, derivedState: MutableState<T>) -> Unit
    ): State<T> = ReferenceHoldingBasicState(initialValue).apply { builder(this, this) }
}

private class LegacyObserverImpl(val observed: MutableSet<State<*>>) : Observer, ObserverImpl {
    override val observerImpl: ObserverImpl
        get() = this
}

/** A simple implementation of [MutableState], containing only a backing field */
private open class BasicState<T>(private var valueBacker: T) : MutableState<T> {
    private val referenceQueue = ReferenceQueue<Any>()
    private val listeners = mutableListOf<ListenerEntry<T>>()

    /**
     * Contains the size of the [listeners] list which we currently iterate over.
     * We must not directly modify these entries as that may mess up the iteration, anything after those entries is fair
     * game though.
     * Additions always happen at the end of the list, so those are trivial.
     * For removals we instead set the [ListenerEntry.removed] flag and let the iteration code clean up the entry when
     * it passes over it.
     * We can't solely rely on that for all cleanup because we only iterate the listener list when the value of the state
     * changes, so if it doesn't, we need to clean up entries immediately.
     */
    private var liveSize = 0

    override fun Observer.get(): T {
        (this@get.observerImpl as? LegacyObserverImpl)?.observed?.add(this@BasicState)
        return getUntracked()
    }

    override fun getUntracked(): T = valueBacker

    override fun onSetValue(owner: ReferenceHolder, listener: (T) -> Unit): () -> Unit {
        cleanupStaleListeners()
        val ownerCallback = WeakReference(owner.holdOnto(Pair(this, listener)))
        return ListenerEntry(this, listener, ownerCallback).also { listeners.add(it) }
    }

    override fun set(mapper: (T) -> T) {
        val oldValue = valueBacker
        val newValue = mapper(oldValue)
        if (oldValue == newValue) {
            return
        }

        valueBacker = newValue

        // Iterate over listeners while allowing for concurrent add to the end of the list (newly added entries will not get
        // called) and concurrent remove from anywhere in the list (via `removed` flag in each entry, or directly for newly
        // added listeners). See [liveSize] docs.
        liveSize = listeners.size
        var i = 0
        while (i < liveSize) {
            val entry = listeners[i]
            if (entry.removed) {
                listeners.removeAt(i)
                liveSize--
            } else {
                entry.get()?.invoke(newValue)
                i++
            }
        }
        liveSize = 0
    }

    private fun cleanupStaleListeners() {
        while (true) {
            val reference = referenceQueue.poll() ?: break
            (reference as ListenerEntry<*>).invoke()
        }
    }

    private class ListenerEntry<T>(
        private val state: BasicState<T>,
        listenerCallback: (T) -> Unit,
        private val ownerCallback: WeakReference<() -> Unit>,
    ) : WeakReference<(T) -> Unit>(listenerCallback, state.referenceQueue), () -> Unit {
        var removed = false

        override fun invoke() {
            // If we do not currently iterate over the listener list, we can directly remove this entry from the list,
            // otherwise we merely mark it as deleted and let the iteration code take care of it.
            val index = state.listeners.indexOf(this@ListenerEntry)
            if (index >= state.liveSize) {
                state.listeners.removeAt(index)
            } else {
                removed = true
            }

            ownerCallback.get()?.invoke()
        }
    }
}

/** Base class for implementations of Delegating(Mutable)State classes. */
private open class DelegatingStateBase<T, S : State<T>>(protected var delegate: S) : State<T> {
    private val referenceQueue = ReferenceQueue<Any>()
    private var listeners = mutableListOf<ListenerEntry<T>>()

    override fun Observer.get(): T {
        (this@get.observerImpl as? LegacyObserverImpl)?.observed?.add(this@DelegatingStateBase)
        return getUntracked()
    }

    override fun getUntracked(): T = delegate.get()

    override fun onSetValue(owner: ReferenceHolder, listener: (T) -> Unit): () -> Unit {
        cleanupStaleListeners()
        val ownerCallback = WeakReference(owner.holdOnto(Pair(this, listener)))
        val removeCallback = delegate.onSetValue(ReferenceHolder.Weak, listener)
        return ListenerEntry(this, listener, removeCallback, ownerCallback).also { listeners.add(it) }
    }

  
    fun rebind(newState: S) {
        val oldState = delegate
        if (oldState == newState) {
            return
        }

        delegate = newState

        listeners =
            listeners.mapNotNullTo(mutableListOf()) { entry ->
                entry.removeCallback()
                val listenerCallback = entry.get() ?: return@mapNotNullTo null
                val removeCallback = newState.onSetValue(ReferenceHolder.Weak, listenerCallback)
                ListenerEntry(this, listenerCallback, removeCallback, entry.ownerCallback)
            }

        val oldValue = oldState.get()
        val newValue = newState.get()
        if (oldValue != newValue) {
            listeners.forEach { it.get()?.invoke(newValue) }
        }
    }

    private fun cleanupStaleListeners() {
        while (true) {
            val reference = referenceQueue.poll() ?: break
            (reference as ListenerEntry<*>).invoke()
        }
    }

    private class ListenerEntry<T>(
        private val state: DelegatingStateBase<T, *>,
        listenerCallback: (T) -> Unit,
        val removeCallback: () -> Unit,
        val ownerCallback: WeakReference<() -> Unit>,
    ) : WeakReference<(T) -> Unit>(listenerCallback, state.referenceQueue), () -> Unit {
        override fun invoke() {
            state.listeners.remove(this@ListenerEntry)
            removeCallback()
            ownerCallback.get()?.invoke()
        }
    }
}

/** Default implementation of [DelegatingState] */
private class DelegatingStateImpl<T>(delegate: State<T>) :
    DelegatingStateBase<T, State<T>>(delegate), DelegatingState<T>

/** Default implementation of [DelegatingMutableState] */
private class DelegatingMutableStateImpl<T>(delegate: MutableState<T>) :
    DelegatingStateBase<T, MutableState<T>>(delegate), DelegatingMutableState<T> {
    override fun set(mapper: (T) -> T) {
        delegate.set(mapper)
    }
}

/** A [BasicState] which additionally implements [ReferenceHolder] */
private class ReferenceHoldingBasicState<T>(value: T) : BasicState<T>(value), ReferenceHolder {
    private val heldReferences = mutableListOf<Any>()

    override fun holdOnto(listener: Any): () -> Unit {
        heldReferences.add(listener)
        return { heldReferences.remove(listener) }
    }
}
