package com.wallora.app.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.wallora.app.data.local.dao.WallpaperDao
import kotlinx.coroutines.CancellationException
import com.wallora.app.data.local.entity.WallpaperEntity
import com.wallora.app.domain.WallpaperSource
import com.wallora.app.domain.model.Category
import com.wallora.app.domain.model.SourceId
import com.wallora.app.domain.model.Wallpaper
import java.io.IOException

/**
 * Multi-source fan-out PagingSource.
 *
 * For each page load:
 * 1. On [LoadParams.Refresh] (first load or pull-to-refresh): bypass the TTL cache and
 *    always fetch from network — guarantees fresh data after source/category changes.
 * 2. On [LoadParams.Append]: check Room TTL cache; only fetch from network if expired.
 * 3. Insert fresh items into Room cache.
 * 4. Round-robin interleave results from all sources.
 * 5. Deduplicate by [Wallpaper.globalKey].
 * 6. If ALL sources threw and the result set is empty, return [LoadResult.Error] so the
 *    UI can show an error state instead of a silent blank screen.
 *
 * Key type: a [PageKey] holding per-source cursors so each source can page
 * independently without blocking the others.
 */
class MultiSourcePagingSource(
    private val sources: List<WallpaperSource>,
    private val categories: List<Category>,
    private val query: String?,
    private val wallpaperDao: WallpaperDao,
    private val cacheTtlMs: Long,
    /**
     * Active subreddit pool — included in the Reddit cache key so that different
     * subreddit configurations never share cached rows. Passed from the ViewModel
     * so it matches what RedditSource will actually use.
     */
    private val userSubreddits: List<String> = emptyList(),
    /**
     * User-defined free-text topics (e.g. "Iron Man"). Rotated alongside [categories] so each
     * source cycles through subjects across pages. Queried via [WallpaperSource.search].
     */
    private val customKeywords: List<String> = emptyList(),
    /**
     * Per-session offset added to the topic rotation so the first screen differs across launches
     * while staying stable within a browsing session. Comes from [com.wallora.app.di.SessionSeed].
     */
    private val topicOffset: Int = 0,
) : PagingSource<MultiSourcePagingSource.PageKey, Wallpaper>() {

    /** A subject to query: either a built-in [category] or a free-text [keyword]. */
    private data class Topic(val category: Category?, val keyword: String?)

    /** Combined rotation pool: built-in categories first, then custom keywords. */
    private val topics: List<Topic> =
        categories.map { Topic(it, null) } + customKeywords.map { Topic(null, it) }

    /**
     * Tracks globalKeys already emitted by this PagingSource instance.
     * Scoped to the instance lifetime so dedup spans all pages in a generation
     * (resets on refresh when a new instance is created). Thread-safe because
     * Paging 3 may call load() from multiple threads in theory.
     */
    private val seenKeys: MutableSet<String> =
        java.util.Collections.synchronizedSet(HashSet())

    /**
     * Last few items emitted on the previous page, so [FeedDiversifier] can keep the first item
     * of the next page different from the end of the last. Instance-scoped like [seenKeys].
     */
    private val recentTail: MutableList<Wallpaper> =
        java.util.Collections.synchronizedList(ArrayList())

    /** Holds a map of sourceId → next-page cursor for that source. */
    data class PageKey(val cursors: Map<String, String>)

    companion object {
        private const val TAG = "MultiSourcePagingSource"
        val FIRST_PAGE = PageKey(emptyMap())
    }

    override fun getRefreshKey(state: PagingState<PageKey, Wallpaper>): PageKey? = null

    override suspend fun load(params: LoadParams<PageKey>): LoadResult<PageKey, Wallpaper> {
        val key = params.key ?: FIRST_PAGE
        val resultLists = mutableListOf<List<Wallpaper>>()
        val nextCursors = mutableMapOf<String, String>()
        val isRefresh = params is LoadParams.Refresh

        // Track failures for the error-on-total-failure path
        var lastException: Exception? = null
        var anyThrew = false

        for ((index, source) in sources.withIndex()) {
            val sourceKey = source.id.name
            val cursor = key.cursors[sourceKey] ?: "1"
            val pageNum = cursor.toIntOrNull() ?: 1

            // Each source gets ONE topic per page, rotated by (source, page, session seed) so the
            // grid shows different subjects side-by-side AND each source cycles subjects as you
            // scroll — instead of a source being pinned to the same subject forever.
            val topic: Topic? = when {
                query != null || topics.isEmpty() -> null
                else -> topics[(index + pageNum + topicOffset).mod(topics.size)]
            }
            val effectiveCategories = topic?.category?.let { listOf(it) } ?: emptyList()
            val effectiveKeyword = query ?: topic?.keyword

            val items: List<Wallpaper>
            val nextCursor: String?

            // On Refresh, always bypass the cache so the user always sees fresh data
            // (e.g., after changing categories / sources / subreddits).
            val cacheKey = buildCacheKey(source.id, cursor, effectiveCategories, effectiveKeyword)
            val cached = if (isRefresh) emptyList()
            else {
                val minTimestamp = System.currentTimeMillis() - cacheTtlMs
                wallpaperDao.getByCacheKey(cacheKey, minTimestamp)
            }

            if (cached.isNotEmpty()) {
                items = cached.map { it.toDomain() }
                // For cached pages, assume there's a next page unless it's clearly empty
                nextCursor = cursor.toIntOrNull()?.plus(1)?.toString()
                Log.d(TAG, "${source.id}: cache hit (${items.size} items)")
            } else {
                try {
                    val page = if (effectiveKeyword != null) source.search(effectiveKeyword, cursor)
                               else source.browse(effectiveCategories, cursor)
                    items = page.items
                    nextCursor = page.nextPage

                    val now = System.currentTimeMillis()
                    wallpaperDao.insertAll(items.map { WallpaperEntity.fromDomain(it, cacheKey, now) })
                    Log.d(TAG, "${source.id}: network fetch (${items.size} items)")
                } catch (e: CancellationException) {
                    throw e  // never swallow — flatMapLatest cancels in-flight loads on category change
                } catch (e: Exception) {
                    Log.w(TAG, "${source.id}: fetch failed, skipping source", e)
                    lastException = e
                    anyThrew = true
                    // Don't advance cursor on failure — retry on next page load
                    continue
                }
            }

            resultLists.add(items)
            if (nextCursor != null) nextCursors[sourceKey] = nextCursor
        }

        // Round-robin interleave results from all sources for a fair base order.
        val interleaved = roundRobinInterleave(resultLists)

        // Cross-page dedup: filter by seenKeys so the same wallpaper never appears
        // twice across pages. distinctBy would only catch within-page duplicates;
        // cross-page duplicates cause an IllegalArgumentException in LazyStaggeredGrid.
        val deduped = interleaved.filter { seenKeys.add(it.globalKey) }

        // If EVERY source threw and nothing loaded, surface an error instead of a silent blank.
        // This gives the user a Retry button and a reason rather than an empty grid.
        if (deduped.isEmpty() && anyThrew) {
            return LoadResult.Error(lastException!!)
        }

        // Reorder so adjacent tiles differ in colour, category and source. Runs AFTER dedup so
        // the LazyStaggeredGrid unique-key invariant is preserved. `tail` carries continuity from
        // the previous page.
        val diversified = FeedDiversifier.diverse(deduped, tail = tailSnapshot())
        rememberTail(diversified)

        val nextKey = if (nextCursors.isEmpty()) null else PageKey(nextCursors)

        return LoadResult.Page(
            data = diversified,
            prevKey = null,
            nextKey = nextKey,
        )
    }

    private fun tailSnapshot(): List<Wallpaper> =
        synchronized(recentTail) { recentTail.toList() }

    private fun rememberTail(page: List<Wallpaper>) = synchronized(recentTail) {
        recentTail.addAll(page)
        while (recentTail.size > FeedDiversifier.DEFAULT_WINDOW) recentTail.removeAt(0)
    }

    /**
     * Cache key encodes source, categories/query, pagination cursor, and (for Reddit) the
     * effective subreddit pool — so distinct configurations never collide on the same cached rows.
     */
    private fun buildCacheKey(
        sourceId: SourceId,
        page: String,
        effectiveCategories: List<Category>,
        effectiveKeyword: String?,
    ): String {
        val catPart = when {
            effectiveKeyword != null -> "search:$effectiveKeyword"
            else -> effectiveCategories.joinToString(",") { it.name }
        }
        val subredditPart = if (sourceId == SourceId.REDDIT && userSubreddits.isNotEmpty()) {
            ":" + userSubreddits.sorted().joinToString("+")
        } else ""
        return "${sourceId.name}:$catPart$subredditPart:$page"
    }

    /** Round-robin interleave: take one item at a time from each list, in turn. */
    private fun roundRobinInterleave(lists: List<List<Wallpaper>>): List<Wallpaper> {
        val result = mutableListOf<Wallpaper>()
        val iters = lists.map { it.iterator() }
        while (iters.any { it.hasNext() }) {
            for (iter in iters) {
                if (iter.hasNext()) result.add(iter.next())
            }
        }
        return result
    }
}
