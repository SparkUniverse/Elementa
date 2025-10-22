package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.inspector.Inspector
import gg.essential.elementa.dsl.boundTo
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.effects.Effect
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

fun UIComponent.getChildModifier() =
    effects
        .filterIsInstance<ChildModifierMarker>()
        .map { it.childModifier }
        .reduceOrNull { acc, it -> acc then it }
        ?: Modifier

fun UIComponent.addChildModifier(modifier: Modifier) {
    enableEffect(ChildModifierMarker(modifier))
}

// Serves as a marker only. FIXME: integrate directly into the component class when we transition this DSL to Elementa?
private class ChildModifierMarker(val childModifier: Modifier) : Effect()