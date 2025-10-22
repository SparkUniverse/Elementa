package gg.essential.elementa

import gg.essential.elementa.components.UIText
import gg.essential.elementa.components.UIWrappedText
import gg.essential.elementa.components.UpdateFunc
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.SuperConstraint
import gg.essential.elementa.constraints.animation.AnimationComponent
import gg.essential.elementa.effects.Effect
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState

/**
 * Sometimes it is necessary or desirable to introduce breaking behavioral changes to Elementa. In order to maintain
 * full backwards compatibility in these cases, library consumers must explicitly opt-in to such changes for their
 * [gg.essential.elementa.components.Window]s. This allows Elementa to evolve without breaking mods which rely on old,
 * suboptimal behavior.
 *
 * This opt-in, if supplied to [gg.essential.elementa.components.Window]'s constructor, will only be active during the
 * Window's draw call (or any other affected methods, if extended by a future version).
 * To opt-in to the new behavior outside of these methods, you may use [enableFor].
 */
enum class ElementaVersion {

    /**
     * The initial version of Elementa. This is the default behavior if no opt-in is active.
     */
    @Deprecated(DEPRECATION_MESSAGE)
    V0,

    /**
     * [gg.essential.elementa.components.UIBlock.drawBlock] and the similar methods all starting with `drawBlock` will
     * now always render the block with depth testing enabled and set to always pass. This will result in them always
     * writing their depth to the depth buffer and allows components rendered in front to not be unexpectedly influenced
     * by elements rendered behind them.
     * Additionally they will always reset the depth test state to disabled and the depth test function to LEQUAL before
     * returning (this matches the default state during GUI rendering but may be important in some special use cases).
     */
    @Deprecated(DEPRECATION_MESSAGE)
    V1,

    /**
     * This Elementa version improves the behavior of mouse input in three ways
     *
     * 1. In Minecraft versions <=1.12.2, the game calculates the position of the mouse input in an integer context
     * relative to scaled pixels. However, Elementa uses real pixels to determine the position of components leading
     * to situations where certain components or parts of components are not clickable due the game truncating the fractional part.
     * This Elementa version improves this behavior by restoring the fractional component of mouse clicks to the mouse positions
     * in [gg.essential.elementa.WindowScreen.onMouseClicked] if it is not already present
     *
     * 2. Minecraft mouse click input is positioned in the top left corner of a pixel. As a result, the left and top pixel of
     * a component do not register clicks and components with a width or height of 1 are also not clickable. This Elementa version
     * improves this behavior by offsetting mouse coordinates to the center of the real pixel.
     * In particular, [gg.essential.elementa.components.Window.mouseClick] will add half a real pixel to the passed
     * coordinates and [gg.essential.elementa.UIComponent.getMousePosition] will add half a real pixel to the returned
     * coordinates.
     * As a result, if you have previously relied on the exact distance between a click and a component, beware that
     * this value will now be off by up to half a real pixel compared to what it used to be. If required, you can get
     * back the original real-pixel-aligned value by rounding down to the nearest real pixel via
     * [gg.essential.elementa.utils.guiHint].
     * E.g. if the user clicks on the MC pixel at 3/4 with their GUI scale set to 2, the click used to be processed at
     * 3.0/4.0, but with this change it will appear at 3.25/4.25 (because 0.25 is half a real pixel at scale 2).
     *
     * 3. [gg.essential.elementa.components.Window] will now call the new [gg.essential.elementa.UIComponent.dragMouse]
     * override (the one using Float) instead of the old one (using Int). This allows the drag listeners to receive
     * high quality mouse coordinates (including the two changes above) but it may be breaking if you rely on an
     * override of that method. If you do, then you should switch to using the new override at the same time as you
     * upgrade to the new version (or override both if you need to maintain support for old versions).
     */
    @Deprecated(DEPRECATION_MESSAGE)
    V2,

    /**
     * When there are multiple [gg.essential.elementa.effects.Effect] applied to a single component, their [gg.essential.elementa.effects.Effect.afterDraw]
     * are now called in reverse order, such that they form a stack around the draw itself:
     * `effectA.beforeDraw effectB.beforeDraw component.draw effectB.afterDraw effectA.afterDraw`
     *
     * Prior versions called `effectA.afterDraw` before `effectB.afterDraw` which could result in inproper cleanup when
     * both effects modify the same thing.
     */
    @Deprecated(DEPRECATION_MESSAGE)
    V3,

    /**
     * Mark components as initialized and call [gg.essential.elementa.UIComponent.afterInitialization] during `beforeDraw` instead of `draw`.
     *
     * This ensures that [gg.essential.elementa.effects.Effect.setup] is always called before [gg.essential.elementa.effects.Effect.beforeDraw].
     * On prior versions, calling [gg.essential.elementa.UIComponent.enableEffect] on a component that wasn't yet initialized would result
     * in the Effect's `beforeDraw` being called once before `setup`.
     */
    @Deprecated(DEPRECATION_MESSAGE)
    V4,

    /**
     * Change the behavior of scroll components to no longer require holding down shift when horizontal is the only possible scrolling direction.
     */
    @Deprecated(DEPRECATION_MESSAGE)
    V5,

    /**
     * [gg.essential.elementa.components.ScrollComponent] now has a minimum size for scrollbar grips.
     */
    @Deprecated(DEPRECATION_MESSAGE)
    V6,

    /**
     * [gg.essential.elementa.components.Window] now disables input events if an error has occurred during drawing.
     */
    @Deprecated(DEPRECATION_MESSAGE)
    V7,

    /**
     * The [animationFrame][UIComponent.animationFrame] methods are now deprecated and will no longer be called at all
     * for [constraints][SuperConstraint.animationFrame] or if your override is marked as [Deprecated].
     * The relative order in which various things ([UpdateFunc]s, constraint cache invalidation, [UIComponent] timers
     * and field animations, [UIComponent.animationFrame], and [Effect.animationFrame]) will be called has changed
     * because constraint cache invalidation is separate now and [UpdateFunc]s are used internally for timers and field
     * animations now.
     *
     * All custom constraints which currently rely on `animationFrame` must be updated to support the new
     * `animationTime` mechanism described below before this version can be enabled!
     *
     * All custom components and effects which override `animationFrame` should be updated to use the [UpdateFunc] API
     * instead, and some may also require updates to account for the change in relative order mentioned above.
     * Note however that both the UpdateFunc mechanism and the animationTime properties are both available on any
     * [ElementaVersion], so most (if not all) of your components can migrate to them even before opting to enable
     * this [ElementaVersion].
     *
     * If your custom component or effect needs to update some animation or other miscellaneous state before each frame,
     * use the [UpdateFunc] mechanism instead (via [UIComponent.addUpdateFunc]/[Effect.addUpdateFunc]).
     * This way, only components which actually have something that needs updating will need to be called each frame.
     *
     * If your custom component or effect needs to continue to support older [ElementaVersion]s, ideally mark your
     * `animationFrame` override as [Deprecated], which will allow Elementa to no longer call it on newer versions.
     * If it is not annotated, Elementa will continue to call it and pay the corresponding performance penalty to do so.
     *
     * If your custom constraint is animated, use [Window.animationTimeNs]/[animationTimeMs][Window.animationTimeMs]
     * to drive that animation instead.
     *
     * You no longer need to call [SuperConstraint.animationFrame] to cause the cached value in a constraint to be
     * recomputed each frame. Constraints will now automatically register themselves with the [Window] they are
     * evaluated on, so it can invalidate them automatically via [Window.invalidateCachedConstraints].
     * This can be done manually any number of times during one frame and will be called by default at least twice per
     * frame (once before all update funcs and once after).
     *
     *
     * Additionally, given both new mechanisms are variable time, [Window.animationFPS] is now deprecated and the
     * meaning of any existing `frames` parameter which are used for timing and cannot be renamed without breaking ABI
     * (e.g. [AnimationComponent.elapsedFrames]) is changed to now mean "milliseconds" instead.
     *
     *
     * The main reasons for this change are:
     * - Previously it was not possible to get layout information from a component, then update it depending on that
     *   information and still have that update be reflected in the current frame, because there was no safe way to
     *   invalidate the cached layout information. You would either have to call `animationFrame` and accept some
     *   animations running quicker than intended, or wait until the next frame.
     * - A common beginner mistake was to query layout information during `animationFrame`, during that method however
     *   usually parts of the tree still have the old values cached, so evaluating the layout could result in those old
     *   values being used while computing the new values. Now that the two operations are separate, it is safe to query
     *   the layout during [UpdateFunc]s because `invalidateCachedConstraints` will be called again afterwards.
     *   And if you change the layout in response to your measurements, you can call the method yourself to immediately
     *   make visible those changes to all remaining [UpdateFunc]s too.
     * - Another common mistake was making changes to the component hierarchy from `animationFrame`. Given that method
     *   is called from a trivial tree traversal, making changes to that tree could result in
     *   ConcurrentModificationExceptions (or the custom "Cannot modify children while iterating over them." exception).
     *   The [UpdateFunc] implementation does not suffer from this restriction.
     * - `animationFrame` runs on a fixed update rate, which almost certainly won't match the real frame rate perfectly
     *   and will result in multiple calls per frame (by default 244 times per second), which not only wastes cpu time
     *   but also results in slow motion animations if there isn't enough time for all the calls.
     *   Since we mostly use this for animations, and not physics simulations, using variable rate updates is not really
     *   any more difficult (in some cases it's actually easier) and solves both of these.
     * - `animationFrame` will traverse the entire tree, even if an entire branch has neither things that need regular
     *   updates nor had its constraints evaluated (e.g. because it's off-screen).
     *   The new constraint tracking will only invalidate constraints which were evaluated, and the [UpdateFunc]s
     *   are tracked intelligently at registration, such that no more full tree traversals should be necessary.
     */
    @Deprecated(DEPRECATION_MESSAGE)
    V8,

    /**
     * All Minecraft versions now use [URenderPipeline] instead of modifying global GL state.
     * Additionally, [UIText] and [UIWrappedText] no longer enable (and forget to disable) blending.
     */
    @Deprecated(DEPRECATION_MESSAGE)
    V9,

    /**
     * All components now use [BlendState.ALPHA] instead of [BlendState.NORMAL] and variants.
     * This fixes the alpha channel of the render result, allowing it to be correctly composited with other textures.
     * See [UniversalCraft#105](https://github.com/EssentialGG/UniversalCraft/pull/105) for more details.
     */
    V10,

    ;

    /**
     * Run the given block of code with the provided opt-in.
     * This method may be used to downgrade the version if required.
     *
     * The current opt-in is restored after this method returns.
     * This method is not thread-safe and may only be used from the main thread. This may change in the future.
     */
    inline fun <T> enableFor(block: () -> T): T {
        val prevVersion = active
        active = this
        try {
            return block()
        } finally {
            active = prevVersion
        }
    }

    companion object {

        private const val DEPRECATION_MESSAGE = """This version of Elementa has been deprecated.
It may still be used but its behavior has been determined to be unexpected, suboptimal or broken in same way.
We therefore recommend you opt-in to a newer version.
Be sure to read through all the changes between your current version and your new version to be able to act in case you are affected by them.
"""

        // These are all for comparing with the active version so we do not have magic constants anywhere and can easily
        // see where a certain version has any effects.
        // Deprecation is suppressed because we do want to keep supporting old versions.
        @Suppress("DEPRECATION")
        internal val v0 = V0
        @Suppress("DEPRECATION")
        internal val v1 = V1
        @Suppress("DEPRECATION")
        internal val v2 = V2
        @Suppress("DEPRECATION")
        internal val v3 = V3
        @Suppress("DEPRECATION")
        internal val v4 = V4
        @Suppress("DEPRECATION")
        internal val v5 = V5
        @Suppress("DEPRECATION")
        internal val v6 = V6
        @Suppress("DEPRECATION")
        internal val v7 = V7
        @Suppress("DEPRECATION")
        internal val v8 = V8
        @Suppress("DEPRECATION")
        internal val v9 = V9
        internal val v10 = V10

        internal val atLeastV9Active: Boolean
            get() = active >= v9
        internal val atLeastV10Active: Boolean
            get() = active >= v10

        @PublishedApi
        internal var active: ElementaVersion = v0
    }
}