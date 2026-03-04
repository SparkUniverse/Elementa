@file:OptIn(ExperimentalContracts::class)
package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.UIComponent
import gg.essential.elementa.state.State
import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.elementa.unstable.common.not
import gg.essential.elementa.unstable.state.v2.*
import gg.essential.elementa.unstable.state.v2.collections.MutableTrackedList
import gg.essential.elementa.unstable.state.v2.collections.TrackedList
import gg.essential.elementa.unstable.state.v2.collections.trackedListOf
import gg.essential.elementa.unstable.state.v2.combinators.map
import gg.essential.elementa.unstable.state.v2.combinators.not
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import gg.essential.elementa.unstable.state.v2.ListState as ListStateV2
import gg.essential.elementa.unstable.state.v2.State as StateV2

class LayoutScope private constructor(
    private val node: LayoutNodeVirtual,
    private val component: UIComponent,
) {

    constructor(component: UIComponent) : this(LayoutNodeUIComponent(null, component, component).children, component)

    val stateScope: ReferenceHolder
        get() = node.stateScope

    /**
     * As the name says, don't use this unless you really have to.
     */
    val containerDontUseThisUnlessYouReallyHaveTo: UIComponent
        get() = component

    operator fun <T : UIComponent> T.invoke(modifier: Modifier = Modifier, block: LayoutScope.() -> Unit = {}): T {
        addChild(this, modifier, block)
        return this
    }

    fun <T : UIComponent> addChild(childComponent: T, modifier: Modifier = Modifier, block: LayoutScope.() -> Unit = {}) {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }

        modifier.applyToComponent(childComponent)

        val childNode = LayoutNodeUIComponent(node, childComponent, childComponent)
        node.children.add(childNode)

        block(LayoutScope(childNode.children, childComponent))

        node.mountedInComponent?.let { component ->
            childNode.mount(component)
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
        val forEachScope = LayoutNodeVirtual(node, stateScope)
        node.children.add(forEachScope)
        node.mountedInComponent?.let { component ->
            forEachScope.mount(component)
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
                    cachedScope.mount(component)
                }
            } else {
                // If the `forEach` is not cached, we give each child scope its own reference holder.
                // This scope will be dropped once the child scope is removed.
                val childStateScope = if (cache) forEachScope.stateScope else ReferenceHolderImpl()
                val childNode = LayoutNodeVirtual(forEachScope, childStateScope)
                forEachScope.children.add(index, childNode)
                forEachScope.mountedInComponent?.let { component ->
                    childNode.mount(component)
                }
                block(LayoutScope(childNode, component), element)
            }
        }

        fun remove(index: Int, element: T) {
            val removedScope = forEachScope.children.removeAt(index)
            check(removedScope is LayoutNodeVirtual)
            forEachScope.mountedInComponent?.let { component ->
                removedScope.unmount(component)
            }
            getCacheEntry(element)?.add(removedScope)
        }

        fun clear(elements: List<T>) {
            forEachScope.children.forEachIndexed { index, layoutScope ->
                check(layoutScope is LayoutNodeVirtual)
                forEachScope.mountedInComponent?.let { component ->
                    layoutScope.unmount(component)
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
}

private sealed class LayoutNode(
    val parentScope: LayoutNode?,
    val stateScope: ReferenceHolder,
) {
    abstract val childrenScopes: List<LayoutNode>

    var mountedInComponent: UIComponent? = null
        private set

    /** Mounts this node into the given [parentComponent]. */
    fun mount(parentComponent: UIComponent) {
        check(mountedInComponent == null)

        mountedInComponent = parentComponent

        when (this) {
            is LayoutNodeUIComponent -> parentComponent.insertChildAt(component, findInsertionIndex(parentComponent))
            is LayoutNodeVirtual -> children.forEach { it.mount(parentComponent) }
        }
    }

    /** Unmounts this node from the given [parentComponent]. */
    fun unmount(parentComponent: UIComponent) {
        check(mountedInComponent == parentComponent)

        mountedInComponent = null

        when (this) {
            is LayoutNodeUIComponent -> parentComponent.removeChild(component)
            is LayoutNodeVirtual -> children.forEach { it.unmount(parentComponent) }
        }
    }

    /**
     * Finds the index in [parentComponent]`s children at which components of this node should be inserted.
     */
    fun findInsertionIndex(parentComponent: UIComponent): Int {
        if (this is LayoutNodeUIComponent && this.component == parentComponent) {
            return 0
        }
        return when (parentScope!!) {
            is LayoutNodeUIComponent -> 0
            is LayoutNodeVirtual -> {
                val siblings = parentScope.children

                // Check all preceding siblings
                for (index in (0 until siblings.indexOf(this)).reversed()) {
                    siblings[index].findLastMountedComponentIndex(parentComponent)
                        ?.let { return it + 1 }
                }

                // If we can't find anything there, check the siblings one level up, recursively
                return parentScope.findInsertionIndex(parentComponent)
            }
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
    }
}

private class LayoutNodeVirtual(parent: LayoutNode, stateScope: ReferenceHolder) : LayoutNode(parent, stateScope) {
    val children: MutableList<LayoutNode> = mutableListOf()
    override val childrenScopes: List<LayoutNode>
        get() = children
}

private class LayoutNodeUIComponent(parentNode: LayoutNode?, val component: UIComponent, stateScope: ReferenceHolder) : LayoutNode(parentNode, stateScope) {
    val children = LayoutNodeVirtual(this, stateScope)
    init {
        children.mount(component)
    }
    override val childrenScopes: List<LayoutNode>
        get() = listOf(children)
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


interface LayoutDslComponent {
    fun LayoutScope.layout(modifier: Modifier = Modifier)
}
