package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.state.State
import gg.essential.elementa.unstable.common.onSetValueAndNow
import gg.essential.elementa.unstable.state.v2.combinators.map
import gg.essential.elementa.unstable.state.v2.effect
import gg.essential.elementa.unstable.state.v2.toV2
import gg.essential.elementa.unstable.state.v2.State as StateV2

@Deprecated("Using StateV1 is discouraged, use StateV2 instead")
fun Modifier.then(state: State<Modifier>): Modifier {
    return this then {
        var reverse: (() -> Unit)? = null

        val cleanupState = state.onSetValueAndNow {
            reverse?.invoke()
            reverse = it.applyToComponent(this)
        };

        {
            cleanupState()
            reverse?.invoke()
            reverse = null
        }
    }
}

fun Modifier.then(state: StateV2<Modifier>): Modifier {
    return this then component@{
        var reverse: (() -> Unit)? = null

        val cleanupState = effect(this) {
            reverse?.invoke()
            reverse = state().applyToComponent(this@component)
        };

        {
            cleanupState()
            reverse?.invoke()
            reverse = null
        }
    }
}

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Using StateV1 is discouraged, use StateV2 instead")
fun Modifier.whenTrue(state: State<Boolean>, activeModifier: Modifier, inactiveModifier: Modifier = Modifier): Modifier =
    then(state.toV2().map { if (it) activeModifier else inactiveModifier })

fun Modifier.whenTrue(state: StateV2<Boolean>, activeModifier: Modifier, inactiveModifier: Modifier = Modifier): Modifier =
    then(state.map { if (it) activeModifier else inactiveModifier })