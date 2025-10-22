package gg.essential.elementa.constraints

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.MappedState
import gg.essential.elementa.state.State

class ScaleConstraint(val constraint: SuperConstraint<Float>, value: State<Float>) : MasterConstraint {
    constructor(constraint: SuperConstraint<Float>, value: Float) : this(constraint, BasicState(value))
    override var cachedValue = 0f
    override var recalculate = true
    override var constrainTo: UIComponent? = null

    private val valueState: MappedState<Float, Float> = value.map { it }
    var value: Float
        get() = valueState.get()
        set(value) = valueState.set(value)

    fun bindValue(newState: State<Float>) = apply {
        valueState.rebind(newState)
    }

    @Deprecated("See [ElementaVersion.V8].")
    @Suppress("DEPRECATION")
    override fun animationFrame() {
        super.animationFrame()
        constraint.animationFrame()
    }

    override fun getXPositionImpl(component: UIComponent): Float {
        return (constraint as XConstraint).getXPosition(component) * valueState.get()
    }

    override fun getYPositionImpl(component: UIComponent): Float {
        return (constraint as YConstraint).getYPosition(component) * valueState.get()
    }

    override fun getWidthImpl(component: UIComponent): Float {
        return (constraint as WidthConstraint).getWidth(component) * valueState.get()
    }

    override fun getHeightImpl(component: UIComponent): Float {
        return (constraint as HeightConstraint).getHeight(component) * valueState.get()
    }

    override fun getRadiusImpl(component: UIComponent): Float {
        return (constraint as RadiusConstraint).getRadius(component) * valueState.get()
    }

    override fun to(component: UIComponent): SuperConstraint<Float> {
        throw UnsupportedOperationException("Constraint.to(UIComponent) is not available in this context, please apply this to the components beforehand.")
    }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {
        constraint.visit(visitor, type, setNewConstraint = false)
    }
}
