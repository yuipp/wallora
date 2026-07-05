package com.wallora.app

import com.wallora.app.data.paging.FeedDiversifier
import com.wallora.app.domain.model.Category
import com.wallora.app.domain.model.SourceId
import com.wallora.app.domain.model.Wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedDiversifierTest {

    private fun wp(
        id: String,
        source: SourceId = SourceId.PEXELS,
        category: Category? = null,
        color: Int? = null,
    ) = Wallpaper(
        id = id,
        sourceId = source,
        thumbUrl = "https://thumb/$id",
        fullUrl = "https://full/$id",
        width = 1080,
        height = 1920,
        author = "Author",
        authorUrl = "https://author",
        sourcePageUrl = "https://source/$id",
        colorHint = color,
        category = category,
    )

    private val RED = 0xFFFF0000.toInt()
    private val GREEN = 0xFF00FF00.toInt()
    private val BLUE = 0xFF0000FF.toInt()

    // ── HSV conversion ──────────────────────────────────────────────────────

    @Test
    fun `argbToHsv computes primary hues`() {
        assertEquals(0f, FeedDiversifier.argbToHsv(RED)[0], 0.5f)
        assertEquals(120f, FeedDiversifier.argbToHsv(GREEN)[0], 0.5f)
        assertEquals(240f, FeedDiversifier.argbToHsv(BLUE)[0], 0.5f)
        // Saturation and value of a pure primary are both max.
        assertEquals(1f, FeedDiversifier.argbToHsv(RED)[1], 0.01f)
        assertEquals(1f, FeedDiversifier.argbToHsv(RED)[2], 0.01f)
    }

    @Test
    fun `argbToHsv treats black as zero saturation`() {
        val hsv = FeedDiversifier.argbToHsv(0xFF000000.toInt())
        assertEquals(0f, hsv[1], 0.01f)
        assertEquals(0f, hsv[2], 0.01f)
    }

    // ── hue distance ────────────────────────────────────────────────────────

    @Test
    fun `hueDistance is circular`() {
        assertEquals(20f, FeedDiversifier.hueDistance(350f, 10f), 0.01f)
        assertEquals(120f, FeedDiversifier.hueDistance(0f, 240f), 0.01f)
        assertEquals(0f, FeedDiversifier.hueDistance(60f, 60f), 0.01f)
    }

    // ── diverse() ───────────────────────────────────────────────────────────

    @Test
    fun `diverse preserves size and set of items`() {
        val items = listOf(
            wp("1", category = Category.NATURE),
            wp("2", category = Category.SPACE),
            wp("3", category = Category.NATURE),
            wp("4", category = Category.SPACE),
            wp("5", category = Category.CITY),
        )
        val out = FeedDiversifier.diverse(items)
        assertEquals(items.size, out.size)
        assertEquals(items.map { it.globalKey }.toSet(), out.map { it.globalKey }.toSet())
    }

    @Test
    fun `diverse avoids adjacent same-category when avoidable`() {
        val items = listOf(
            wp("n1", category = Category.NATURE),
            wp("n2", category = Category.NATURE),
            wp("s1", category = Category.SPACE),
            wp("s2", category = Category.SPACE),
        )
        val out = FeedDiversifier.diverse(items)
        for (i in 1 until out.size) {
            assertTrue(
                "adjacent items share a category at $i: ${out.map { it.category }}",
                out[i].category != out[i - 1].category,
            )
        }
    }

    @Test
    fun `diverse separates same-hue items using color`() {
        // Two reds and one blue, same category/source: the blue should break up the reds.
        val items = listOf(
            wp("r1", category = Category.NATURE, color = RED),
            wp("r2", category = Category.NATURE, color = RED),
            wp("b1", category = Category.NATURE, color = BLUE),
        )
        val out = FeedDiversifier.diverse(items)
        assertEquals("b1", out[1].id)
    }

    @Test
    fun `diverse is deterministic`() {
        val items = listOf(
            wp("1", source = SourceId.PEXELS, category = Category.NATURE, color = RED),
            wp("2", source = SourceId.WALLHAVEN, category = Category.SPACE, color = BLUE),
            wp("3", source = SourceId.UNSPLASH, category = Category.NATURE, color = GREEN),
            wp("4", source = SourceId.REDDIT, category = Category.CITY, color = null),
            wp("5", source = SourceId.PEXELS, category = Category.SPACE, color = RED),
        )
        val a = FeedDiversifier.diverse(items).map { it.id }
        val b = FeedDiversifier.diverse(items).map { it.id }
        assertEquals(a, b)
    }

    @Test
    fun `diverse uses tail for cross-page continuity`() {
        val tail = listOf(wp("prev", category = Category.NATURE))
        val items = listOf(
            wp("n2", category = Category.NATURE),
            wp("s1", category = Category.SPACE),
        )
        val out = FeedDiversifier.diverse(items, tail = tail)
        // The first item of the new page should differ from the tail's category.
        assertEquals(Category.SPACE, out.first().category)
    }

    @Test
    fun `diverse handles trivial inputs`() {
        assertEquals(emptyList<Wallpaper>(), FeedDiversifier.diverse(emptyList()))
        val single = listOf(wp("only"))
        assertEquals(single, FeedDiversifier.diverse(single))
    }

    // ── Topic rotation formula (mirrors MultiSourcePagingSource) ─────────────

    @Test
    fun `topic rotation covers every topic across pages`() {
        val size = 4
        val offset = 5
        val sourceIndex = 0
        val covered = (1..size).map { page -> (sourceIndex + page + offset).mod(size) }.toSet()
        assertEquals(setOf(0, 1, 2, 3), covered)
    }
}
