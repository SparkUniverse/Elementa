package gg.essential.elementa.font.data

import gg.essential.universal.UGraphics
import gg.essential.universal.utils.ReleasedDynamicTexture
import com.google.gson.JsonParser
import java.io.InputStream

class Font(
    val fontInfo: FontInfo,
    private val atlas: InputStream
) {
    private lateinit var texture: ReleasedDynamicTexture

    fun getTexture(): ReleasedDynamicTexture {
        if (!::texture.isInitialized) {
            texture = UGraphics.getTexture(atlas)
        }

        return texture
    }

    companion object {
        @Deprecated("Does not work properly when used with Java 9 Modules.", ReplaceWith(
            "fromResource(javaClass, path)",
            "gg.essential.elementa.font.data.Font.Companion.fromResource"
        ))
        fun fromResource(path: String): Font = fromResource(javaClass, path)

        fun fromResource(context: Class<*>, path: String): Font {
            val json = context.getResourceAsStream("$path.json")
            val fontInfo = FontInfo.fromJson(JsonParser().parse(json.reader()).asJsonObject)

            return Font(fontInfo, context.getResourceAsStream("$path.png"))
        }
    }
}

