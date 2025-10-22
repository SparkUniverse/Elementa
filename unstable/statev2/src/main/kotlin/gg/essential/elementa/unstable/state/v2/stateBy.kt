package gg.essential.elementa.unstable.state.v2

/**
 * Creates a state that derives its value using the given [block]. The value of any state may be accessed within this
 * block via [StateByScope.invoke]. These accesses are tracked and the block is automatically re-evaluated whenever any
 * one of them changes.
 */
@Deprecated("Use `memo` (result is cached) or `State` lambda (result is not cached)")
fun <T> stateBy(block: StateByScope.() -> T): State<T> {
    return memo {
        val scope = object : StateByScope {
            override fun <T> State<T>.invoke(): T {
                return with(this@memo) { get() }
            }
        }
        block(scope)
    }
}

@Deprecated("Superseded by `Observer`")
interface StateByScope {
    operator fun <T> State<T>.invoke(): T
}
