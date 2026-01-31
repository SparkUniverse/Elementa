package gg.essential.elementa.unstable.state.v2

import gg.essential.elementa.state.v2.ReferenceHolder
import java.util.function.Consumer
import gg.essential.elementa.state.State as V1State

private class V2AsV1State<T>(private val v2State: State<T>, owner: ReferenceHolder) : V1State<T>() {
  @Suppress("unused") // keep effect alive at least as long as this legacy state instance exists
  private val effect = effect(owner) { super.set(v2State()) }

  override fun get(): T = v2State.getUntracked()

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

  effect(referenceHolder) {
    val value = v2()
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
