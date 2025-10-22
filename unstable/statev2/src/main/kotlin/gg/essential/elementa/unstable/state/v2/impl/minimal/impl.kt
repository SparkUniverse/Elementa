package gg.essential.elementa.unstable.state.v2.impl.minimal

import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.elementa.unstable.state.v2.MutableState
import gg.essential.elementa.unstable.state.v2.Observer
import gg.essential.elementa.unstable.state.v2.ObserverImpl
import gg.essential.elementa.unstable.state.v2.State
import gg.essential.elementa.unstable.state.v2.impl.Impl
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/**
 * Minimal mark-then-pull-based node graph implementation.
 *
 * This implementation operates in two simple phases:
 * - The first phase pushes the may-be-dirty state through the graph to all potentially affected nodes
 * - The second phase goes through all potentially affected effect nodes and recursively checks if they need to be
 *   updated.
 *
 * This does make for a fully correct reference implementation.
 * However it always needs to visit all potentially affected effects on every update.
 * In particular if we have a large mutable state (like a list) which is then split off into many smaller ones (like its
 * items), all effects attached to all of the smaller nodes need to be visited, even if only a single item was modified
 * in the list.
 *
 * For a more performant algorithm, see [gg.essential.elementa.unstable.state.v2.impl.markpushpull.MarkThenPushAndPullImpl].
 */
internal object MarkThenPullImpl : Impl {
    override fun <T> mutableState(value: T): MutableState<T> {
        val node = Node(NodeKind.Mutable, NodeState.Clean, UNREACHABLE, value)
        return object : State<T> by node, MutableState<T> {
            override fun set(mapper: (T) -> T) {
                node.set(mapper(node.getUntracked()))
            }
        }
    }

    override fun <T> memo(func: Observer.() -> T): State<T> =
        Node(NodeKind.Memo, NodeState.Dirty, func, null)

    override fun effect(referenceHolder: ReferenceHolder, func: Observer.() -> Unit): () -> Unit {
        val node = Node(NodeKind.Effect, NodeState.Dirty, func, Unit)
        node.update(Update.get())
        val refCleanup = referenceHolder.holdOnto(node)
        return {
            node.cleanup()
            refCleanup()
        }
    }

}

private enum class NodeKind {
    /**
     * A leaf node which represents a manually updated value which only changes when [Node.set] is invoked.
     * It does not have any dependencies nor a [Node.func].
     */
    Mutable,

    /**
     * An intermediate node which is lazily computed and lazily updated via [Node.func].
     * May have any number of both dependencies and dependents.
     */
    Memo,

    /**
     * A node which represents the root of a dependency tree.
     * It does not have any dependents and does not produce any value.
     *
     * Unlike [Memo], it is not lazy and will be updated when any of its dependencies change.
     * If any of its dependencies are lazy, they too will be updated as necessary for this node to obtain a complete
     * view of up-to-date values.
     */
    Effect,
}

private enum class NodeState {
    /**
     * The [Node.value] is up-to-date.
     * For [NodeKind.Effect], the [Node.func] has been run with the latest values.
     */
    Clean,

    /**
     * Some of the node's dependencies, including transitive one, may be [Dirty] and need to be checked.
     */
    ToBeChecked,

    /**
     * The [Node.value] is outdated and needs to be re-evaluated.
     * For [NodeKind.Effect], the [Node.func] needs to be re-run.
     */
    Dirty,

    /**
     * The node has been disposed off and should no longer be updated.
     */
    Dead,
}

private class Node<T>(
    val kind: NodeKind,
    private var state: NodeState,
    private val func: Observer.() -> T,
    private var value: T?,
) : State<T>, Observer, ObserverImpl {
    override val observerImpl: ObserverImpl
        get() = this

    private val observed = mutableSetOf<Node<*>>()
    private val dependencies = mutableListOf<Node<*>>()
    private val dependents: MutableList<WeakReference<Node<*>>> = mutableListOf()

    override fun Observer.get(): T {
        return getTracked(this@get)
    }

    fun getTracked(observer: Observer): T {
        val impl = observer.observerImpl
        if (impl is Node<*>) {
            impl.observed.add(this)
        }
        return getUntracked()
    }

    override fun getUntracked(): T {
        if (state != NodeState.Clean) {
            update(Update.get())
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    fun set(newValue: T) {
        assert(kind == NodeKind.Mutable)

        if (value == newValue) {
            return
        }

        value = newValue

        val update = Update.get()
        for (dep in dependents.iter()) {
            dep.markDirty(update)
        }
        update.flush()
    }

    private fun mark(update: Update, newState: NodeState) {
        val oldState = state
        if (oldState.ordinal >= newState.ordinal) {
            return
        }

        if (kind == NodeKind.Effect && oldState == NodeState.Clean) {
            update.queueNode(this)
        }

        state = newState
    }

    private fun markDirty(update: Update) {
        mark(update, NodeState.Dirty)

        for (dep in dependents.iter()) {
            dep.markToBeChecked(update)
        }
    }

    private fun markToBeChecked(update: Update) {
        if (state != NodeState.Clean) return

        mark(update, NodeState.ToBeChecked)

        for (dep in dependents.iter()) {
            dep.markToBeChecked(update)
        }
    }

    fun update(update: Update) {
        if (state == NodeState.Clean) {
            return
        }

        if (state == NodeState.ToBeChecked) {
            for (dep in dependencies) {
                dep.update(update)
                if (state == NodeState.Dirty) {
                    break
                }
            }
        }

        if (state == NodeState.Dirty) {
            val newValue = func(this)

            if (state == NodeState.Dead) {
                return
            }

            for (i in dependencies.indices.reversed()) {
                val dep = dependencies[i]
                if (dep !in observed) {
                    dependencies.removeAt(i)
                    dep.removeDependent(this)
                }
            }
            for (dep in observed) {
                if (dep !in dependencies) {
                    dependencies.add(dep)
                    dep.addDependent(this)
                }
            }
            observed.clear()

            if (value != newValue) {
                value = newValue

                for (dep in dependents.iter()) {
                    dep.mark(update, NodeState.Dirty)
                }
            }
        }

        state = NodeState.Clean
    }

    fun cleanup() {
        for (dep in dependencies) {
            dep.removeDependent(this)
        }
        dependencies.clear()

        state = NodeState.Dead
    }

    private var referenceQueueField: ReferenceQueue<Node<*>>? = null
    private val referenceQueue: ReferenceQueue<Node<*>>
        get() = referenceQueueField ?: ReferenceQueue<Node<*>>().also { referenceQueueField = it }

    private fun addDependent(node: Node<*>) {
        cleanupStaleReferences()
        dependents.add(WeakReference(node, referenceQueue))
    }

    private fun removeDependent(node: Node<*>) {
        val index = dependents.indexOfFirst { it.get() == node }
        if (index >= 0) {
            dependents.removeAt(index)
        }
    }

    private fun cleanupStaleReferences() {
        val queue = referenceQueueField ?: return

        if (queue.poll() == null) {
            return
        }

        @Suppress("ControlFlowWithEmptyBody")
        while (queue.poll() != null);

        dependents.removeIf { it.get() == null }
    }

    private fun MutableList<WeakReference<Node<*>>>.iter(): Iterator<Node<*>> {
        return asSequence().mapNotNull { it.get() }.iterator()
    }
}

private class Update {
    private var queue: MutableList<Node<*>> = mutableListOf()
    private var processing: Boolean = false

    fun queueNode(node: Node<*>) {
        queue.add(node)
    }

    fun flush() {
        if (processing || queue.isEmpty()) {
            return
        }

        processing = true
        try {
            var i = 0
            while (true) {
                val node = queue.getOrNull(i) ?: break
                node.update(this)
                i++
            }
            queue.clear()
        } finally {
            processing = false
        }
    }

    companion object {
        private val INSTANCE = ThreadLocal.withInitial { Update() }
        fun get(): Update = INSTANCE.get()
    }
}

private val UNREACHABLE: Observer.() -> Nothing = { error("unreachable") }
