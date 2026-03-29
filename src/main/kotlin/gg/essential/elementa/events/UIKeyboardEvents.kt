package gg.essential.elementa.events

import gg.essential.universal.UKeyboard

/**
 * Represents a key typing event whose propagation can be stopped from continuing on to Minecraft
 */
class UIKeyEvent(
    val keyCode: Int,
    val scanCode: Int,
    val modifiers: UKeyboard.Modifiers,
) : UIEvent()


/**
 * Represents a character typing event whose propagation can be stopped from continuing on to Minecraft
 */
class UICharEvent(
    val codepoint: Int,
) : UIEvent() {
    val char = codepoint.toChar()
}