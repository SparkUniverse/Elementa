package gg.essential.elementa.components.inspector

import gg.essential.elementa.UIComponent
import gg.essential.elementa.UIConstraints
import gg.essential.elementa.components.TreeNode
import gg.essential.elementa.components.TreeListComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.*
import gg.essential.elementa.constraints.animation.*
import gg.essential.elementa.constraints.debug.CycleSafeConstraintDebugger
import gg.essential.elementa.constraints.debug.withDebugger
import gg.essential.elementa.dsl.*
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.universal.UMatrixStack
import java.awt.Color

class InfoBlock(private val inspector: Inspector) : UIContainer() {
    private var cachedComponent: UIComponent? = null

    private val tabContainer = UIContainer().constrain {
        width = ChildBasedSizeConstraint() + 15.pixels()
        height = ChildBasedMaxSizeConstraint() + 10.pixels()
    } childOf this

    private val contentContainer = UIContainer().constrain {
        y = SiblingConstraint()
        width = ChildBasedSizeConstraint()
        height = ChildBasedSizeConstraint()
    } childOf this

    private val constraintsContent = TreeListComponent().constrain {
        x = 5.pixels()
        y = 5.pixels()
        width = ChildBasedMaxSizeConstraint()
        height = ChildBasedSizeConstraint()
    } childOf contentContainer

    private val valuesContent = UIContainer().constrain {
        x = 20.pixels()
        y = 5.pixels()
        width = ChildBasedMaxSizeConstraint()
        height = ChildBasedSizeConstraint()
    }

    private lateinit var constraintsText: UIText
    private lateinit var valuesText: UIText

    private val xValueText: UIText
    private val yValueText: UIText
    private val widthValueText: UIText
    private val heightValueText: UIText
    private val radiusValueText: UIText
    private val textScaleValueText: UIText
    private val colorValueText: UIText

    private var constraintsSelected = true
    private val currentRoots = mutableMapOf<ConstraintType, TreeNode?>()

    init {
        constraintsText = UIText("Constraints").constrain {
            x = 5.pixels()
            y = 5.pixels()
            width = TextAspectConstraint()
            color = Color.WHITE.toConstraint()
        }.onMouseEnter {
            if (!constraintsSelected) {
                constraintsText.animate {
                    setColorAnimation(Animations.OUT_EXP, 0.5f, Color.WHITE.toConstraint())
                }
            }
        }.onMouseLeave {
            if (!constraintsSelected) {
                constraintsText.animate {
                    setColorAnimation(Animations.OUT_EXP, 0.5f, Color(255, 255, 255, 102).toConstraint())
                }
            }
        }.onMouseClick {
            if (!constraintsSelected) {
                constraintsSelected = true
                contentContainer.removeChild(valuesContent)
                contentContainer.addChild(constraintsContent)
                valuesText.animate {
                    setColorAnimation(Animations.OUT_EXP, 0.5f, Color(255, 255, 255, 102).toConstraint())
                }
            }
        } as UIText childOf tabContainer

        valuesText = UIText("Values").constrain {
            x = SiblingConstraint(10f)
            y = 5.pixels()
            width = TextAspectConstraint()
            color = Color(255, 255, 255, 102).toConstraint()
        }.onMouseEnter {
            if (constraintsSelected) {
                valuesText.animate {
                    setColorAnimation(Animations.OUT_EXP, 0.5f, Color.WHITE.toConstraint())
                }
            }
        }.onMouseLeave {
            if (constraintsSelected) {
                valuesText.animate {
                    setColorAnimation(Animations.OUT_EXP, 0.5f, Color(255, 255, 255, 102).toConstraint())
                }
            }
        }.onMouseClick {
            if (constraintsSelected) {
                constraintsSelected = false
                contentContainer.removeChild(constraintsContent)
                contentContainer.addChild(valuesContent)
                constraintsText.animate {
                    setColorAnimation(Animations.OUT_EXP, 0.5f, Color(255, 255, 255, 102).toConstraint())
                }
            }
        } as UIText childOf tabContainer

        xValueText = UIText("0")
        yValueText = UIText("0")
        widthValueText = UIText("0")
        heightValueText = UIText("0")
        radiusValueText = UIText("0")
        textScaleValueText = UIText("0")
        colorValueText = UIText("Color(255, 255, 255)")

        initializeText("x", xValueText) childOf valuesContent
        initializeText("y", yValueText) childOf valuesContent
        initializeText("width", widthValueText) childOf valuesContent
        initializeText("height", heightValueText) childOf valuesContent
        initializeText("radius", radiusValueText) childOf valuesContent
        initializeText("textScale", textScaleValueText) childOf valuesContent
        initializeText("color", colorValueText) childOf valuesContent
    }

    private fun initializeText(name: String, valueText: UIText): UIContainer {
        val container = UIContainer().constrain {
            y = SiblingConstraint()
            width = ChildBasedSizeConstraint()
            height = ChildBasedMaxSizeConstraint() + 3.pixels()
        }

        UIText("$name: ").constrain {
            width = TextAspectConstraint()
        } childOf container

        valueText.constrain {
            x = SiblingConstraint()
            width = TextAspectConstraint()
        } childOf container

        return container
    }

    private fun setNewConstraints(constraints: UIConstraints) {
        setConstraintNodes(constraints)

        constraints.addObserver { _, arg ->
            if (arg !is ConstraintType)
                return@addObserver
            val constraint = when (arg) {
                ConstraintType.X -> constraints.x
                ConstraintType.Y -> constraints.y
                ConstraintType.WIDTH -> constraints.width
                ConstraintType.HEIGHT -> constraints.height
                ConstraintType.RADIUS -> constraints.radius
                ConstraintType.COLOR -> constraints.color
                ConstraintType.TEXT_SCALE -> constraints.textScale
                ConstraintType.FONT_PROVIDER -> constraints.fontProvider
            }

            when (arg) {
                ConstraintType.COLOR -> {
                    currentRoots[arg] =
                        if (constraint !is ConstantColorConstraint || constraint.color != Color.WHITE) getNodeFromConstraint(
                            constraint,
                            arg.prettyName
                        ) else null
                }
                ConstraintType.TEXT_SCALE -> {
                    currentRoots[ConstraintType.TEXT_SCALE] =
                        if (constraint !is PixelConstraint || constraint.value != 1f) getNodeFromConstraint(
                            constraint,
                            arg.prettyName
                        ) else null
                }
                else -> {
                    currentRoots[arg] =
                        if (constraint !is PixelConstraint || constraint.value != 0f) getNodeFromConstraint(
                            constraint,
                            arg.prettyName
                        ) else null
                }
            }

            constraintsContent.setRoots(currentRoots.values.filterNotNull())
        }
    }

    private fun setConstraintNodes(constraints: UIConstraints) {
        currentRoots.clear()

        listOf(
            constraints.x to ConstraintType.X,
            constraints.y to ConstraintType.Y,
            constraints.width to ConstraintType.WIDTH,
            constraints.height to ConstraintType.HEIGHT,
            constraints.radius to ConstraintType.RADIUS
        ).forEach { (constraint, type) ->
            currentRoots[type] =
                if (constraint !is PixelConstraint || constraint.value != 0f) getNodeFromConstraint(
                    constraint,
                    type.prettyName
                ) else null
        }

        constraints.textScale.also {
            currentRoots[ConstraintType.TEXT_SCALE] =
                if (it !is PixelConstraint || it.value != 1f) getNodeFromConstraint(it, "TextScale") else null
        }

        constraints.color.also {
            currentRoots[ConstraintType.COLOR] =
                if (it !is ConstantColorConstraint || it.color != Color.WHITE) getNodeFromConstraint(
                    it,
                    "Color"
                ) else null
        }

        constraintsContent.setRoots(currentRoots.values.filterNotNull())
    }

    private fun getNodeFromConstraint(constraint: SuperConstraint<*>, name: String? = null): TreeNode {
        val baseInfoNode = InfoBlockNode(constraint, name)

        return when (constraint) {
            is AdditiveConstraint -> baseInfoNode.withChildren {
                add(getNodeFromConstraint(constraint.constraint1))
                add(getNodeFromConstraint(constraint.constraint2))
            }
            is CoerceAtMostConstraint -> baseInfoNode.withChildren {
                add(getNodeFromConstraint(constraint.constraint))
                add(getNodeFromConstraint(constraint.maxConstraint))
            }
            is CoerceAtLeastConstraint -> baseInfoNode.withChildren {
                add(getNodeFromConstraint(constraint.constraint))
                add(getNodeFromConstraint(constraint.minConstraint))
            }
            is SubtractiveConstraint -> baseInfoNode.withChildren {
                add(getNodeFromConstraint(constraint.constraint1))
                add(getNodeFromConstraint(constraint.constraint2))
            }
            is XAnimationComponent -> baseInfoNode.withChildren {
                add(getNodeFromConstraint(constraint.oldConstraint, name = "From"))
                add(getNodeFromConstraint(constraint.newConstraint, name = "To"))
            }
            is YAnimationComponent -> baseInfoNode.withChildren {
                add(getNodeFromConstraint(constraint.oldConstraint, name = "From"))
                add(getNodeFromConstraint(constraint.newConstraint, name = "To"))
            }
            is WidthAnimationComponent -> baseInfoNode.withChildren {
                add(getNodeFromConstraint(constraint.oldConstraint, name = "From"))
                add(getNodeFromConstraint(constraint.newConstraint, name = "To"))
            }
            is HeightAnimationComponent -> baseInfoNode.withChildren {
                add(getNodeFromConstraint(constraint.oldConstraint, name = "From"))
                add(getNodeFromConstraint(constraint.newConstraint, name = "To"))
            }
            is RadiusAnimationComponent -> baseInfoNode.withChildren {
                add(getNodeFromConstraint(constraint.oldConstraint, name = "From"))
                add(getNodeFromConstraint(constraint.newConstraint, name = "To"))
            }
            is ColorAnimationComponent -> baseInfoNode.withChildren {
                add(getNodeFromConstraint(constraint.oldConstraint, name = "From"))
                add(getNodeFromConstraint(constraint.newConstraint, name = "To"))
            }
            else -> baseInfoNode
        }
    }

    override fun extractComponent(extractor: ElementaExtractor) {
        update()
    }

    @Deprecated(
        "`draw`-style rendering is deprecated. Override `extractComponent` instead. Call `extract` to extract this component, its effects, and its children.",
        replaceWith = ReplaceWith("extract(extractor)")
    )
    @Suppress("DEPRECATION")
    override fun draw(matrixStack: UMatrixStack) {
        super.draw(matrixStack)
        update()
    }

    private fun update() {
        if (cachedComponent != inspector.selectedNode?.targetComponent) {
            cachedComponent = inspector.selectedNode?.targetComponent
            cachedComponent?.let {
                setNewConstraints(it.constraints)
                it.addObserver { _, arg ->
                    if (arg is UIConstraints) {
                        setNewConstraints(arg)
                    }
                }
            }
        }

        if (!constraintsSelected && cachedComponent != null) withDebugger(CycleSafeConstraintDebugger()) {
            xValueText.setText("%.2f".format(cachedComponent!!.getLeft()))
            yValueText.setText("%.2f".format(cachedComponent!!.getTop()))
            widthValueText.setText("%.2f".format(cachedComponent!!.getWidth()))
            heightValueText.setText("%.2f".format(cachedComponent!!.getHeight()))
            radiusValueText.setText("%.2f".format(cachedComponent!!.getRadius()))
            textScaleValueText.setText("%.2f".format(cachedComponent!!.getTextScale()))

            val color = cachedComponent!!.getColor()
            colorValueText.setText(if (color.alpha == 255) {
                "Color(%d, %d, %d)".format(color.red, color.green, color.blue)
            } else {
                "Color(%d, %d, %d, %d)".format(color.red, color.green, color.blue, color.alpha)
            })
        }
    }
}
