package gg.essential.elementa.effects

import gg.essential.elementa.UIComponent
import gg.essential.elementa.UIComponent.Flags
import gg.essential.elementa.components.UpdateFunc
import gg.essential.universal.UMatrixStack

/**
 * Basic interface all effects need to follow.
 *
 * This is where you can affect any drawing done.
 */
abstract class Effect {
    internal var flags: Flags = Flags.initialFor(javaClass)
        set(newValue) {
            val oldValue = field
            if (oldValue == newValue) return
            field = newValue
            updateFuncParent?.let { parent ->
                if (oldValue in newValue) { // merely additions?
                    parent.effectFlags += newValue
                } else {
                    parent.recomputeEffectFlags()
                }
            }
        }

    protected lateinit var boundComponent: UIComponent

    fun bindComponent(component: UIComponent) {
        if (::boundComponent.isInitialized && boundComponent != component)
            throw IllegalStateException("Attempt to bind component of a ${this::class.simpleName} " +
                "which already has a bound component")
        boundComponent = component
    }

    internal var updateFuncParent: UIComponent? = null
    internal var updateFuncs: MutableList<UpdateFunc>? = null // only allocated if used

    protected fun addUpdateFunc(func: UpdateFunc) {
        val updateFuncs = updateFuncs ?: mutableListOf<UpdateFunc>().also { updateFuncs = it }
        updateFuncs.add(func)

        updateFuncParent?.addUpdateFunc(this, updateFuncs.lastIndex, func)
    }

    protected fun removeUpdateFunc(func: UpdateFunc) {
        val updateFuncs = updateFuncs ?: return
        val index = updateFuncs.indexOf(func)
        if (index == -1) return
        updateFuncs.removeAt(index)

        updateFuncParent?.removeUpdateFunc(this, index)
    }

    /**
     * Called once inside of the component's afterInitialization function
     */
    open fun setup() {}

    /**
     * Called in the component's animationFrame function
     */
    @Deprecated("See [ElementaVersion.V8].")
    open fun animationFrame() {}

    /**
     * Set up all drawing, turn on shaders, etc.
     */
    open fun beforeDraw(matrixStack: UMatrixStack) {}

    /**
     * Called after this component draws but before it's children are drawn.
     */
    open fun beforeChildrenDraw(matrixStack: UMatrixStack) {}

    /**
     * Clean up all of this feature's GL states, etc.
     */
    open fun afterDraw(matrixStack: UMatrixStack) {}


    @Deprecated(
        UMatrixStack.Compat.DEPRECATED,
        ReplaceWith("beforeDraw(matrixStack)")
    )
    open fun beforeDraw() = beforeDraw(UMatrixStack.Compat.get())

    @Deprecated(
        UMatrixStack.Compat.DEPRECATED,
        ReplaceWith("beforeChildrenDraw(matrixStack)")
    )
    open fun beforeChildrenDraw() = beforeChildrenDraw(UMatrixStack.Compat.get())

    @Deprecated(
        UMatrixStack.Compat.DEPRECATED,
        ReplaceWith("afterDraw(matrixStack)")
    )
    open fun afterDraw() = afterDraw(UMatrixStack.Compat.get())

    @Suppress("DEPRECATION")
    fun beforeDrawCompat(matrixStack: UMatrixStack) = UMatrixStack.Compat.runLegacyMethod(matrixStack) { beforeDraw() }
    @Suppress("DEPRECATION")
    fun beforeChildrenDrawCompat(matrixStack: UMatrixStack) = UMatrixStack.Compat.runLegacyMethod(matrixStack) { beforeChildrenDraw() }
    @Suppress("DEPRECATION")
    fun afterDrawCompat(matrixStack: UMatrixStack) = UMatrixStack.Compat.runLegacyMethod(matrixStack) { afterDraw() }
}
