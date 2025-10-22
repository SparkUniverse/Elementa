package gg.essential.elementa.components

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.UIComponent.Flags

/**
 * Called once at the start of every frame to update any animations and miscellaneous state.
 *
 * @param dt Time (in seconds) since last frame
 * @param dtMs Time (in milliseconds) since last frame
 *
 * This differs from `(dt / 1000).toInt()` in that it will account for the fractional milliseconds which would
 * otherwise be lost to rounding. E.g. if there are three frames each lasting 16.4ms,
 * `(dt / 1000).toInt()` would be 16 each time, but `dtMs` will be 16 on the first two frames and 17 on the third.
 */
typealias UpdateFunc = (dt: Float, dtMs: Int) -> Unit

internal val NOP_UPDATE_FUNC: UpdateFunc = { _, _ -> }

internal class NopUpdateFuncList(override val size: Int) : AbstractList<UpdateFunc>() {
    override fun get(index: Int): UpdateFunc = NOP_UPDATE_FUNC
}

/**
 * Internal utility for components which used to use `animationFrame`, and therefore still have to do that for backwards
 * compatibility until v8 is enabled, but which then use UpdateFunc once v8 is enabled.
 */
internal fun UIComponent.addUpdateFuncOnV8ReplacingAnimationFrame(func: UpdateFunc) {
    // we override animationFrame only for backwards compatibility and use this UpdateFunc on newer versions
    ownFlags -= Flags.RequiresAnimationFrame

    addUpdateFunc(object : UpdateFunc {
        override fun invoke(dt: Float, dtMs: Int) {
            if (Window.of(this@addUpdateFuncOnV8ReplacingAnimationFrame).version < ElementaVersion.v8) {
                // handled by animationFrame
                removeUpdateFunc(this)
                return
            }
            func(dt, dtMs)
        }
    })
}
