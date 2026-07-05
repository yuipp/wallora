package com.wallora.app.data.paging

import com.wallora.app.domain.model.Wallpaper
import kotlin.math.abs

/**
 * Reorders a page of wallpapers so that visually and topically similar images are not placed
 * next to each other in the grid.
 *
 * Why: the multi-source feed otherwise clusters same-source / same-category items together, and
 * many photography sources skew toward muted, similar-looking shots. Spreading out by dominant
 * colour ([Wallpaper.colorHint]), category and source makes adjacent tiles tell "different
 * stories" — the effect the user asked for (a red hero next to a black one next to a blue one).
 *
 * Pure Kotlin (no `android.graphics`) so it unit-tests without Robolectric. Deterministic for a
 * given input (stable index tie-break) so Paging stays stable across recompositions.
 */
object FeedDiversifier {

    /** How many recently-placed items a candidate is compared against. Approximates 2–3 columns. */
    const val DEFAULT_WINDOW = 3

    private const val CATEGORY_PENALTY = 1.0f
    private const val SOURCE_PENALTY = 0.35f
    private const val HUE_WEIGHT = 1.0f

    /**
     * Convert an (A)RGB int to HSV. Returns `[hue 0..360, saturation 0..1, value 0..1]`.
     * Alpha is ignored.
     */
    fun argbToHsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        var h = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        if (h < 0f) h += 360f
        val s = if (max == 0f) 0f else delta / max
        return floatArrayOf(h, s, max)
    }

    /** Shortest distance between two hues on the colour wheel, in degrees (0..180). */
    fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    /** Saturation × value — higher means a punchier, more colourful image. */
    private fun colorfulness(w: Wallpaper): Float {
        val c = w.colorHint ?: return 0f
        val hsv = argbToHsv(c)
        return hsv[1] * hsv[2]
    }

    /** How similar two wallpapers look/read. Higher = more similar = worse to place adjacently. */
    private fun similarity(a: Wallpaper, b: Wallpaper): Float {
        var s = 0f
        if (a.category != null && a.category == b.category) s += CATEGORY_PENALTY
        if (a.sourceId == b.sourceId) s += SOURCE_PENALTY
        val ca = a.colorHint
        val cb = b.colorHint
        if (ca != null && cb != null) {
            val ha = argbToHsv(ca)[0]
            val hb = argbToHsv(cb)[0]
            s += HUE_WEIGHT * (1f - hueDistance(ha, hb) / 180f)
        }
        return s
    }

    /** Worst (max) similarity of [candidate] against the recent window. */
    private fun badness(candidate: Wallpaper, window: List<Wallpaper>): Float {
        var worst = 0f
        for (w in window) {
            val s = similarity(candidate, w)
            if (s > worst) worst = s
        }
        return worst
    }

    /**
     * Greedily reorder [items]: at each step pick the remaining item least similar to the last
     * [windowK] placed. Tie-break by higher [colorfulness], then by original index (stable →
     * deterministic). [tail] carries the last items emitted on the previous page so the first
     * item of this page differs from the end of the last. Size is preserved — nothing is dropped.
     */
    fun diverse(
        items: List<Wallpaper>,
        windowK: Int = DEFAULT_WINDOW,
        tail: List<Wallpaper> = emptyList(),
    ): List<Wallpaper> {
        if (items.size <= 1) return items
        val remaining = ArrayList(items)
        val result = ArrayList<Wallpaper>(items.size)
        val window = ArrayDeque<Wallpaper>()
        tail.takeLast(windowK).forEach { window.addLast(it) }

        while (remaining.isNotEmpty()) {
            var bestIdx = 0
            var bestBadness = Float.MAX_VALUE
            var bestColor = -1f
            for (i in remaining.indices) {
                val cand = remaining[i]
                val bad = badness(cand, window)
                if (bad < bestBadness) {
                    bestIdx = i
                    bestBadness = bad
                    bestColor = colorfulness(cand)
                } else if (bad == bestBadness) {
                    val col = colorfulness(cand)
                    if (col > bestColor) {
                        bestIdx = i
                        bestColor = col
                    }
                    // equal badness AND colour → keep the earlier index (deterministic)
                }
            }
            val picked = remaining.removeAt(bestIdx)
            result.add(picked)
            window.addLast(picked)
            if (window.size > windowK) window.removeFirst()
        }
        return result
    }
}
