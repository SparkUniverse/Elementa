import gg.essential.gradle.multiversion.StripReferencesTransform.Companion.registerStripReferencesAttribute
import gg.essential.gradle.util.setJvmDefault
import gg.essential.gradle.util.versionFromBuildIdAndBranch

plugins {
    kotlin("jvm")
    id("gg.essential.defaults")
    id("gg.essential.defaults.maven-publish")
}

version = versionFromBuildIdAndBranch()
group = "gg.essential"

dependencies {
    compileOnly(project(":"))
    api(project(":unstable:statev2"))

    val common = registerStripReferencesAttribute("common") {
        excludes.add("net.minecraft")
    }
    compileOnly(libs.versions.universalcraft.map { "gg.essential:universalcraft-1.8.9-forge:$it" }) {
        attributes { attribute(common, true) }
    }
    // Depending on LWJGL3 instead of 2 so we can choose opengl bindings only
    compileOnly("org.lwjgl:lwjgl-opengl:3.3.1")
}
tasks.compileKotlin.setJvmDefault("all")

kotlin.jvmToolchain {
    (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(8))
}

java.withSourcesJar()

publishing {
    publications {
        named<MavenPublication>("maven") {
            artifactId = "elementa-unstable-${project.name}"
        }
    }
}