package gg.essential.elementa.unstable.util

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.Window
import gg.essential.elementa.effects.Effect
import gg.essential.elementa.unstable.state.v2.MutableState
import gg.essential.elementa.unstable.state.v2.State
import gg.essential.elementa.unstable.state.v2.combinators.or
import gg.essential.elementa.unstable.state.v2.mutableStateOf
import gg.essential.elementa.unstable.state.v2.stateOf
import gg.essential.elementa.unstable.state.v2.toV2
import gg.essential.elementa.unstable.layoutdsl.Modifier
import gg.essential.elementa.unstable.layoutdsl.tag
import gg.essential.elementa.unstable.layoutdsl.then
import gg.essential.universal.UKeyboard

data class Focusable(val disabled: State<Boolean>) : Tag

/** Marks this component as [Focusable], meaning that it can be navigated to via the keyboard. */
fun Modifier.focusable(disabled: State<Boolean> = stateOf(false)): Modifier {
    return tag(Focusable(disabled))
        .then {
            val keyListener = setupKeyboardNavigation()
            return@then { keyTypedListeners.remove(keyListener) }
        }
        .then { makeFocusOrHoverScope(); { throw NotImplementedError() } }
}

/** Creates a hover scope for this component based on whether it is hovered by the mouse OR it has the Window's focus. */
private fun UIComponent.makeFocusOrHoverScope() {
    val focused = focusedState()
    val hovered = hoveredState().toV2()

    makeHoverScope(focused or hovered)
}

/** Returns a state indicating whether this component has the [Window]'s focus or not. */
fun UIComponent.focusedState(): State<Boolean> {
    class CachedState(val state: State<Boolean>) : Tag
    getTag<CachedState>()?.let { return it.state }

    val state = mutableStateOf(Window.ofOrNull(this)?.focusedComponent == this)

    onFocus { state.set(true) }
    onFocusLost { state.set(false) }
    addTag(CachedState(state))

    return state
}
/**
 * Reacts to keyboard-navigation related events if the component is focused.
 * @return The key listener, mainly intended for removing it at a future point in time.
 */
private fun UIComponent.setupKeyboardNavigation(): UIComponent.(Char, Int) -> Unit {
    val keyListener: UIComponent.(Char, Int) -> Unit = keyListener@{ _, keyCode ->
        if (!hasFocus()) {
            return@keyListener
        }

        when (keyCode) {
            UKeyboard.KEY_ENTER -> simulateLeftClick()
            UKeyboard.KEY_TAB -> passFocusToNextComponent(backwards = UKeyboard.isShiftKeyDown())
        }
    }

    onKeyType(keyListener)

    return keyListener
}

/** Intended for use by keyboard navigation implementations in order to fake a left-click event on a component. */
fun UIComponent.simulateLeftClick() {
    // We need to make sure that we're still in the window, as another key listener which ran before us
    // may have already reacted to the event. This function isn't exactly a key-listener, but is most
    // likely being called from one.
    if (!isInComponentTree()) {
        return
    }

    mouseClick(
        getLeft().toDouble() + (getWidth() / 2),
        getTop().toDouble() + (getHeight() / 2),
        0,
    )
}

private fun UIComponent.passFocusToNextComponent(backwards: Boolean = false) {
    val focusable = Window.of(this).findChildrenByTag<Focusable>(recursive = true) {
        this == this@passFocusToNextComponent || !it.disabled.getUntracked()
    }

    val currentIndex = focusable.indexOf(this)
    if (currentIndex == -1) {
        return
    }

    val direction = if (backwards) -1 else 1
    val nextComponent = focusable[(currentIndex + direction).mod(focusable.size)]
    nextComponent.grabWindowFocus()
}