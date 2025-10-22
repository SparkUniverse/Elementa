package gg.essential.elementa.unstable.state.v2

import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.elementa.unstable.state.v2.impl.Impl
import gg.essential.elementa.unstable.state.v2.impl.basic.MarkThenPushAndPullImpl

private val impl: Impl = MarkThenPushAndPullImpl

/**
 * Note: This interface must not be implemented by user code. The State implementation may cast it to its internal
 *       implementation type without checking.
 */
interface ObserverImpl

/**
 * A marker interface for an object which may observe which states are being accessed, such that it can then subscribe
 * to these states to be updated when they change.
 *
 * Note that the duration during which a given [Observer] can be used is usually limited to the call in which it was
 * received.
 * It should not be stored (neither in a field, nor implicitly in an asynchronous lambda) and then used at a later time.
 */
interface Observer {
    val observerImpl: ObserverImpl

    /**
     * Get the current value of the State object and subscribe the observer to be re-evaluated when it changes.
     */
    operator fun <T> State<T>.invoke(): T = with(this@Observer) { get() }
}

/**
 * An [Observer] which does not track accesses.
 *
 * May be used to evaluate a method which requires an [Observer] once to get the current value when you do not care
 * about future changes.
 * To get the current value of a [State], one can also use the [State.getUntracked] shortcut.
 */
object Untracked : Observer, ObserverImpl {
    override val observerImpl: ObserverImpl
        get() = this
}

/**
 * Creates a [State] which lazily computes its value via the given pure function [func] and caches the result until
 * one of the observed dependencies changes.
 *
 * You **MUST NOT** use [memo] when [func] triggers any side effects; there are no guarantees for when or even how often
 * [func] is called (it is however guaranteed to always see a consistent view of all other [State]s).
 * To have an external system react to changes in the State system (i.e. for it to "have an effect"), use [effect].
 *
 * The two main use cases for [memo] are:
 * - [func] represents a non-trivial / expensive computation which you do not want to re-evaluate on each access
 * - [func] simplifies its dependencies (e.g. picks one item out of a list) and you do not want its dependents to
 *   unnecessarily be re-evaluated even though the simplified value is unchanged (e.g. whenever any other entry in the
 *   list is changed)
 *
 * If neither of the above applies, consider simply creating a custom [State] implementation which computes your [func]
 * every time its [State.get] is called (e.g. instead of `memo { myState() + 1 }` write `State { myState() + 1 }`).
 * Doing so has significantly lower overhead (just the cost of a single lambda) than [memo].
 */
fun <T> memo(func: Observer.() -> T): State<T> = impl.memo(func)

/**
 * Creates a [State] which lazily [get][State.get]s its value from `this` State and caches the result until one of the
 * observed dependencies changes.
 * May return `this` if it is already such a State.
 *
 * Semantically `State { func() }.memo()` is equivalent to `memo { func() }`.
 *
 * @see [memo]
 */
fun <T> State<T>.memo(): State<T> = memo inner@{ this@memo() }

/**
 * Runs the given function [func] once immediately and whenever any of the [State]s it [observes][Observer] change.
 *
 * A "cleanup" function is returned which when invoked will unregister the effect, such that it will no longer be called
 * thereafter.
 *
 * Hint: If a [State] you wish to use often has unrelated changes you do not care about, consider breaking it down into
 *       a smaller [State] ahead of time using [memo].
 *
 * ### Lifetime
 *
 * The effect registration is weak by default.
 * This means that it may be garbage collected if no other strong references to the returned function exist.
 * Once an effect is garbage collected, it will (obviously) no longer be called.
 *
 * Keeping a strong reference to the returned function is easy to forget, so this method requires you
 * to explicitly pass in an object which will maintain a strong reference to it for you.
 * With that, your effect will stay active **at least** as long as the given [owner] is alive (unless the returned
 * function is explicitly invoked, in which case it ceases operation immediately).
 *
 * In general, the lifetime of your effect should match the lifetime of the passed [owner], usually the thing
 * (e.g. [UIComponent]) the effect is modifying.
 * If the owner far outlives your effect, you may be unnecessarily running your effect and leaking memory because owner
 * will keep all those effects and anything they reference alive far beyond the point where they are needed.
 * If your effect outlives the owner, then it may become inactive sooner than you expected and whatever it is
 * updating might no longer update properly.
 *
 * If you wish to manually keep your effect alive (by holding on to the returned function), pass [ReferenceHolder.Weak]
 * as the owner.
 *
 * ### Recursion
 *
 * You should avoid calling [MutableState.set] from the given function.
 *
 * While the State system does support recursion, such nested state changes cannot be performed atomically and as such
 * it is very much possible that another [effect] has already observed both the value that trigger your [effect] but
 * also the old value of the state you want to update;
 * it will then be invoked again which, depending on what it does, may have unintended consequences.
 *
 * To have the value of [State] depend on one or more other [State]s, use [memo] to create it.
 */
fun effect(referenceHolder: ReferenceHolder, func: Observer.() -> Unit): () -> Unit = impl.effect(referenceHolder, func)

/**
 * Runs the given function [func] whenever the value of `this` State changes.
 *
 * See [effect] for details.
 */
fun <T> State<T>.onChange(referenceHolder: ReferenceHolder, func: Observer.(value: T) -> Unit): () -> Unit {
    var first = true
    return effect(referenceHolder) {
        val value = this@onChange()
        if (first) {
            first = false
        } else {
            func(value)
        }
    }
}

/**
 * The base for all Elementa State objects.
 *
 * State objects are essentially just a wrapper around a (potentially computed) value with the ability to subscribe to
 * changes.
 *
 * The primary advantage of using state is that a single state object can be shared between multiple
 * components or constraints as well as re-used and combined to derive other State from it.
 * All in a declarative way, i.e. no need to manually go and remember to update every piece of GUI, you only update
 * the base [MutableState] instance, and everyone who cares will have subscribed (directly or indirectly) and
 * automatically be updated accordingly.
 *
 * This allows one value update to be seen by multiple components or constraints.
 * For example, if a component has many text children, and they all share the same
 * color state variable, then whenever the value of the state object is updated, all of the text
 * components will instantly change color.
 *
 * State also composes well, e.g. a function which returns a `State<Boolean>` for whether a component is hovered can
 * easily be mapped to one or more `State<Color>` (potentially taking into account other state too) which can then be
 * used to color the background/outline/etc. of the same or other components.
 *
 * The most important primitives of the State system:
 * - To create a simple [MutableState] which can be updated manually, use [mutableStateOf].
 * - To create [State] which derives its value from other [State], use [memo] or a custom [State] implementation (see
 *   the documentation on the former for details).
 * - To make external systems react to [State] changes, use [effect].
 *
 * The Elementa State system also provides a bunch of more subtle functionality that may not be apparent at first
 * glance. E.g. it will allow state and effect nodes to be be garbage collected when they are no longer needed, and it
 * will generally guarantee that all views of the State system are consistent, i.e. when there are states derived from
 * other states, you'll either see the old value of all of them, or the updated values for all of them, but never an
 * inconsistent mix of the two.
 *
 * Those readers familiar with other reactive/signal libraries (e.g. SolidJS, Leptos, Angular, MobX) may notice
 * many similarities to these because [State] is pretty much Elementa's solution to the same set of problems.
 */
fun interface State<out T> {
    /**
     * Get the current value of this State object and subscribe the observer to be re-evaluated when it changes.
     */
    fun Observer.get(): T

    /**
     * Get the current value of this State object.
     */
    fun getUntracked(): T = with(Untracked) { get() }

  /** Get the value of this State object */
  @Deprecated("Calls to this method are not tracked. If this is intentional, use `getUntracked` instead.")
  fun get(): T = getUntracked()

  @Deprecated("If this method is used to update dependent states, use `stateBy` instead.\n" +
          "Otherwise the State system cannot be guaranteed that downsteam states have a consistent view of upstream" +
          "values (i.e. so called \"glitches\" may occur) and all dependences will be forced to evaluate eagerly" +
          "instead of the usual lazy behavior (where states are only updated if there is a consumer).\n" +
          "\n" +
          "If this method is used to drive a final effect (e.g. updating some non-State UI property), and you also" +
          "care about the initial value of the state, consider using `effect` instead.\n" +
          "If you really only care about changes and not the inital value, use `onChange`.")
  fun onSetValue(owner: ReferenceHolder, listener: (T) -> Unit): () -> Unit = onChange(owner) { listener(it) }
}

/* ReferenceHolder is defined in Elementa as:
/**
 * Holds strong references to listeners to prevent them from being garbage collected.
 * @see State.onSetValue
 */
interface ReferenceHolder {
  fun holdOnto(listener: Any): () -> Unit

  object Weak : ReferenceHolder {
    override fun holdOnto(listener: Any): () -> Unit = {}
  }
}
 */

/** A [State] with a value that can be changed via [set] */
@JvmDefaultWithoutCompatibility
interface MutableState<T> : State<T> {
  /**
   * Update the value of this State object.
   *
   * After the value has been updated, all listeners of this State object are notified.
   *
   * The provided lambda must be a pure function which will return the new value for this State give
   * the current value.
   *
   * Note that while most basic State implementations will call the lambda and notify listeners
   * immediately, there is no general requirement for them to do so, and specialized State
   * implementations may delay either or both to e.g. batch multiple updates together.
   */
  fun set(mapper: (T) -> T)

  /**
   * Update the value of this State object.
   *
   * After the value has been updated, all listeners of this State object are notified.
   *
   * Note that while most basic State implementations will update and notify listeners immediately,
   * there is no general requirement for them to do so, and specialized State implementations may
   * delay either or both to e.g. batch multiple updates together.
   *
   * @see [set]
   */
  fun set(value: T) = set { value }
}

/** A [State] delegating to a configurable target [State] */
interface DelegatingState<T> : State<T> {
  fun rebind(newState: State<T>)
}

/** A [MutableState] delegating to a configurable target [MutableState] */
@JvmDefaultWithoutCompatibility
interface DelegatingMutableState<T> : MutableState<T> {
  fun rebind(newState: MutableState<T>)
}

/** Creates a new [State] with the given value. */
fun <T> stateOf(value: T): State<T> = ImmutableState(value)

/** Creates a new [MutableState] with the given initial value. */
fun <T> mutableStateOf(value: T): MutableState<T> = impl.mutableState(value)

/** Creates a new [DelegatingState] with the given target [State]. */
fun <T> stateDelegatingTo(state: State<T>): DelegatingState<T> = impl.stateDelegatingTo(state)

/** Creates a new [DelegatingMutableState] with the given target [MutableState]. */
fun <T> mutableStateDelegatingTo(state: MutableState<T>): DelegatingMutableState<T> =
    impl.mutableStateDelegatingTo(state)

/** Creates a [State] which derives its value in a user-defined way from one or more other states */
@Deprecated("See `State.onSetValue`. Use `stateBy` instead.")
fun <T> derivedState(
    initialValue: T,
    builder: (owner: ReferenceHolder, derivedState: MutableState<T>) -> Unit,
): State<T> = impl.derivedState(initialValue, builder)

/**
 * Combines this [State] with the given custom [setter] function to create a custom [MutableState].
 */
fun <T, S : State<T>> S.withSetter(setter: S.(update: (value: T) -> T) -> Unit): MutableState<T> =
    object : MutableState<T>, State<T> by this {
        override fun set(mapper: (T) -> T) {
            setter(mapper)
        }
    }

/** A simple, immutable implementation of [State] */
private class ImmutableState<T>(private val value: T) : State<T> {
  override fun get(): T = value
  override fun onSetValue(owner: ReferenceHolder, listener: (T) -> Unit): () -> Unit = {}
  override fun Observer.get(): T = value
  override fun getUntracked(): T = value
}

/** A simple implementation of [ReferenceHolder] */
class ReferenceHolderImpl : ReferenceHolder {
  private val heldReferences = mutableListOf<Any>()

  override fun holdOnto(listener: Any): () -> Unit {
    heldReferences.add(listener)
    return { heldReferences.remove(listener) }
  }
}
