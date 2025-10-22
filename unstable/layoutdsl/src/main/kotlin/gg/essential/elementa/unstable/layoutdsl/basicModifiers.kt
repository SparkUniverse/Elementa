package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.*

infix fun Modifier.then(other: UIComponent.() -> () -> Unit) = this then BasicModifier(other)

private class BasicModifier(private val setup: UIComponent.() -> () -> Unit) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        return component.setup()
    }
}

class BasicXModifier(private val constraint: () -> XConstraint) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val oldX = component.constraints.x
        component.setX(constraint())
        return {
            component.setX(oldX)
        }
    }
}

class BasicYModifier(private val constraint: () -> YConstraint) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val oldY = component.constraints.y
        component.setY(constraint())
        return {
            component.setY(oldY)
        }
    }
}

class BasicWidthModifier(private val constraint: () ->  WidthConstraint) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val oldWidth = component.constraints.width
        component.setWidth(constraint())
        return {
            component.setWidth(oldWidth)
        }
    }
}

class BasicHeightModifier(private val constraint: () -> HeightConstraint) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val oldHeight = component.constraints.height
        component.setHeight(constraint())
        return {
            component.setHeight(oldHeight)
        }
    }
}

class BasicColorModifier(private val constraint: () -> ColorConstraint) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val oldColor = component.constraints.color
        component.setColor(constraint())
        return {
            component.setColor(oldColor)
        }
    }
}