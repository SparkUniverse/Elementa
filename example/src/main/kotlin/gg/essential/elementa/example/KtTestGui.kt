package gg.essential.elementa.example

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.WindowScreen
import gg.essential.elementa.components.*
import gg.essential.elementa.components.input.UIMultilineTextInput
import gg.essential.elementa.components.inspector.Inspector
import gg.essential.elementa.constraints.*
import gg.essential.elementa.constraints.animation.Animations
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.elementa.font.DefaultFonts
import gg.essential.elementa.font.ElementaFonts
import gg.essential.elementa.utils.withAlpha
import java.awt.Color

class KtTestGui : WindowScreen(ElementaVersion.V2) {
    private val myTextBox = UIBlock(Color(0, 0, 0, 255))

    init {
        val container = UIContainer().constrain {
            x = RelativeConstraint(.25f)
            y = RelativeConstraint(.25f)
            width = RelativeConstraint(.5f)
            height = RelativeConstraint(.5f)
        } childOf window
        for (i in 50..500) {
            if (i % 15 != 0) continue
            UIBlock(Color.RED).constrain {
                x = CramSiblingConstraint(10 / 3f)
                y = CramSiblingConstraint(10f / 3f)
                width = 5.pixels()
                height = 5.pixels()
            } childOf container effect OutlineEffect(Color.BLUE, 1f);
        }
    }
}