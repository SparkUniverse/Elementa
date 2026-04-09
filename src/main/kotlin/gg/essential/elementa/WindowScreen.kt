package gg.essential.elementa

import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.animation.*
import gg.essential.elementa.events.UICharEvent
import gg.essential.elementa.events.UIKeyEvent
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UMouse
import gg.essential.universal.UScreen

import java.awt.Color
import kotlin.math.floor
import kotlin.reflect.KMutableProperty0

/**
 * Version of [UScreen] with a [Window] provided and a few useful
 * functions for Elementa Gui programming.
 */
abstract class WindowScreen @JvmOverloads constructor(
    private val version: ElementaVersion,
    private val enableRepeatKeys: Boolean = true,
    private val drawDefaultBackground: Boolean = true,
    restoreCurrentGuiOnClose: Boolean = false,
    newGuiScale: Int = -1
) : UScreen(restoreCurrentGuiOnClose, newGuiScale), UScreen.InputHandler {
    val window = Window(version)
    private var isInitialized = false

    @Deprecated("Add ElementaVersion as the first argument to opt-in to improved behavior.")
    @JvmOverloads
    constructor(
        enableRepeatKeys: Boolean = true,
        drawDefaultBackground: Boolean = true,
        restoreCurrentGuiOnClose: Boolean = false,
        newGuiScale: Int = -1
    ) : this(ElementaVersion.v0, enableRepeatKeys, drawDefaultBackground, restoreCurrentGuiOnClose, newGuiScale)

    init {
        if (version >= ElementaVersion.v12) {
            window.onKeyPressed.add { keyEvent ->
                super.uKeyPressed(keyEvent.key, keyEvent.scanCode, keyEvent.modifiers)
            }
            window.onCharTyped.add { charEvent ->
                super.uCharTyped(charEvent.codepoint)
            }
        } else {
            @Suppress("DEPRECATION")
            window.onKeyType { typedChar, keyCode ->
                defaultKeyBehavior(typedChar, keyCode)
            }

            // This will ensure that inputs go to the old `on*(): Unit` input functions
            // rather than the V12+ `u*(): Boolean` functions
            inputHandler = null
        }
    }

    open fun afterInitialization() { }

    override fun onDrawScreen(matrixStack: UMatrixStack, mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (!isInitialized) {
            isInitialized = true
            afterInitialization()
        }

        if (drawDefaultBackground)
            super.onDrawBackground(matrixStack, 0)

        super.onDrawScreen(matrixStack, mouseX, mouseY, partialTicks)

        // Now, we need to hook up Elementa to this GuiScreen. In practice, Elementa
        // is not constrained to being used solely inside of a GuiScreen, all the programmer
        // needs to do is call the [Window] events when appropriate, whenever that may be.
        // In our example, it is in the overridden [GuiScreen#drawScreen] method.
        window.draw(matrixStack)
    }

    @Deprecated("See [ElementaVersion.V12]")
    @Suppress("DEPRECATION")
    override fun onMouseClicked(mouseX: Double, mouseY: Double, mouseButton: Int) {
        super.onMouseClicked(mouseX, mouseY, mouseButton)

        // Restore decimal value to mouse locations if not present.
        // See [ElementaVersion.V2] for more info
        val (adjustedMouseX, adjustedMouseY) =
            if (version >= ElementaVersion.v2 && (mouseX == floor(mouseX) && mouseY == floor(mouseY))) {
                val x = UMouse.Scaled.x
                val y = UMouse.Scaled.y

                mouseX + (x - floor(x)) to mouseY + (y - floor(y))
            } else {
                mouseX to mouseY
            }

        // We also need to pass along clicks
        window.mouseClick(adjustedMouseX, adjustedMouseY, mouseButton)
    }

    // Called only when ElementaVersion >= V12
    override fun uMouseClicked(x: Double, y: Double, button: Int, modifiers: UKeyboard.Modifiers): Boolean {
        uSuperInputHandler().uMouseClicked(x, y, button, modifiers)

        // We also need to pass along clicks
        window.mouseClick(x, y, button)

        return false // TODO returning implementation, see uKeyPressed()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onMouseReleased(mouseX: Double, mouseY: Double, state: Int) {
        super.onMouseReleased(mouseX, mouseY, state)

        // We also need to pass along mouse releases
        window.mouseRelease()
    }

    // Called only when ElementaVersion >= V12
    override fun uMouseReleased(x: Double, y: Double, button: Int, modifiers: UKeyboard.Modifiers): Boolean {
        uSuperInputHandler().uMouseReleased(x, y, button, modifiers)

        // We also need to pass along mouse releases
        window.mouseRelease()

        return false // TODO returning implementation, see uKeyPressed()
    }

    @Suppress("DEPRECATION")
    @Deprecated(
        "Provided `delta` values have different units depending on Minecraft versions. See ElementaVersion.V11 for details.",
        replaceWith = ReplaceWith("onMouseScrolled(mouseX, mouseY, deltaHorizontal, deltaVertical)")
    )
    override fun onMouseScrolled(delta: Double) {
        super.onMouseScrolled(delta)

        if (version < ElementaVersion.v11) {
            // We also need to pass along scrolling
            window.mouseScroll(delta.coerceIn(-1.0, 1.0))
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onMouseScrolled(mouseX: Double, mouseY: Double, deltaHorizontal: Double, deltaVertical: Double) {
        super.onMouseScrolled(mouseX, mouseY, deltaHorizontal, deltaVertical)

        if (version >= ElementaVersion.v11) {
            window.mouseScroll(deltaHorizontal, deltaVertical)
        }
    }

    // Called only when ElementaVersion >= V12
    override fun uMouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        uSuperInputHandler().uMouseScrolled(x, y, scrollX, scrollY)

        window.mouseScroll(scrollX, scrollY)

        return false // TODO returning implementation, see uKeyPressed()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Method is not called when ElementaVersion >= V12. See ElementaVersion.V12 for details.")
    override fun onKeyPressed(keyCode: Int, typedChar: Char, modifiers: UKeyboard.Modifiers?) {
        // We also need to pass along typed keys
        window.keyType(typedChar, keyCode)
    }

    // Called only when ElementaVersion >= V12
    override fun uKeyPressed(key: Int, scanCode: Int, modifiers: UKeyboard.Modifiers): Boolean {
        return window.keyPressed(UIKeyEvent(key, scanCode, modifiers))
    }

    // Called only when ElementaVersion >= V12
    override fun uCharTyped(codepoint: Int): Boolean {
        return window.charTyped(UICharEvent(codepoint))
    }

    override fun initScreen(width: Int, height: Int) {
        window.onWindowResize()

        super.initScreen(width, height)

        // Since we want our users to be able to hold a key
        // to type. This is a wrapper around a base LWJGL function.
        // - Keyboard.enableRepeatEvents in <= 1.12.2
        if (enableRepeatKeys)
            UKeyboard.allowRepeatEvents(true)
    }

    override fun onScreenClose() {
        super.onScreenClose()

        // We need to disable repeat events when leaving the gui.
        if (enableRepeatKeys)
            UKeyboard.allowRepeatEvents(false)
    }

    @Deprecated("[See ElementaVersion.V12] This method is not called when ElementaVersion >= V12.")
    fun defaultKeyBehavior(typedChar: Char, keyCode: Int) {
        @Suppress("DEPRECATION")
        super.onKeyPressed(keyCode, typedChar, UKeyboard.getModifiers())
    }

    /**
     * Field animation API
     */

    fun KMutableProperty0<Int>.animate(strategy: AnimationStrategy, time: Float, newValue: Int, delay: Float = 0f) {
        window.apply { this@animate.animate(strategy, time, newValue, delay) }
    }

    fun KMutableProperty0<Float>.animate(strategy: AnimationStrategy, time: Float, newValue: Float, delay: Float = 0f) {
        window.apply { this@animate.animate(strategy, time, newValue, delay) }
    }

    fun KMutableProperty0<Long>.animate(strategy: AnimationStrategy, time: Float, newValue: Long, delay: Float = 0f) {
        window.apply { this@animate.animate(strategy, time, newValue, delay) }
    }

    fun KMutableProperty0<Double>.animate(strategy: AnimationStrategy, time: Float, newValue: Double, delay: Float = 0f) {
        window.apply { this@animate.animate(strategy, time, newValue, delay) }
    }

    fun KMutableProperty0<Color>.animate(strategy: AnimationStrategy, time: Float, newValue: Color, delay: Float = 0f) {
        window.apply { this@animate.animate(strategy, time, newValue, delay) }
    }

    fun KMutableProperty0<*>.stopAnimating() {
        window.apply { this@stopAnimating.stopAnimating() }
    }
}
