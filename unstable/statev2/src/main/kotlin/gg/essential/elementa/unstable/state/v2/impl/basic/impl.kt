package gg.essential.elementa.unstable.state.v2.impl.basic

import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.elementa.unstable.state.v2.MutableState
import gg.essential.elementa.unstable.state.v2.Observer
import gg.essential.elementa.unstable.state.v2.ObserverImpl
import gg.essential.elementa.unstable.state.v2.State
import gg.essential.elementa.unstable.state.v2.impl.Impl
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import kotlin.collections.asSequence

/**
 * Semi-lazy node graph implementation.
 *
 * The actual code is extremely similar to [gg.essential.elementa.unstable.state.v2.impl.minimal.MarkThenPullImpl] (literally
 * only a single line difference), however the mechanism by which it functions is not.
 * The code has been duplicated, so we continue to have a simple reference implementation even when this implementation
 * evolves further.
 *
 * This implementation operates in three phases:
 * - The first phase propagates a may-be-dirty state to all potentially affected nodes
 * - The second phase goes through all dirty nodes and run the third phase for each of them
 * - The phase phase checks if the given node needs to be updated, recursively. And if so, updates it, marks all its
 *   direct dependents as dirty (to be processed by the second phase), and then returns to the second phase.
 *
 * Unlike [gg.essential.elementa.unstable.state.v2.impl.minimal.MarkThenPullImpl], this means that sub-graphs which are
 * potentially affected but whose dependencies have not actually changed, will not be visited (more than once per
 * them actually changing; as opposed to having to re-visit every time they are potentially affected).
 * That does mean that this implementation will in exchange potentially visit intermediate nodes which do not actually
 * have any effects attached to them any more (hence it only being "semi lazy").
 * However, in practice, non-affected nodes usually vastly outnumber dead intermediate nodes (especially because
 * those are usually garbage collected together with the respective effects that used them) by one to two orders of
 * magnitude, making this well worth it.
 */
internal object MarkThenPushAndPullImpl : Impl {
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

    private val allDependencies = mutableListOf<Edge>()
    private val allDependents = mutableListOf<Edge>()

    private val dependencies: Sequence<Node<*>>
        get() = allDependencies.asSequence().filterNot { it.suspended }.map { it.dependency }
    private val dependents: Sequence<Node<*>>
        get() = allDependents.asSequence().filterNot { it.suspended }.mapNotNull { it.dependent }

    override fun Observer.get(): T {
        return getTracked(this@get)
    }

    fun getTracked(observer: Observer): T {
        val impl = observer.observerImpl
        if (impl !is Node<*>) return getUntracked()
        if (impl.state == NodeState.Dead) return getUntracked()

        // Note: Need to get value before registering the dependent, otherwise if this node is dirty, getUntracked will
        // re-evaluate it which marks all dependents as dirty, but this new dependent hasn't seen the old value, so it'd
        // be wrong to mark it as dirty.
        val value = getUntracked()

        val dependency = this
        val dependent = impl

        // See if there's already an existing edge
        // Any existing edge will be in both lists, so we can pick the smaller one to iterate.
        val listA = dependency.allDependents
        val listB = dependent.allDependencies
        for (edge in if (listA.size < listB.size) listA else listB) {
            if (edge.dependency == dependency && edge.dependent == dependent) {
                edge.suspended = false // may need to re-enable the edge if it's currently suspended
                return value
            }
        }

        // Create a new edge
        val edge = Edge(dependency, dependent)
        dependency.allDependents.add(edge)
        dependent.allDependencies.add(edge)

        // To prevent unbounded growth, we'll clean up any stale edges whenever we add a new one
        // (this is really fast in when there isn't anything to do thanks to the ReferenceQueue)
        cleanupStaleReferences()

        return value
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
        for (dep in dependents) {
            dep.markDirty(update)
        }
        update.flush()
    }

    private fun mark(update: Update, newState: NodeState) {
        val oldState = state
        if (oldState.ordinal >= newState.ordinal) {
            return
        }

        if (newState == NodeState.Dirty) {
            update.queueNode(this)
        }

        state = newState
    }

    private fun markDirty(update: Update) {
        mark(update, NodeState.Dirty)

        for (dep in dependents) {
            dep.markToBeChecked(update)
        }
    }

    private fun markToBeChecked(update: Update) {
        if (state != NodeState.Clean) return

        mark(update, NodeState.ToBeChecked)

        for (dep in dependents) {
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

        val wasDirty = state == NodeState.Dirty
        state = NodeState.Clean

        if (wasDirty) {
            for (edge in allDependencies) {
                edge.suspended = true
            }

            // Beware: This invocation may throw an exception if user code is faulty! We should handle that correctly.
            val newValue = func(this)

            if (state == NodeState.Dead) {
                return
            }

            allDependencies.removeIf { edge ->
                if (edge.suspended) {
                    edge.dependency.allDependents.remove(edge)
                    true
                } else {
                    false
                }
            }

            if (value != newValue) {
                value = newValue

                for (dep in dependents) {
                    dep.mark(update, NodeState.Dirty)
                }
            }
        }
    }

    fun cleanup() {
        assert(kind == NodeKind.Effect)
        assert(allDependents.isEmpty())

        for (edge in allDependencies) {
            edge.dependency.allDependents.remove(edge)
        }
        allDependencies.clear()

        state = NodeState.Dead
    }

    private var referenceQueueField: ReferenceQueue<Node<*>>? = null
    private val referenceQueue: ReferenceQueue<Node<*>>
        get() = referenceQueueField ?: ReferenceQueue<Node<*>>().also { referenceQueueField = it }

    private fun cleanupStaleReferences() {
        val queue = referenceQueueField ?: return

        if (queue.poll() == null) {
            return
        }

        @Suppress("ControlFlowWithEmptyBody")
        while (queue.poll() != null);

        allDependents.removeIf { it.dependent == null }
    }

    /**
     * This class represents an edge in the dependency graph between one node ([dependent]) which depends on the value
     * of another node ([dependency]).
     * Both nodes keep a reference to the same [Edge] instance in their [Node.allDependencies] and [Node.allDependents]
     * lists respectively.
     *
     * The [dependent] node of an edge is stored in a [WeakReference], such that it can be garbage collected if nothing
     * else is keeping it alive any more. Once garbage collected, the edge becomes "stale" and will eventually be
     * cleaned up from the list of the [dependency] node by [Node.cleanupStaleReferences].
     *
     * An [Edge] may also become temporarily [suspended]. In this state, the nodes should act as if the edge did not
     * exist.
     * This is an optimization for when the [dependent] is re-evaluated, which would naively require invalidating
     * all its dependencies (and removing all its edges from all lists) and then likely re-adding most of them as they
     * are re-observed. With the [suspended] flag, we can simply mark all edges as suspended and don't need to modify
     * the lists unless edges are actually removed.
     */
    class Edge(
        val dependency: Node<*>,
        dependent: Node<*>,
        var suspended: Boolean = false,
    ) : WeakReference<Node<*>>(dependent, dependency.referenceQueue) {
        val dependent: Node<*>?
            get() = get()
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

        var exception: Throwable? = null

        processing = true
        try {
            var i = 0
            while (true) {
                val node = queue.getOrNull(i) ?: break
                try {
                    node.update(this)
                } catch (e: Throwable) {
                    if (exception == null) {
                        exception = e
                    } else {
                        exception.addSuppressed(e)
                    }
                }
                i++
            }
            queue.clear()
        } finally {
            processing = false
        }

        if (exception != null) {
            throw exception
        }
    }

    companion object {
        private val INSTANCE = ThreadLocal.withInitial { Update() }
        fun get(): Update = INSTANCE.get()
    }
}

private val UNREACHABLE: Observer.() -> Nothing = { error("unreachable") }
