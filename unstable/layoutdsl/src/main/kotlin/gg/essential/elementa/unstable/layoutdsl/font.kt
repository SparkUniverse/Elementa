package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.UIComponent
import gg.essential.elementa.font.FontProvider

fun Modifier.font(fontProvider: FontProvider) = this then FontModifier { fontProvider }

class FontModifier(private val provider: () -> FontProvider) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val oldFontProvider = component.constraints.fontProvider
        component.constraints.fontProvider = provider()
        return {
            component.constraints.fontProvider = oldFontProvider
        }
    }
}
