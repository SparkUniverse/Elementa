package gg.essential.elementa.unstable.layoutdsl

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.components.inspector.Inspector
import gg.essential.elementa.unstable.state.v2.MutableState
import gg.essential.elementa.unstable.state.v2.mutableStateOf
import gg.essential.universal.UMatrixStack

/**
 * Lazily initializes the inner scope by first only placing a [box] as described by [modifier] without any children and
 * only initializing the inner scope once that box has been rendered once.
 *
 * This should be a last reserve for initializing a large list of poorly optimized components, not a common shortcut to
 * "make it not lag". Properly profiling and fixing initialization performance issues should always be preferred.
 */
fun LayoutScope.lazyBox(modifier: Modifier = Modifier.fillParent(), block: LayoutScope.() -> Unit) {
    val initialized = mutableStateOf(false)
    box(modifier) {
        if_(initialized, cache = false /** don't need it; once initialized, we are never going back */) {
            block()
        } `else` {
            LazyComponent(initialized)(Modifier.fillParent())
        }
    }.apply {
        automaticComponentName("lazyBox")
    }
}

private class LazyComponent(private val initialized: MutableState<Boolean>) : UIContainer() {
    override fun draw(matrixStack: UMatrixStack) {
        super.draw(matrixStack)

        Window.enqueueRenderOperation {
            initialized.set(true)
        }
    }
}

@Suppress("unused")
private val init = run {
    Inspector.registerComponentFactory(null)
}
