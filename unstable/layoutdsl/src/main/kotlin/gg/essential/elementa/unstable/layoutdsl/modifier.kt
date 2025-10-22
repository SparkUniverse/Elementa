package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.UIComponent

interface Modifier {
    /**
     * Applies this modifier to the given component, and returns a function which can be called to undo the applied changes.
     */
    fun applyToComponent(component: UIComponent): () -> Unit

    infix fun then(other: Modifier) = if (other === Modifier) this else CombinedModifier(this, other)

    companion object : Modifier {
        override fun applyToComponent(component: UIComponent): () -> Unit = {}

        override infix fun then(other: Modifier) = other
    }
}

private class CombinedModifier(
    private val first: Modifier,
    private val second: Modifier
) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val undoFirst = first.applyToComponent(component)
        val undoSecond = second.applyToComponent(component)
        return {
            undoSecond()
            undoFirst()
        }
    }
}
