package gg.essential.elementa.components.plot

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack

class PlotComponent(
    private val points: List<PlotPoint>,
    private val xBounds: Bounds = Bounds.fromPoints(points.map { it.x }),
    private val yBounds: Bounds = Bounds.fromPoints(points.map { it.y }),
    private val style: PlotStyle = PlotStyle()
) : UIComponent() {
    private val container = UIContainer().constrain {
        x = style.padding.left.pixels()
        y = style.padding.top.pixels()
        width = RelativeConstraint() - (style.padding.left + style.padding.right).pixels()
        height = RelativeConstraint() - (style.padding.top + style.padding.bottom).pixels()
    } childOf this

    private val xLabelContainer = UIContainer()
    private val yLabelContainer = UIContainer()

    private var drawWidth = -1f
    private var drawHeight = -1f
    private var drawLeft = -1f
    private var drawTop = -1f

    init {
        if (points.groupBy { it.x }.any { it.value.size > 1 })
            throw IllegalArgumentException("The point list contains two points that share the same x value")

        if (xBounds.numberOfGridLines != 0 && xBounds.numberOfGridLines < 2)
            throw IllegalArgumentException("xBounds.numberOfGridLines lines must be either 0 or >= 2")

        if (yBounds.numberOfGridLines != 0 && yBounds.numberOfGridLines < 2)
            throw IllegalArgumentException("yBounds.numberOfGridLines lines must be either 0 or >= 2")

        constrain {
            width = 100.percent()
            height = 100.percent()
        }

        when {
            xBounds.showLabels && !yBounds.showLabels -> {
                xLabelContainer.constrain {
                    y = 0.pixels(alignOpposite = true)
                    width = RelativeConstraint()
                    height = ChildBasedMaxSizeConstraint()
                } childOf container
            }
            !xBounds.showLabels && yBounds.showLabels -> {
                yLabelContainer.constrain {
                    width = ChildBasedMaxSizeConstraint() + 5.pixels()
                    height = RelativeConstraint()
                } childOf container
            }
            xBounds.showLabels && yBounds.showLabels -> {
                yLabelContainer.constrain {
                    width = ChildBasedMaxSizeConstraint() + 5.pixels()
                    height = basicHeightConstraint { container.getHeight() - xLabelContainer.getHeight() }
                } childOf container

                xLabelContainer.constrain {
                    x = basicXConstraint { yLabelContainer.getRight() }
                    y = 0.pixels(alignOpposite = true)
                    width = basicWidthConstraint { container.getWidth() - yLabelContainer.getWidth() }
                    height = ChildBasedMaxSizeConstraint()
                } childOf container
            }
        }

        if (yBounds.showLabels) {
            repeat(yBounds.numberOfGridLines) { index ->
                val percentage = index.toFloat() / (yBounds.numberOfGridLines - 1)
                val number = (1f - percentage) * yBounds.range + yBounds.min

                UIText(yBounds.labelToString(number)).constrain {
                    x = CenterConstraint()
                    y = RelativeConstraint(percentage) - (percentage * 3f).pixels()
                    width = TextAspectConstraint()
                    height = 6.pixels()
                    color = yBounds.labelColor.toConstraint()
                } childOf yLabelContainer
            }
        }

        if (xBounds.showLabels) {
            repeat(xBounds.numberOfGridLines) { index ->
                val percentage = (1f - index.toFloat() / (xBounds.numberOfGridLines - 1))
                val number = percentage * xBounds.range + xBounds.min

                val text = xBounds.labelToString(number)
                val textComponent = UIText(text)
                textComponent.constrain {
                    x = basicXConstraint {
                        val width = container.getWidth() - yLabelContainer.getWidth()
                        width * percentage + yLabelContainer.getRight() - textComponent.getWidth() / 2f + 2f
                    }
                    width = TextAspectConstraint()
                    height = 6.pixels()
                    color = xBounds.labelColor.toConstraint()
                } childOf xLabelContainer
            }
        }
    }

    private fun drawGrid(bounds: Bounds, isX: Boolean) {
        if (bounds.numberOfGridLines == 0)
            return

        val values = (0 until bounds.numberOfGridLines).map {
            bounds.min + bounds.range * (it.toFloat() / (bounds.numberOfGridLines - 1))
        }

        values.forEach {
            val (start, end) = if (isX) {
                PlotPoint(it, yBounds.min) to PlotPoint(it, yBounds.max)
            } else {
                PlotPoint(xBounds.min, it) to PlotPoint(xBounds.max, it)
            }.let { (a, b) -> transformPoint(a) to transformPoint(b) }

            style.gridStyle.type.draw(listOf(start, end), style.gridStyle.color, style.gridStyle.width)
        }
    }

    private fun drawPoints() {
        style.pointStyle.type.draw(points.map(::transformPoint), style)
    }

    private fun drawLines() {
        style.lineStyle.type.draw(
            points.map(::transformPoint),
            style.lineStyle.color,
            style.lineStyle.width
        )
    }

    private fun transformPoint(point: PlotPoint): PlotPoint {
        val xPercent = (point.x - xBounds.min) / xBounds.range
        val yPercent = (point.y - yBounds.min) / yBounds.range

        val newX = drawLeft + xPercent * drawWidth
        val newY = drawTop + yPercent * drawHeight

        return PlotPoint(newX, newY)
    }

    @Deprecated(
        "`draw`-style rendering is deprecated. Override `extractComponent` instead. Call `extract` to extract this component, its effects, and its children.",
        replaceWith = ReplaceWith("extract(extractor)")
    )
    override fun draw(matrixStack: UMatrixStack) {
        @Suppress("DEPRECATION")
        super.draw(matrixStack)

        drawWidth = if (yBounds.showLabels) {
            container.getWidth() - yLabelContainer.getWidth()
        } else container.getWidth()

        drawHeight = when {
            xBounds.showLabels && yBounds.showLabels -> container.getHeight() - xLabelContainer.getHeight() - 6f
            xBounds.showLabels -> container.getHeight() - xLabelContainer.getHeight() - 3f
            yBounds.showLabels -> container.getHeight() - 3f
            else -> container.getHeight()
        }

        drawLeft = if (yBounds.showLabels) {
            yLabelContainer.getRight()
        } else container.getLeft()

        drawTop = container.getTop() + if (yBounds.showLabels) 3f else 0f

        // TODO propagate matrix stack and switch to line shader for 1.17
        matrixStack.runWithGlobalState {
            drawGrid(xBounds, isX = true)
            drawGrid(yBounds, isX = false)
            drawPoints()
            drawLines()
        }
    }
}
