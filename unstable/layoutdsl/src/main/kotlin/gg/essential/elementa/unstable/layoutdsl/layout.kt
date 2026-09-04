@file:OptIn(ExperimentalContracts::class)
package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.UIComponent
import gg.essential.elementa.state.State
import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.elementa.unstable.state.v2.*
import gg.essential.elementa.unstable.state.v2.collections.MutableTrackedList
import gg.essential.elementa.unstable.state.v2.collections.TrackedList
import gg.essential.elementa.unstable.state.v2.collections.trackedListOf
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import gg.essential.elementa.unstable.state.v2.ListState as ListStateV2
import gg.essential.elementa.unstable.state.v2.State as StateV2

class LayoutScope private constructor(
    private val node: LayoutNodeVirtual,
) {

    constructor(component: UIComponent) : this(LayoutNodeUIComponent(component, component, true).children)

    val stateScope: ReferenceHolder
        get() = node.stateScope

    /**
     * As the name says, don't use this unless you really have to.
     */
    val containerDontUseThisUnlessYouReallyHaveTo: UIComponent
        get() = generateSequence<LayoutNode>(node) { it.mountedInNode }.firstNotNullOf { it as? LayoutNodeUIComponent }.component

    operator fun <T : UIComponent> T.invoke(modifier: Modifier = Modifier, block: LayoutScope.() -> Unit = {}): T {
        addChild(this, modifier, block)
        return this
    }

    fun <T : UIComponent> addChild(childComponent: T, modifier: Modifier = Modifier, block: LayoutScope.() -> Unit = {}) {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }

        modifier.applyToComponent(childComponent)

        val childNode = LayoutNodeUIComponent(childComponent, childComponent, false)
        node.children.add(childNode)

        block(LayoutScope(childNode.children))

        node.mountedInComponent?.let { component ->
            childNode.mount(node, component)
        }
    }

    operator fun DetachedLayout.invoke() = addChild(this@invoke)
    fun addChild(detachedLayout: DetachedLayout) = attachChild(detachedLayout.node)

    private fun attachChild(detachedNode: LayoutNodeDetached) {
        if (detachedNode in node.children) {
            throw IllegalStateException("DetachedLayout has already been placed in this scope.")
        }
        node.children.add(detachedNode)
        node.mountedInComponent?.let { component ->
            detachedNode.mount(node, component)
        }
    }

    operator fun LayoutDslComponent.invoke(modifier: Modifier = Modifier) = layout(modifier)

    @Suppress("FunctionName")
    fun if_(state: State<Boolean>, cache: Boolean = true, block: LayoutScope.() -> Unit): IfDsl {
        return if_(state.toV2(), cache, block)
    }

    fun if_(state: StateV2<Boolean>, cache: Boolean = true, block: LayoutScope.() -> Unit): IfDsl {
        forEach({ if (state()) trackedListOf(Unit) else trackedListOf() }, cache) { block() }
        return IfDsl({ !state() }, cache)
    }

    fun <T> ifNotNull(state: State<T?>, cache: Boolean = false, block: LayoutScope.(T) -> Unit): IfDsl {
        return ifNotNull(state.toV2(), cache, block)
    }

    fun <T> ifNotNull(state: StateV2<T?>, cache: Boolean = false, block: LayoutScope.(T) -> Unit): IfDsl {
        forEach({ state()?.let { trackedListOf(it) } ?: trackedListOf() }, cache) { block(it) }
        return IfDsl({ state() == null }, true)
    }

    class IfDsl(internal val elseState: StateV2<Boolean>, internal var cache: Boolean)

    infix fun IfDsl.`else`(block: LayoutScope.() -> Unit) {
        if_(elseState, cache, block)
    }

    /** Makes available to the inner scope the value of the given [state]. */
    fun <T> bind(state: State<T>, cache: Boolean = false, block: LayoutScope.(T) -> Unit) {
        bind(state.toV2(), cache, block)
    }

    /** Makes available to the inner scope the value of the given [state]. */
    fun <T> bind(state: StateV2<T>, cache: Boolean = false, block: LayoutScope.(T) -> Unit) {
        forEach({ trackedListOf(state()) }, cache) { block(it) }
    }

    /**
     * Repeats the inner block for each element in the given list state.
     * If the list state changes, components from old scopes are removed and new scopes are created and initialized as
     * required.
     * Order relative to other components within the same [layout] call is kept automatically at all times.
     *
     * If the space of possible [T] is very limited, [cache] may be set to `true` to retain old scopes after they are
     * removed and to re-use them if their corresponding [T] value is re-introduced at a later time.
     * This requires that [T] be usable as a key in a HashMap.
     */
    fun <T> forEach(list: ListStateV2<T>, cache: Boolean = false, block: LayoutScope.(T) -> Unit) {
        val forEachScope = LayoutNodeVirtual(stateScope)
        node.children.add(forEachScope)
        node.mountedInComponent?.let { component ->
            forEachScope.mount(node, component)
        }

        val cacheMap =
            if (cache) mutableMapOf<T, MutableList<LayoutNodeVirtual>>()
            else null
        fun getCacheEntry(key: T) = cacheMap?.getOrPut(key) { mutableListOf() }

        fun add(index: Int, element: T) {
            val cachedScope = getCacheEntry(element)?.removeLastOrNull()
            if (cachedScope != null) {
                forEachScope.children.add(index, cachedScope)
                forEachScope.mountedInComponent?.let { component ->
                    cachedScope.mount(forEachScope, component)
                }
            } else {
                // If the `forEach` is not cached, we give each child scope its own reference holder.
                // This scope will be dropped once the child scope is removed.
                val childStateScope = if (cache) forEachScope.stateScope else ReferenceHolderImpl()
                val childNode = LayoutNodeVirtual(childStateScope)
                forEachScope.children.add(index, childNode)
                forEachScope.mountedInComponent?.let { component ->
                    childNode.mount(forEachScope, component)
                }
                block(LayoutScope(childNode), element)
            }
        }

        fun remove(index: Int, element: T) {
            val removedScope = forEachScope.children.removeAt(index)
            check(removedScope is LayoutNodeVirtual)
            forEachScope.mountedInComponent?.let { component ->
                removedScope.unmount(forEachScope, component)
            }
            getCacheEntry(element)?.add(removedScope)
        }

        fun clear(elements: List<T>) {
            forEachScope.children.forEachIndexed { index, layoutScope ->
                check(layoutScope is LayoutNodeVirtual)
                forEachScope.mountedInComponent?.let { component ->
                    layoutScope.unmount(forEachScope, component)
                }
                getCacheEntry(elements[index])?.add(layoutScope)
            }
            forEachScope.children.clear()
        }

        fun update(change: TrackedList.Change<T>) {
            when (change) {
                is TrackedList.Add -> {
                    val (index, element) = change.element
                    add(index, element)
                }
                is TrackedList.Remove -> {
                    val (index, element) = change.element
                    remove(index, element)
                }
                is TrackedList.Clear -> {
                    clear(change.oldElements)
                }
            }
        }

        var trackedList: TrackedList<T> = MutableTrackedList()
        effect(stateScope) {
            val newList = list()
            val oldList = trackedList
            newList.getChangesSince(oldList).forEach { change -> update(change) }
            trackedList = newList
        }
    }

    companion object {
        @PublishedApi
        internal fun makeDetached(): Pair<DetachedLayout, LayoutScope> {
            val layout = DetachedLayout()
            val scope = LayoutScope(layout.node.children)
            return Pair(layout, scope)
        }
    }
}

internal sealed class LayoutNode(
    val stateScope: ReferenceHolder,
) {
    var mountedInNode: LayoutNode? = null
        private set
    var mountedInComponent: UIComponent? = null
        private set

    var root: LayoutNodeUIComponent? = null // actively maintained only if [isInterestedInRoot]; otherwise may be outdated
    var isInterestedInRoot: Boolean = false
        set(value) {
            if (field == value) return
            if (value) {
                val parentNode = mountedInNode
                if (parentNode != null) {
                    parentNode.isInterestedInRoot = true
                    root = parentNode.root
                } else {
                    root = null
                }
                field = true
            } else {
                throw UnsupportedOperationException("cannot be un-set")
            }
        }

    /** Mounts this node into the given [parentComponent]. */
    open fun mount(parentNode: LayoutNode, parentComponent: UIComponent) {
        check(mountedInNode == null)
        check(mountedInComponent == null)

        mountedInNode = parentNode
        mountedInComponent = parentComponent

        if (isInterestedInRoot) {
            parentNode.isInterestedInRoot = true
            root = parentNode.root
        }

        when (this) {
            is LayoutNodeUIComponent -> {
                parentComponent.insertChildAt(component, findInsertionIndex(parentComponent))
                if (isInterestedInRoot) {
                    children.updateRootRecursively()
                }
            }
            is LayoutNodeVirtual -> children.forEach { it.mount(this, parentComponent) }
            is LayoutNodeDetached -> children.mount(this, parentComponent)
        }
    }

    /** Unmounts this node from the given [parentComponent]. */
    open fun unmount(parentNode: LayoutNode, parentComponent: UIComponent) {
        check(mountedInNode == parentNode)
        check(mountedInComponent == parentComponent)

        mountedInNode = null
        mountedInComponent = null
        root = null

        when (this) {
            is LayoutNodeUIComponent -> {
                parentComponent.removeChild(component)
                if (isInterestedInRoot) {
                    children.updateRootRecursively()
                }
            }
            is LayoutNodeVirtual -> children.forEach { it.unmount(this, parentComponent) }
            is LayoutNodeDetached -> children.unmount(this, parentComponent)
        }
    }

    /** Updates [LayoutNode.root] recursively in this sub-tree on all nodes that [have declared an interest][isInterestedInRoot]. */
    protected open fun updateRootRecursively() {
        if (!isInterestedInRoot) return

        val updatedRoot = mountedInNode?.root
        if (updatedRoot == root) return
        root = updatedRoot

        when (this) {
            is LayoutNodeUIComponent -> children.updateRootRecursively()
            is LayoutNodeVirtual -> children.forEach { it.updateRootRecursively() }
            is LayoutNodeDetached -> children.updateRootRecursively()
        }
    }

    /**
     * Finds the index in [parentComponent]`s children at which components of this node should be inserted.
     */
    fun findInsertionIndex(parentComponent: UIComponent): Int {
        if (this is LayoutNodeUIComponent && this.component == parentComponent) {
            return 0
        }
        return when (val mountedInNode = this@LayoutNode.mountedInNode!!) {
            is LayoutNodeUIComponent -> 0
            is LayoutNodeVirtual -> {
                val siblings = mountedInNode.children

                // Check all preceding siblings
                for (index in (0 until siblings.indexOf(this)).reversed()) {
                    siblings[index].findLastMountedComponentIndex(parentComponent)
                        ?.let { return it + 1 }
                }

                // If we can't find anything there, check the siblings one level up, recursively
                return mountedInNode.findInsertionIndex(parentComponent)
            }
            is LayoutNodeDetached -> mountedInNode.findInsertionIndex(parentComponent)
        }
    }

    /**
     * Finds the last component in this sub-tree which is currently mounted in [parentComponent], and returns the index
     * of that component within the `children` of the given [parentComponent].
     */
    private fun findLastMountedComponentIndex(parentComponent: UIComponent): Int? = when (this) {
        is LayoutNodeUIComponent -> parentComponent.children.indexOf(component).takeIf { it != -1 }
        is LayoutNodeVirtual -> {
            for (index in children.indices.reversed()) {
                children[index].findLastMountedComponentIndex(parentComponent)
                    ?.let { return it }
            }
            null
        }
        is LayoutNodeDetached -> children.asLayoutNode().findLastMountedComponentIndex(parentComponent)
    }

    // Workaround for what is presumably a Kotlin bug where it refuses to call private methods if the type doesn't
    // exactly match (e.g. on sub-classes).
    private fun LayoutNode.asLayoutNode(): LayoutNode = this
}

internal class LayoutNodeVirtual(stateScope: ReferenceHolder) : LayoutNode(stateScope) {
    val children: MutableList<LayoutNode> = mutableListOf()
}

internal class LayoutNodeUIComponent(val component: UIComponent, stateScope: ReferenceHolder, isRoot: Boolean) : LayoutNode(stateScope) {
    init {
        if (isRoot) {
            isInterestedInRoot = true
            root = this
        }
    }

    val children = LayoutNodeVirtual(stateScope)
    init {
        children.mount(this, component)
    }
}

internal class LayoutNodeDetached() : LayoutNode(ReferenceHolderImpl()) {
    val children = LayoutNodeVirtual(stateScope)

    init {
        isInterestedInRoot = true
    }

    private val requestedMountPoints = mutableListOf<LayoutNode>()

    override fun mount(parentNode: LayoutNode, parentComponent: UIComponent) {
        check(parentNode !in requestedMountPoints)
        requestedMountPoints.add(parentNode)

        parentNode.isInterestedInRoot = true
        val parentRoot = parentNode.root
        if (parentRoot == null) return

        checkRoot(parentRoot)

        if (mountedInNode != null) return // currently already mounted elsewhere

        super.mount(parentNode, parentComponent)
    }

    override fun unmount(parentNode: LayoutNode, parentComponent: UIComponent) {
        check(parentNode in requestedMountPoints)
        requestedMountPoints.remove(parentNode)

        if (mountedInNode != parentNode) return // currently mounted elsewhere

        super.unmount(parentNode, parentComponent)

        mountWhereRequested()
    }

    private fun mountWhereRequested() {
        assert(mountedInNode == null)

        val newParent = requestedMountPoints.firstOrNull { it.root != null }
        if (newParent != null) {
            checkRoot(newParent.root!!)
            super.mount(newParent, newParent.mountedInComponent!!)
        }
    }

    override fun updateRootRecursively() {
        val parentNode = mountedInNode
        if (parentNode != null) {
            val parentRoot = parentNode.root
            if (parentRoot != null) {
                checkRoot(parentRoot)
            } else {
                super.unmount(parentNode, mountedInComponent!!)
            }
        }

        if (mountedInNode == null) {
            mountWhereRequested()
        }

        super.updateRootRecursively()
    }

    /**
     * We can only detect unmounts within a LayoutDSL tree. If the tree as a whole gets unmounted, we won't know
     * and so won't be able to unmount this detached node from it so it can be mounted somewhere else.
     * There isn't anything wrong with a detached node being mounted in two different trees, but when one is not aware
     * of that and hasn't made sure that it's only mounted in one of them at a time, it can lead to confusing situations
     * where the detached node will refuse to show up in the new location for no apparent reason.
     * To make these less confusing, we'll remember the first tree root we're mounted in, and then throw an exception
     * when someone attempts to mount us in a different root.
     * This does mean that we'll artificially disallow the cases where one might want to mount it in different roots,
     * but in exchange for hopefully avoiding some very painful debugging sessions.
     * If you ever run into this limitation, the recommendation is to convert more of your component tree to LayoutDSL,
     * which is generally recommended anyway.
     */
    private var boundRoot: LayoutNodeUIComponent? = null
    private fun checkRoot(root: LayoutNodeUIComponent) {
        when (val boundRoot = boundRoot) {
            null -> this.boundRoot = root
            root -> {}
            else -> throw IllegalStateException(
                "detachedLayout was mounted to more than one LayoutDSL tree. Attempted to mount into " +
                        "root ${root.component.componentName} (${root.component}) but was previously in " +
                        "root ${boundRoot.component.componentName} (${boundRoot.component}). " +
                        "See KDocs above for explanation of why this is disallowed."
            )
        }
    }
}

/**
 * Runs [block] to lay out children of `this` component.
 *
 * The passed [modifier], if any, is applied to `this` component.
 *
 * Note: This does **not** change the constraints of `this`. These must be set up manually or via the passed [modifier].
 *
 * Note: Direct children of `this` will by default be top-left aligned as with all plain Elementa components.
 *   Consider using one of [layoutAsBox], [layoutAsRow], or [layoutAsColumn] instead to get the default center alignment
 *   that is typical for Layout DSL.
 */
inline fun UIComponent.layout(modifier: Modifier = Modifier, block: LayoutScope.() -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    modifier.applyToComponent(this)
    LayoutScope(this).block()
}

/**
 * Runs [block] to lay out children of `this` component as if it was a [box].
 *
 * Note: This does **not** change the size constrains of `this`. These must be set up manually or via [modifier].
 */
fun UIComponent.layoutAsBox(modifier: Modifier = Modifier, block: LayoutScope.() -> Unit): UIComponent {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    setDefaultChildAlignment()
    layout(modifier, block)
    return this
}

/**
 * Runs [block] to lay out children of `this` component as if it was a [row].
 *
 * Note: This does **not** change the size constrains of `this`. These must be set up manually or via [modifier].
 *   For the width, one would typically use [Modifier.fillWidth] or [Modifier.childBasedWidth].
 *   For the height, one would typically use [Modifier.fillHeight] or [Modifier.childBasedMaxHeight].
 */
fun UIComponent.layoutAsRow(modifier: Modifier, horizontalArrangement: Arrangement = Arrangement.spacedBy(), verticalAlignment: Alignment = Alignment.Center, block: LayoutScope.() -> Unit): UIComponent {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    setDefaultChildAlignment(y = verticalAlignment)
    layout(modifier, block)
    horizontalArrangement.initialize(this, Axis.HORIZONTAL)
    return this
}

/**
 * Runs [block] to lay out children of `this` component as if it was a [column].
 *
 * Note: This does **not** change the size constrains of `this`. These must be set up manually or via [modifier].
 *   For the width, one would typically use [Modifier.fillWidth] or [Modifier.childBasedMaxWidth].
 *   For the height, one would typically use [Modifier.fillHeight] or [Modifier.childBasedHeight].
 */
fun UIComponent.layoutAsColumn(modifier: Modifier, verticalArrangement: Arrangement = Arrangement.spacedBy(), horizontalAlignment: Alignment = Alignment.Center, block: LayoutScope.() -> Unit): UIComponent {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    setDefaultChildAlignment(x = horizontalAlignment)
    layout(modifier, block)
    verticalArrangement.initialize(this, Axis.VERTICAL)
    return this
}

// Overloads without Modifier argument
/**
 * Runs [block] to lay out children of `this` component as if it was a [row].
 *
 * Note: This does **not** change the size constrains of `this`. These must be set up manually or via [modifier].
 *   For the width, one would typically use [Modifier.fillWidth] or [Modifier.childBasedWidth].
 *   For the height, one would typically use [Modifier.fillHeight] or [Modifier.childBasedMaxHeight].
 */
fun UIComponent.layoutAsRow(horizontalArrangement: Arrangement = Arrangement.spacedBy(), verticalAlignment: Alignment = Alignment.Center, block: LayoutScope.() -> Unit): UIComponent {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return layoutAsRow(Modifier, horizontalArrangement, verticalAlignment, block)
}
/**
 * Runs [block] to lay out children of `this` component as if it was a [column].
 *
 * Note: This does **not** change the size constrains of `this`. These must be set up manually or via [modifier].
 *   For the width, one would typically use [Modifier.fillWidth] or [Modifier.childBasedMaxWidth].
 *   For the height, one would typically use [Modifier.fillHeight] or [Modifier.childBasedHeight].
 */
fun UIComponent.layoutAsColumn(verticalArrangement: Arrangement = Arrangement.spacedBy(), horizontalAlignment: Alignment = Alignment.Center, block: LayoutScope.() -> Unit): UIComponent {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return layoutAsColumn(Modifier, verticalArrangement, horizontalAlignment, block)
}

/** @see detachedLayout */
class DetachedLayout @PublishedApi internal constructor() {
    internal val node = LayoutNodeDetached()
}

/**
 * Creates a detached [LayoutScope] and runs [block] to populate its content.
 *
 * This returns a [DetachedLayout] which can be mounted into another [LayoutScope] via [LayoutScope.invoke].
 * If the [DetachedLayout] is unmounted again and remounted in a different place, the content will be re-parented
 * accordingly. Note that this will not re-run [block]; it will not re-create the components in a different place; it
 * will actually move them!
 *
 * If the [DetachedLayout] is mounted in multiple places at the same time, it will only appear in one of them. Which
 * one is undefined.
 * So while it doesn't make sense to have it in multiple places at the same time, it is safe to temporarily have it
 * mounted in multiple places, so you do not have to worry about the exact ordering of unmount-remount.
 */
inline fun detachedLayout(block: LayoutScope.() -> Unit): DetachedLayout {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val (detachedLayout, detachedScope) = LayoutScope.makeDetached()
    detachedScope.block()
    return detachedLayout
}


interface LayoutDslComponent {
    fun LayoutScope.layout(modifier: Modifier = Modifier)
}
