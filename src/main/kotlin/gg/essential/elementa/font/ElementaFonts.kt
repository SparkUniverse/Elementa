package gg.essential.elementa.font

import gg.essential.elementa.font.data.Font

object ElementaFonts {
    private val MINECRAFT_FONT = Font.fromResource(javaClass, "/fonts/Minecraft-Regular")
    private val MINECRAFT_BOLD_RAW = Font.fromResource(javaClass, "/fonts/Minecraft-Bold")
    private val JETBRAINS_MONO_FONT = Font.fromResource(javaClass, "/fonts/JetBrainsMono-Regular")
    private val MINECRAFT_FIVE_FONT = Font.fromResource(javaClass, "/fonts/Minecraft-Five")

    @JvmStatic
    val MINECRAFT = FontRenderer(MINECRAFT_FONT, boldFont = MINECRAFT_BOLD_RAW)

    @JvmStatic
    val MINECRAFT_BOLD = FontRenderer(MINECRAFT_BOLD_RAW)

    @JvmStatic
    val JETBRAINS_MONO = FontRenderer(JETBRAINS_MONO_FONT)

    @JvmStatic
    val MINECRAFT_FIVE = BasicFontRenderer(MINECRAFT_FIVE_FONT)

}
