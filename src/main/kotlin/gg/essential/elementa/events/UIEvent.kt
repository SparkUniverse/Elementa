package gg.essential.elementa.events

abstract class UIEvent {
    var propagationStopped = false
    var propagationStoppedImmediately = false

    /**
     * Stops this event from continuing to bubble to parent components. If there are
     * multiple listeners on the current component, those "sibling" listeners will continue to fire.
     */
    fun stopPropagation() {
        propagationStopped = true
    }

    /**
     * Stops this event from continuing to bubble to parent components and stops it from being passed
     * to the remaining listeners on the current component, meaning the event is completely stopped.
     */
    fun stopImmediatePropagation() {
        propagationStoppedImmediately = true
    }
}