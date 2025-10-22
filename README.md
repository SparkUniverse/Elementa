# Elementa

Elementa (from the name of the first book published on Geometry by Euclid) is a library
that aims to make GUI creation extremely simple. It's based on a couple key concepts, some of which
may already be familiar to those who have worked with a browser's DOM.

The library is based around the idea of being [declarative](https://en.wikipedia.org/wiki/Declarative_programming).
This is a shift from how one would normally do graphics programming in Minecraft, or most other coding in general.
In Elementa, you do not have to write code to calculate _how_ to place a component at a certain point on the screen,
instead you simply have to describe _what_ you want.

## Dependency

```kotlin
repository {
    // All versions of Elementa and UniversalCraft are published to Essential's public maven repository.
    // (if you're still using Groovy build scripts, replace `()` with `{}`)
    maven(url = "https://repo.essential.gg/repository/maven-public")
}
dependencies {
    // Add Elementa dependency. For the latest $elementaVersion, see the badge below this code snippet.
    implementation("gg.essential:elementa:$elementaVersion")
    
    // Optionally, add some of the unstable Elementa features.
    // Note that these MUST be relocated to your own package because future versions may contain breaking changes
    // and therefore MUST NOT be simply included via Fabric's jar-in-jar mechanism.
    implementation("gg.essential:elementa-unstable-layoutdsl:$elementaVersion")
    
    // Elementa itself is independent of Minecraft versions and mod loaders, instead it depends on UniversalCraft which
    // provides bindings to specific Minecraft versions.
    // As such, you must include the UniversalCraft version for the Minecraft version + mod loader you're targeting.
    // For a list of all available platforms, see https://github.com/EssentialGG/UniversalCraft
    // For your convenience, the latest $ucVersion is also included in a badge below this code snippet.
    // (Note: if you are not using Loom, replace `modImplementation` with `implementation` or your equivalent)
    modImplementation("gg.essential:universalcraft-1.8.9-forge:$ucVersion")
    
    // If you're using Fabric, you may use its jar-in-jar mechanism to bundle Elementa and UniversalCraft with your
    // mod by additionally adding them to the `include` configuration like this (in place of the above):
    implementation(include("gg.essential:elementa:$elementaVersion")!!)
    modImplementation(include("gg.essential:universalcraft-1.8.9-forge:$ucVersion"))
    // If you're using Forge, you must instead include them directly into your jar file and relocate them to your
    // own package (this is important! otherwise you will be incompatible with other mods!)
    // using e.g. https://gradleup.com/shadow/configuration/relocation/
    // For an example, read the IMPORTANT section below.
}
```
<img alt="gg.essential:elementa" src="https://img.shields.io/badge/dynamic/xml?color=A97BFF&label=Latest%20Elementa&query=/metadata/versioning/versions/version[not(contains(text(),'%2B'))][last()]&url=https://repo.essential.gg/repository/maven-releases/gg/essential/elementa/maven-metadata.xml">
<img alt="gg.essential:universalcraft-1.8.9-forge" src="https://img.shields.io/badge/dynamic/xml?color=A97BFF&label=Latest%20UniversalCraft&query=/metadata/versioning/versions/version[not(contains(text(),'%2B'))][last()]&url=https://repo.essential.gg/repository/maven-releases/gg/essential/universalcraft-1.8.9-forge/maven-metadata.xml">

<h2><span style="font-size:3em; color:red;">IMPORTANT!</span></h2>

If you are using Forge, you must also relocate Elementa to avoid incompatibility with other mods.
To do this, you may use the Shadow Gradle plugin:

<details><summary>Groovy Version</summary>

You can do this by either putting it in your plugins block:
```groovy
plugins {
    id "com.github.johnrengelman.shadow" version "$version"
}
```
or by including it in your buildscript's classpath and applying it:
```groovy
buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath "gradle.plugin.com.github.jengelman.gradle.plugins:shadow:$version"
    }
}

apply plugin: "com.github.johnrengelman.shadow"
```
You'll then want to relocate Elementa to your own package to avoid breaking other mods
```groovy
shadowJar {
    archiveClassifier.set(null)
    relocate("gg.essential.elementa", "your.package.elementa")
    // elementa dependencies
    relocate("gg.essential.universalcraft", "your.package.universalcraft")
}
tasks.named("reobfJar").configure { dependsOn(tasks.named("shadowJar")) }
```

</details>

<details><summary>Kotlin Script Version</summary>

You can do this by either putting it in your plugins block:
```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "$version"
}
```
or by including it in your buildscript's classpath and applying it:
```kotlin
buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("gradle.plugin.com.github.jengelman.gradle.plugins:shadow:$version")
    }
}

apply(plugin = "com.github.johnrengelman.shadow")
```
You'll then want to relocate Elementa to your own package to avoid breaking other mods
```kotlin
tasks.shadowJar {
    archiveClassifier.set(null)
    relocate("gg.essential.elementa", "your.package.elementa")
    // elementa dependencies
    relocate("gg.essential.universalcraft", "your.package.universalcraft")
}
tasks.reobfJar { dependsOn(tasks.shadowJar) }
```

</details>

## Legacy Builds
In your dependencies block, add:

```groovy
implementation "club.sk1er:Elementa:1.7.1-$mcVersion"
```

If you were previously using v1.7.1 of Elementa and are now on the v2.0.0 builds, please refer to the
[migration](docs/migration.md) document to know what has changed.

To learn about all the new features in v2.0.0, please read the [what's new](docs/whatsnew.md) document.

## Components

All the drawing in Elementa is done via UIComponents. There is a root component named `Window`
that MUST be in the hierarchy of all components, thus making it the top of the component tree. 
All components have exactly `1` parent, and all components have `0-n` children.

To create a component, simply instantiate an existing implementation such as `UIBlock`, 
or extend `UIComponent` yourself.

```kotlin
// Manually create and store a window instance. The Window is the entry point for Elementa's event system,
// in that you must call events on the window instance manually, the most common of which would be Window#draw.
// This call must be made every frame or else the library will never render your components. If your Gui extends
// Elementa's WindowScreen, this step will already be done and a window will be provided.
val window = Window()

// Here we are creating an instance of one of the simplest components available, a UIBlock.
// Next, we must give it positions to tell the library where to draw the component. Here we
// simply give it a 10 pixel width and height.
// Finally, we have to add it to our hierarchy in some way, and in this instance we want it to be
// a child of the Window. Now that it is in the hierarchy, it will be drawn when we render our Window.
val box = UIBlock(Color.RED /* java.awt.Color */).constrain {
    width = 10.pixels()
    height = 10.pixels()
} childOf window
```

A showcase of all the components provided by Elementa:

![Components GUI Photo](https://i.imgur.com/bw2VLua.png)

Read more about all of these components [here](docs/components.md).

## Constraints

All components have a set of constraints that determine its X/Y position, width/height, and color.
The default set of constraints sets a component's x, y, width, height to be 0, and color to be Color.WHITE.

A key thing to realize with these components is that everything is relative to its parent. When we
center a component, it will be in the center of its _direct_ parent, whether it is the Window or
perhaps another UIBlock.

This also showcases exactly how declarative the library is. Our code is saying that we would like our box
to be in the center of our parent, and that is all we need to do. No code to figure out how to position it there,
no code to calculate. We simply describe exactly what we want, and Elementa will do the rest for you.

```kotlin
val box = UIBlock().constrain {
    x = CenterConstraint()
    y = 10.pixels()
    width = 20.pixels()
    height = 36.pixels()
}
```

## Effects

Additionally, a component can have a list of effects, special modifiers that can affect the rendering of
a component or its children. One of the most common effects is the `ScissorEffect`. When enabled for
an arbitrary component, this effect restricts all of its children to be drawn inside its own boundaries.
Anything drawn outside that area will simply be cut off. Any component that is not a child (direct or indirect)
of the component where the effect is enabled will not have their rendering affected.

```kotlin
val box = UIBlock() effect ScissorEffect()
```

## Animations

Elementa also provides a strong animation API. When you make an animation, you set all the
new constraints you would like to animate to, as well as the length (and optionally, delay)
of the animation.

When animating, you have a wide variety of animation strategies (algorithms) to choose from, and you can
of course implement more yourself. All the built-in animation strategies come from
the `Animations` enum.

```kotlin
box.animate {
    // Algorithm, length, new constraint, and optionally, delay.
    // All times are in seconds.
    setWidthAnimation(Animations.OUT_EXP, 0.5f, ChildBasedSizeConstraint(2f))
}
``` 

## Basic Events

Elementa also provides some basic events that can run your animations, or anything else of your choosing.

```kotlin
box.animate {
    setWidthAnimation(Animations.OUT_EXP, 0.5f, ChildBasedSizeConstraint(2f))
    
    // This will run when the animation is complete.
    // If this animation had multiple "animation components",
    // this would trigger when they were all complete.
    onComplete {
        // Trigger new animation or anything.    
    }
}

// Runs a single time when the mouse moves from a state of not hovering to hovering.
box.onMouseEnter {
    // Animate, set color, run business logic, etc.
}
```

There are many more events than solely those two, and they can be found throughout `UIComponent`.
Keep in mind that all events stem from the Window component, and events must be manually
called on the Window. For example, in order to receive an `onMouseClick` event,
you MUST call Window#mouseClick. This is also all handled by Elementa's WindowScreen.

## All together

This is a basic excerpt of code from an Elementa GUI. To see a more fleshed out
example, look to the [ExampleGui class](example/src/main/kotlin/gg/essential/elementa/example/ExampleGui.kt) and other
files in that sub-project.
You can run those examples via `./gradlew :example:run`.

```kotlin
val window = Window()

val box = UIBlock().constrain {
    x = CenterConstraint()
    y = 10f.pixels()
    width = 10f.pixels()
    height = 36f.pixels()
} effect ScissorEffect() childOf window

box.animate {
    setWidthAnimation(Animations.OUT_EXP, 0.5f, ChildBasedSizeConstraint(2f))

    onComplete {
        // Trigger new animation or anything.    
    }
}

box.onMouseEnter {
    // Animate, set color, etc.
}
```
