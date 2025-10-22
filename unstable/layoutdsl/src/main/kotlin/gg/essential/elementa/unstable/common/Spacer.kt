package gg.essential.elementa.unstable.common

import gg.essential.elementa.constraints.HeightConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.constraints.WidthConstraint
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels

/**
 * A simple UIContainer where you can specify [width], [height], or both.
 *
 * If only [width] is specified, X-axis will be constrained to [SiblingConstraint].
 *
 * If only [height] is specified, Y-axis will be constrained to [SiblingConstraint].
 */
class Spacer(width: WidthConstraint = 0.pixels, height: HeightConstraint = 0.pixels) : HollowUIContainer() {
    constructor(width: Float, _desc: Int = 0) : this(width = width.pixels) { setX(SiblingConstraint()) }
    constructor(height: Float, _desc: Short = 0) : this(height = height.pixels) { setY(SiblingConstraint()) }
    constructor(width: Float, height: Float) : this(width = width.pixels, height = height.pixels)

    init {
        constrain {
            this.width = width
            this.height = height
        }
    }
}
