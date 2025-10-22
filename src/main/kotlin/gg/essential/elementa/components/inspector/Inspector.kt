package gg.essential.elementa.components.inspector

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.*
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.elementa.utils.ObservableAddEvent
import gg.essential.elementa.utils.ObservableClearEvent
import gg.essential.elementa.utils.ObservableRemoveEvent
import gg.essential.elementa.utils.devPropSet
import gg.essential.elementa.utils.elementaDebug
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.DrawCallBuilder
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UBufferBuilder
import java.awt.Color
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.URL
import java.text.NumberFormat

class Inspector @JvmOverloads constructor(
    rootComponent: UIComponent,
    backgroundColor: Color = Color(40, 40, 40),
    outlineColor: Color = Color(20, 20, 20),
    outlineWidth: Float = 2f,
    maxSectionHeight: HeightConstraint? = null
) : UIContainer() {
    private val rootNode = componentToNode(rootComponent)
    private val treeBlock: UIContainer
    private var TreeListComponent: TreeListComponent
    internal val container: UIComponent
    internal var selectedNode: InspectorNode? = null
        private set
    private val infoBlockScroller: ScrollComponent
    private val separator1: UIBlock
    private val separator2: UIBlock

    private var clickPos: Pair<Float, Float>? = null
    private val outlineEffect = OutlineEffect(outlineColor, outlineWidth, drawAfterChildren = true)

    private var isClickSelecting = false

    init {
        constrain {
            width = ChildBasedSizeConstraint()
            height = ChildBasedSizeConstraint()
        }

        isFloating = true

        container = UIBlock(backgroundColor).constrain {
            width = ChildBasedMaxSizeConstraint()
            height = ChildBasedSizeConstraint()
        } effect outlineEffect childOf this

        val titleBlock = UIContainer().constrain {
            x = CenterConstraint()
            width = ChildBasedSizeConstraint() + 30.pixels()
            height = ChildBasedMaxSizeConstraint() + 20.pixels()
        }.onMouseClick {
            clickPos =
                if (it.relativeX < 0 || it.relativeY < 0 || it.relativeX > getWidth() || it.relativeY > getHeight()) {
                    null
                } else {
                    it.relativeX to it.relativeY
                }
        }.onMouseRelease {
            clickPos = null
        }.onMouseDrag { mouseX, mouseY, button ->
            if (clickPos == null)
                return@onMouseDrag

            if (button == 0) {
                this@Inspector.constrain {
                    x = (this@Inspector.getLeft() + mouseX - clickPos!!.first).pixels()
                    y = (this@Inspector.getTop() + mouseY - clickPos!!.second).pixels()
                }
            }
        } childOf container

        val title = UIText("Inspector").constrain {
            x = 10.pixels()
            y = CenterConstraint()
            width = TextAspectConstraint()
            height = 14.pixels()
        } childOf titleBlock

        UIImage.ofResourceCached("/textures/inspector/click.png").constrain {
            x = SiblingConstraint(10f)
            y = CenterConstraint()
            width = AspectConstraint(1f)
            height = 12.pixels
        }.apply {
            textureMinFilter = UIImage.TextureScalingMode.LINEAR
        }.onMouseClick { event ->
            event.stopPropagation()
            isClickSelecting = true

            val rootWindow = Window.of(this)
            rootWindow.clickInterceptor = { mouseX, mouseY, _ ->
                rootWindow.clickInterceptor = null
                isClickSelecting = false

                val targetComponent = getClickSelectTarget(mouseX.toFloat(), mouseY.toFloat())
                if (targetComponent != null) {
                    findAndSelect(targetComponent)
                }
                true
            }
        } childOf titleBlock

        separator1 = UIBlock(outlineColor).constrain {
            y = SiblingConstraint()
            height = 2.pixels()
        } childOf container

        treeBlock = UIContainer().constrain {
            width = ChildBasedSizeConstraint() + 10.pixels()
            height = ChildBasedSizeConstraint() + 10.pixels()
        }

        val treeBlockScroller = ScrollComponent().constrain {
            y = SiblingConstraint()
            width = RelativeConstraint(1f) boundTo treeBlock
            height = RelativeConstraint(1f).boundTo(treeBlock) coerceAtMost (maxSectionHeight
                ?: RelativeWindowConstraint(1 / 3f))
        } childOf container

        treeBlock childOf treeBlockScroller

        TreeListComponent = TreeListComponent(rootNode).constrain {
            x = 5.pixels()
            y = SiblingConstraint() + 5.pixels()
        } childOf treeBlock

        separator2 = UIBlock(outlineColor).constrain {
            y = SiblingConstraint()
            height = 2.pixels()
        }

        val infoBlock = InfoBlock(this).constrain {
            y = SiblingConstraint()
            width = ChildBasedMaxSizeConstraint() + 10.pixels()
            height = ChildBasedSizeConstraint() + 10.pixels()
        }

        infoBlockScroller = ScrollComponent().constrain {
            y = SiblingConstraint()
            width = RelativeConstraint(1f) boundTo infoBlock
            height = RelativeConstraint(1f) boundTo infoBlock coerceAtMost (maxSectionHeight
                ?: RelativeWindowConstraint(1 / 3f))
        }

        infoBlock childOf infoBlockScroller
    }

    private fun componentToNode(component: UIComponent): InspectorNode {
        val node = InspectorNode(this, component).withChildren {
            component.children.forEach {
                if (it != this@Inspector)
                    add(componentToNode(it))
            }
        } as InspectorNode

        component.children.addObserver { _, event ->
            val (index, childComponent) = when (event) {
                is ObservableAddEvent<*> -> event.element
                is ObservableRemoveEvent<*> -> event.element
                is ObservableClearEvent<*> -> {
                    node.clearChildren()
                    return@addObserver
                }
                else -> return@addObserver
            }

            // We do not want to show the inspector itself
            if (childComponent == this) {
                return@addObserver
            }

            // So we also need to offset any indices after it
            val offset = -(0 until index).count { component.children[it] == this }

            when (event) {
                is ObservableAddEvent<*> -> {
                    val childNode = componentToNode(childComponent as UIComponent)
                    node.addChildAt(index + offset, childNode)
                }
                is ObservableRemoveEvent<*> -> node.removeChildAt(index + offset)
            }
        }

        return node
    }

    internal fun setSelectedNode(node: InspectorNode?) {
        if (node == null) {
            container.removeChild(separator2)
            container.removeChild(infoBlockScroller)
        } else if (selectedNode == null) {
            separator2 childOf container
            infoBlockScroller childOf container
        }
        selectedNode = node
        if (node != null) {
            val source = node.targetComponent.primarySource
            if (source != null) {
                node.selectedSourceIndex = node.targetComponent.filteredSource!!.indexOf(source)
                openSourceFile(source)
            }
        }
    }

    init {
        // Workaround for ScrollComponent.actualContent breaking scroll event propagation
        var recursive = false
        onMouseScroll { event ->
            if (recursive) return@onMouseScroll
            recursive = true
            treeBlock.mouseScroll(event.delta)
            recursive = false
        }
    }

    internal fun scrollSource(node: InspectorNode, up: Boolean) {
        val elements = node.targetComponent.filteredSource ?: return
        var index = node.selectedSourceIndex
        index = (index + if (up) 1 else -1).coerceIn(elements.indices)
        node.selectedSourceIndex = index
        openSourceFile(elements[index])
    }

    private fun openSourceFile(frame: StackTraceElement) {
        val folder = frame.className.substringBeforeLast(".").replace(".", "/")
        val file = folder + "/" + frame.fileName
        val line = frame.lineNumber
        try {
            // For docs see https://www.develar.org/idea-rest-api/#api-Platform-file
            // For impl see https://github.com/JetBrains/intellij-community/blob/d9b508478de6d3b5d2e765738d561ead77c97824/plugins/remote-control/src/org/jetbrains/ide/OpenFileHttpService.kt
            val url = URL("$INTELLIJ_REST_API/api/file?file=$file&line=$line&focused=false")
            val conn = url.openConnection()
            // IntelliJ uses Origin/Referrer to determine whether to trust a given request (a JavaScript snippet in your
            // browser could try to request this page too after all!), so we use localhost which is trusted by default.
            // See https://github.com/JetBrains/intellij-community/blob/d9b508478de6d3b5d2e765738d561ead77c97824/platform/built-in-server/src/org/jetbrains/ide/RestService.kt#L272
            // We can't use Origin because Java considers that a restricted header and will just ignore it.
            conn.setRequestProperty("Referer", "http://localhost")
            conn.connect()
            conn.inputStream.skip(Long.MAX_VALUE)
        } catch (ignored: ConnectException) {
        } catch (e: FileNotFoundException) { // HTTP 404
            if (!hintedIntelliJSupport) {
                hintedIntelliJSupport = true
                println("IntelliJ detected! Install JetBrain's `IDE Remote Control` plugin to automatically jump to the source of the selected component.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getClickSelectTarget(mouseX: Float, mouseY: Float): UIComponent? {
        val rootComponent = rootNode.targetComponent
        val hitComponent = (rootComponent as? Window)?.hoveredFloatingComponent?.hitTest(mouseX, mouseY)
            ?: rootComponent.hitTest(mouseX, mouseY)

        return if (hitComponent == this || hitComponent.isChildOf(this)) null
        else hitComponent
    }

    internal fun findAndSelect(component: UIComponent) {
        fun findNodeAndExpandParents(component: UIComponent): InspectorNode? {
            if (component == rootNode.targetComponent) {
                return rootNode
            }
            if (component.parent == component) {
                return null
            }
            val parentNode = findNodeAndExpandParents(component.parent) ?: return null
            val parentDisplay = parentNode.displayComponent
            parentDisplay.opened = true
            return parentDisplay.childNodes.filterIsInstance<InspectorNode>().find { it.targetComponent == component }
        }

        val node = findNodeAndExpandParents(component) ?: return
        if (selectedNode != node) {
            node.toggleSelection()
        }
    }

    override fun draw(matrixStack: UMatrixStack) {
        separator1.setWidth(container.getWidth().pixels())
        separator2.setWidth(container.getWidth().pixels())

        if (isClickSelecting) {
            val (mouseX, mouseY) = getMousePosition()
            getClickSelectTarget(mouseX, mouseY)
        } else {
            selectedNode?.targetComponent
        }?.also { component ->
            val scissors = generateSequence(component) { if (it.parent != it) it.parent else null }
                .flatMap { it.effects.filterIsInstance<ScissorEffect>().asReversed() }
                .toList()
                .reversed()

            val x1 = component.getLeft().toDouble()
            val y1 = component.getTop().toDouble()
            val x2 = component.getRight().toDouble()
            val y2 = component.getBottom().toDouble()

            fun drawQuad(pipeline: URenderPipeline, color: Color, configure: DrawCallBuilder.() -> Unit = {}) {
                val builder = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)
                UIBlock.drawBlock(builder, matrixStack, color, x1, y1, x2, y2)
                builder.build()?.drawAndClose(pipeline, configure)
            }

            // Clear the depth buffer cause we will be using it to draw our outside-of-scissor-bounds block
            // Note that we cannot just use glClear because MC 1.21.5+ no longer has any framebuffer bound by default.
            matrixStack.push()
            matrixStack.translate(0f, 0f, -100f)
            drawQuad(CLEAR_DEPTH_PIPELINE, Color.WHITE) {
                noScissor()
            }
            matrixStack.pop()

            // Draw a highlight on the element respecting its scissor effects
            scissors.forEach { it.beforeDraw(matrixStack) }
            drawQuad(HIGHLIGHT_PIPELINE, Color(129, 212, 250, 100))
            scissors.asReversed().forEach { it.afterDraw(matrixStack) }

            // Then draw another highlight (with depth testing such that we do not overwrite the previous one)
            // which does not respect the scissor effects and thereby indicates where the element is drawn outside of
            // its scissor bounds.
            drawQuad(HIGHLIGHT_PIPELINE, Color(255, 100, 100, 100)) {
                noScissor()
            }
        }

        val debugState = elementaDebug
        elementaDebug = false
        try {
            super.draw(matrixStack)
        } finally {
            elementaDebug = debugState
        }
    }

    companion object {
        internal val percentFormat: NumberFormat = NumberFormat.getPercentInstance()

        private val INTELLIJ_REST_API = System.getProperty("elementa.intellij_rest_api", "http://localhost:63342")
        private var hintedIntelliJSupport = false

        internal val factoryMethods: MutableList<Pair<String, String?>> = mutableListOf()

        fun registerComponentFactory(cls: Class<*>?, method: String? = null) {
            if (!devPropSet) return
            factoryMethods.add(Pair(cls?.name ?: callerClassName(), method))
        }

        private fun callerClassName(): String =
            Throwable().stackTrace.asSequence()
                .filterNot { it.methodName.endsWith("\$default") } // synthetic Kotlin defaults method
                .drop(2) // this method + caller of this method
                .first()
                .className

        private val CLEAR_DEPTH_PIPELINE = URenderPipeline.builderWithDefaultShader(
            "elementa:inspector_clear_depth",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
        ).apply {
            colorMask = Pair(false, false)
            depthTest = URenderPipeline.DepthTest.Always
        }.build()

        private val HIGHLIGHT_PIPELINE = URenderPipeline.builderWithDefaultShader(
            "elementa:inspector_highlight",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
        ).apply {
            blendState = BlendState.ALPHA
            depthTest = URenderPipeline.DepthTest.Less
        }.build()
    }
}
