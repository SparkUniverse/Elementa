package gg.essential.elementa.markdown

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.MarkdownNode
import gg.essential.elementa.components.TreeListComponent
import gg.essential.elementa.components.TreeNode
import gg.essential.elementa.components.UpdateFunc
import gg.essential.elementa.components.Window
import gg.essential.elementa.components.addUpdateFuncOnV8ReplacingAnimationFrame
import gg.essential.elementa.constraints.HeightConstraint
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.events.UIEvent
import gg.essential.elementa.markdown.selection.Cursor
import gg.essential.elementa.markdown.selection.Selection
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.State
import gg.essential.elementa.font.ElementaFonts
import gg.essential.elementa.font.FontProvider
import gg.essential.elementa.markdown.drawables.*
import gg.essential.elementa.utils.elementaDebug
import gg.essential.universal.UDesktop
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack

/**
 * Component that parses a string as Markdown and renders it.
 *
 * This component's width and height must be non-child-related
 * constraints. This is because the actual text rendering is
 * done with direct render calls instead of through the component
 * hierarchy.
 */
class MarkdownComponent(
    text: String,
    config: MarkdownConfig = MarkdownConfig(),
    private val codeFontPointSize: Float = 10f,
    private val codeFontRenderer: FontProvider = ElementaFonts.JETBRAINS_MONO,
    private val disableSelection: Boolean = false,
) : UIComponent() {

    @JvmOverloads
    constructor(
        text: String,
        config: MarkdownConfig = MarkdownConfig(),
        codeFontPointSize: Float = 10f,
        codeFontRenderer: FontProvider = ElementaFonts.JETBRAINS_MONO,
    ) : this(text, config, codeFontPointSize, codeFontRenderer, false)

    private val configState = BasicState(config)
    val config: MarkdownConfig
        get() = configState.get()

    private var textState: State<String> = BasicState(text)
    private var removeListener = textState.onSetValue {
        reparse()
        layout()
    }

    val drawables = DrawableList(this, emptyList())
    var sectionOffsets: Map<String, Float> = emptyMap()
        private set
    private var baseX: Float = -1f
    private var baseY: Float = -1f
    private lateinit var lastValues: ConstraintValues
    private var maxHeight: HeightConstraint = Int.MAX_VALUE.pixels()
    private var cursor: Cursor<*>? = null
    private var selection: Selection? = null
    private var canDrag = false
    private var needsInitialLayout = true
    private val linkClickListeners = mutableListOf<MarkdownComponent.(LinkClickEvent) -> Unit>()

    var maxTextLineWidth = 0f
        private set

    init {
        onMouseClick {
            val xShift = getLeft() - baseX
            val yShift = getTop() - baseY
            cursor =
                drawables.cursorAt(it.absoluteX - xShift, it.absoluteY - yShift, dragged = false, it.mouseButton)

            selection?.remove()
            selection = null
            releaseWindowFocus()
        }
        if (!disableSelection) {
            onMouseClick {
                canDrag = true
            }

            onMouseRelease {
                canDrag = false
            }

            onMouseDrag { mouseX, mouseY, mouseButton ->
                if (mouseButton != 0 || !canDrag)
                    return@onMouseDrag

                val x = baseX + mouseX.coerceIn(0f, getWidth())
                val y = baseY + mouseY.coerceIn(0f, getHeight())

                val otherEnd = drawables.cursorAt(x, y, dragged = true, mouseButton)

                if (cursor == otherEnd)
                    return@onMouseDrag

                selection?.remove()
                selection = Selection.fromCursors(cursor!!, otherEnd)
                grabWindowFocus()
            }

            onKeyType { _, keyCode ->
                if (selection != null && keyCode == UKeyboard.KEY_C && UKeyboard.isCtrlKeyDown()) {
                    UDesktop.setClipboardString(drawables.selectedText(UKeyboard.isShiftKeyDown()))
                }
            }
        }
        configState.onSetValue {
            reparse()
            layout()
        }
    }

    fun bindText(state: State<String>) = apply {
        removeListener()
        textState = state
        reparse()
        layout()

        removeListener = textState.onSetValue {
            reparse()
            layout()
        }
    }

    fun setMaxHeight(maxHeight: HeightConstraint) = apply {
        this.maxHeight = maxHeight
    }

    /**
     * Parses the text into a markdown tree. This is called everytime
     * that the text of this component changes, and is always followed
     * by a call to layout().
     */
    private fun reparse() {
        drawables.setDrawables(MarkdownRenderer(textState.get(), this, config).render())
    }

    /**
     * This method is responsible for laying out the markdown tree.
     *
     * @see Drawable.layout
     */
    fun layout() {
        baseX = getLeft()
        baseY = getTop()
        var currY = baseY
        val width = getWidth()

        drawables.forEach {
            currY += it.layout(baseX, currY, width).height
        }

        sectionOffsets = drawables.filterIsInstance<HeaderDrawable>().associate { it.id to it.y }

        setHeight((currY - baseY).coerceAtMost(maxHeight.getHeight(this)).pixels())

        maxTextLineWidth = drawables.maxOfOrNull { drawable ->
            when (drawable) {
                is ParagraphDrawable -> drawable.maxTextLineWidth
                is HeaderDrawable -> drawable.children.filterIsInstance<ParagraphDrawable>().maxOfOrNull { it.maxTextLineWidth } ?: 0f
                is ListDrawable -> drawable.maxTextLineWidth
                is BlockquoteDrawable -> drawable.maxTextLineWidth
                else -> 0f
            }
        } ?: 0f
    }

    init { addUpdateFuncOnV8ReplacingAnimationFrame { _, _ -> update() }}
    @Deprecated("See [ElementaVersion.V8].")
    override fun animationFrame() {
        @Suppress("DEPRECATION")
        super.animationFrame()
        if (versionOrV0 >= ElementaVersion.v8) return // handled by UpdateFunc
        update()
    }

    private fun update() {
        if (needsInitialLayout) {
            needsInitialLayout = false
            reparse()
            layout()
            lastValues = constraintValues()
        }

        // Re-layout if important constraint values have changed
        val currentValues = constraintValues()
        if (currentValues != lastValues)
            layout()
        lastValues = currentValues
    }

    /**
     * Updates the MarkdownConfig this component uses.
     */
    fun updateConfig(config: MarkdownConfig)  {
        configState.set(config)
    }

    /**
     * Returns a [TreeListComponent] that contains the markdown tree.
     */
    internal fun createLayoutTree(): TreeListComponent {
        val nodes = mutableListOf<TreeNode>()
        drawables.forEach {
            nodes.add(MarkdownNode(it))
        }

        return TreeListComponent(nodes)
    }

    override fun draw(matrixStack: UMatrixStack) {
        if (needsInitialLayout) {
            update()
        }
        beforeDraw(matrixStack)

        val drawState = DrawState(getLeft() - baseX, getTop() - baseY)
        val parentWindow = Window.of(this)

        drawables.forEach { it.beforeDraw(drawState) }

        drawables.forEach {

            if (!parentWindow.isAreaVisible(
                    it.layout.left.toDouble() + drawState.xShift, it.layout.top.toDouble() + drawState.yShift,
                    it.layout.right.toDouble() + drawState.xShift, it.layout.bottom.toDouble() + drawState.yShift
            )) return@forEach

            if (elementaDebug) {
                drawDebugOutline(
                    matrixStack,
                    it.layout.left.toDouble() + drawState.xShift,
                    it.layout.top.toDouble() + drawState.yShift,
                    it.layout.right.toDouble() + drawState.xShift,
                    it.layout.bottom.toDouble() + drawState.yShift,
                    this
                )
            }

            it.draw(matrixStack, drawState)
        }
        if (!disableSelection)
            selection?.draw(matrixStack, drawState) ?: cursor?.draw(matrixStack, drawState)

        super.draw(matrixStack)
    }

    private fun constraintValues() = ConstraintValues(
        getWidth(),
        getTextScale(),
    )

    fun onLinkClicked(block: MarkdownComponent.(LinkClickEvent) -> Unit) {
        linkClickListeners.add(block)
    }

    internal fun fireLinkClickEvent(event: LinkClickEvent): Boolean {
        for (listener in linkClickListeners) {
            this.listener(event)

            if (event.propagationStoppedImmediately) return false
        }
        return !event.propagationStopped
    }

    class LinkClickEvent internal constructor(val url: String) : UIEvent()

    /**
     * This class stores the values of the important constraints of this
     * component. If these values change between frames, we need to do a
     * complete re-layout of the entire markdown tree.
     */
    data class ConstraintValues(
        val width: Float,
        val textScale: Float,
    )

    companion object {
        // TODO: Remove
        const val DEBUG = false
    }
}
