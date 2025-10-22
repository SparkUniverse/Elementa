package gg.essential.elementa.components.inspector

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.TreeNode
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.constraints.TextAspectConstraint
import gg.essential.elementa.dsl.toConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import java.awt.Color

class InspectorNode(private val inspector: Inspector, val targetComponent: UIComponent) : TreeNode() {
    private val componentClassName = targetComponent.javaClass.simpleName.ifEmpty { "UnknownType" }
    private val componentDisplayName = targetComponent.componentName.let { if (it == componentClassName) it else "$componentClassName: $it" }

    private var selectedSource = targetComponent.primarySource
    internal var selectedSourceIndex: Int = targetComponent.filteredSource?.indexOf(selectedSource) ?: 0
        set(value) {
            field = value
            selectedSource = targetComponent.filteredSource?.get(value)
        }

    private val component: UIComponent = object : UIBlock(Color(0, 0, 0, 0)) {
        private val text = UIText(componentDisplayName).constrain {
            width = TextAspectConstraint()
        } childOf this

        override fun draw(matrixStack: UMatrixStack) {
            super.draw(matrixStack)

            val isCurrentlyHidden = targetComponent.parent != targetComponent && !targetComponent.parent.children.contains(
                targetComponent
            )
            val file = selectedSource?.let { " §7${it.fileName ?: it.className.substringAfterLast(".")}:${it.lineNumber}" } ?: ""
            val hidden = if (isCurrentlyHidden) " §7§o(Hidden)" else ""
            text.setText("§r$componentDisplayName$file$hidden")
        }
    }.constrain {
        x = SiblingConstraint()
        y = 2.pixels()
        width = ChildBasedSizeConstraint()
        height = ChildBasedSizeConstraint()
    }.onMouseClick { event ->
        event.stopImmediatePropagation()
        toggleSelection()
    }.onMouseScroll { event ->
        if (!UKeyboard.isShiftKeyDown()) return@onMouseScroll
        event.stopImmediatePropagation()
        inspector.scrollSource(this@InspectorNode, event.delta > 0)
    }

    internal fun toggleSelection() {
        inspector.selectedNode?.component?.setColor(Color(0, 0, 0, 0).toConstraint())

        if (inspector.selectedNode == this@InspectorNode) {
            inspector.setSelectedNode(null)
        } else {
            inspector.setSelectedNode(this@InspectorNode)
            component.setColor(Color(32, 78, 138).toConstraint())
        }
    }

    override fun getArrowComponent() = ArrowComponent(targetComponent.children.isEmpty())

    override fun getPrimaryComponent() = component
}
