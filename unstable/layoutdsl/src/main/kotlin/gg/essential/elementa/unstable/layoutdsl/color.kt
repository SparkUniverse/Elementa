package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ColorConstraint
import gg.essential.elementa.constraints.animation.Animations
import gg.essential.elementa.dsl.animate
import gg.essential.elementa.dsl.toConstraint
import gg.essential.elementa.state.State
import gg.essential.elementa.state.toConstraint
import gg.essential.elementa.unstable.state.v2.color.toConstraint
import gg.essential.elementa.unstable.state.v2.effect
import gg.essential.elementa.unstable.state.v2.stateOf
import gg.essential.elementa.unstable.state.v2.toV2
import gg.essential.elementa.unstable.util.hasWindow
import java.awt.Color
import gg.essential.elementa.unstable.state.v2.State as StateV2

fun Modifier.color(color: Color) = this then BasicColorModifier { color.toConstraint() }

@Deprecated("Using StateV1 is discouraged, use StateV2 instead")
fun Modifier.color(color: State<Color>) = this then BasicColorModifier { color.toConstraint() }

fun Modifier.color(color: StateV2<Color>) = this then BasicColorModifier { color.toConstraint() }

fun Modifier.hoverColor(color: Color, duration: Float = 0f) = hoverColor(stateOf(color), duration)

@Deprecated("Using StateV1 is discouraged, use StateV2 instead")
fun Modifier.hoverColor(color: State<Color>, duration: Float = 0f) = whenHovered(if (duration == 0f) Modifier.color(color) else Modifier.animateColor(color, duration))

fun Modifier.hoverColor(color: StateV2<Color>, duration: Float = 0f) = whenHovered(if (duration == 0f) Modifier.color(color) else Modifier.animateColor(color, duration))

fun Modifier.animateColor(color: Color, duration: Float = .3f) = animateColor(stateOf(color), duration)

@Deprecated("Using StateV1 is discouraged, use StateV2 instead")
fun Modifier.animateColor(color: State<Color>, duration: Float = .3f) = animateColor(color.toV2(), duration)

fun Modifier.animateColor(color: StateV2<Color>, duration: Float = .3f) = this then AnimateColorModifier(color, duration)

private class AnimateColorModifier(private val colorState: StateV2<Color>, private val duration: Float) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val oldColor = component.constraints.color

        fun animate(color: ColorConstraint) {
            if (component.hasWindow) {
                component.animate {
                    setColorAnimation(Animations.OUT_EXP, duration, color)
                }
            } else {
                component.setColor(color)
            }
        }

        val removeListenerCallback = effect(component) {
            animate(colorState().toConstraint())
        }

        return {
            removeListenerCallback()
            animate(oldColor)
        }
    }
}
