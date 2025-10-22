package gg.essential.elementa.unstable.state.v2.utils

import gg.essential.elementa.unstable.state.v2.State
import gg.essential.elementa.unstable.state.v2.mutableStateOf
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

fun <T> CompletableFuture<T>.toState(mainThreadExecutor: Executor): State<T?> {
    if (isDone) {
        return State { get() }
    }

    val resolved by lazy(LazyThreadSafetyMode.NONE) {
        val resolved = mutableStateOf<T?>(null)
        thenAcceptAsync({ resolved.set(it) }, mainThreadExecutor)
        resolved
    }

    return State { if (isDone) get() else resolved() }
}
