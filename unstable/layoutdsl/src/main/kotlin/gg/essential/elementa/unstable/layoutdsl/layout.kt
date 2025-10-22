@file:OptIn(ExperimentalContracts::class)
package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.UIComponent
import gg.essential.elementa.state.State
import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.elementa.unstable.common.ListState
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

class LayoutScope(
    private val component: UIComponent,
    private val parentScope: LayoutScope?,
    val stateScope: ReferenceHolder,
) {
    /**
     * As the name says, don't use this unless you really have to.
     */
    val containerDontUseThisUnlessYouReallyHaveTo: UIComponent
        get() = component

    private val childrenScopes = mutableListOf<LayoutScope>()

    operator fun <T : UIComponent> T.invoke(modifier: Modifier = Modifier, block: LayoutScope.() -> Unit = {}): T {
        this@LayoutScope.component.getChildModifier().applyToComponent(this)
        modifier.applyToComponent(this)

        val childScope = LayoutScope(this, this@LayoutScope, this)
        childrenScopes.add(childScope)

        childScope.block()

        val index = childScope.findNextIndexIn(component) ?: 0
        component.insertChildAt(this, index)

        return this
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
     * Note that given old scopes are discarded, care must be taken to not inadvertently leak child components, e.g. via
     * listener subscriptions or other links that cannot be cleaned up automatically.
     * If the space of possible [T] is very limited, [cache] may be set to `true` to retain old scopes after they are
     * removed and to re-use them if their corresponding [T] value is re-introduced at a later time.
     * This requires that [T] be usable as a key in a HashMap.
     */
    fun <T> forEach(state: ListState<T>, cache: Boolean = false, block: LayoutScope.(T) -> Unit) {
        forEach(state.toV2().toListState(), cache, block)
    }

    /**
     * StateV2 support for forEach
     */
    fun <T> forEach(list: ListStateV2<T>, cache: Boolean = false, block: LayoutScope.(T) -> Unit) {
        val forEachScope = LayoutScope(component, this@LayoutScope, stateScope)
        childrenScopes.add(forEachScope)

        val cacheMap =
            if (cache) mutableMapOf<T, MutableList<LayoutScope>>()
            else null
        fun getCacheEntry(key: T) = cacheMap?.getOrPut(key) { mutableListOf() }

        fun add(index: Int, element: T) {
            val cachedScope = getCacheEntry(element)?.removeLastOrNull()
            if (cachedScope != null) {
                forEachScope.childrenScopes.add(index, cachedScope)
                if (forEachScope.isVirtualScopeMounted()) {
                    cachedScope.remount()
                }
            } else {
                // If the `forEach` is not cached, we give each child scope its own reference holder.
                // This scope will be dropped once the child scope is removed.
                val childStateScope = if (cache) forEachScope.stateScope else ReferenceHolderImpl()
                val newScope = LayoutScope(component, forEachScope, childStateScope)

                forEachScope.childrenScopes.add(index, newScope)
                newScope.block(element)
                if (!forEachScope.isVirtualScopeMounted()) {
                    newScope.unmount()
                }
            }
        }

        fun remove(index: Int, element: T) {
            val removedScope = forEachScope.childrenScopes.removeAt(index)
            removedScope.unmount()
            getCacheEntry(element)?.add(removedScope)
        }

        fun clear(elements: List<T>) {
            forEachScope.childrenScopes.forEachIndexed { index, layoutScope ->
                layoutScope.unmount()
                getCacheEntry(elements[index])?.add(layoutScope)
            }
            forEachScope.childrenScopes.clear()
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

    /** Whether this scope is a virtual "forEach" scope. These share their target component with their parent scope. */
    private fun isVirtual(): Boolean {
        return parentScope?.component == component
    }

    /** Whether this virtual ("forEach") scope is presently (virtually) mounted inside its parent [component]. */
    private fun isVirtualScopeMounted(): Boolean {
        val parent = parentScope ?: return true // if we don't have a parent, we can only assume that we're mounted

        // Check if this scope is currently mounted in its parent scope
        if (this !in parent.childrenScopes) {
            return false
        }

        // If the parent scope is a virtual scope as well, we can only be mounted if it is
        if (parent.isVirtual()) {
            return parent.isVirtualScopeMounted()
        }

        return true
    }

    /** Removes from [component] all components that where added within this scope. */
    private fun unmount() {
        for (childScope in childrenScopes) {
            if (childScope.component == this.component) {
                // This is a forEach scope, recurse down into its children
                childScope.unmount()
            } else {
                component.removeChild(childScope.component)
            }
        }
    }

    /** Inverse of [unmount]. Re-adds to [component] all components that where added within this scope. */
    private fun remount() {
        for (childScope in childrenScopes) {
            if (childScope.component == this.component) {
                // This is a forEach scope, recurse down into its children
                childScope.remount()
            } else {
                val index = childScope.findNextIndexIn(component) ?: 0
                component.insertChildAt(childScope.component, index)
            }
        }
    }

    /**
     * Finds the index in [parent]'s children at which a component should be inserted to end up right after [component].
     * Works even when [component] is not currently present in [parent] by recursively searching the layout tree.
     * If [parent] has no children in the layout tree, `null` is returned.
     */
    private fun findNextIndexIn(parent: UIComponent): Int? {
        /** Searches this subtree for an index. */
        fun LayoutScope.searchSubTree(range: IntProgression = childrenScopes.indices.reversed()): Int? {
            if (component == parent) {
                // This is a node in the subtree belonging to [parent] (e.g. the main scope, or a forEach scope),
                // so we recursively search the children
                for (index in range) {
                    childrenScopes[index].searchSubTree()
                        ?.let { return it }
                }
                return null
            } else {
                // Check if this child is currently present within its parent
                return parent.children.indexOf(component).takeIf { it != -1 }
            }
        }

        /** Searches by recursively traversing upwards the tree if no index can be found in this subtree. */
        fun LayoutScope.search(beforeScope: LayoutScope): Int? {
            val beforeIndex = childrenScopes.indexOf(beforeScope)

            // Check all preceding siblings
            searchSubTree((0 until beforeIndex).reversed())
                ?.let { return it }

            // If we can't find anything there, check the siblings one level up, recursively
            val parentScope = parentScope ?: return null
            // Though once we've found a scope that targets [parent], then we can stop ascending if we find a scope
            // that doesn't target [parent] (i.e. one for parent's parent) because we only want to search all scopes
            // targeting [parent].
            if (component == parent && parentScope.component != parent) {
                return null
            }
            return parentScope.search(this)
        }

        return parentScope?.search(this)?.let { it + 1 }
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
    LayoutScope(this, null, this).block()
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
    addChildModifier(Modifier.alignBoth(Alignment.Center))
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
    addChildModifier(Modifier.alignVertical(verticalAlignment))
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
    addChildModifier(Modifier.alignHorizontal(horizontalAlignment))
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
