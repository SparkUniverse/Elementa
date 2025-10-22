package gg.essential.elementa.unstable.state.v2

import gg.essential.elementa.state.v2.ReferenceHolder
import java.util.function.Consumer
import gg.essential.elementa.state.State as V1State

private class V2AsV1State<T>(private val v2State: State<T>, owner: ReferenceHolder) : V1State<T>() {
  @Suppress("unused") // keep effect alive at least as long as this legacy state instance exists
  private val effect = effect(owner) { super.set(v2State()) }

  override fun get(): T = v2State.get()

  override fun set(value: T) {
    if (v2State is MutableState<*>) {
      (v2State as MutableState<T>).set { value }
    } else {
      super.set(value)
    }
  }
}

/**
 * Converts this state into a v1 [State][V1State].
 *
 * If [V1State.set] is called on the returned state and this value is a [MutableState], then the call is forwarded to
 * [MutableState.set], otherwise only the internal field of the v1 state will be updated (and overwritten again the next
 * time this state changes; much like the old mapped states).
 *
 * Note that as with any listener on a v2 state, the returned v1 state may be garbage collected once there are no more
 * strong references to it. This v2 state will not by itself keep it alive.
 * The [owner] argument serves to prevent this from happening too early, see [effect].
 */
fun <T> State<T>.toV1(owner: ReferenceHolder): V1State<T> = V2AsV1State(this, owner)

/**
 * Converts this state into a v2 [MutableState].
 *
 * The returned state is registered as a listener on the v1 state and as such will live as long as the v1 state.
 * This matches v1 state behavior. If this is not desired, stop using v1 state.
 */
fun <T> V1State<T>.toV2(): MutableState<T> {
  val referenceHolder = ReferenceHolderImpl()
  val v1 = this
  val v2 = mutableStateOf(get())

  v2.onSetValue(referenceHolder) { value ->
    if (v1.get() != value) {
      v1.set(value)
    }
  }
  v1.onSetValue(object : Consumer<T> {
    @Suppress("unused") // keep this alive for as long as the v1 state
    val referenceHolder = referenceHolder

    override fun accept(value: T) {
      v2.set(value)
    }
  })

  return object : MutableState<T> by v2 {
    @Suppress("unused") // keep this alive for as long as the returned v2 state
    val referenceHolder = referenceHolder
  }
}

/**
 * Returns a delegating state with internal mutability. That is, the value of the returned state generally follows the
 * value of the input state (or the state passed to [DelegatingState.rebind]), but [MutableState.set] is not forwarded
 * to the bound state. Instead the new value is stored internally and returned until the input state changes again, at
 * which point it'll be overwritten again.
 *
 * Using such a state (`input.map { it }`) with a `rebindState` method and direct getter+setter methods for the state
 * content was a common anti-pattern used in many places throughout Element.
 * To preserve backwards compatibility for this behavior, this method exists to quickly construct such a state in the v2
 * world.
 * New code should instead just use a regular delegating state and have the setter rebind it to a new immutable state.
 */
internal fun <T> State<T>.wrapWithDelegatingMutableState(): MutableDelegatingState<T> {
  val delegatingState = stateDelegatingTo(this)
  val derivedState =
      derivedState(get()) { owner, derivedState ->
        delegatingState.onSetValue(owner) { derivedState.set(it) }
      }
  // Note: this in an implementation detail of `derivedState`, do not rely on it outside of Elementa
  val mutableState = derivedState as MutableState<T>

  return object : DelegatingState<T>, MutableState<T> by mutableState, MutableDelegatingState<T> {
    override fun rebind(newState: State<T>) {
      delegatingState.rebind(newState)
    }
  }
}

internal interface MutableDelegatingState<T> : DelegatingState<T>, MutableState<T>
