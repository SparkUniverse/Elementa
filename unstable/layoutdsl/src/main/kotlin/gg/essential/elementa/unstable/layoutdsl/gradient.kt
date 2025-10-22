package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.effects.Effect
import gg.essential.elementa.unstable.effects.GradientEffect
import gg.essential.elementa.unstable.state.v2.State
import gg.essential.elementa.unstable.state.v2.stateOf
import java.awt.Color

fun Modifier.gradient(top: Color, bottom: Color, _desc: GradientVertDesc = GradientDesc) = gradient(stateOf(top), stateOf(bottom), _desc)
fun Modifier.gradient(left: Color, right: Color, _desc: GradientHorzDesc = GradientDesc) = gradient(stateOf(left), stateOf(right), _desc)

fun Modifier.gradient(top: State<Color>, bottom: State<Color>, _desc: GradientVertDesc = GradientDesc) = gradient(top, top, bottom, bottom)
fun Modifier.gradient(left: State<Color>, right: State<Color>, _desc: GradientHorzDesc = GradientDesc) = gradient(left, right, left, right)

sealed interface GradientVertDesc
sealed interface GradientHorzDesc
private object GradientDesc : GradientVertDesc, GradientHorzDesc

fun Modifier.gradient(
    topLeft: State<Color>,
    topRight: State<Color>,
    bottomLeft: State<Color>,
    bottomRight: State<Color>,
) = effect { GradientEffect(topLeft, topRight, bottomLeft, bottomRight) }

private fun Modifier.effect(effect: () -> Effect) = this then {
    val instance = effect()
    enableEffect(instance)
    return@then {
        removeEffect(instance)
    }
}
