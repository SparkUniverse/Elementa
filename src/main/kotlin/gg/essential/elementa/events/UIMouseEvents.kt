package gg.essential.elementa.events

import gg.essential.elementa.UIComponent

data class UIClickEvent(
    val absoluteX: Float,
    val absoluteY: Float,
    val mouseButton: Int,
    val target: UIComponent,
    val currentTarget: UIComponent,
    val clickCount: Int
) : UIEvent() {
    val relativeX = absoluteX - currentTarget.getLeft()
    val relativeY = absoluteY - currentTarget.getTop()
}

data class UIScrollEvent(
    val deltaVertical: Double,
    val target: UIComponent,
    val currentTarget: UIComponent,
    val deltaHorizontal: Double,
) : UIEvent() {
    // Added to ensure backwards binary compatibility
    constructor(delta: Double, target: UIComponent, currentTarget: UIComponent) : this(delta, target, currentTarget, 0.0)

    // Added to ensure backwards binary compatibility
    fun copy(
        deltaVertical: Double = this.deltaVertical,
        target: UIComponent = this.target,
        currentTarget: UIComponent = this.currentTarget,
    ) = copy(deltaVertical = deltaVertical, target = target, currentTarget = currentTarget, deltaHorizontal = deltaHorizontal)

    // Added to ensure backwards binary compatibility
    val delta: Double
        get() = deltaVertical
}
