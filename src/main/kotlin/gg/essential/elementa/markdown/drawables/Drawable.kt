package gg.essential.elementa.markdown.drawables

import gg.essential.elementa.markdown.DrawState
import gg.essential.elementa.markdown.MarkdownComponent
import gg.essential.elementa.markdown.MarkdownConfig
import gg.essential.elementa.markdown.selection.Cursor
import gg.essential.elementa.markdown.selection.TextCursor
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.universal.UMatrixStack
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

abstract class Drawable(val md: MarkdownComponent) {
    val config: MarkdownConfig
        get() = md.config

    // Cache the layout between draws, as calculating this is fairly
    // expensive.
    lateinit var layout: Layout

    // Layout helpers
    var x: Float
        get() = layout.x
        set(value) { layout.x = value }
    var y: Float
        get() = layout.y
        set(value) { layout.y = value }
    var width
        get() = layout.width
        set(value) { layout.width = value }
    var height
        get() = layout.height
        set(value) { layout.height = value }
    var margin
        get() = layout.margin
        set(value) { layout.margin = value }

    // Used to disable top and bottom padding in some elements
    var insertSpaceBefore = true
    var insertSpaceAfter = true

    // For tree-like navigation
    var previous: Drawable? = null
    var next: Drawable? = null
    // parent == null indicates the parent is the MarkdownComponent
    var parent: Drawable? = null
    open val children: List<Drawable> = emptyList()

    /**
     * Layout this element with the given x, y, and width constraints.
     * Returns all of the information necessary to place both this
     * component and any following components on the screen.
     *
     * This should be considered an expensive function call, and as such
     * is only called (from MarkdownComponent) when necessary (i.e. when
     * the x, y, or width values change).
     *
     * The result of this computation is cached in the [layout] property.
     */
    fun layout(x: Float, y: Float, width: Float): Layout {
        return layoutImpl(x, y, width).also {
            layout = it
        }
    }

    /**
     * Implementation of layout functionality
     */
    protected abstract fun layoutImpl(x: Float, y: Float, width: Float): Layout

    @Deprecated(UMatrixStack.Compat.DEPRECATED, ReplaceWith("draw(matrixStack, state)"))
    @Suppress("DEPRECATION")
    open fun draw(state: DrawState) = draw(UMatrixStack.Compat.get(), state)

    @Suppress("DEPRECATION")
    fun drawCompat(matrixStack: UMatrixStack, state: DrawState) = UMatrixStack.Compat.runLegacyMethod(matrixStack) { draw(state) }

    @Deprecated("`draw`-style rendering is deprecated. Use `extract` instead.")
    open fun draw(matrixStack: UMatrixStack, state: DrawState) {
    }

    open fun extract(extractor: ElementaExtractor, state: DrawState) {
    }

    open fun beforeDraw(state: DrawState) {
        children.forEach { it.beforeDraw(state) }
    }

    fun isHovered(mouseX: Float, mouseY: Float): Boolean {
        return mouseX in layout.left..layout.right && mouseY in layout.top..layout.bottom
    }

    /**
     * Produces a TextCursor for this drawable in the specified position.
     *
     * For higher-level drawables (like headers and lists), this simply
     * delegates to a lower-level drawable (DrawableList and
     * ParagraphDrawable).
     */
    abstract fun cursorAt(mouseX: Float, mouseY: Float, dragged: Boolean, mouseButton: Int): Cursor<*>

    /**
     * Produces a TextCursor for the start of this drawable
     */
    abstract fun cursorAtStart(): Cursor<*>

    /**
     * Produces a TextCursor for the end of this drawable
     */
    abstract fun cursorAtEnd(): Cursor<*>

    /**
     * Whether or not this drawable contains a selected TextDrawable anywhere
     * in its children tree
     */
    open fun hasSelectedText(): Boolean {
        return children.any {
            (it is TextDrawable && it.selectionStart != -1) || it.hasSelectedText()
        }
    }

    /**
     * The text that is currently selected inside of this drawable.
     *
     * @param asMarkdown Whether or not to format the returned text as markdown
     *                   text (e.g. "> text" vs "text" for block quotes)
     */
    abstract fun selectedText(asMarkdown: Boolean): String

    data class Layout(
        var x: Float,
        var y: Float,
        var width: Float,
        var height: Float,
        var margin: Margin = Margin()
    ) {
        val elementWidth get() = width - margin.left - margin.right
        val elementHeight get() = height - margin.top - margin.bottom

        val top get() = y
        val bottom get() = y + height
        val elementTop get() = y + margin.top
        val elementBottom get() = y + margin.top + height

        val left get() = x
        val right get() = x + width
        val elementLeft get() = x + margin.left
        val elementRight get() = x + margin.left + width
    }

    data class Margin(
        var left: Float = 0f,
        var top: Float = 0f,
        var right: Float = 0f,
        var bottom: Float = 0f
    )

    companion object {
        // To resolve the ambiguity
        fun trim(drawableList: DrawableList) {
            trim(drawableList as List<Drawable>)
        }

        /**
         * Disables the start padding from the first element of
         * this list, as well as the end padding from the last
         * element of this list
         */
        fun trim(drawables: List<Drawable>) {
            drawables.firstOrNull()?.also {
                it.insertSpaceBefore = false
            }
            drawables.lastOrNull()?.also {
                it.insertSpaceAfter = false
            }
        }

        /**
         * Disables both the start and end padding
         */
        fun trim(drawable: Drawable) {
            if (drawable is DrawableList) {
                drawable.first().insertSpaceBefore = false
                drawable.last().insertSpaceAfter = false
            } else {
                drawable.insertSpaceBefore = false
                drawable.insertSpaceAfter = false
            }
        }
    }
}

fun <R> lazy(property: () -> KMutableProperty0<R>): LazyPropertyDelegate<R> {
    return LazyPropertyDelegate(property)
}

class LazyPropertyDelegate<R>(private val provider: () -> KMutableProperty0<R>) {
    private var propertyBacker: KMutableProperty0<R>? = null

    private fun property(): KMutableProperty0<R> {
        if (propertyBacker == null)
            propertyBacker = provider()
        return propertyBacker!!
    }

    operator fun getValue(d: Drawable, p: KProperty<*>): R {
        return property().get()
    }

    operator fun setValue(d: Drawable, p: KProperty<*>, value: R) {
        property().set(value)
    }
}

