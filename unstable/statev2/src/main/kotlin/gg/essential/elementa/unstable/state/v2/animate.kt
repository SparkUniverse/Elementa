package gg.essential.elementa.unstable.state.v2

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UpdateFunc
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.animation.AnimationStrategy
import gg.essential.elementa.constraints.animation.Animations
import gg.essential.elementa.effects.Effect
import gg.essential.elementa.state.v2.ReferenceHolder
import java.lang.ref.WeakReference

fun State<Float>.animateTransitions(
    driverComponent: UIComponent,
    duration: Float,
    animationStrategy: AnimationStrategy = Animations.OUT_EXP,
): State<Float> {
    if (duration <= 0f) {
        return this
    }
    return State { stateOf(this@animateTransitions()) }.animateTransitions(driverComponent, duration, animationStrategy)
}

@JvmName("animateTransitionsNested")
fun State<State<Float>>.animateTransitions(
    driverComponent: UIComponent,
    duration: Float,
    animationStrategy: AnimationStrategy = Animations.OUT_EXP,
): State<Float> {
    if (duration <= 0f) {
        return State { this@animateTransitions()() }
    }
    val resultState = mutableStateOf(this.getUntracked())
    driverComponent.enableEffect(AnimationDriver(this, WeakReference(resultState), duration, animationStrategy))
    return memo { resultState()() }
}

private class AnimationDriver(
    driver: State<State<Float>>,
    private val resultStateWeakReference: WeakReference<MutableState<State<Float>>>,
    private val duration: Float,
    private val animationStrategy: AnimationStrategy
): Effect(), UpdateFunc {
    private val animationEventList = mutableListOf<AnimationEvent>()
    private val driverEffect: () -> Unit

    private var previousDriverStateValue = stateOf(0f)
    private var isDestroying = false

    init {
        previousDriverStateValue = driver.getUntracked()
        driverEffect = driver.onChange(ReferenceHolder.Weak) { input ->
            if (animationEventList.isEmpty()) {
                addUpdateFunc(this@AnimationDriver)
            }
            animationEventList.add(AnimationEvent(previousDriverStateValue, input))
            previousDriverStateValue = input
        }
    }

    override fun invoke(dt: Float, dtMs: Int) {
        val resultState = resultStateWeakReference.get()
        if (resultState == null) {
            destroy()
        } else {
            animationEventList.forEach { it.age += dt }
            animationEventList.removeIf { it.age >= duration }
            if (animationEventList.isEmpty()) removeUpdateFunc(this@AnimationDriver)
            resultState.set(State { getAnimationValue() })
        }
    }

    private fun destroy() {
        if (isDestroying) {
            return
        }
        isDestroying = true
        driverEffect()
        Window.enqueueRenderOperation {
            boundComponent.removeEffect(this)
        }
    }

    private fun Observer.getAnimationValue(): Float {
        if (animationEventList.isEmpty()) {
            return previousDriverStateValue()
        }

        return animationEventList.fold(animationEventList.first().startValue()) { acc, event ->
            val linearProgress = event.age / duration
            val animatedProgress = animationStrategy.getValue(linearProgress)
            acc + ((event.endValue() - acc) * animatedProgress)
        }
    }

    private data class AnimationEvent(
        val startValue: State<Float>,
        val endValue: State<Float>,
        var age: Float = 0f,
    )

}