package gg.essential.elementa.unstable.state.v2.color

import gg.essential.elementa.constraints.ColorConstraint
import gg.essential.elementa.dsl.basicColorConstraint
import gg.essential.elementa.unstable.state.v2.State
import java.awt.Color

fun State<Color>.toConstraint() = basicColorConstraint { get() }

val State<Color>.constraint: ColorConstraint
    get() = toConstraint()
