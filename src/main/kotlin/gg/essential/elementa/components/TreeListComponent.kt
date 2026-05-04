package gg.essential.elementa.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UKeyboard
import kotlin.math.max

abstract class TreeArrowComponent : UIComponent() {
    abstract fun open()
    abstract fun close()
}

open class TreeListComponent(roots: List<TreeNode>) : UIContainer() {
    constructor(root: TreeNode) : this(listOf(root))

    constructor() : this(emptyList())

    init {
        constrain {
            width = ChildBasedMaxSizeConstraint()
            height = ChildBasedSizeConstraint()
        }

        setRoots(roots)
    }

    fun setRoots(roots: List<TreeNode>) = apply {
        clearChildren()
        roots.forEach { it.displayComponent childOf this }
    }
}

abstract class TreeNode {
    open var indentationOffset: Float = 10f

    private var displayComponentBacker: TreeNodeComponent? = null

    val displayComponent: TreeNodeComponent
        get() {
            if (displayComponentBacker == null)
                displayComponentBacker = TreeNodeComponent()
            return displayComponentBacker!!
        }

    abstract fun getArrowComponent(): TreeArrowComponent

    abstract fun getPrimaryComponent(): UIComponent

    fun addChild(node: TreeNode) = apply {
        displayComponent.addNodeChild(node.displayComponent)
    }

    fun addChildAt(index: Int, node: TreeNode) = apply {
        displayComponent.addNodeChildAt(index, node.displayComponent)
    }

    fun removeChildAt(index: Int) = apply {
        displayComponent.removeNodeChildAt(index)
    }

    fun clearChildren() = apply {
        displayComponent.clearNodeChildren()
    }

    fun withChildren(childBuilder: TreeNodeBuilder.() -> Unit): TreeNode {
        val builder = TreeNodeBuilder(this)
        builder.apply(childBuilder)
        return builder.root
    }

    inner class TreeNodeComponent : UIContainer() {
        private val node: TreeNode = this@TreeNode
        private val arrowComponent = getArrowComponent()
        internal var opened = false
            set(value) {
                if (value == field) {
                    return
                }
                field = value
                if (value) {
                    arrowComponent.open()
                    childContainer.unhide()
                } else {
                    arrowComponent.close()
                    childContainer.hide()
                    close()
                }
            }
        private val childContainer = UIContainer().constrain {
            x = indentationOffset.pixels()
            y = SiblingConstraint()
            width = ChildBasedMaxSizeConstraint()
            height = ChildBasedSizeConstraint()
        }
        private val ownContent = UIContainer().constrain {
            width = ChildBasedSizeConstraint()
            height = ChildBasedMaxSizeConstraint()
        }

        init {
            constrain {
                y = SiblingConstraint()
                width = basicWidthConstraint {
                    max(ownContent.getWidth(), childContainer.getWidth() + indentationOffset)
                }
                height = ChildBasedSizeConstraint()
            }

            ownContent childOf this
            arrowComponent childOf ownContent

            UIContainer().constrain {
                x = SiblingConstraint()
                width = 5.pixels()
            } childOf ownContent

            getPrimaryComponent() childOf ownContent
            childContainer childOf this
            childContainer.hide(instantly = true)

            arrowComponent.onMouseClick { event ->
                event.stopImmediatePropagation()
                @Suppress("DEPRECATION") //TODO pass modifiers when they become available, see [ElementaVersion.V12]
                val isRecursive = UKeyboard.isShiftKeyDown()

                if (opened) {
                    close(isRecursive)
                } else {
                    open(isRecursive)
                }
            }
        }

        fun open(isRecursive: Boolean = false) {
            opened = true
            if (isRecursive)
                recursivelyApplyToChildNodes(this@TreeNodeComponent, TreeNodeComponent::open)
        }

        fun close(isRecursive: Boolean = false) {
            opened = false
            if (isRecursive)
                recursivelyApplyToChildNodes(this@TreeNodeComponent, TreeNodeComponent::close)
        }

        fun addNodeChild(component: UIComponent) = apply {
            childContainer.addChild(component)
        }

        fun addNodeChildAt(index: Int, component: UIComponent) = apply {
            childContainer.insertChildAt(component, index)
        }

        fun removeNodeChildAt(index: Int) = apply {
            childContainer.children.removeAt(index)
        }

        fun clearNodeChildren() = apply {
            childContainer.clearChildren()
        }

        private fun recursivelyApplyToChildNodes(node: TreeNodeComponent, method: (TreeNodeComponent) -> Unit) {
            node.childContainer.children.filterIsInstance<TreeNodeComponent>().forEach {
                method(it)
                recursivelyApplyToChildNodes(it, method)
            }
        }

        internal val childNodes
            get() = childContainer.children.mapNotNull { (it as? TreeNodeComponent)?.node }
    }
}

class TreeNodeBuilder(val root: TreeNode) {
    fun add(node: TreeNode) {
        root.addChild(node)
    }
}
