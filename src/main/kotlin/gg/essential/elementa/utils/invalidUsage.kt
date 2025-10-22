package gg.essential.elementa.utils

import gg.essential.universal.UMinecraft

internal enum class InvalidUsageBehavior {
    IGNORE,
    WARN,
    THROW,
}
private val invalidUsageBehaviorProp = when (System.getProperty("elementa.invalid_usage", "")) {
    "throw" -> InvalidUsageBehavior.THROW
    "warn" -> InvalidUsageBehavior.WARN
    "ignore" -> InvalidUsageBehavior.IGNORE
    else -> null
}
internal val invalidUsageBehavior: InvalidUsageBehavior
    get() = invalidUsageBehaviorProp ?: if (devPropSet) InvalidUsageBehavior.THROW else InvalidUsageBehavior.WARN

internal fun handleInvalidUsage(message: String) {
    when (invalidUsageBehavior) {
        InvalidUsageBehavior.IGNORE -> return
        InvalidUsageBehavior.WARN -> {
            IllegalStateException(message).printStackTrace()
        }
        InvalidUsageBehavior.THROW -> {
            throw IllegalStateException(message).also {
                it.printStackTrace() // print anyway so they cannot accidentally silence it
            }
        }
    }
}

internal fun requireState(state: Boolean, message: String) {
    if (!state) {
        handleInvalidUsage(message)
    }
}

/** Ensure a method can only be called from the main thread. Lack of this check does **not** imply thread-safety.  */
internal fun requireMainThread(message: String = "This method is not thread-safe and must be called from the main thread. " +
        "Consider the thread-safety of the calling code and use Window.enqueueRenderOperation if applicable.") {
    requireState(UMinecraft.isCallingFromMinecraftThread(), message)
}
