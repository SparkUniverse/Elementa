package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.*
import gg.essential.elementa.constraints.animation.*
import gg.essential.elementa.dsl.*
import gg.essential.elementa.unstable.common.constraints.FillConstraintIncludingPadding
import gg.essential.elementa.unstable.state.v2.State
import gg.essential.elementa.unstable.state.v2.effect
import gg.essential.elementa.unstable.state.v2.stateOf
import gg.essential.elementa.unstable.util.hasWindow

fun Modifier.fillParent(fraction: Float = 1f, padding: Float = 0f) =
    fillWidth(fraction, padding).fillHeight(fraction, padding)

/** Fills [fraction] of parent width minus [leftPadding] and aligns [leftPadding] pixels from the left */
fun Modifier.fillWidth(fraction: Float = 1f, leftPadding: Float, _desc: Int = 0) =
    fillWidth(fraction, leftPadding, false).alignHorizontal(Alignment.Start(leftPadding))

/** Fills [fraction] of parent width minus [rightPadding] and aligns [rightPadding] pixels from the right */
fun Modifier.fillWidth(fraction: Float = 1f, rightPadding: Float, _desc: Short = 0) =
    fillWidth(fraction, rightPadding, false).alignHorizontal(Alignment.End(rightPadding))

/** Fills [fraction] of parent width minus [padding] from both sides */
fun Modifier.fillWidth(fraction: Float = 1f, padding: Float = 0f) = fillWidth(fraction, padding, true)

private fun Modifier.fillWidth(fraction: Float, padding: Float, doublePadding: Boolean) =
    this then when {
        padding == 0f -> BasicWidthModifier { RelativeConstraint(fraction) }
        doublePadding && padding == padding.toInt().toFloat() -> BasicWidthModifier { RelativeConstraint(fraction) - (padding * 2).pixels }
        doublePadding -> BasicWidthModifier { RelativeConstraint(fraction) - padding.pixels * 2 }
        else -> BasicWidthModifier { RelativeConstraint(fraction) - padding.pixels }
    }

/** Fills [fraction] of parent height minus [topPadding] and aligns [topPadding] pixels from the top */
fun Modifier.fillHeight(fraction: Float = 1f, topPadding: Float, _desc: Int = 0) =
    fillHeight(fraction, topPadding, false).alignVertical(Alignment.Start(topPadding))

/** Fills [fraction] of parent height minus [bottomPadding] and aligns [bottomPadding] pixels from the bottom */
fun Modifier.fillHeight(fraction: Float = 1f, bottomPadding: Float, _desc: Short = 0) =
    fillHeight(fraction, bottomPadding, false).alignVertical(Alignment.End(bottomPadding))

/** Fills [fraction] of parent height minus [padding] from both sides */
fun Modifier.fillHeight(fraction: Float = 1f, padding: Float = 0f) = fillHeight(fraction, padding, true)

private fun Modifier.fillHeight(fraction: Float, padding: Float, doublePadding: Boolean) =
    this then when {
        padding == 0f -> BasicHeightModifier { RelativeConstraint(fraction) }
        doublePadding && padding == padding.toInt().toFloat() -> BasicHeightModifier { RelativeConstraint(fraction) - (padding * 2).pixels }
        doublePadding -> BasicHeightModifier { RelativeConstraint(fraction) - padding.pixels * 2 }
        else -> BasicHeightModifier { RelativeConstraint(fraction) - padding.pixels }
    }

fun Modifier.childBasedSize(padding: Float = 0f) = childBasedWidth(padding).childBasedHeight(padding)

fun Modifier.childBasedWidth(padding: Float = 0f) =
    this then when {
        padding == 0f -> BasicWidthModifier { ChildBasedSizeConstraint() }
        padding == padding.toInt().toFloat() -> BasicWidthModifier { ChildBasedSizeConstraint() + (padding * 2).pixels }
        else -> BasicWidthModifier { ChildBasedSizeConstraint() + padding.pixels * 2 }
    }

fun Modifier.childBasedHeight(padding: Float = 0f) =
    this then when {
        padding == 0f -> BasicHeightModifier { ChildBasedSizeConstraint() }
        padding == padding.toInt().toFloat() -> BasicHeightModifier { ChildBasedSizeConstraint() + (padding * 2).pixels }
        else -> BasicHeightModifier { ChildBasedSizeConstraint() + padding.pixels * 2 }
    }

fun Modifier.childBasedMaxSize(padding: Float = 0f) = childBasedMaxWidth(padding).childBasedMaxHeight(padding)

fun Modifier.childBasedMaxWidth(padding: Float = 0f) =
    this then when {
        padding == 0f -> BasicWidthModifier { ChildBasedMaxSizeConstraint() }
        padding == padding.toInt().toFloat() -> BasicWidthModifier { ChildBasedMaxSizeConstraint() + (padding * 2).pixels }
        else -> BasicWidthModifier { ChildBasedMaxSizeConstraint() + padding.pixels * 2 }
    }

fun Modifier.childBasedMaxHeight(padding: Float = 0f) =
    this then when {
        padding == 0f -> BasicHeightModifier { ChildBasedMaxSizeConstraint() }
        padding == padding.toInt().toFloat() -> BasicHeightModifier { ChildBasedMaxSizeConstraint() + (padding * 2).pixels }
        else -> BasicHeightModifier { ChildBasedMaxSizeConstraint() + padding.pixels * 2 }
    }

fun Modifier.fillRemainingWidth() = this then BasicWidthModifier { FillConstraintIncludingPadding(useSiblings = true) }

fun Modifier.fillRemainingHeight() = this then BasicHeightModifier { FillConstraintIncludingPadding(useSiblings = true) }

fun Modifier.width(width: Float) = this then BasicWidthModifier { width.pixels() }

fun Modifier.height(height: Float) = this then BasicHeightModifier { height.pixels() }

fun Modifier.width(other: UIComponent) = this then BasicWidthModifier { (CopyConstraintFloat() boundTo other) }

fun Modifier.height(other: UIComponent) = this then BasicHeightModifier { CopyConstraintFloat() boundTo other }

fun Modifier.widthAspect(aspect: Float) = this then BasicWidthModifier { AspectConstraint(aspect) }

fun Modifier.heightAspect(aspect: Float) = this then BasicHeightModifier { AspectConstraint(aspect) }

fun Modifier.animateWidth(width: Float, duration: Float, strategy: AnimationStrategy = Animations.OUT_EXP) = animateWidth(stateOf { width.pixels }, duration, strategy)

fun Modifier.animateHeight(height: Float, duration: Float, strategy: AnimationStrategy = Animations.OUT_EXP) = animateHeight(stateOf { height.pixels }, duration, strategy)

fun Modifier.animateWidth(width: State<() -> WidthConstraint>, duration: Float, strategy: AnimationStrategy = Animations.OUT_EXP) = this then AnimateWidthModifier(width, duration, strategy)

fun Modifier.animateHeight(height: State<() -> HeightConstraint>, duration: Float, strategy: AnimationStrategy = Animations.OUT_EXP) = this then AnimateHeightModifier(height, duration, strategy)

fun Modifier.maxWidth(width: Float) = this then MaxWidthModifier(width)

fun Modifier.maxHeight(height: Float) = this then MaxHeightModifier(height)

private class AnimateWidthModifier(private val newWidth: State<() -> WidthConstraint>, private val duration: Float, private val strategy: AnimationStrategy) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val oldWidth = component.constraints.width

        fun animate(widthConstraint: WidthConstraint) {
            if (component.hasWindow) {
                component.animate {
                    setWidthAnimation(strategy, duration, widthConstraint)
                }
            } else {
                component.setWidth(widthConstraint)
            }
        }

        val removeListenerCallback = effect(component) {
            animate(newWidth()())
        }

        return {
            removeListenerCallback()
            animate(oldWidth)
        }
    }
}

private class AnimateHeightModifier(private val newHeight: State<() -> HeightConstraint>, private val duration: Float, private val strategy: AnimationStrategy) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val oldHeight = component.constraints.height

        fun animate(heightConstraint: HeightConstraint) {
            if (component.hasWindow) {
                component.animate {
                    setHeightAnimation(strategy, duration, heightConstraint)
                }
            } else {
                component.setHeight(heightConstraint)
            }
        }

        val removeListenerCallback = effect(component) {
            animate(newHeight()())
        }

        return {
            removeListenerCallback()
            animate(oldHeight)
        }
    }
}

private class MaxWidthModifier(private val width: Float) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val oldWidth = component.constraints.width
        component.setWidth(min(oldWidth, width.pixels))

        return {
            component.setWidth(oldWidth)
        }
    }
}

private class MaxHeightModifier(private val height: Float) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val oldHeight = component.constraints.height
        component.setHeight(min(oldHeight, height.pixels))
        return {
            component.setHeight(oldHeight)
        }
    }
}
