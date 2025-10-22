package gg.essential.elementa.components

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.SuperConstraint
import gg.essential.elementa.constraints.resolution.ConstraintResolutionGui
import gg.essential.elementa.constraints.resolution.ConstraintResolver
import gg.essential.elementa.constraints.resolution.ConstraintResolverV2
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.elementa.font.FontRenderer
import gg.essential.elementa.utils.elementaDev
import gg.essential.elementa.utils.requireMainThread
import gg.essential.universal.*
import org.lwjgl.opengl.GL11
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * "Root" component. All components MUST have a Window in their hierarchy in order to do any rendering
 * or animating.
 */
class Window @JvmOverloads constructor(
    internal val version: ElementaVersion,
    @Deprecated("See [ElementaVersion.V8].")
    val animationFPS: Int = 244
) : UIComponent() {
    private var legacyAnimationFrameTime = -1L
    private var lastDrawTime: Long = -1
    var animationTimeNs: Long = 0
        private set
    val animationTimeMs: Long
        get() = animationTimeNs / 1_000_000

    internal var allUpdateFuncs: MutableList<UpdateFunc> = mutableListOf()
    internal var nextUpdateFuncIndex = 0

    internal val cachedConstraints: MutableList<SuperConstraint<*>> = mutableListOf()

    private var currentMouseButton = -1

    private var legacyFloatingComponents = mutableListOf<UIComponent>()

    var hoveredFloatingComponent: UIComponent? = null
    var focusedComponent: UIComponent? = null
        private set
    private var componentRequestingFocus: UIComponent? = null

    var hasErrored = false
        private set

    internal var clickInterceptor: ((mouseX: Double, mouseY: Double, button: Int) -> Boolean)? = null

    @Deprecated("Add ElementaVersion as the first argument to opt-in to improved behavior.")
    @JvmOverloads
    constructor(animationFPS: Int = 244) : this(ElementaVersion.v0, animationFPS)

    init {
        super.parent = this
        cachedWindow = this
    }

    override fun afterInitialization() {
        super.afterInitialization()

        enqueueRenderOperation {
            FontRenderer.initShaders()
            UICircle.initShaders()
            UIRoundedRectangle.initShaders()
        }
    }

    override fun draw(matrixStack: UMatrixStack) =
        version.enableFor { doDraw(matrixStack) }

    private fun doDraw(matrixStack: UMatrixStack) {
        if (hasErrored)
            return

        requireMainThread()

        val startTime = System.nanoTime()

        val it = renderOperations.iterator()
        while (it.hasNext() && System.nanoTime() - startTime < TimeUnit.MILLISECONDS.toNanos(5)) {
            it.next()()
            it.remove()
        }

        if (legacyAnimationFrameTime == -1L)
            legacyAnimationFrameTime = System.currentTimeMillis()
        if (lastDrawTime == -1L)
            lastDrawTime = System.currentTimeMillis()

        val now = System.currentTimeMillis()
        val dtMs = now - lastDrawTime
        lastDrawTime = now

        animationTimeNs += dtMs * 1_000_000

        try {

            if (version >= ElementaVersion.v8) {
                dispatchMouseDragging()

                if (componentRequestingFocus != null) {
                    dealWithFocusRequests()
                }

                invalidateCachedConstraints()
            }

            assertUpdateFuncInvariants()
            nextUpdateFuncIndex = 0
            while (true) {
                val func = allUpdateFuncs.getOrNull(nextUpdateFuncIndex) ?: break
                nextUpdateFuncIndex++
                func(dtMs / 1000f, dtMs.toInt())
            }

            if (version >= ElementaVersion.v8) {
                invalidateCachedConstraints()
            }

            //If this Window is more than 5 seconds behind, reset it be only 5 seconds.
            //This will drop missed frames but avoid the game freezing as the Window tries
            //to catch after a period of inactivity
            if (System.currentTimeMillis() - this.legacyAnimationFrameTime > TimeUnit.SECONDS.toMillis(5))
                this.legacyAnimationFrameTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(5)

            @Suppress("DEPRECATION")
            val animationFPS = animationFPS
            val target = System.currentTimeMillis() + 1000 / animationFPS
            val animationFrames = (target - this.legacyAnimationFrameTime).toInt() * animationFPS / 1000
            // If the window is sufficiently complex, it's possible for the average `animationFrame` to take so long
            // we'll start falling behind with no way to ever catch up. And the amount of frames we're behind will
            // quickly grow to the point where we'll be spending five seconds in `animationFrame` before we can get a
            // real frame on the screen.
            // To prevent that, we limit the `animationFrame` calls we make per real frame such that we'll still be able
            // to render approximately 30 real frames per second at the cost of animations slowing down.
            repeat(animationFrames.coerceAtMost((animationFPS / 30).coerceAtLeast(1))) {
                @Suppress("DEPRECATION")
                animationFrame()
                this.legacyAnimationFrameTime += 1000 / animationFPS

                if (version >= ElementaVersion.v8) {
                    invalidateCachedConstraints()
                }
            }

            hoveredFloatingComponent = null
            val (mouseX, mouseY) = getMousePosition()
            for (component in allFloatingComponentsInReverseOrder()) {
                if (component.isPointInside(mouseX, mouseY)) {
                    hoveredFloatingComponent = component
                    break
                }
            }

            mouseMove(this)
            beforeDraw(matrixStack)
            super.draw(matrixStack)
        } catch (e: Throwable) {
            hasErrored = true

            val guiName = UMinecraft.currentScreenObj?.javaClass?.simpleName ?: "<unknown>"
            when (e) {
                is StackOverflowError -> {
                    println("Elementa: Cyclic constraint structure detected!")
                    println("If you are a developer, set the environment variable \"elementa.dev=true\" to assist in debugging the issue.")
                }
                else -> {
                    println("Elementa: encountered an error while drawing a GUI")
                }
            }
            println("Gui name: $guiName")
            e.printStackTrace()

            // We may have thrown in the middle of a ScissorEffect, in which case we
            // need to disable the scissor if we don't want half the user's screen gone
            ScissorEffect.currentScissorState = null
            GL11.glDisable(GL11.GL_SCISSOR_TEST)

            UMinecraft.currentScreenObj = when {
                e is StackOverflowError && elementaDev -> {
                    val cyclicNodes = when (System.getProperty("elementa.dev.cycle_resolver", "2")) {
                        "2" -> ConstraintResolverV2(this).getCyclicNodes()
                        "1" -> ConstraintResolver(this).getCyclicNodes()
                        else -> {
                            println("Invalid value for \"elementa.dev.cycle_resolver\", falling back to V2 solver.")
                            ConstraintResolverV2(this).getCyclicNodes()
                        }
                    }
                    ConstraintResolutionGui(guiName, this, cyclicNodes)
                }
                else -> {
                    UChat.chat("§cElementa encountered an error while drawing a GUI. Check your logs for more information.")
                    null
                }
            }
        }
    }

    internal fun drawEmbedded(matrixStack: UMatrixStack) {
        super.draw(matrixStack)
    }

    @Deprecated(UMatrixStack.Compat.DEPRECATED, ReplaceWith("drawFloatingComponents(matrixStack)"))
    fun drawFloatingComponents() = drawFloatingComponents(UMatrixStack())

    fun drawFloatingComponents(matrixStack: UMatrixStack) {
        requireMainThread()

        val it = legacyFloatingComponents.iterator()
        while (it.hasNext()) {
            val component = it.next()
            if (ofOrNull(component) == null) {
                it.remove()
                continue
            }
            component.drawCompat(matrixStack)
        }
        for (component in floatingComponents ?: emptyList()) {
            component.drawCompat(matrixStack)
        }
    }

    override fun mouseScroll(delta: Double) {
        if (hasErrored && version >= ElementaVersion.v7) {
            return
        }

        requireMainThread()

        val (mouseX, mouseY) = getMousePosition()
        for (floatingComponent in allFloatingComponentsInReverseOrder()) {
            if (floatingComponent.isPointInside(mouseX, mouseY)) {
                floatingComponent.mouseScroll(delta)
                return
            }
        }

        super.mouseScroll(delta)
    }

    override fun mouseClick(mouseX: Double, mouseY: Double, button: Int) {
        if (hasErrored && version >= ElementaVersion.v7) {
            return
        }

        requireMainThread()

        //  Override mouse positions to be in the center of the pixel on Elementa versions
        //  2 and over. See [ElementaVersion.V2] for more info.
        val (adjustedX, adjustedY) = pixelCoordinatesToPixelCenter(mouseX, mouseY)

        prevDraggedMouseX = adjustedX.toFloat()
        prevDraggedMouseY = adjustedY.toFloat()

        doMouseClick(adjustedX, adjustedY, button)
    }

    private fun doMouseClick(mouseX: Double, mouseY: Double, button: Int) {
        currentMouseButton = button

        clickInterceptor?.let {
            if (it(mouseX, mouseY, button)) {
                return
            }
        }

        for (floatingComponent in allFloatingComponentsInReverseOrder()) {
            if (floatingComponent.isPointInside(mouseX.toFloat(), mouseY.toFloat())) {
                floatingComponent.mouseClick(mouseX, mouseY, button)
                dealWithFocusRequests()
                return
            }
        }

        super.mouseClick(mouseX, mouseY, button)
        dealWithFocusRequests()
    }

    private fun dealWithFocusRequests() {
        if (componentRequestingFocus == null) {
            unfocus()
        } else if (componentRequestingFocus != focusedComponent) {
            if (focusedComponent != null)
                focusedComponent?.loseFocus()

            focusedComponent = componentRequestingFocus
            focusedComponent?.focus()
        }

        componentRequestingFocus = null
    }

    override fun mouseRelease() {
        if (hasErrored && version >= ElementaVersion.v7) {
            return
        }

        requireMainThread()

        super.mouseRelease()

        prevDraggedMouseX = null
        prevDraggedMouseY = null

        currentMouseButton = -1
    }

    override fun keyType(typedChar: Char, keyCode: Int) {
        if (hasErrored && version >= ElementaVersion.v7) {
            return
        }

        requireMainThread()

        // If the typed character is in a PUA (https://en.wikipedia.org/wiki/Private_Use_Areas), we don't want to
        // pass down the character, only the keycode.
        val character = if (typedChar in CharCategory.PRIVATE_USE) Char.MIN_VALUE else typedChar

        if (focusedComponent != null) {
            focusedComponent?.keyType(character, keyCode)
        } else {
            super.keyType(character, keyCode)
        }
    }

    internal var prevDraggedMouseX: Float? = null
    internal var prevDraggedMouseY: Float? = null

    private fun dispatchMouseDragging() {
        if (currentMouseButton != -1) {
            val (mouseX, mouseY) = getMousePosition()
            if (version >= ElementaVersion.v2) {
                if (prevDraggedMouseX != mouseX || prevDraggedMouseY != mouseY) {
                    prevDraggedMouseX = mouseX
                    prevDraggedMouseY = mouseY
                    dragMouse(mouseX, mouseY, currentMouseButton)
                }
            } else {
                if (prevDraggedMouseX != mouseX.toInt().toFloat() || prevDraggedMouseY != mouseY.toInt().toFloat()) {
                    prevDraggedMouseX = mouseX.toInt().toFloat()
                    prevDraggedMouseY = mouseY.toInt().toFloat()
                    @Suppress("DEPRECATION")
                    dragMouse(mouseX.toInt(), mouseY.toInt(), currentMouseButton)
                }
            }
        }
    }

    @Deprecated("See [ElementaVersion.V8].")
    override fun animationFrame() {
        if (version >= ElementaVersion.v8) {
            // In v8, dragging and focus is handled before the UpdateFunc calls, we only need to call super to support
            // components or effects which may still use animationFrame.
            if (Flags.RequiresAnimationFrame in combinedFlags) {
                @Suppress("DEPRECATION")
                super.animationFrame()
            }
            return
        }

        dispatchMouseDragging()

        if (componentRequestingFocus != null && componentRequestingFocus != focusedComponent) {
            if (focusedComponent != null)
                focusedComponent?.loseFocus()

            focusedComponent = componentRequestingFocus
            focusedComponent?.focus()
        }
        componentRequestingFocus = null

        @Suppress("DEPRECATION")
        super.animationFrame()
    }

    // Note: Constraints are cached this way only with ElementaVersion.V8 and above,
    //       prior version require calling `animationFrame` which may have additional side-effects.
    fun invalidateCachedConstraints() {
        for (constraint in cachedConstraints) {
            constraint.recalculate = true
        }
        cachedConstraints.clear()
    }

    override fun getLeft(): Float {
        return 0f
    }

    override fun getTop(): Float {
        return 0f
    }

    override fun getWidth(): Float {
        return UResolution.scaledWidth.toFloat()
    }

    override fun getHeight(): Float {
        return UResolution.scaledHeight.toFloat()
    }

    override fun getRight() = getWidth()
    override fun getBottom() = getHeight()

    fun isAreaVisible(left: Double, top: Double, right: Double, bottom: Double): Boolean {
        if (right < getLeft() ||
            left > getRight() ||
            bottom < getTop() ||
            top > getBottom()
        ) return false

        val currentScissor = ScissorEffect.currentScissorState ?: return true
        val sf = UResolution.scaleFactor

        val realX = currentScissor.x / sf
        val realWidth = currentScissor.width / sf

        val bottomY = ((UResolution.scaledHeight * sf) - currentScissor.y) / sf
        val realHeight = currentScissor.height / sf

        return right > realX &&
                left < realX + realWidth &&
                bottom >= bottomY - realHeight &&
                top <= bottomY
    }

    /*
     * Floating API
     */

    private fun allFloatingComponentsInReverseOrder(): Sequence<UIComponent> =
        (floatingComponents ?: emptyList()).asReversed().asSequence() +
                // Note: needs to be copied to guard against CME and for backwards compatibility
                legacyFloatingComponents.reversed()

    @Deprecated("Internal API.", replaceWith = ReplaceWith("component.setFloating(true)"))
    fun addFloatingComponent(component: UIComponent) {
        if (isInitialized) {
            requireMainThread()
        }

        if (legacyFloatingComponents.contains(component)) return

        legacyFloatingComponents.add(component)
    }

    @Deprecated("Internal API.", replaceWith = ReplaceWith("component.setFloating(false)"))
    fun removeFloatingComponent(component: UIComponent) {
        if (isInitialized) {
            requireMainThread()
        }

        legacyFloatingComponents.remove(component)
    }

    /**
     * Overridden to including floating components.
     */
    override fun hitTest(x: Float, y: Float): UIComponent {
        for (component in allFloatingComponentsInReverseOrder()) {
            if (component.isPointInside(x, y)) {
                return component.hitTest(x, y)
            }
        }
        return super.hitTest(x, y)
    }

    /*
     * Focus API
     */

    /**
     * Focus a component. Focusing means that this component will only propagate keyboard
     * events to the currently focused component. The component to be focused does
     * NOT have to be a direct child of this component.
     */
    fun focus(component: UIComponent) {
        if (isInitialized) {
            requireMainThread()
        }

        componentRequestingFocus = component
    }

    /**
     * Remove the currently focused component. This means only the window will receive
     * keyboard events until another component is focused.
     */
    fun unfocus() {
        if (isInitialized) {
            requireMainThread()
        }

        focusedComponent?.loseFocus()
        focusedComponent = null
    }

    @Suppress("DEPRECATION")
    internal val animationFPSOr1000: Int
        get() = if (version >= ElementaVersion.v8) 1000 else animationFPS

    companion object {
        private val renderOperations = ConcurrentLinkedQueue<() -> Unit>()

        fun enqueueRenderOperation(operation: Runnable) {
            renderOperations.add {
                operation.run()
            }
        }

        fun enqueueRenderOperation(operation: () -> Unit) {
            renderOperations.add(operation)
        }

        fun of(component: UIComponent): Window {
            return ofOrNull(component) ?: throw IllegalStateException(
                "No window parent? It's possible you haven't called Window.addChild() at this point in time."
            )
        }

        fun ofOrNull(component: UIComponent): Window? = component.cachedWindow ?: run {
            var current = component

            while (current !is Window && current.hasParent && current.parent != current) {
                current = current.parent
            }

            current as? Window
        }
    }
}
