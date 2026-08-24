package gg.essential.elementa

import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.animation.*
import gg.essential.elementa.renderer.ElementaRenderState
import gg.essential.elementa.renderer.ElementaRenderer
import gg.essential.universal.UGraphics
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UMouse
import gg.essential.universal.UScreen
import gg.essential.universal.render.UGpuFormat
import gg.essential.universal.render.UGpuTexture
import gg.essential.universal.render.UGpuTextureView

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
) : UScreen(restoreCurrentGuiOnClose, newGuiScale) {
    val window = Window(version)

    /**
     * Whether the new [ElementaRenderer], and therefore [Window.extractRenderState] instead of [Window.draw], should be
     * used. This is required as of Minecraft 26.3, and the new preferred mode of operation on other versions as well.
     *
     * **This requires that all your components and effects support the new `extract`-style methods.**
     *
     * This flag exists to allow for migration to the new methods independent from other [ElementaVersion] changes.
     * It may become mandatory with a future [ElementaVersion] though if supporting the old renderer becomes too much of
     * a burdon.
     */
    var useElementaRenderer: Boolean = isRendererRequired
        set(value) {
            if (field && !value) {
                throw UnsupportedOperationException("Cannot be disabled once enabled.")
            }
            field = value
        }

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
        window.onKeyType { typedChar, keyCode ->
            defaultKeyBehavior(typedChar, keyCode)
        }
    }

    open fun afterInitialization() { }

    override fun uCreateRenderer(): Renderer? {
        return if (useElementaRenderer) ElementaUScreenRenderer() else null
    }

    override fun uExtractRenderState(mouseX: Int, mouseY: Int, partialTicks: Float): RenderState {
        if (!isInitialized) {
            isInitialized = true
            afterInitialization()
        }

        window.prepareFrame()
        return ElementaUScreenRenderState(
            window.extractRenderState(),
            drawDefaultBackground,
        )
    }

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
        @Suppress("DEPRECATION")
        window.draw(matrixStack)
    }

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

    override fun onMouseReleased(mouseX: Double, mouseY: Double, state: Int) {
        super.onMouseReleased(mouseX, mouseY, state)

        // We also need to pass along mouse releases
        window.mouseRelease()
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

    override fun onMouseScrolled(mouseX: Double, mouseY: Double, deltaHorizontal: Double, deltaVertical: Double) {
        super.onMouseScrolled(mouseX, mouseY, deltaHorizontal, deltaVertical)

        if (version >= ElementaVersion.v11) {
            window.mouseScroll(deltaHorizontal, deltaVertical)
        }
    }

    override fun onKeyPressed(keyCode: Int, typedChar: Char, modifiers: UKeyboard.Modifiers?) {
        // We also need to pass along typed keys
        window.keyType(typedChar, keyCode)
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

    fun defaultKeyBehavior(typedChar: Char, keyCode: Int) {
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

private class ElementaUScreenRenderer : UScreen.Renderer {
    private val elementaRenderer = ElementaRenderer()

    private var lastWidth = 0
    private var lastHeight = 0
    private var lastTextureView: UGpuTextureView? = null

    override fun render(state: UScreen.RenderState): UGpuTextureView {
        state as ElementaUScreenRenderState

        var width = state.elementaRenderState.screenWidth
        var height = state.elementaRenderState.screenHeight

        if (width == 0 || height == 0) {
            width = 1
            height = 1
        }

        if (lastWidth != width || lastHeight != height) {
            lastWidth = width
            lastHeight = height
            lastTextureView?.texture?.close()
            lastTextureView?.close()
            lastTextureView = null
        }

        val textureView = lastTextureView ?: run {
            val device = UGraphics.getDevice()
            val texture = device.createTexture(
                null,
                UGpuTexture.Usage.TEXTURE_BINDING + UGpuTexture.Usage.RENDER_ATTACHMENT,
                UGpuFormat.DEFAULT_RGBA,
                width,
                height,
                1,
            )
            device.createTextureView(texture, 0, 1)
        }.also { lastTextureView = it }

        elementaRenderer.renderToTexture(
            textureView,
            0, 0,
            0, 0,
            width, height,
            state.elementaRenderState,
        )

        return textureView
    }

    override fun close() {
        lastTextureView?.texture?.close()
        lastTextureView?.close()
        lastTextureView = null

        elementaRenderer.close()
    }
}

private class ElementaUScreenRenderState(
    val elementaRenderState: ElementaRenderState,
    override val background: Boolean,
) : UScreen.RenderState
