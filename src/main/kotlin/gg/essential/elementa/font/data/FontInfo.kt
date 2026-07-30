package gg.essential.elementa.font.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class FontInfo(
    val atlas: Atlas,
    val metrics: Metrics,
    val glyphs: Map<Int, Glyph>
) {
    companion object {
        private val gson = Gson()

        fun fromJson(json: JsonObject): FontInfo {
            val atlas = gson.fromJson(json.getAsJsonObject("atlas"), Atlas::class.java)
            val metrics = gson.fromJson(json.getAsJsonObject("metrics"), Metrics::class.java)
            val glyphs = json.getAsJsonArray("glyphs").associate { glyphElement ->
                val glyph = gson.fromJson(glyphElement, Glyph::class.java)
                glyph.unicode to glyph
            }

            return FontInfo(atlas, metrics, glyphs)
        }
    }
}

data class Atlas(
    val type: String,
    val distanceRange: Float,
    val size: Float,
    val width: Float,
    val height: Float,
    val yOrigin: String,
    val baseCharHeight: Float,
    val belowLineHeight: Float,
    val shadowHeight: Float
)

data class Metrics(
    val lineHeight: Float,
    val ascender: Float,
    val descender: Float,
    val underlineY: Float,
    val underlineThickness: Float
)

class Glyph(
    val unicode: Int,
    val advance: Float,
    val planeBounds: PlaneBounds? = null,
    val atlasBounds: AtlasBounds? = null
)

data class PlaneBounds(
    @SerializedName("left")
    private val _left: Float,
    @SerializedName("bottom")
    private val _bottom: Float,
    @SerializedName("right")
    private val _right: Float,
    @SerializedName("top")
    private val _top: Float
) {
    val l: Float get() = _left
    val b: Float get() = _bottom
    val r: Float get() = _right
    val t: Float get() = _top

    @Deprecated("Returns incorrect value") // technically this one's right, but `top` and `bottom` aren't
    val left: Float
        get() = _left
    @Deprecated("Returns incorrect value")
    val bottom: Float
        get() = _bottom + 0.025f
    @Deprecated("Returns incorrect value") // technically this one's right, but `top` and `bottom` aren't
    val right: Float
        get() = _right
    @Deprecated("Returns incorrect value")
    val top: Float
        get() = _top + 0.025f
}


data class AtlasBounds(
    @SerializedName("left")
    private val _left: Float,
    @SerializedName("bottom")
    private val _bottom: Float,
    @SerializedName("right")
    private val _right: Float,
    @SerializedName("top")
    private val _top: Float
) {
    val l: Float get() = _left
    val b: Float get() = _bottom
    val r: Float get() = _right
    val t: Float get() = _top

    @Deprecated("Returns incorrect value")
    val left: Float
        get() = _left + .5f
    @Deprecated("Returns incorrect value")
    val bottom: Float
        get() = _bottom + .5f
    @Deprecated("Returns incorrect value")
    val right: Float
        get() = _right + .5f
    @Deprecated("Returns incorrect value")
    val top: Float
        get() = _top + .5f
}

/**
 * msdfgen inflates all glyphs by half a pixel so all the edges are drawn properly when using MSDF.
 * This function un-does that, because we don't need it in our pixel-font renderer
 * ([gg.essential.elementa.font.BasicFontRenderer]) and it makes any sizing math more difficult.
 */
internal fun FontInfo.shrinkGlyphsByHalfAPixel(): FontInfo {
    val a = 0.5f // half a pixel in atlas coordinates
    val p = a / atlas.size // half a pixel in plane coordinates
    return FontInfo(
        atlas,
        metrics,
        glyphs.mapValues { (_, glyph) ->
            Glyph(
                glyph.unicode,
                glyph.advance,
                glyph.planeBounds?.let { bounds ->
                    PlaneBounds(bounds.l + p, bounds.b + p, bounds.r - p, bounds.t - p)
                },
                glyph.atlasBounds?.let { bounds ->
                    AtlasBounds(bounds.l + a, bounds.b + a, bounds.r - a, bounds.t - a)
                },
            )
        },
    )
}
