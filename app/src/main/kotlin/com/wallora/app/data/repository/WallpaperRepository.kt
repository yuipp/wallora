package com.wallora.app.data.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wallora.app.data.local.dao.FavoriteDao
import com.wallora.app.data.local.dao.HistoryDao
import com.wallora.app.data.local.dao.WallpaperDao
import com.wallora.app.data.local.entity.FavoriteEntity
import com.wallora.app.data.local.entity.HistoryEntity
import com.wallora.app.data.paging.MultiSourcePagingSource
import com.wallora.app.data.remote.PixabaySource
import com.wallora.app.di.SessionSeed
import com.wallora.app.domain.WallpaperSource
import com.wallora.app.domain.model.Category
import com.wallora.app.domain.model.SourceId
import com.wallora.app.domain.model.Wallpaper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperRepository @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards WallpaperSource>,
    private val wallpaperDao: WallpaperDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val settingsRepository: SettingsRepository,
    private val sessionSeed: SessionSeed,
) {

    companion object {
        private const val TAG = "WallpaperRepository"
        private const val PAGE_SIZE = 20
        private const val CACHE_TTL_MS = 3_600_000L // 1 hour
        // Pixabay documents webformatURL as valid for 24h; re-resolve saved items at most hourly.
        private const val PIXABAY_REFRESH_INTERVAL_MS = 3_600_000L
        // After a failed attempt, wait before retrying so reopening History doesn't spam the API.
        private const val PIXABAY_RETRY_BACKOFF_MS = 300_000L
    }

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pixabayRefreshMutex = kotlinx.coroutines.sync.Mutex()
    @Volatile private var lastPixabayRefreshMs = 0L
    @Volatile private var lastPixabayFailureMs = 0L

    /**
     * Pixabay image URLs expire after 24h, so thumbs/full URLs stored in history and favorites
     * go dead (HTTP 400). Re-fetch them by image ID and update the stored rows. Throttled, and
     * only marks itself done after a successful pass so a failed (offline) attempt is retried.
     */
    suspend fun refreshPixabayUrls() {
        val pixabay = sources.filterIsInstance<PixabaySource>().firstOrNull() ?: return
        if (!pixabay.isConfigured) return
        pixabayRefreshMutex.lock()
        try {
            val now = System.currentTimeMillis()
            if (now - lastPixabayRefreshMs < PIXABAY_REFRESH_INTERVAL_MS) return
            if (now - lastPixabayFailureMs < PIXABAY_RETRY_BACKOFF_MS) return
            val prefix = SourceId.PIXABAY.name
            val histKeys = historyDao.getAll().filter { it.sourceId == prefix }.map { it.globalKey to it.id }
            val favKeys = favoriteDao.getAll().filter { it.sourceId == prefix }.map { it.globalKey to it.id }
            val ids = (histKeys + favKeys).map { it.second }.distinct()
            if (ids.isEmpty()) { lastPixabayRefreshMs = now; return }
            val fresh = pixabay.fetchByIds(ids).associateBy { it.id }
            for ((key, id) in histKeys) fresh[id]?.let { historyDao.updateUrls(key, it.thumbUrl, it.fullUrl) }
            for ((key, id) in favKeys) fresh[id]?.let { favoriteDao.updateUrls(key, it.thumbUrl, it.fullUrl) }
            lastPixabayRefreshMs = now
            Log.d(TAG, "Pixabay URLs refreshed: requested=${ids.size} returned=${fresh.size}")
        } catch (e: Exception) {
            lastPixabayFailureMs = System.currentTimeMillis()
            Log.w(TAG, "Pixabay URL refresh failed: ${e.message}")
        } finally {
            pixabayRefreshMutex.unlock()
        }
    }

    /**
     * Order active sources so Wallhaven (the keyless, high-quality anchor) leads, then the rest
     * by their declared order. Deterministic ordering also keeps the feed stable per session.
     */
    private fun orderedActiveSources(enabledSources: Set<SourceId>): List<WallpaperSource> =
        sources
            .filter { it.isConfigured && it.id in enabledSources }
            .sortedBy { if (it.id == SourceId.WALLHAVEN) -1 else it.id.ordinal }

    /** Browse wallpapers by categories + custom keywords — returns a Paging 3 flow. */
    fun browse(
        categories: List<Category>,
        enabledSources: Set<SourceId>,
        userSubreddits: List<String> = emptyList(),
        customKeywords: List<String> = emptyList(),
    ): Flow<PagingData<Wallpaper>> {
        val activeSources = orderedActiveSources(enabledSources)
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = false,
                prefetchDistance = PAGE_SIZE / 2,
            ),
            pagingSourceFactory = {
                MultiSourcePagingSource(
                    sources = activeSources,
                    categories = categories,
                    query = null,
                    wallpaperDao = wallpaperDao,
                    cacheTtlMs = CACHE_TTL_MS,
                    userSubreddits = userSubreddits,
                    customKeywords = customKeywords,
                    topicOffset = sessionSeed.topicOffset,
                )
            },
        ).flow
    }

    /** Search across all enabled sources — returns a Paging 3 flow. */
    fun search(
        query: String,
        enabledSources: Set<SourceId>,
    ): Flow<PagingData<Wallpaper>> {
        val activeSources = orderedActiveSources(enabledSources)
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                MultiSourcePagingSource(
                    sources = activeSources,
                    categories = emptyList(),
                    query = query,
                    wallpaperDao = wallpaperDao,
                    cacheTtlMs = CACHE_TTL_MS,
                )
            },
        ).flow
    }

    // ── Favorites ────────────────────────────────────────────────────────────

    fun observeFavorites(): Flow<List<Wallpaper>> =
        favoriteDao.observeAll().onStart { refreshScope.launch { refreshPixabayUrls() } }.map { list ->
            list.map { entity ->
                Wallpaper(
                    id = entity.id,
                    sourceId = SourceId.valueOf(entity.sourceId),
                    thumbUrl = entity.thumbUrl,
                    fullUrl = entity.fullUrl,
                    width = entity.width,
                    height = entity.height,
                    author = entity.author,
                    authorUrl = entity.authorUrl,
                    sourcePageUrl = entity.sourcePageUrl,
                    colorHint = entity.colorHint,
                )
            }
        }

    fun observeIsFavorite(globalKey: String): Flow<Boolean> =
        favoriteDao.observeIsFavorite(globalKey)

    suspend fun addFavorite(wallpaper: Wallpaper) {
        favoriteDao.insert(
            FavoriteEntity(
                globalKey = wallpaper.globalKey,
                sourceId = wallpaper.sourceId.name,
                id = wallpaper.id,
                thumbUrl = wallpaper.thumbUrl,
                fullUrl = wallpaper.fullUrl,
                width = wallpaper.width,
                height = wallpaper.height,
                author = wallpaper.author,
                authorUrl = wallpaper.authorUrl,
                sourcePageUrl = wallpaper.sourcePageUrl,
                colorHint = wallpaper.colorHint,
                tags = wallpaper.tags.joinToString(","),
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun removeFavorite(globalKey: String) = favoriteDao.delete(globalKey)

    // ── History ──────────────────────────────────────────────────────────────

    fun observeHistory(): Flow<List<Wallpaper>> =
        historyDao.observeAll().onStart { refreshScope.launch { refreshPixabayUrls() } }.map { list ->
            list.map { entity ->
                Wallpaper(
                    id = entity.id,
                    sourceId = SourceId.valueOf(entity.sourceId),
                    thumbUrl = entity.thumbUrl,
                    fullUrl = entity.fullUrl,
                    width = entity.width,
                    height = entity.height,
                    author = entity.author,
                    authorUrl = entity.authorUrl,
                    sourcePageUrl = entity.sourcePageUrl,
                    colorHint = entity.colorHint,
                )
            }
        }

    suspend fun addToHistory(wallpaper: Wallpaper) {
        historyDao.insert(
            HistoryEntity(
                globalKey = wallpaper.globalKey,
                sourceId = wallpaper.sourceId.name,
                id = wallpaper.id,
                thumbUrl = wallpaper.thumbUrl,
                fullUrl = wallpaper.fullUrl,
                width = wallpaper.width,
                height = wallpaper.height,
                author = wallpaper.author,
                authorUrl = wallpaper.authorUrl,
                sourcePageUrl = wallpaper.sourcePageUrl,
                colorHint = wallpaper.colorHint,
                tags = wallpaper.tags.joinToString(","),
                setAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun clearHistory() = historyDao.deleteAll()

    /**
     * Return the most-recent [limit] applied wallpaper globalKeys (newest first).
     * Used by [com.wallora.app.domain.rotation.RotationEngine] for no-repeat logic.
     */
    suspend fun getRecentHistoryKeys(limit: Int): List<String> =
        historyDao.getAll()
            .sortedByDescending { it.setAt }
            .take(limit)
            .map { it.globalKey }

    /** Snapshot of all favorited wallpapers (newest first). Used by rotation playlist. */
    suspend fun getFavoritesSnapshot(): List<Wallpaper> {
        refreshPixabayUrls()
        return favoriteDao.getAll().sortedByDescending { it.addedAt }.map { entity ->
            Wallpaper(
                id = entity.id,
                sourceId = SourceId.valueOf(entity.sourceId),
                thumbUrl = entity.thumbUrl,
                fullUrl = entity.fullUrl,
                width = entity.width,
                height = entity.height,
                author = entity.author,
                authorUrl = entity.authorUrl,
                sourcePageUrl = entity.sourcePageUrl,
                colorHint = entity.colorHint,
            )
        }
    }

    // ── Cache maintenance ─────────────────────────────────────────────────────

    suspend fun clearCache() {
        wallpaperDao.deleteAll()
        Log.d(TAG, "Cache cleared")
    }

    suspend fun evictExpiredCache() {
        val cutoff = System.currentTimeMillis() - CACHE_TTL_MS
        wallpaperDao.deleteExpired(cutoff)
    }
}
