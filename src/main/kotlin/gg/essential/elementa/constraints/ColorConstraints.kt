package gg.essential.elementa.constraints

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.State
import gg.essential.elementa.dsl.*
import gg.essential.elementa.state.MappedState
import java.awt.Color
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sets the color to be a constant, determined color.
 */
class ConstantColorConstraint(color: State<Color>) : ColorConstraint {
    @JvmOverloads constructor(color: Color = Color.WHITE) : this(BasicState(color))
    override var cachedValue: Color = Color.WHITE
    override var recalculate = true
    override var constrainTo: UIComponent? = null

    private val colorState: MappedState<Color, Color> = color.map { it }

    var color: Color
        get() = colorState.get()
        set(value) { colorState.set(value) }

    fun bindColor(newState: State<Color>) = apply {
        colorState.rebind(newState)
    }

    override fun getColorImpl(component: UIComponent): Color {
        return colorState.get()
    }

    override fun to(component: UIComponent) = apply {
        throw UnsupportedOperationException("Constraint.to(UIComponent) is not available in this context!")
    }

    // Color constraints will only ever have parent dependencies, so there is no possibility
    // of an invalid constraint here
    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) { }
}

/**
 * Sets the color to be constant but with an alpha based off of its parent.
 */
class AlphaAspectColorConstraint(color: State<Color>, alphaValue: State<Float>) : ColorConstraint {
    constructor(color: Color = Color.WHITE, alphaValue: Float = 1f) : this(BasicState(color), BasicState(alphaValue))
    constructor() : this(Color.WHITE, 1f)
    override var cachedValue: Color = Color.WHITE
    override var recalculate = true
    override var constrainTo: UIComponent? = null

    private val colorState: MappedState<Color, Color> = color.map { it }
    private val alphaState: MappedState<Float, Float> = alphaValue.map { it }

    var color: Color
        get() = colorState.get()
        set(value) { colorState.set(value) }
    var alpha: Float
        get() = alphaState.get()
        set(value) { alphaState.set(value) }

    fun bindColor(newState: State<Color>) = apply {
        colorState.rebind(newState)
    }

    fun bindAlpha(newState: State<Float>) = apply {
        alphaState.rebind(newState)
    }

    override fun getColorImpl(component: UIComponent): Color {
        val (r, g, b) = colorState.get()
        val a = ((constrainTo ?: component.parent).getColor().alpha * alphaState.get()).toInt()
        return Color(r, g, b, a)
    }

    // Color constraints will only ever have parent dependencies, so there is no possibility
    // of an invalid constraint here
    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) { }
}

/**
 * Changes this component's color every frame, using a sin wave to create
 * a chroma effect.
 */
class RainbowColorConstraint(val alpha: Int = 255, val speed: Float = 50f) : ColorConstraint {
    override var cachedValue = Color.WHITE
    override var recalculate = true
    override var constrainTo: UIComponent? = null

    private val randomOffset = Random.nextInt(500).toFloat()

    override fun getColorImpl(component: UIComponent): Color {
        val currentStep = randomOffset + (Window.ofOrNull(component)?.animationTimeMs ?: 0) * (244 / 1000f)

        val red = ((sin((currentStep / speed).toDouble()) + 0.75) * 170).toInt()
        val green = ((sin(currentStep / speed + 2 * Math.PI / 3) + 0.75) * 170).toInt()
        val blue = ((sin(currentStep / speed + 4 * Math.PI / 3) + 0.75) * 170).toInt()

        return Color(
            red.coerceIn(0, 255),
            green.coerceIn(0, 255),
            blue.coerceIn(0, 255),
            alpha.coerceIn(0, 255)
        )
    }

    override fun to(component: UIComponent) = apply {
        throw java.lang.UnsupportedOperationException("Constraint.to(UIComponent) is not available in this context!")
    }

    // Color constraints will only ever have parent dependencies, so there is no possibility
    // of an invalid constraint here
    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) { }
}
