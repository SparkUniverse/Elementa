package gg.essential.elementa.renderer.impl

import kotlin.math.max
import kotlin.math.min

internal data class Rect(val x1: Int, val y1: Int, val x2: Int, val y2: Int) : Comparable<Rect> {
    val x: Int get() = x1
    val y: Int get() = y1
    val w: Int get() = x2 - x1
    val h: Int get() = y2 - y1

    val xy: Pos get() = Pos(x, y)

    fun isEmpty() = w == 0 || h == 0

    val ifPositiveElseNull: Rect?
        get() = if (w > 0 && h > 0) this else null

    operator fun contains(other: Rect): Boolean {
        return x1 <= other.x1 && y1 <= other.y1 && x2 >= other.x2 && y2 >= other.y2
    }

    fun intersects(other: Rect): Boolean {
        return x1 < other.x2 && other.x1 < x2 && y1 < other.y2 && other.y1 < y2
    }

    fun intersection(other: Rect): Rect {
        val x1 = max(this.x1, other.x1)
        val y1 = max(this.y1, other.y1)
        var x2 = min(this.x2, other.x2)
        var y2 = min(this.y2, other.y2)
        if (x2 < x1) x2 = x1
        if (y2 < y1) y2 = y1
        return Rect(x1, y1, x2, y2)
    }

    fun union(other: Rect): Rect {
        if (this.isEmpty()) return other
        if (other.isEmpty()) return this
        return Rect(
            min(this.x1, other.x1),
            min(this.y1, other.y1),
            max(this.x2, other.x2),
            max(this.y2, other.y2),
        )
    }

    /**
     * Takes this [Rect] and makes an [other]-shaped hole in it.
     * Returns zero to four [Rect]s which cover exactly the resulting shape.
     */
    fun subtract(other: Rect): List<Rect> {
        if (!intersects(other)) return listOf(this)
        if (other.contains(this)) return emptyList()
        return listOfNotNull(
            Rect(x1, y1, x2, min(y2, other.y1)).ifPositiveElseNull,
            Rect(max(x1, other.x2), max(y1, other.y1), x2, min(y2, other.y2)).ifPositiveElseNull,
            Rect(x1, max(y1, other.y1), min(x2, other.x1), min(y2, other.y2)).ifPositiveElseNull,
            Rect(x1, max(y1, other.y2), x2, y2).ifPositiveElseNull,
        )
    }

    override fun compareTo(other: Rect): Int {
        if (this === other) return 0
        if (x1 != other.x1) return x1 - other.x1
        if (y1 != other.y1) return y1 - other.y1
        if (x2 != other.x2) return x2 - other.x2
        if (y2 != other.y2) return y2 - other.y2
        return 0
    }

    operator fun plus(pos: Pos): Rect =
        Rect(x1 + pos.x, y1 + pos.y, x2 + pos.x, y2 + pos.y)

    operator fun minus(pos: Pos): Rect = plus(-pos)

    companion object {
        fun ltrb(l: Int, t: Int, r: Int, b: Int): Rect =
            Rect(l, t, r, b)

        fun ltrbChecked(l: Int, t: Int, r: Int, b: Int): Rect {
            require(l <= r && t <= b)
            return ltrb(l, t, r, b)
        }

        fun xywh(x: Int, y: Int, w: Int, h: Int): Rect =
            Rect(x, y, x + w, y + h)

        fun xywhChecked(x: Int, y: Int, w: Int, h: Int): Rect {
            require(w >= 0 && h >= 0)
            return xywh(x, y, w, h)
        }
    }
}

internal data class Pos(val x: Int, val y: Int) {
    operator fun unaryMinus(): Pos = Pos(-x, -y)
    operator fun plus(other: Pos): Pos = Pos(x + other.x, y + other.y)
    operator fun minus(other: Pos): Pos = Pos(x - other.x, y - other.y)
}
