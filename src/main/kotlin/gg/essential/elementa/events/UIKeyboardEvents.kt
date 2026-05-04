package gg.essential.elementa.events

import gg.essential.universal.UKeyboard

/**
 * Represents a key typing event whose propagation can be stopped from continuing on to Minecraft
 *
 * Requires ElementaVersion.V12
 */
class UIKeyEvent(
    val key: Int,
    val scanCode: Int,
    val modifiers: UKeyboard.Modifiers,
) {
    val isCopy get() = key == UKeyboard.KEY_C && modifiers.isOnlyPlatformModifierActive()
    val isCut get() = key == UKeyboard.KEY_X && modifiers.isOnlyPlatformModifierActive()
    val isPaste get() = key == UKeyboard.KEY_V && modifiers.isOnlyPlatformModifierActive()
}


/**
 * Represents a character typing event whose propagation can be stopped from continuing on to Minecraft
 *
 * Requires ElementaVersion.V12
 */
class UICharEvent(
    val codepoint: Int,
) {
    val string = String(Character.toChars(codepoint))
}