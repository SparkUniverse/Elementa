package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.inspector.Inspector
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.PixelConstraint
import gg.essential.elementa.constraints.SuperConstraint
import gg.essential.elementa.constraints.XConstraint
import gg.essential.elementa.constraints.YConstraint
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import gg.essential.elementa.dsl.boundTo
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.utils.ObservableAddEvent
import gg.essential.elementa.utils.ObservableClearEvent
import gg.essential.elementa.utils.ObservableListEvent
import gg.essential.elementa.utils.ObservableRemoveEvent
import gg.essential.elementa.utils.elementaDev
import gg.essential.elementa.unstable.common.Spacer
import java.awt.Color

@Suppress("FunctionName")
fun TransparentBlock() = UIBlock(Color(0, 0, 0, 0))

fun LayoutScope.spacer(width: Float, height: Float) = Spacer(width = width.pixels, height = height.pixels)()
fun LayoutScope.spacer(width: Float, _desc: WidthDesc = Desc) = spacer(width, 0f)
fun LayoutScope.spacer(height: Float, _desc: HeightDesc = Desc) = spacer(0f, height)
fun LayoutScope.spacer(width: UIComponent, height: UIComponent) = Spacer(100.percent boundTo width, 100.percent boundTo height)()
fun LayoutScope.spacer(width: UIComponent, _desc: WidthDesc = Desc) = Spacer(100.percent boundTo width, 0f.pixels)()
fun LayoutScope.spacer(height: UIComponent, _desc: HeightDesc = Desc) = Spacer(0f.pixels, 100.percent boundTo height)()

sealed interface WidthDesc
sealed interface HeightDesc
private object Desc : WidthDesc, HeightDesc

@Suppress("unused")
private val init = run {
    Inspector.registerComponentFactory(null)
}

// How is this not in the stdlib?
internal inline fun <T> Iterable<T>.sumOf(selector: (T) -> Float): Float {
    var sum = 0f
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

fun UIComponent.automaticComponentName(default: String) {
    if (!elementaDev) return

    componentName = Throwable().stackTrace
        .asSequence()
        .filterNot { it.lineNumber == 1 } // synthetic accessor methods
        .filterNot { it.methodName.endsWith("\$default") } // synthetic Kotlin defaults methods
        .map { it.methodName }
        .distinct() // collapse overloads
        .drop(1) // "automaticComponentName"
        .drop(1) // caller method (e.g. "box")
        .firstOrNull()
        ?.takeUnless { it == "invoke" } // anonymous component (the `block` of `LayoutScope.invoke`)
        ?.takeUnless { it == "<init>" } // anonymous component (likely direct child of a class component)
        ?: default
}

private class DefaultAlignmentConstraint(private val alignment: Alignment) : XConstraint, YConstraint {
    override fun getXPositionImpl(component: UIComponent): Float =
        component.parent.getLeft() + alignment.align(component.parent.getWidth(), component.getWidth())

    override fun getYPositionImpl(component: UIComponent): Float =
        component.parent.getTop() + alignment.align(component.parent.getHeight(), component.getHeight())

    override var cachedValue: Float = 0f
    override var recalculate: Boolean = true
    override var constrainTo: UIComponent?
        get() = null
        set(_) = throw UnsupportedOperationException()

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {}
}

fun UIComponent.setDefaultChildAlignment(x: Alignment = Alignment.Center, y: Alignment = Alignment.Center) {
    fun SuperConstraint<Float>.isDefault(): Boolean =
        this is DefaultAlignmentConstraint || this is PixelConstraint && value == 0f && !alignOpposite && !alignOutside

    fun conformChild(child: UIComponent) {
        if (child.constraints.x.isDefault()) child.setX(DefaultAlignmentConstraint(x))
        if (child.constraints.y.isDefault()) child.setY(DefaultAlignmentConstraint(y))
    }

    children.forEach(::conformChild)
    children.addObserver { _, arg ->
        @Suppress("UNCHECKED_CAST")
        when (val event = arg as? ObservableListEvent<UIComponent> ?: return@addObserver) {
            is ObservableAddEvent -> conformChild(event.element.value)
            is ObservableRemoveEvent -> {}
            is ObservableClearEvent -> {}
        }
    }
}
