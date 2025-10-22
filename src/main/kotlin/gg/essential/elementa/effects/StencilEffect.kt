package gg.essential.elementa.effects

import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import org.lwjgl.opengl.GL11.*

/**
 * Allows for arbitrary scissoring of any shaped component.
 *
 * In order to use, you must call [enableStencil] in mod initialization.
 */
@Deprecated("Does not work on 1.14+")
class StencilEffect : Effect() {
    override fun beforeDraw(matrixStack: UMatrixStack) {
        glEnable(GL_STENCIL_TEST)
        // commented to make component still draw
        //glColorMask ( false, false, false, false)
        glStencilFunc(GL_ALWAYS, 2, 0xffffffff.toInt())
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE)
    }

    override fun beforeChildrenDraw(matrixStack: UMatrixStack) {
        //glColorMask (true, true, true, true)
        glStencilFunc(GL_EQUAL, 2, 0xffffffff.toInt())
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)
    }

    override fun afterDraw(matrixStack: UMatrixStack) {
        glDisable(GL_STENCIL_TEST)
    }

    companion object {
        /**
         * Must be called in mod initialization to use [StencilEffect]
         */
        @JvmStatic fun enableStencil() {
            @Suppress("DEPRECATION")
            UGraphics.enableStencil()
        }
    }
}
