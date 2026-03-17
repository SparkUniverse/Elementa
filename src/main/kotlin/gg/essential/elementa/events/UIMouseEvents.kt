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
    val scrollY: Double,
    val target: UIComponent,
    val currentTarget: UIComponent,
    val scrollX: Double, // only on Minecraft 1.20.2+ and ElementaVersion.V11+
) : UIEvent() {
    constructor(scrollX: Double, scrollY: Double, target: UIComponent, currentTarget: UIComponent)
            : this(scrollY, target, currentTarget, scrollX)

    // Added to ensure backwards binary compatibility
    constructor(delta: Double, target: UIComponent, currentTarget: UIComponent) : this(delta, target, currentTarget, 0.0)

    // Added to ensure backwards binary compatibility
    fun copy(
        delta: Double = this.scrollY,
        target: UIComponent = this.target,
        currentTarget: UIComponent = this.currentTarget,
    ) = copy(scrollY = delta, target = target, currentTarget = currentTarget, scrollX = scrollX)

    // Added to ensure backwards binary compatibility
    val delta: Double
        get() = scrollY
}
