package gg.essential.elementa.example

import gg.essential.universal.UScreen
import gg.essential.universal.standalone.runUniversalCraft

fun main() = runUniversalCraft("Elementa Example", 854, 480) { window ->
    UScreen.displayScreen(ExamplesGui())
    window.renderScreenUntilClosed()
}
