package gg.essential.elementa.unstable.common

import gg.essential.elementa.components.UIContainer

/**
 * A UIContainer that does not return true for [isPointInside] unless
 * any of the child are hovered
 */
open class HollowUIContainer : UIContainer() {

    override fun isPointInside(x: Float, y: Float): Boolean {
        return children.any {
            it.isPointInside(x, y)
        }
    }
}