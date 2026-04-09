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

    // These checks read the current keyboard state to account for OS differences where we cannot fully rely on `modifiers`.
    // E.G. macOS using CMD + C for copy
    val isCopy get() = UKeyboard.isKeyComboCtrlC(key)
    @Suppress("unused")
    val isCut get() = UKeyboard.isKeyComboCtrlX(key)
    @Suppress("unused")
    val isPaste get() = UKeyboard.isKeyComboCtrlV(key)
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