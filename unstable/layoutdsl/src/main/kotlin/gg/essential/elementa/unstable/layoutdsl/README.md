# Layout DSL

The Layout DSL provides a high-level DSL to declare the overall structure and layout of Elementa components or entire
screens.

Note: This document does not cover the `State` API (in part because State V2 is still being worked on).
      The Layout DSL does make heavy use of it though (at least for anything dynamic). Until someone writes a guide for
      it, here's a one paragraph primer:
      Most of the high-level state of your GUI lives in multiple `MutableState` instances, one per factum. You then
      `map` or `zip` these to derive various other `State`s (like the text in a specific gui label) from them. Most gui
      components as well as the dynamic parts of the Layout DSL can accept `State`s and will then automatically update 
      whenever you change any of your `MutableState`s. There's also special support for `List`s in states (though V1's
      special case for this, ListState, has various footguns), with V2 also for `Set`s in states. And the last thing
      that should be mentioned is `stateBy` for when `map` and `zip` don't cut it (may actually become the new standard
      for V2, still to be decided).
      For something more detailed, take a look at the public members of the main file (and potentially other files) of
      State V2 [1].
      V1 is similar but more messy, see [2] for an overview of differences.
      Though you will still need to look at various existing uses (outside of Elementa where backwards-compatibility
      complicates everything; would recommend the Wardrobe as it's the most recently written one and also uses the
      Layout DSL) to see something real.

[1]: https://github.com/EssentialGG/Elementa/blob/feature/state-v2/src/main/kotlin/gg/essential/elementa/state/v2/state.kt
[2]: https://github.com/EssentialGG/Elementa/pull/88#issue-1347835067

## Motivation

This section explains the main problem(s) the Layout DSL was meant to solve by starting from a regular, old Elementa
example and gradually transforming it to use the Layout DSL instead.
This assumes you have at least a rough idea of how Elementa components and constraints work.

The main issue the Layout DSL tries to solve is that in a regular Elementa screen, which consists of many sub-components
are that usually all declared in a field of the screen class, it is difficult to grasp how all these components relate
to each other without carefully following all `childOf` calls while also keeping an eye on all the constraints at all
times.
And the constraints part is not to be underestimated because the way Elementa constraints are currently declared does
not make it particularly easy to understand the layout of a particular component's children from just glancing at one
child.

Consider for example this relatively simple screen that's just two equally-sized boxes (as big as possible with a fixed
padding) next to each other, the left has a centered text that reads Left, the right one is split vertically with the
center of the top half saying Top and the bottom half saying Bottom:
```
|-----------------------------|
|                             |
| |----------|   |----------| |
| |          |   |          | |
| |          |   |   TOP    | |
| |   LEFT   |   |          | |
| |          |   |  BOTTOM  | |
| |          |   |          | |
| |----------|   |----------| |
|                             |
|-----------------------------|
```

The regular Elementa code for this would look something like this:
```kotlin
val window: Window = WindowScreen()

// This extra wrapper may seem redundant here, the reason we have it will become clear later
val wrapper by UIContainer().constrain {
    width = 100.percent
    height = 100.percent
} childOf window

val content by UIContainer().constrain {
    x = CenterConstraint()
    y = CenterConstraint()
    width = 100.percent - 2.pixels
    height = 100.percent - 2.pixels
} childOf wrapper

val left by UIContainer().constrain {
    width = 50.percent - 1.5.pixels
    height = 100.percent
} childOf content

val right by UIContainer().constrain {
    x = 0.pixels(alignOpposite = true)
    width = 50.percent - 1.5.pixels
    height = 100.percent
} childOf content

val leftText by UIText("Left").constrain {
    x = CenterConstraint()
    y = CenterConstraint()
} childOf left

val top by UIContainer().constrain {
    width = 100.percent
    height = 50.percent
} childOf right

val bottom by UIContainer().constrain {
    y = 0.pixels(alignOpposite = true)
    width = 100.percent
    height = 50.percent
} childOf right

val topText by UIText("Top").constrain {
    x = CenterConstraint()
    y = CenterConstraint()
} childOf top

val bottomText by UIText("Bottom").constrain {
    x = CenterConstraint()
    y = CenterConstraint()
} childOf bottom
```

And this kind of code is **extremely** common since almost everything in most UIs is hierarchical.
But, without the ascii sketch above, it's unreasonably difficult to tell what this will actually look like until you
run it (or, with quite some effort, mentally evaluate it).

It shouldn't be hard to imagine how bad this can get with more complex layouts.
It gets even worse once you start making some things dynamic because then you really need to go searching to find the
parent/children.
And reading the constraints can become quite difficult too because Elementa does not at all force you to actually use
additional wrapper components to define the layout, you could (and frequently it's convenient in the short term) totally
just define the three text components and give them highly complex constraints which compute the same thing.

In the simplest case, layout dsl can at least allow you to more easily understand the parent-child relations.
For this first version, we effectively keep everything from above except for the `childOf` calls which are now handled
by the layout dsl:
```kotlin
window.layout {
    wrapper {
        content {
            left {
                leftText()
            }
            right {
                top {
                    topText()
                }
                bottom {
                    bottomText()
                }
            }
        }
    }
}
```
With that, it is immediately clear now that the top and bottom parts are children of the right side only.
But, if we did not name our components by their direction, it would still be difficult to tell whether things are layed
out vertically, horizontally or some other way. Additionally, things like size and alignment also still require you to
look at the component definitions.

This is where `Modifier`s come in. A modifier expresses a set of configurations/modifications that one wishes to apply
to a given component. There are modifiers for a variety of things like position, size, color, effects, callbacks, etc.
Modifiers can be chained together so you get a single modifier that applies multiple modifications.
There even exist higher-order modifiers that e.g. apply a given modifier only while the component is being hovered.

For now, let's use only the basic ones to exactly replicate the above example, but this time we can also remove the
`constrain` blocks from the original code:
```kotlin
val halfWidth = Modifier.fillWidth(fraction = 0.5f, padding = 1.5f).fillHeight()
window.layout {
    wrapper(Modifier.fillParent()) {
        content(Modifier.alignBoth(Alignment.Center).fillParent(padding = 1f)) {
            left(Modifier.alignHorizontal(Alignment.Start).then(halfWidth)) {
                leftText(Modifier.alignBoth(Alignment.Center))
            }
            right(Modifier.alignHorizontal(Alignment.End).then(halfWidth)) {
                top(Modifier.alignHorizontal(Alignment.Start).fillWidth().fillHeight(0.5f)) {
                    topText(Modifier.alignBoth(Alignment.Center))
                }
                bottom(Modifier.alignHorizontal(Alignment.End).fillWidth().fillHeight(0.5f)) {
                    bottomText(Modifier.alignBoth(Alignment.Center))
                }
            }
        }
    }
}
```

You may notice that while some of these map relatively directly on existing constraints (e.g.
`fillWidth(fraction, padding)` is just `fraction.percent - (padding * 2).pixels`), there are also plenty higher-level
modifiers (e.g. `fillParent` which is both `fillWidth` as well as `fillHeight`) to reduce repetition and make it easier
to understand what a modifier does at first glance.
There are also modifiers that let you set constraints directly (`BasicXModifier`) but these exist only as an escape
hatch and you should ideally never need them.

Ok, so the above is definitely more compact than the original code, and you can kind of tell the general layout if you
look carefully at the modifiers. But we can still do **a lot** better.

For starters, all that is left in the fields at this point are the constructor calls, so it might be tempting to inline
them. And generally there's nothing wrong with this as long as they really are as simple as in the example.
If there's still a lot of component configuration left, for example click handlers, then you'll usually want to keep the
fields as to not blow up the DSL block (remember: it is meant to show the layout, not every last details; and it is
supposed to be easy to grasp as a whole, a 50 line click handler in the middle makes that a lot harder).

```kotlin
// With fields:
bottom(Modifier.alignHorizontal(Alignment.End).fillWidth().fillHeight(0.5f)) {
    bottomText(Modifier.alignBoth(Alignment.Center))
}
// Constructors inlined:
UIComponent()(Modifier.alignHorizontal(Alignment.End).fillWidth().fillHeight(0.5f)) {
    UIText("Bottom")(Modifier.alignBoth(Alignment.Center))
}
// Because text and simple containers are quite common, there exist `box` and `text` methods which will create the
// components with the given modifiers.
box(Modifier.alignHorizontal(Alignment.End).fillWidth().fillHeight(0.5f)) {
    text("Bottom", modifier = Modifier.alignBoth(Alignment.Center))
}
```

Next, notice how there's quite a lot of `align(Center)`?
That's actually quite common and arguably because Elementa has bad default constraints.
Yes, `0.pixels` can make sense some times, but usually, if it is the only child and there is wiggle room in the parent,
you want your components to be centered, you don't want everything slanted to the left.

To remedy this, any children of `box` will automatically be centered by default. You can still overwrite the positioning
by explicitly specifying an alignment as above, but the default is already what you want in quite a lot of cases.

The default for size in Elementa is even worse. You practically never want your component to be `0.pixels` in size, yet
that's what you get by default.
Layout DSL improves that as well: The default size of a `box` is `ChildBasedSizeConstraint()`. It doesn't come up in our
example because it is sized fully top-down but for components that are sized bottom-up, this is a much better default
than the useless `0.pixels`.

Applying this to our example, we get:
```kotlin
val halfWidth = Modifier.fillWidth(fraction = 0.5f, padding = 1.5f).fillHeight()
window.layout {
    box(Modifier.fillParent()) {
        box(Modifier.fillParent(padding = 1f)) {
            box(Modifier.alignHorizontal(Alignment.Start).then(halfWidth)) {
                text("Left")
            }
            box(Modifier.alignHorizontal(Alignment.End).then(halfWidth)) {
                box(Modifier.alignHorizontal(Alignment.Start).fillWidth().fillHeight(0.5f)) {
                    text("Top")
                }
                box(Modifier.alignHorizontal(Alignment.End).fillWidth().fillHeight(0.5f)) {
                    text("Bottom")
                }
            }
        }
    }
}
```

Note: We cannot, at this point, get rid of an `alignBoth` on the outer-most box because its parent is a `Window`, not
      another `box`. This case doesn't actually happen very often in practice because it only happens on the outermost
      Layout DSL layer, and if you're building a component to be used by other code, then that other code is usually the
      one that specifies the position for you.
      This is why we introduced the extra `wrapper` component in our original example, with it being full-size, we don't
      need to align it. And more importantly, everything inside of it can fully use the Layout DSL with no distractions.

The final two observations: There's still quite a lot of `align` happening, and unlike the field names `box` doesn't
really tell us anything about the relative positioning of the components, we have to look at the specific `align` calls.

But there's no reason we can't introduce more methods like `box` with more meaningful names and more defaults. The two
common builtin ones are `row` and `column`:

```kotlin
val halfWidth = Modifier.fillWidth(fraction = 0.5f, padding = 1.5f).fillHeight()
window.layout {
    box(Modifier.fillParent()) {
        row(Modifier.fillParent(padding = 1f), Arrangement.SpaceBetween) {
            box(halfWidth) {
                text("Left")
            }
            column(halfWidth) {
                box(Modifier.fillWidth().fillHeight(0.5f)) {
                    text("Top")
                }
                box(Modifier.fillWidth().fillHeight(0.5f)) {
                    text("Bottom")
                }
            }
        }
    }
}
```

`row` and `column`, unlike `box`, use a new `Arrangement`-based layout system on their primary axis and the already
introduced `Alignment`s in another optional argument as the default for their secondary axis (defaulting to Center if
not specified).

They also have different default sizes, their primary axis being sized by the same `Arrangement` system with their
secondary axis sized by a `ChildBasedMaxSizeConstraint` (i.e. a `row` is as high as its highest child and as wide as all
its children together, plus some additional spacing depending on the arrangement).

## Usage

### LayoutScope

The Layout DSL may be used to lay out the children of any Elementa component.
To use it, simply call the `layout` extension function on the component.

The `layout` function takes an optional `Modifier` to be applied to the component itself as well as a block which, via
its receiver, has access to a `LayoutScope` instance through which it can add children to the component:
```kotlin
val myComponent = UIContainer()
val myChild = UIContainer()
val myInnerChild = UIContainer()

myComponent.layout(Modifier.width(100).height(20)) {
    // Adds `myChild` as a child of `myComponent`
    invoke(myChild)
    // Or, because it's actually an extension function on UIComponent, one could also call it like this:
    myChild.invoke()
    // The name may seem a bit odd, but that's because it's also an operator function,
    // so the normal way to call it is actually just:
    myChild()
    
    // This call may also receive a Modifier to be applied to the child as well as a block that opens another
    // `LayoutScope`, this time for the child:
    myChild(Modifier.fillParent()) {
        // Adds `myInnerChild` as a child of `myChild`
        myInnerChild()
    }
    
    // But `myChild` doesn't have to be declared in a variable outside, it could also be declared inline, though
    // this is usually discouraged if it's more than just a simple constructor call:
    UIContainer()()
    // Note the double `()`: the first one is the constructor `UIContainer` call, the second is the call to `invoke`
    // that adds it as a child and can receive a Modifier and a block that opens another layout scope.
}
```

### Modifier

A `Modifier` expresses a set of configurations/modifications that one wishes to apply to a given component.
There are modifiers for a variety of things like position, size, color, effects, callbacks, etc.

One modifier, unlike a `Constraint`, is also explicitly meant to be re-usable on multiple components, such that a more
complex modifier can be build once, stored in a variable and then re-used for multiple components.

#### DSL

Modifiers have their own mini-DSL that allows them to be easily composed.

You can get an empty modifier that does nothing with simply `Modifier` (syntactically, that's the companion object of
thi `Modifier` interface). You can then call various extension methods on this `Modifier` to add extra modifications
that should happen, like `Modifier.width(100).height(20)`.

So with most modifiers, you don't actually get a `Modifier` instance directly, you merely get a constructor extension
method to tag the modifier onto your existing modifier because that's usually more convenient.

When you do however have two modifiers instances that you want to chain together, you can do so via the `then` method,
like `modifierA.then(modifierB)`. The resulting modifier will first apply all modifications from `modifierA` and then
all modifications from `modifierB`. You can also call `then` as an infix operator, like `modifierA then modifierB`.

Most of the extension methods should simply be defined as `fun Modifier.something() = this then ...`.

#### Sizing modifiers

The two main ways to size component hierarchies is either top-down or bottom-up, i.e. either the parent component has a
fixed size (such as the screen) and its children try to grow as big as there is space, or the child has a fixed size and
the parent tries to shrink as far as possible while still containing the child.

Usually any given screen will make use of both methods and they will meet at some point in the middle, e.g. a button is
sized as big as the text it contains plus some padding but the container in which the button resides in is as big as
possible (and the button may for example be centered within it).

The point at which these meet is also frequently different depending on the axis. E.g. a button may be as high as
required by its text but as wide as its parent permits.

Elementa does not currently allow for both approaches to be applied to the same component at the same time.

##### Fixed size

The `width`/`height` modifiers will assign a fixed size in pixels to a component.

They do have overloads that accept another component and copy its size, though these are rarely used.

Not quite fixed but dependent on neither parent nor children, the `widthAspect`/`heightAspect` modifiers will set the
width/height of a component to a multiple of its height/width.

##### Top-down

The single most common modifier for trying to grow a component as big as its parent permits is `fillParent`.
Its first argument is the fraction it should attempt to grow to (e.g. `0.5` would make it grow to 50% of the parent's
size).
Its second argument is a fixed padding in pixels that it should maintain on each side.

E.g. if the parent is 10 pixels wide, then with `Modifier.fillParent(0.5, 1)` the child will be `0.5 * 10 - 1 * 2 = 3`

Similarly `fillWidth` and `fillHeight` can be used to configure a single axis.

Another, less commonly used but still important modifier is `fillRemainingWidth`/`fillRemainingHeight` which can only
be used by a single child and will cause that child to take up any remaining space in the parent.

##### Bottom-up

The `childBasedWidth`/`childBasedHeight` will size the component to match the total size of its children along the
respective axis.
The `childBasedMaxWidth`/`childBasedMaxHeight` will size the component to match the biggest of its children along the
respective axis.

Both of the above accept an optional `padding` parameter which will add an extra, fixed amount of pixels for each side
to the width/height of this component. That is, the padding is between this component and all its children. Not between
any two children individually: `this.width = padding + sum(child.width) + padding`.

It should be noted that, unless you're using the padding parameter, you usually don't need to explicitly use any of
these because the common `box`, `row`, and `column` containers use them by default.

#### Alignment modifiers

If there is more spaces in your parent than your child needs, you may need to specify how it should be aligned inside
its parent. The `alignHorizontal`/`alignVertical` modifiers will set the `x`/`y` position of the component according to
the given `Alignment`. The `alignBoth` modifier will use the same alignment for both axes.

Note that the common `box` container, as well as the secondary axes of `row` and `column` already use
`Aligntment.Center` by default for all their children, so often you do not need to explicitly set the alignment.
The primary axes of `row` and `column` use the `Arrangement` system for positioning, so Alignment does not apply there.

The three most common alignments are `Start`, `Center` and `End`.

`Start` and `End` can additionally accept an optional `padding` parameter.

`Center` puts the component in the center of its parent aligned to the nearest full MC pixel in the context of its
parent. E.g. if the component is 2 in height and its parent is 5 in height, then this will place the component at one
pixel distance from the top of its parent, and two pixels from the bottom.
This is usually preferred design-wise.
You can get the true center (1.5 in above example) with the `TrueCenter` alignment.

#### Constraint modifiers

There exist `BasicXModifier` where `X` may be replaced with any of the standard constraint types which simply set the
given constraint on the component.

Note that usually you shouldn't need these, there's usually a more high-level modifier or container you can use instead.
These only exist as an escape hatch.

#### Conditional modifiers

Modifiers can be dynamically applied and reverted in response to the value of a `State`.

The main primitive is an overload of `then` that takes a `State<Modifier>` as its argument and applies the modifier in
the State, reverting it and re-applying whenever the State changes.

For the special case of `State<Boolean>` a `whenTrue` modifier exists that applies a given modifier only while the given
state is `true` (and optionally applies a different modifier while it isn't).

#### Event modifiers

There exist modifiers that register a callback on the component for various events such as mouse enter, mouse leave,
left click, etc.

Note that you usually don't want to use the mouse enter/leave callbacks because they are rather coarse, see the Hovering
section instead.

#### Custom modifiers

So what exactly is a modifier? How would I define my own?
Simply put, it's a function that can apply a change to a component, and returns another function to undo the change
again:
```kotlin
interface Modifier {
    fun applyToComponent(component: UIComponent): () -> Unit
}
```

If it is not possible to cleanly undo the change, or if it is difficult to implement and highly unlikely to ever be
used, the undo function may simply throw an `UnsupportedOperationException`.

There even exists an overload of `then` that takes such a function directly, so you can easily define your own modifier
extensions like this:
```kotlin
fun Modifier.something() = this then {
    // The component is passed as the receiver, so you can simply call its methods
    val orgConstraint = constraints.x
    constrain {
        // Do keep in mind that modifiers are supposed to be re-usable, so you need to create a new constraint here
        // every time, you cannot for example re-use a single constraint passed via arguments.
        // That's why the BasicXModifier takes a constraint factory as its argument rather than a single constraint.
        x = 10.pixels
    }

    // And finally return a function that will clean up your change (or throw a NotImplementedError if your modifier
    // can/does not support that)
    {
        constrain {
            x = orgConstraint
        }
    }
}
```

### Containers

While not strictly enforced by Elementa, a component tree is generally built from a whole bunch of arbitrarily nested
containers (tree nodes) with content components (tree leafs) at the bottom.

Most UIs can be broken down into just three types of fundamental container types:
- Simple `row`s that contain multiple children left to right
- Simple `column`s that contain multiple children top to bottom
- Plain `box`es that contain one or more children in no particular layout

Due to how common these are, the Layout DSL has dedicated methods to easily create them and most importantly apply their
layouts in an intuitive way.

```kotlin
window.layout {
    box(Modifier.width(500).height(500)) {
        column {
            row { 
                text("top left")
                text("top right")
            }
            text("*second row*")
        }
    }
}
```

If for some reason you need to refer to one of these at a later point, you can store their return value in a local
variable or field:
```kotlin
val wrapper: UIComponent
val content: UIComponent
window.layout {
    wrapper = box(Modifier.width(500).height(500)) {
        content = column {
            // ...
        }
    }
}
```

#### box

A `box` is a plain container, fairly similar to `UIContainer`.
It does however have different defaults for its size as well as the position of all its children, and it functions as
expected with the `color` modifier (it's more like `UIBlock` in that respect).

By default a `box` will try to match the size of its children. Or rather, child, because if there are multiple things
that should go into the box, it's usually better to wrap those with either `row` or `column`.
`box` is usually only used to add padding or a fixed size and/or background color.

The default position for children of `box` is `Alignment.Center`.

E.g. a button 100x20 with a 1px outline:
```kotlin
box(Modifier.width(100f).height(20f).color(outlineColor)) {
    box(Modifier.fillParent(padding = 1f).color(backgroundColor)) {
        text(label)
    }
}
```

#### row

A `row` is a container fairly similar to `box` except that it is meant to handle multiple children arranged horizontally
in some way.
As such, it can accept not just a modifier but also a horizontal `Arrangement` and a default vertical `Alignment`.

By default a `row` will try to match the height of its tallest child and the width of all its children summed up plus
any padding as specified by the arrangement.
That is, it will try to be as small as it can be, just like all the other common containers.

If a child is less tall than its parent row, i.e. if it could float up and down, it will be positioned vertically
according to the passed `Alignment` (unless a different alignment was applied to the specific child directly).

The way surplus space is distributed on the main, horizontal axis is determined by the `Arrangement`.
See the Arrangement section for more information.

Note: Currently the default Alignment is `spacedBy(0, FloatPosition.Left)`, this may be changed to
      `FloatPosition.Center` in the future.

#### column

See `row` and swap horizontal and vertical.

#### flowContainer

A `flowContainer` acts similar to a `row` except that it expects to be limited in width and will start new rows when
no more items fit into the current one.

The `minSeparation` argument determines the minimal horizontal padding between any two children in the same row.
The `verticalSeparation` argument determines the vertical padding between rows.

Note: This container is likely subject to change in the future because its design wasn't very thought out and it
      currently only serves a single use-case.
      In particular it currently suffers from the following assumptions:

- it assumes that all children are the same size
- it assumes `Arrangement.SpaceBetween` for any surplus space
- it assumes the row as its primary axis, there's no way to change it to fill columns first

#### scrollable

A `scrollable` is like a `box` with a single child which can be scrolled vertically and/or horizontally if it is larger
than the scrollable on the given axis.
Content that ends up outside the bounds of the scrollable will not be rendered.

If the child is smaller than the parent, it will by default be centered (just like `box`).
If you wish to have multiple children, it is recommended that you wrap them in a `column`, `row` or other container
according to your needs as there is no way to change the default arrangement of the scrollable.

```kotlin
scrollable(Modifier.fillHeight(), vertictal = true) {
    column {
        text("top")
        spacer(height = 1000)
        text("bottom")
    }
}
```

Note: This component has not yet seen much use and may still need some refinement.

Note: The `scrollable` method returns an instance of `ScrollComponent`. This may change in the future and you are
      advised to refrain from using most of its functionality as it is very overloaded and will often act different
      than what you would expect.
      Generally the only things that are safe to use are the scroll events and `scrollTo`-type methods.

#### lazyBox

Lazily initializes the inner scope by first only placing a `box` as described by the given `modifier` without any
children and only initializing the inner scope once that box has been rendered once.

This should be a last reserve for initializing a large list of poorly optimized components, not a common shortcut to
"make it not lag". Properly profiling and fixing initialization performance issues should always be preferred.

### Content

Similar to the previous "Containers" section, while one could just declare all their components in a field or directly
in-line, some components are so common that more convenient shorthands exist.

There's not really anything special about most of these, so they don't need much explanation:
- `text`: Creates single line of text (`EssentialUIText`)
- `wrappedText`: Creates text that wraps into multiple lines if there is not enough space in its parent (`EssentialUIWrappedText`)
- `icon`: Creates an icon with a shadow (`ShadowIcon`)

#### spacer

The `spacer` method creates simple, invisible, one-dimensional components. Their sole purpose is to take up a specific
amount of space at one specific place anywhere between/before/after regular components/containers.

```kotlin
row {
    spacer(width = 2f)
    text("Hello")
    spacer(width = 10f)
    text("World")
}
// results in: |  Hello          World|
```

When to use `spacer` or `Arrangement` often depends more on the intend behind the layout than anything else.
If you just want some arbitrary amount of extra space somewhere, then `spacer` is probably want you want.
If you want there to be a symmetrical padding inside your component, then maybe `spacer` isn't the best for the job.

If you have non-symmetrical padding, frequently that can be broken down into a symmetrical part and an extra part (but
only do so if that makes from a layout point of view), and then both can be wrapped up into a `row` or `container`
depending on the axis you're working with.

```kotlin
//            V V one space each
//   |       a b c  |
//    ^ 7 spaces  ^ 2 spaces
// could be written as:
row {
    spacer(width = 7f)
    row(Arrangement.spacedBy(1f)) {
        text("a")
        text("b")
        text("c")
    }
    spacer(width = 2f)
}
// and depending on why you want the space to be there, that may be totally reasonable.
// But if parts of the space are meant as padding around the text, and the remainder is just to keep space from
// whatever is to the left of the row, then introducing another container may be preferable as now if we want to
// increase the padding around the content, we don't have to modify two magic numbers:
row {
    spacer(width = 5f)
    box(Modifier.childBasedWidth(padding = 2f)) {
        row(Arrangement.spacedBy(1f)) {
            text("a")
            text("b")
            text("c")
        }
    }
}
```

Frequently, introducing another layer, even if it is seemingly redundant based on what is drawn, does actually make more
sense than using spacers because it has semantic significance in the layout.

The only pattern that should categorically be avoided is using spacer in a `row`/`column` that itself is using
`spacedBy` or a top-down layout with surplus space to contribute, except in the case where the spacer actually
represents an empty entry in the container.
For the above example, this would be:
```kotlin
// This does give the same result as above, but neither of the spacers represents anything tangible and the actual space
// before / after the text is different than what you would think after quickly skimming the code.
row(Arrangement.spacedBy(1f)) {
    spacer(width = 6f)
    text("a")
    text("b")
    text("c")
    spacer(width = 1f)
}
```

#### scrollGradient

The `scrollGradient` method adds a shadow-like gradient at the top and the bottom of a `scrollable`.
The gradient will fade in/out as you the scrollable is scrolled. That is, the top gradient won't be visible if it is
scrolled to the very top, and the bottom gradient will become invisible when it is scrolled to the very bottom.

They will usually be added directly after the scroller in a shared box that matches the size of the scrollable:
```kotlin
box(Modifier.fillParent()) {
    val scroller = scrollable(Modifier.fillParent(), vertictal = true) {
        column {
            text("top")
            spacer(height = 1000)
            text("bottom")
        }
    }

    val gradientHeight = Modifier.height(30)
    scrollGradient(scroller, top = true, gradientHeight)
    scrollGradient(scroller, top = false, gradientHeight)
}
```

Note: This component has not yet seen much use and may still need some refinement.

#### Custom components

##### Function components

While the regular sub-class way of creating custom Elementa components can be used just fine with the Layout DSL, a
pattern that's ofter easier is to simply pull out certain parts of your Layout DSL tree into separate functions:

```kotlin
fun LayoutScope.button(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
      box(Modifier.width(100f).height(20f).color(Palette.buttonOutlineColor).onLeftClick(onClick).then(modifier)) {
            box(Modifier.fillParent(padding = 1f).color(Palette.buttonBackgroundColor)) {
                  text(label)
            }
      }
}

window.layout {
    column(Arrangement.spacedBy(5f)) {
        row(Arrangement.spacedBy(3f)) {
            button("Yes", ::accept)
            button("No", ::reject)
        }
        button("Cancel", ::cancel, Modifier.width(30f).height(10f))
    }
}
```

There is nothing magical about these functions. 
They are just regular extension functions which have `LayoutScope` as their receiver and follow the general feel of
builtin content or container methods.

They will frequently live either as inner functions (if the component is specific to one use-case) or as top-level
functions in their own file if they are reusable or big enough to warrant their own file.

They usually have an optional Modifier argument (by convention it's usually the first optional argument) used to
configure the component (primarily its position).
Custom containers will also have an optional `block: LayoutScope.() -> Unit` argument (usually the final argument, so
it is eligible as the DSL-like trailing lambda) that configures the children.

It is of course also possible for a function component to add multiple children in the passed scope, however this should
be used with care because the relative position / spacing of these children is not usually defined by the caller and so
the function by itself is ambiguous. And similarly the caller might expect the function to define a single child and by
then surprised that it throws off things because there's suddenly more children than expected.
So the only time this functionality may be useful is in local helper functions that are defined very close to their
usage (acting more like a template than a function component at that point); though even in these cases, often it makes
sense to add a wrapper container in the function component anyway.

##### Class components

Sometimes you need your custom component to be a full blown, regular Elementa component class.
But you can still use the Layout DSL to configure the inner working of such components:

```kotlin
class Button(label: String, onClick: () -> Unit) : UIContainer() {
    init {
        layout(Modifier.width(100f).height(20f).color(Palette.buttonOutlineColor).onLeftClick(onClick)) {
            box(Modifier.alignBoth(Alignment.Center).fillParent(padding = 1f).color(Palette.buttonBackgroundColor)) {
                text(label)
            }
        }
    }
}

window.layout {
    column(Arrangement.spacedBy(5f)) {
        row(Arrangement.spacedBy(3f)) {
            Button("Yes", ::accept)()
            Button("No", ::reject)()
        }
        Button("Cancel", ::cancel)(Modifier.width(30f).height(10f))
    }
}
```

The main disadvantage here is that your custom component can no longer be a `box`/`row`/`column`, you need to deal with
positioning of your immediate children manually. And, if your component is sized bottom-up, you also need to deal with
the sizing of it manually.

### Arrangement

`Arrangement` provides a way to declare how multiple components should be arranged (i.e. where surplus space goes) on 
a particular axis.

In terms old regular Elementa, it provides position constraints (for one axis) for all children of a given container and
centrally decides where all components will go.

Note: A single `Arrangement` cannot currently be shared between multiple rows/columns; this should be fixed at some
      point, because `Alignment` and `Modifier` do allow for this (and even explicitly encourage it).

Suppose we have a row with three equally sized children and 8 pixels of surplus space:
```kotlin
row(Modifier.width(38), arrangementGoesHere) {
    box(Modifier.width(10))
    box(Modifier.width(10))
    box(Modifier.width(10))
}
```

#### SpacedAround

Simply divides the available free space in two and places it on both sides of the children:
```
|    |--------||--------||--------|    |
```

#### SpacedBetween

Divides up the available free space and places it between the children:
```
||--------|    |--------|    |--------||
```

#### SpacedEvenly

Divides up the available free space and places it between and around the children:
```
|  |--------|  |--------|  |--------|  |
```

#### spacedBy

Uses a given fixed `spacing` between the children and positions the entire block according to the given `float`:
```
Arrangement.spacedBy(1f, FloatPosition.Start)
||--------| |--------| |--------|      |
Arrangement.spacedBy(1f, FloatPosition.Center)
|   |--------| |--------| |--------|   |
Arrangement.spacedBy(1f, FloatPosition.End)
|      |--------| |--------| |--------||
```

Unlike the previous arrangements, `spacedBy` is usually used for bottom-up layouts. If no explicit width is set on the
row, its width will be the sum of the widths of its children plus the spacing between them:

```kotlin
row(Arrangement.spacedBy(1)) {
    box(Modifier.width(10))
    box(Modifier.width(10))
    box(Modifier.width(10))
}
// Results in ||--------| |--------| |--------||
```

Note: Currently the default FloatPosition is `Start`, this may be changed to `Center` in the future.
      Use of the floating parameter is actually quite rare because spacedBy in top-down layouts is quite rare and
      because the same effect can be achieved by putting a box around a bottom-up spacedBy row and then simply
      controlling the float of the entire row within that box.

#### equalWeight

Uses a given fixed `spacing` between the children and distributes remaining space **into** the children.
That is, it overwrites the width of all its children and sets them all to the same width such that no surplus space
remains.

```
Arrangement.equalWeight(1f)
||----------| |----------| |----------||
```

Note how the children end up being 12 wide, not 10.
But it can also shrink the children:

```
Arrangement.equalWeight(10f)
||----|          |----|          |----||
```

### Dynamic content

So far we have only built static component trees but quite frequently components will only be visible under certain
circumstances (like when a certain State is true), usually this boils down calling `hide` and `show` on the component
from the state change listener. But doing this correctly is actually deceptively hard (especially keeping the correct
order between multiple conditional components).

With Layout DSL, this is now possible and it's stupidly simple (at least to use; the implementation, not so much):
```kotlin
val myBoolState = mutableStateOf(true)
window.layout {
    text("Before")
    if_(myBoolState) {
        text("It's true!")
    } `else` {
        box(Modifier.color(Color.RED)) {
            text("Oh no")
        }
    }
    text("After")
}
```

This will at first only evaluate one of the two inner blocks.
When the value changes, then it'll then remove all children from that block and evaluate the other block.
By default, if the value then changes again, it will have remembered the components of the original block and simple add
them back after removing the ones from the other block.

This is usually what you want because it makes switching back and forth fast at the usually small cost of keeping
components for both in memory.
If for some reason you do not want to keep the inactive components around, you can pass `cache = false` in the `if_`
call to disable this caching. It will then re-evaluate the branches on each change.

Note that without the cache, care must be taken to not create any memory leaks when using StateV1, as change listeners
registered on StateV1 do not get cleaned up automatically until both the state and all its listeners are eligible for
garbage collection.

#### bind

But what if you have more than just true and false?
`bind` will accept any state, and re-evaluate the block whenever its value changes.

Note that unlike with `if`, since there can theoretically be an unbounded amount of values, caching is disabled for
`bind` by default. You can enable it via the optional parameter and probably should do so wherever it makes sense.

```kotlin
val myStrState = mutableStateOf("Test")
window.layout {
    bind(myStrState) { myStr ->
        text("My string is $myStr")
    }
}
```

Because it is quite common, there is a specialized variant meant for states that can be null:
```kotlin
val myStrState = mutableStateOf<String?>(null)
window.layout {
    ifNotNull(myStrState) { myStr ->
        text("My string is $myStr (and never null)")
    }
    
    // Effectively equivalent to:
    bind(myStrState) { myStr ->
        if (myStr == null) return@bind
        text("My string is $myStr (and never null)")
    }
}
```

#### forEach

But what if you want a variable number of components?
`forEach` will accept a `ListState<T>` and call the block for each `T`, disposing of the correct scopes when values are
removed from the state and inserting new scopes at the right place as new values are added to the scope.

Note that unlike with `if`, since there can theoretically be an unbounded amount of values, caching is disabled for
`forEach` by default.
You can enable it via the optional parameter and probably should do so wherever it makes sense. This is especially true
if you have a practically limited amount of values but want to implement something like search where having to re-create
all the components whenever you remove characters from your search term would be quite expensive.

```kotlin
val myListState = mutableListStateOf("a", "b", "c")
window.layout {
    forEach(myListState) { myStr ->
        text(myStr)
    }
}
```

### Hovering

Components will frequently change their looks when they are hovered.
This is generally achieved with the `whenHovered` modifier.
For many modifiers there also exist variants with the `hovered` prefix (e.g. `hoveredColor`) which are shortcuts for
this modifier.

```kotlin
// A box that's red when hovered and black otherwise
box(Modifier.whenHovered(Modifier.color(Color.RED), Modifier.color(Color.BLACK)).then(size))
// or, same thing, a black box that turns red when hovered:
box(Modifier.color(Color.Black).whenHovered(Modifier.color(Color.RED)).then(size))
// or, same thing, with the `hovered`-prefixed `color` modifier
box(Modifier.color(Color.Black).hoverColor(Color.RED).then(size))
```

A hover scope is **required** to use these (see next section).
This is because aside from toy examples, you usually want one.

#### Hover Scope

Usually however, we don't actually care about whether any specific component, like the text of a button, is hovered.
What we really care about is whether the button as a whole is hovered.
And, if it is, then all children of the button should act as if they are hovered as well.

Such a scope of elements (specifically a sub-tree of components), that should all act together with respect to hovering,
is declared with the `hoverScope` modifier.

If declared with default arguments on a component, the hover state of that container will be tracked, and all
(direct and indirect) children as well as the component itself will follow that state for their `whenHovered` modifiers.

If more control is required over when the hover state is true or false, the `hoverScope` modifier can optionally
receive a `State` to use as the hover state.

(TODO this currently uses StateV1, and as such may cause leaks if the children are highly dynamic; need to update to V2)

Note: The `hoverScope` modifier should not be confused with the `UIComponent.hoverScope` extension function.
      The former is used to declare a new hover scope while the latter is used to retrieve the hover scope applicable
      to a component like `whenHovered` does.
      It should also not be confused with the `UIComponent.hoverState` extension function, which is a lower-level
      function commonly used prior to the introduction of hover scopes. It simply returns a State for whether that
      specific component is hovered. That is what is used by the `hoverScope` modifier if you do not pass a custom
      State.

#### Default hover scope and inheritance

There are standalone components which will usually want to be treated as a single hover scope, e.g. a button component
will in the vast majority of cases be the root of a hover scope.
To that end, they will usually apply the `hoverScope` modifier to themselves (or `makeHoverScope` for class components).

But what if we want to disable hovering of such a component (assuming the component doesn't have a dedicated way to do
that)?

This is not much of a problem, calling `hoverScope` again on the same component will simply replace the default one
installed by the component itself: `Button()(Modifier.hoverScope(BasicState(false)))`

But what if you want to use such a component as part of a larger component where hovering anywhere on the larger
component will affect that component as well?

By default hover scopes are not inherited, meaning even though both scopes will show as hovered when you place your
cursor in such a way that it is inside both, the same is not true when it is only over the larger one. In that case, by
default, only the larger component will appear hovered.
We can however override the hover scope of the inner component as above and simply pass the hover state of the outer
component for it to use. The `inheritHoverScope` modifier when applied to the inner component does exactly that.

## Style Guide

This section list various code style rules related to the Layout DSL and surrounding mechanisms.
Most of these are fuzzy and much less strict than general code style guidelines and should be considered recommendations
rather than hard rules.
Where possible, you should follow these as they aid in making the code easier to read for anyone used to seeing code
that follows these rules, but if they worsen readability in some specific case, then you should not feel obliged to
follow them just for the sake of it.

This list is likely incomplete and should be expanded whenever we find us adhering to any yet unwritten rules.

The guiding principle which most of these follow is to keep in mind the original purpose of the Layout DSL as explained
in the "Motivation" section: Being able to understand the overall structure/layout of a GUI without having to run or
laboriously mentally evaluate them.

### Keep it short

Within the DSL, keep the closing parenthesis on the same line as the respective opening parenthesis.
If that makes the line too long, you're probably doing too much in there. Some of your options are:

If you have a click handler or any other non-trivial lambda in there, move it to a function outside the Layout DSL.

If your modifier chain is too long, remember that modifiers were meant to be re-usable, so there's usually nothing
wrong with declaring a local variable with the modifier beforehand and then using that (potentially in multiple places).
Do try to keep non-custom layout information (i.e. positioning and sizing modifiers) in-line though, as these are
usually required to understand the layout, which is the point of the DSL after all.
Another exception to this is the `hoverScope` modifier due to it conceptually being more of a property of the entire
sub-tree rather than any specific component.

If you are deeply indented (or even if you are not yet), consider extracting out function components where it makes
semantic sense.
This is especially useful for things with click handler or other lambdas (like mapped states) as these can nicely be
put at the start of the function component, where they're still close to their usage, just not too close.

### Miscellaneous

- Instead of `whenHovered`, prefer using the `hovered` variants and the regular variant where those exist,
  e.g. `.color(regular).hoveredColor(hovered)`. Easier to read because the regular/non-hovered variant can go first.
- When you need a `row` or `column` with non-standard arrangement but no special modifier, use the overload instead
  of using a keyword argument to pass the arrangement. The keyword is quite long and standard arrangements are prefixed
  by `Arrangement.` already.
- Usually `align(Center)` is redundant. See the "Containers" section.
- Avoid `onMouseEnter`/`onMouseLeave`/`whenMouseEntered`. These do not even handle occlusion properly.
  See the "Hovering" section instead.
- When order of modifiers does not matter semantically, prefer
    - size before position before everything else, `hoverScope` last
    - width before height, x before Y
