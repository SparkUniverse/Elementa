package gg.essential.elementa.unstable.state.v2

import gg.essential.elementa.state.v2.ReferenceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Waits until this [State] has a value which [equals] the given [value]. */
suspend fun <T> State<T>.awaitValue(value: T): T = await { it == value }

/** Waits until this [State] has a non-null value. */
suspend fun <T> State<T?>.awaitNotNull(): T = await { it != null }!!

/** Waits until this [State] has a value for which [accept] returns `true` and returns that value. */
suspend fun <T> State<T>.await(accept: (T) -> Boolean): T {
    // Fast-path
    get().let { if (accept(it)) return it }

    // Slow path
    return suspendCancellableCoroutine { continuation ->
        // We need to have the continuation carry a reference to the unregister function (we do this via
        // invokeOnCancellation at the end of this block), so the effect is kept alive at least as long as the coroutine
        // has any interest in it.
        // The same reference also serves as a cancellation indicator: If it's been set to `null`, the continuation
        // was cancelled and we can unregister the effect the next time it's called.
        var referenceFromContinuation: Any? = null

        var unregister: (() -> Unit)? = null
        unregister = onChange(ReferenceHolder.Weak) { value ->
            if (referenceFromContinuation == null) {
                unregister?.invoke()
                unregister = null
                return@onChange
            }
            if (accept(value)) {
                unregister?.invoke()
                unregister = null
                continuation.resume(value)
            }
        }

        referenceFromContinuation = unregister
        continuation.invokeOnCancellation {
            // Note: we cannot call `unregister` here because `invokeOnCancellation` makes no guarantee about which
            // thread we run on, and `unregister` isn't thread safe.
            // So we'll instead merely drop our reference to the unregister function and leave it to State's weakness
            // properties (or the next onChange invocation) to clean up the registration.
            referenceFromContinuation = null
        }
    }
}

fun <T> State<T>.currentAndFutureValues(): StateIterable<T> = object : StateIterable<T> {
    override fun iterator(): StateIterator<T> {
        return object : StateIterator<T> {
            private var initial = true
            private var next: T = getUntracked()

            override suspend fun hasNext(): Boolean {
                if (initial) {
                    return true
                }
                next = await { it != next }
                return true
            }

            override fun next(): T {
                initial = false
                return next
            }
        }
    }
}

fun <T> State<T>.futureValues(excludingInitial: T = getUntracked()): StateIterable<T> = object : StateIterable<T> {
    override fun iterator(): StateIterator<T> {
        return object : StateIterator<T> {
            private var next: T = excludingInitial

            override suspend fun hasNext(): Boolean {
                next = await { it != next }
                return true
            }

            override fun next(): T {
                return next
            }
        }
    }
}

interface StateIterable<T> {
    operator fun iterator(): StateIterator<T>
}

interface StateIterator<T> {
    suspend operator fun hasNext(): Boolean
    operator fun next(): T
}

/**
 * Returns a new [State] which contains result of applying the given suspending [block] to the value of `this` [State].
 * The returned [State] will return `null` while the function is suspended.
 * When the value of `this` [State] changes, the suspending function is cancelled and a new one is launched.
 *
 * If [previousWhileWorking] is `true` and the value of `this` [State] changes, the returned [State] will continue to
 * provide the latest result (if any) instead of returning `null`. Consequently, if the given [block] never returns
 * `null`, the returned [State] will only ever be `null` during the initial load.
 *
 * If the given [block] returns a result without suspending, the returned [State] is guaranteed to immediately return
 * this value, and does not suffer from the "Recursion" issue described in [effect]'s KDocs.
 * Note that to this end, the [block] is launched on the given [scope] with [CoroutineStart.UNDISPATCHED], so the
 * dispatcher on this [scope] will not be respected until the first suspension point. However, given [State] is not
 * generally thread-safe, the given dispatcher must dispatch to the same thread as the thread currently evaluating the
 * State anyway, so this should not be an issue in practice.
 */
fun <I, R> State<I>.asyncMap(scope: CoroutineScope, previousWhileWorking: Boolean = true, block: suspend (I) -> R): State<R?> {
    val prevResultState = mutableStateOf<Pair<I, R>?>(null)
    var jobInput: I? = null
    var job: Job? = null
    return State {
        val input = this@asyncMap()

        // If we have a previous result and the input is unchanged, we can just use that
        val prevResult = prevResultState()
        if (prevResult != null && prevResult.first == input) {
            return@State prevResult.second
        }

        // Otherwise we need to go compute the result for the new input, unless we're already doing that of course
        if (jobInput != input) {
            jobInput = input
            job?.cancel()
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val result = block(input)
                prevResultState.set(Pair(input, result))
            }

            // If the block returned a result without ever suspending, then we can immediately make use of that
            // result.
            val latestResult = prevResultState()
            if (latestResult != null && latestResult.first == input) {
                return@State latestResult.second
            }
        }

        // Computation in progress, best we can do is provide the previous value if that's acceptable
        return@State if (previousWhileWorking) prevResult?.second else null
    }
}
