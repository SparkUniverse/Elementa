plugins {
    kotlin("jvm")
    application
    id("gg.essential.defaults.repo")
}

dependencies {
    implementation(libs.universalcraft.standalone)
    implementation(project(":"))
    implementation(project(":unstable:layoutdsl"))
}

kotlin.jvmToolchain(8)

application {
    mainClass.set("gg.essential.elementa.example.MainKt")
}
