package gg.essential.elementa.markdown.drawables

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIImage
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.XConstraint
import gg.essential.elementa.constraints.YConstraint
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.dsl.toConstraint
import gg.essential.elementa.markdown.DrawState
import gg.essential.elementa.markdown.MarkdownComponent
import gg.essential.elementa.markdown.selection.ImageCursor
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.universal.UMatrixStack
import java.awt.Color
import java.net.URL

class ImageDrawable(md: MarkdownComponent, val url: URL, private val fallback: Drawable) : Drawable(md) {
    var selected = false
        set(value) {
            field = value
            if (value) {
                image.setColor(Color(200, 200, 255, 255).toConstraint())
            } else {
                image.setColor(Color.WHITE.toConstraint())
            }
        }

    private lateinit var imageX: ShiftableMDPixelConstraint
    private lateinit var imageY: ShiftableMDPixelConstraint

    private val image = UIImage.ofURL(url) childOf md
    private var hasLoaded = false

    override fun layoutImpl(x: Float, y: Float, width: Float): Layout {
        return if (image.isLoaded) {
            imageX = ShiftableMDPixelConstraint(x, 0f)
            imageY = ShiftableMDPixelConstraint(y, 0f)
            image.setX(imageX)
            image.setY(imageY)

            val aspectRatio = image.imageWidth / image.imageHeight
            val imageWidth = image.imageWidth.coerceAtMost(width)
            val imageHeight = imageWidth / aspectRatio

            image.setWidth(imageWidth.pixels())
            image.setHeight(imageHeight.pixels())

            Layout(x, y, imageWidth, imageHeight)
        } else fallback.layout(x, y, width)
    }

    override fun draw(matrixStack: UMatrixStack, state: DrawState) {
        if (!hasLoaded) {
            fallback.drawCompat(matrixStack, state)
        } else {
            image.drawCompat(matrixStack)
        }
    }

    override fun extract(extractor: ElementaExtractor, state: DrawState) {
        if (!hasLoaded) {
            fallback.extract(extractor, state)
        } else {
            image.extractComponent(extractor)
        }
    }

    override fun beforeDraw(state: DrawState) {
        if (image.isLoaded && !hasLoaded) {
            hasLoaded = true
            md.layout()
        }
        if (hasLoaded) {
            imageX.shift = state.xShift
            imageY.shift = state.yShift
        }
    }

    // ImageDrawable mouse selection is managed by ParagraphDrawable#select
    override fun cursorAt(mouseX: Float, mouseY: Float, dragged: Boolean, mouseButton: Int) = throw IllegalStateException("never called")
    override fun cursorAtStart() = ImageCursor(this)
    override fun cursorAtEnd() = ImageCursor(this)

    override fun selectedText(asMarkdown: Boolean): String {
        if (asMarkdown) {
            // TODO: `fallback.selectedText(true)` will be empty since the children aren't
            // marked as selected
            return " ![${fallback.selectedText(true)}]($url) "
        }
        return " $url "
    }

    // TODO: Rename this function?
    override fun hasSelectedText() = selected

    private inner class ShiftableMDPixelConstraint(val base: Float, shift: Float) : XConstraint, YConstraint {
        var shift: Float = shift
            set(value) {
                recalculate = true
                field = value
            }
        override var cachedValue = 0f
        override var recalculate = true
        override var constrainTo: UIComponent? = null

        override fun getXPositionImpl(component: UIComponent) = base + shift
        override fun getYPositionImpl(component: UIComponent) = base + shift

        override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) { }
    }
}
