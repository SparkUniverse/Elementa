package gg.essential.elementa.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.RelativeConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.elementa.utils.LineUtils
import gg.essential.universal.UMatrixStack
import java.awt.Color
import kotlin.math.max
import kotlin.properties.Delegates

data class TreeGraphStyle(
    val widthBetweenNodes: Float = 10f,
    val heightBetweenRows: Float = 10f,
    val lineColor: Color = Color.WHITE,
    val lineWidth: Float = 2f,
    val isHorizontal: Boolean = false,
    val lineDrawer: (UIPoint, UIPoint) -> Unit = { p0, p1 ->
        @Suppress("DEPRECATION") // impossible to fix because TreeGraphStyle is public API
        LineUtils.drawLine(p0, p1, lineColor, lineWidth)
    }
)

abstract class TreeGraphNode {
    var depth by Delegates.notNull<Int>()
    val children = mutableListOf<TreeGraphNode>()
    val component by lazy { makeComponent() }

    // TODO: invalidate these if component tree changes
    private var widthBacker: Double? = null
    private var heightBacker: Double? = null

    fun width(style: TreeGraphStyle): Double {
        if (widthBacker == null) {
            widthBacker = if (children.isEmpty()) {
                component.getWidth().toDouble()
            } else {
                max(
                    children.sumOf { it.width(style) } + (children.size - 1) * style.widthBetweenNodes,
                    component.getWidth().toDouble()
                )
            }
        }

        return widthBacker!!
    }

    fun height(style: TreeGraphStyle): Double {
        if (heightBacker == null) {
            heightBacker = if (children.isEmpty()) {
                component.getHeight().toDouble()
            } else {
                max(
                    children.sumOf { it.height(style) } + (children.size - 1) * style.heightBetweenRows,
                    component.getHeight().toDouble()
                )
            }
        }

        return heightBacker!!
    }

    // Must have absolutely-resolvable width and height. The position
    // will be handled by the caller
    protected abstract fun makeComponent(): UIComponent

    fun withChildren(childBuilder: TreeGraphBuilder.() -> Unit): TreeGraphNode {
        val builder = TreeGraphBuilder(this)
        builder.apply(childBuilder)
        return builder.root
    }

    fun forAllChildren(action: (TreeGraphNode, depth: Int) -> Unit) {
        children.forEach {
            action(it, it.depth)
            it.forAllChildren(action)
        }
    }

    // Should only be called on the root node component
    internal fun layoutChildren(style: TreeGraphStyle) {
        component.setX(0.pixels())
        component.setY(0.pixels())

        if (style.isHorizontal) {
            layoutChildrenHelper(style, component.getWidth() + style.widthBetweenNodes.toDouble(), 0.0)
        } else {
            layoutChildrenHelper(style, 0.0, component.getHeight() + style.heightBetweenRows.toDouble())
        }

        normalizePositions()
    }

    private fun layoutChildrenHelper(style: TreeGraphStyle, x_: Double, y_: Double) {
        if (children.isEmpty())
            return

        if (style.isHorizontal) {
            val totalHeight = children.sumOf { it.height(style) } + (children.size - 1) * style.heightBetweenRows
            val maxWidth = children.maxOf { it.component.getWidth().toDouble() }

            var y = y_ - totalHeight / 2.0

            children.forEach { node ->
                y += node.height(style) / 2f

                if (children.size == 1)
                    y += (component.getHeight() - node.component.getHeight()) / 2f

                node.component.setX((x_ + (maxWidth - node.component.getWidth()) / 2.0).pixels())
                node.component.setY(y.pixels())

                node.layoutChildrenHelper(style, x_ + maxWidth + style.widthBetweenNodes, y)

                y += node.height(style) / 2f + style.heightBetweenRows
            }
        } else {
            val totalWidth = children.sumOf { it.width(style) } + (children.size - 1) * style.widthBetweenNodes
            val maxHeight = children.maxOf { it.component.getHeight().toDouble() }

            var x = x_ - totalWidth / 2.0

            children.forEach { node ->
                x += node.width(style) / 2f

                if (children.size == 1)
                    x += (component.getWidth() - node.component.getWidth()) / 2f

                node.component.setX(x.pixels())
                node.component.setY((y_ + (maxHeight - node.component.getHeight()) / 2.0).pixels())

                node.layoutChildrenHelper(style, x, y_ + maxHeight + style.heightBetweenRows)

                x += node.width(style) / 2f + style.widthBetweenNodes
            }
        }
    }

    private fun normalizePositions() {
        var minX = component.getLeft()
        var minY = component.getTop()

        forAllChildren { node, _ ->
            node.component.getLeft().also {
                if (it < minX)
                    minX = it
            }
            node.component.getTop().also {
                if (it < minY)
                    minY = it
            }
        }

        component.setX((component.getLeft() - minX).pixels())
        component.setY((component.getTop() - minY).pixels())

        forAllChildren { node, _ ->
            node.component.apply {
                setX((getLeft() - minX).pixels())
                setY((getTop() - minY).pixels())
            }
        }
    }

    internal fun setDepths(depth: Int = 0) {
        this.depth = depth

        children.forEach {
            it.depth = depth + 1
            it.setDepths(depth + 1)
        }
    }

    internal fun collectLines(isHorizontal: Boolean): List<Pair<UIPoint, UIPoint>> {
        val modifiers = if (children.isEmpty()) emptyList() else {
            val delta = 1f / (children.size + 1)
            (1..children.size).map { it * delta }
        }.map {
            (if (isHorizontal) component.getHeight() else component.getWidth()) * (it - 0.5f)
        }

        return children.mapIndexed { index, node ->
            val p1 = point(isHorizontal, align = true).let {
                if (isHorizontal) {
                    it.withY(it.y + modifiers[index].pixels())
                } else it.withX(it.x + modifiers[index].pixels())
            }

            p1 to node.point(isHorizontal, align = false)
        } + children.map {
            it.collectLines(isHorizontal)
        }.flatten()
    }

    private fun point(isHorizontal: Boolean, align: Boolean): UIPoint {
        return if (isHorizontal) {
            val extraWidth = if (align) component.getWidth() else 0f

            UIPoint(
                (component.getLeft() + extraWidth - component.parent.getLeft()).pixels(),
                (component.getTop() + component.getHeight() / 2f - component.parent.getTop()).pixels()
            )
        } else {
            val extraHeight = if (align) component.getHeight() else 0f

            UIPoint(
                (component.getLeft() + component.getWidth() / 2f - component.parent.getLeft()).pixels(),
                (component.getTop() + extraHeight - component.parent.getTop()).pixels()
            )
        }
    }
}

class TreeGraphComponent(
    private val rootNode: TreeGraphNode,
    private val style: TreeGraphStyle = TreeGraphStyle()
) : UIComponent() {
    private var layedOut = false

    private val scroll = ScrollComponent(horizontalScrollEnabled = true).constrain {
        width = RelativeConstraint()
        height = RelativeConstraint()
    } effect ScissorEffect() childOf this

    private lateinit var lines: List<Pair<UIPoint, UIPoint>>

    init {
        enableEffect(ScissorEffect())

        val horizontalScroll = ScrollComponent.DefaultScrollBar(isHorizontal = true) childOf this
        val verticalScroll = ScrollComponent.DefaultScrollBar(isHorizontal = false) childOf this

        scroll.setHorizontalScrollBarComponent(horizontalScroll.grip, hideWhenUseless = true)
        scroll.setVerticalScrollBarComponent(verticalScroll.grip, hideWhenUseless = true)

        rootNode.setDepths(0)

        val componentMatrix = mutableListOf(mutableListOf(rootNode))

        rootNode.component childOf scroll

        rootNode.forAllChildren { node, depth ->
            while (depth >= componentMatrix.size)
                componentMatrix.add(mutableListOf())
            componentMatrix[depth].add(node)

            node.component childOf scroll
        }
    }

    override fun draw(matrixStack: UMatrixStack) {
        beforeDrawCompat(matrixStack)

        if (!layedOut) {
            rootNode.layoutChildren(style)
            lines = rootNode.collectLines(style.isHorizontal)
            lines.forEach { (p0, p1) ->
                scroll.insertChildAt(p0, 0)
                scroll.insertChildAt(p1, 1)
            }
            layedOut = true
        }

        lines.forEach { style.lineDrawer(it.first, it.second) }

        super.draw(matrixStack)
    }
}

class TreeGraphBuilder(val root: TreeGraphNode) {
    fun add(node: TreeGraphNode) {
        root.children.add(node)
    }
}
