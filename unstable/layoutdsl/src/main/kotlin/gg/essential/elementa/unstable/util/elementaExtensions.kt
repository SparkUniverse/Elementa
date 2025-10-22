package gg.essential.elementa.unstable.util

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.Window
import gg.essential.elementa.effects.Effect
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.State
import gg.essential.elementa.utils.ObservableAddEvent
import gg.essential.elementa.utils.ObservableClearEvent
import gg.essential.elementa.utils.ObservableList
import gg.essential.elementa.utils.ObservableRemoveEvent
import gg.essential.elementa.unstable.common.onSetValueAndNow
import gg.essential.elementa.unstable.state.v2.*
import gg.essential.elementa.unstable.state.v2.collections.MutableTrackedList
import gg.essential.universal.UMouse
import gg.essential.universal.UResolution
import gg.essential.elementa.unstable.state.v2.ListState as ListStateV2
import gg.essential.elementa.unstable.state.v2.State as StateV2

val UIComponent.hasWindow: Boolean
    get() = this is Window || hasParent && parent.hasWindow

inline fun <reified T : Effect> UIComponent.get() =
    effects.firstNotNullOfOrNull { it as? T }
inline fun <reified T : Effect> UIComponent.getOrPut(init: () -> T) =
    get<T>() ?: init().also { enableEffect(it) }

fun <T> UIComponent.pollingState(initialValue: T? = null, getter: () -> T): State<T> {
    val state = BasicState(initialValue ?: getter())
    addUpdateFunc { _, _ -> state.set(getter()) }
    return state
}

fun <T> UIComponent.pollingStateV2(initialValue: T? = null, getter: () -> T): StateV2<T> {
    val state = mutableStateOf(initialValue ?: getter())
    addUpdateFunc { _, _ -> state.set(getter()) }
    return state
}

@Deprecated("pollingState is now layout-safe by default", ReplaceWith("pollingStateV2(initialValue, getter)"))
fun <T> UIComponent.layoutSafePollingState(initialValue: T? = null, getter: () -> T): StateV2<T> {
    return pollingStateV2(initialValue, getter)
}

/**
 * Creates a state that derives its value using the given [block]. The value of any state may be accessed within this
 * block via [StateScope.invoke]. These accesses are tracked and the block is automatically re-evaluated whenever any
 * one of them changes.
 */
@Deprecated("Using StateV1 is discouraged, use StateV2 instead", ReplaceWith("stateBy", "gg.essential.elementa.unstable.state.v2.StateByKt.stateBy"))
fun <T> stateBy(block: StateScope.() -> T): State<T> {
    val subscribed = mutableMapOf<State<*>, () -> Unit>()
    val observed = mutableSetOf<State<*>>()
    val scope = object : StateScope {
        override fun <T> State<T>.invoke(): T {
            observed.add(this)
            return get()
        }
    }

    val result = BasicState(block(scope))

    fun updateSubscriptions() {
        observed.forEach { state ->
            if (state !in subscribed) {
                val unregister = state.onSetValue {
                    // FIXME this should really just run immediately but State is currently very prone to CME if you
                    //  register or remove a listener while it its callback, so we need to delay here until that's fixed
                    Window.enqueueRenderOperation {
                        val newValue = block(scope)
                        updateSubscriptions()
                        result.set(newValue)
                    }
                }
                subscribed[state] = unregister
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

    return result
}

interface StateScope {
    operator fun <T> State<T>.invoke(): T
}

/**
 * Executes the supplied [block] on this component's animationFrame
 */
fun UIComponent.onAnimationFrame(block: () -> Unit) =
    enableEffect(object : Effect() {
        override fun animationFrame() {
            block()
        }
    })

/**
 * Returns a state representing whether this UIComponent is hovered
 */
fun UIComponent.hoveredStateV2(): StateV2<Boolean> {
    return getOrPut { ComponentHoveredState() }.state
}

private class WindowHoveredState : Effect() {
    private var oldHovered = listOf<UIComponent>()

    init { addUpdateFunc { _, _ -> update() } }
    private fun update() {
        val window = boundComponent as Window

        // Positions the mouse in the center of pixels so isPointInside will
        // pass for items 1 pixel wide objects. See ElementaVersion v2 for more details
        val halfPixel = 0.5f / UResolution.scaleFactor.toFloat()
        val mouseX = UMouse.Scaled.x.toFloat() + halfPixel
        val mouseY = UMouse.Scaled.y.toFloat() + halfPixel

        val hit = (window.hoveredFloatingComponent?.hitTest(mouseX, mouseY)) ?: window.hitTest(mouseX, mouseY)
        val newHovered = hit.selfAndParents().toList()
        for (component in oldHovered) {
            if (component !in newHovered) {
                component.get<ComponentHoveredState>()?.state?.set(false)
            }
        }
        for (component in newHovered) {
            if (component !in oldHovered) {
                component.get<ComponentHoveredState>()?.state?.set(true)
            }
        }
        oldHovered = newHovered
    }
}

private class ComponentHoveredState : Effect() {
    val state = mutableStateOf(false)

    override fun setup() {
        Window.of(boundComponent).getOrPut { WindowHoveredState() }
    }
}

fun UIComponent.hoveredState(): State<Boolean> =
    hoveredStateV2().toV1(this)

/** Marker effect for [makeHoverScope]/[hoverScope]. */
private class HoverScope(val state: StateV2<Boolean>) : Effect()

/**
 * This method declares this component and its children to be part of one hover scope.
 * Whether any component inside a hover scope is considered "hovered" depends on whether the scope declares it as such.
 * By default the scope is considered hovered based on the [hoveredState] of this component but this may be overridden
 * by passing a custom non-null [state].
 *
 * Scopes are resolved once on the first draw. As such they should be declared before the component is first drawn,
 * cannot be removed, and are not updated if components are moved between different parents.
 *
 * If multiple scopes are nested, components within the inner scope will solely follow their direct parent scope and
 * be completely oblivious to the outer scope.
 * This can easily be customized by passing a different [state], e.g. passing
 * `hoverScope(parentOnly = true) or hoveredState()` to make children appear as hovered when either the other or the
 * inner scope is hovered.
 *
 * A hover scope may be re-declared on the same component to overwrite its source `state`. This allows a mostly
 * self-contained component to declare a hover scope on itself by default; and if this default hover scope is not
 * appropriate for some use case, the user may call `makeHoverScope` again on the component from the outside with a
 * custom [state] (e.g. with `hoverScope(parentOnly = true)` to simply make it inherit from an outer scope as if it
 * wasn't declared in the first place).
 * Note that the same rules about first-time resolving still apply.
 */
fun UIComponent.makeHoverScope(state: State<Boolean>? = null) =
    makeHoverScope(state?.toV2() ?: hoveredStateV2())

fun UIComponent.makeHoverScope(state: StateV2<Boolean>) = apply {
    removeEffect<HoverScope>()
    enableEffect(HoverScope(state))
}

/**
 * Receives the hover scope which this component is subject to.
 *
 * This method must not be called on components which are not part of any hover scope.
 *
 * @see [makeHoverScope]
 */
fun UIComponent.hoverScopeV2(parentOnly: Boolean = false): StateV2<Boolean> {
    class HoverScopeConsumer : Effect() {
        private val boundTo = mutableStateOf<StateV2<Boolean>?>(null)
        val state = StateV2 { (boundTo.getUntracked() ?: boundTo())?.invoke() ?: false }

        override fun setup() {
            val sequence = if (parentOnly) parent.selfAndParents() else selfAndParents()
            val scope =
                sequence.firstNotNullOfOrNull { component ->
                    component.effects.firstNotNullOfOrNull { it as? HoverScope }
                } ?: throw IllegalStateException("No hover scope found for ${this@hoverScopeV2}.")
            Window.enqueueRenderOperation {
                boundTo.set(scope.state)
            }
        }
    }
    val consumer = HoverScopeConsumer()
    enableEffect(consumer)
    return consumer.state
}

fun UIComponent.hoverScope(parentOnly: Boolean = false): State<Boolean> =
    hoverScopeV2(parentOnly).toV1(this)

/** Once inherited, you can apply this to a component via [addTag] to be able to [findChildrenByTag]. */
interface Tag

/** Holder effect for a [Tag] */
private class TagEffect(val tag: Tag) : Effect()

/** Applies a [Tag] to this component. */
fun UIComponent.addTag(tag: Tag) = apply { enableEffect(TagEffect(tag)) }

/** Removes a [Tag] from this component. */
fun UIComponent.removeTag(tag: Tag) = apply { effects.removeIf { it is TagEffect && it.tag == tag } }

/** Returns a [Tag] of [T] which may or may not be attached to this component. */
inline fun <reified T: Tag> UIComponent.getTag(): T? = getTag(T::class.java)

/** Returns a [Tag] of [T] which may or may not be attached to this component. */
fun <T: Tag> UIComponent.getTag(type: Class<T>): T? {
    val effect = effects.firstNotNullOfOrNull {
        effect -> (effect as? TagEffect)?.takeIf { type.isInstance(it.tag) }
    } ?: return null

    return type.cast(effect.tag)
}

/**
 * Searches for any children which contain a certain [Tag].
 * See [addTag] for applying a [Tag] to a component.
 */
fun UIComponent.findChildrenByTag(tag: Tag, recursive: Boolean = false) = findChildrenByTag<Tag>(recursive) { it == tag }

/**
 * Finds any children which have a tag which matches the [predicate].
 * By default, this predicate will match any [Tag] of [T].
 *
 * See [addTag] for applying a [Tag] to a component.
 */
inline fun <reified T: Tag> UIComponent.findChildrenByTag(
    recursive: Boolean = false,
    noinline predicate: UIComponent.(T) -> Boolean = { true },
) = findChildrenByTag(T::class.java, recursive, predicate)

/**
 * Returns a map of [UIComponent]s (children) to their [Tag]s of [T].
 * By default, this predicate will match any [Tag] of [T].
 *
 * See [addTag] for applying a [Tag] to a component.
 */
inline fun <reified T: Tag> UIComponent.findChildrenAndTags(
    recursive: Boolean = false,
    noinline predicate: UIComponent.(T) -> Boolean = { true },
) = findChildrenAndTags(T::class.java, recursive, predicate)

/**
 * Finds any children which have a tag which matches the [predicate].
 * By default, this predicate will match any [Tag] of [T].
 *
 * See [addTag] for applying a [Tag] to a component.
 */
fun <T: Tag> UIComponent.findChildrenByTag(
    type: Class<T>,
    recursive: Boolean = false,
    predicate: UIComponent.(T) -> Boolean = { true }
): List<UIComponent> {
    val found = mutableListOf<UIComponent>()

    fun addToFoundIfHasTag(component: UIComponent) {
        for (child in component.children) {
            val tag = child.getTag(type)
            if (tag != null && child.predicate(tag)) {
                found.add(child)
            }

            if (recursive) {
                addToFoundIfHasTag(child)
            }
        }
    }

    addToFoundIfHasTag(this)

    return found
}

/**
 * Returns a map of [UIComponent]s (children) to their [Tag]s of [T].
 * By default, this predicate will match any [Tag] of [T].
 *
 * See [addTag] for applying a [Tag] to a component.
 */
fun <T: Tag> UIComponent.findChildrenAndTags(
    type: Class<T>,
    recursive: Boolean = false,
    predicate: UIComponent.(T) -> Boolean = { true }
): List<Pair<UIComponent, T>> {
    val found = mutableListOf<Pair<UIComponent, T>>()

    fun addToFoundIfHasTag(component: UIComponent) {
        for (child in component.children) {
            val tag = child.getTag(type)
            if (tag != null && child.predicate(tag)) {
                found.add(child to tag)
            }

            if (recursive) {
                addToFoundIfHasTag(child)
            }
        }
    }

    addToFoundIfHasTag(this)

    return found
}

/** Returns a [Sequence] consisting of this component and its parents (including the Window) in that order. */
fun UIComponent.selfAndParents() =
    generateSequence(this) { if (it.parent != it) it.parent else null }


fun UIComponent.isComponentInParentChain(target: UIComponent): Boolean {
    var component: UIComponent = this
    while (component.hasParent && component !is Window) {
        component = component.parent
        if (component == target)
            return true
    }

    return false
}

fun UIComponent.isInComponentTree(): Boolean =
    this is Window || hasParent && this in parent.children && parent.isInComponentTree()

fun <E> ObservableList<E>.onItemRemoved(callback: (E) -> Unit) {
    addObserver { _, arg ->
        if (arg is ObservableRemoveEvent<*>) {
            callback(arg.element.value as E)
        }
    }
}

fun <E> ObservableList<E>.onItemAdded(callback: (E) -> Unit) {
    addObserver { _, arg ->
        if (arg is ObservableAddEvent<*>) {
            callback(arg.element.value as E)
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <E> ObservableList<E>.toStateV2List(): ListStateV2<E> {
    val stateList = mutableStateOf(MutableTrackedList(this.toMutableList()))

    this.addObserver { _, arg ->
        when (arg) {
            is ObservableAddEvent<*> -> stateList.add(arg.element.index, arg.element.value as E)
            is ObservableClearEvent<*> -> stateList.clear()
            is ObservableRemoveEvent<*> -> stateList.removeAt(arg.element.index)
        }
    }

    return stateList
}
