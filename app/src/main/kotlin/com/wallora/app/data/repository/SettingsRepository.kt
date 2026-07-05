package com.wallora.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.wallora.app.BuildConfig
import com.wallora.app.di.ApplicationScope
import com.wallora.app.domain.model.Category
import com.wallora.app.domain.model.EditParams
import com.wallora.app.domain.model.SourceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists all user settings via DataStore (Preferences).
 * All writes are suspend functions; all reads are [Flow]s for reactive UI.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    // ── Schema migration ──────────────────────────────────────────────────────
    // Version 1: auto-enable sources added in v1.4 (OPENVERSE, NASA, FLICKR, WIKIMEDIA)
    //            that upgrading users never explicitly disabled (they didn't exist before).
    private val sourcesSchemaVersionKey = intPreferencesKey("sources_schema_version")
    private val SOURCES_SCHEMA_CURRENT = 1

    init {
        appScope.launch {
            dataStore.edit { prefs ->
                if ((prefs[sourcesSchemaVersionKey] ?: 0) < SOURCES_SCHEMA_CURRENT) {
                    val current = prefs[stringSetPreferencesKey("enabled_sources")]?.toMutableSet()
                    if (current != null) {
                        current.addAll(listOf("OPENVERSE", "NASA", "FLICKR", "WIKIMEDIA"))
                        prefs[stringSetPreferencesKey("enabled_sources")] = current
                    }
                    prefs[sourcesSchemaVersionKey] = SOURCES_SCHEMA_CURRENT
                }
            }
        }
    }

    // ── Source toggles ──────────────────────────────────────────────────────
    private val enabledSourcesKey = stringSetPreferencesKey("enabled_sources")

    val enabledSources: Flow<Set<SourceId>> = dataStore.data.map { prefs ->
        val raw = prefs[enabledSourcesKey] ?: defaultEnabledSourceNames()
        raw.mapNotNull { runCatching { SourceId.valueOf(it) }.getOrNull() }.toSet()
    }

    suspend fun setSourceEnabled(source: SourceId, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[enabledSourcesKey]?.toMutableSet()
                ?: defaultEnabledSourceNames().toMutableSet()
            if (enabled) current.add(source.name) else current.remove(source.name)
            prefs[enabledSourcesKey] = current
        }
    }

    // ── Category defaults ────────────────────────────────────────────────────
    private val selectedCategoriesKey = stringSetPreferencesKey("selected_categories")

    val selectedCategories: Flow<Set<Category>> = dataStore.data.map { prefs ->
        // null = all categories
        prefs[selectedCategoriesKey]
            ?.mapNotNull { runCatching { Category.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?: DEFAULT_CATEGORIES  // fresh install: start with curated defaults
    }

    suspend fun setSelectedCategories(categories: Set<Category>) {
        dataStore.edit { prefs ->
            prefs[selectedCategoriesKey] = categories.map { it.name }.toSet()
        }
    }

    // ── Rotation settings ────────────────────────────────────────────────────
    private val rotationEnabledKey = booleanPreferencesKey("rotation_enabled")
    private val rotationIntervalMsKey = longPreferencesKey("rotation_interval_ms")
    private val rotationTimesKey = stringSetPreferencesKey("rotation_times")       // "HH:mm" strings
    private val rotationOnUnlockKey = booleanPreferencesKey("rotation_on_unlock")
    private val rotationWifiOnlyKey = booleanPreferencesKey("rotation_wifi_only")
    private val rotationChargingOnlyKey = booleanPreferencesKey("rotation_charging_only")
    private val rotationPlaylistKey = stringPreferencesKey("rotation_playlist")    // "FAVORITES" | "CATEGORIES"

    val rotationEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[rotationEnabledKey] ?: false
    }
    val rotationIntervalMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[rotationIntervalMsKey] ?: 3_600_000L // 1 hour default
    }
    val rotationTimes: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[rotationTimesKey] ?: emptySet()
    }
    val rotationOnUnlock: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[rotationOnUnlockKey] ?: false
    }
    val rotationWifiOnly: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[rotationWifiOnlyKey] ?: false
    }
    val rotationChargingOnly: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[rotationChargingOnlyKey] ?: false
    }
    val rotationPlaylist: Flow<String> = dataStore.data.map { prefs ->
        prefs[rotationPlaylistKey] ?: "CATEGORIES"
    }

    suspend fun setRotationEnabled(enabled: Boolean) =
        dataStore.edit { it[rotationEnabledKey] = enabled }

    suspend fun setRotationIntervalMs(ms: Long) =
        dataStore.edit { it[rotationIntervalMsKey] = ms }

    suspend fun setRotationTimes(times: Set<String>) =
        dataStore.edit { it[rotationTimesKey] = times }

    suspend fun setRotationOnUnlock(enabled: Boolean) =
        dataStore.edit { it[rotationOnUnlockKey] = enabled }

    suspend fun setRotationWifiOnly(enabled: Boolean) =
        dataStore.edit { it[rotationWifiOnlyKey] = enabled }

    suspend fun setRotationChargingOnly(enabled: Boolean) =
        dataStore.edit { it[rotationChargingOnlyKey] = enabled }

    suspend fun setRotationPlaylist(playlist: String) =
        dataStore.edit { it[rotationPlaylistKey] = playlist }

    // ── Current wallpaper (for live engine observability) ────────────────────
    private val currentWallpaperFullUrlKey = stringPreferencesKey("current_wallpaper_full_url")
    private val currentWallpaperThumbUrlKey = stringPreferencesKey("current_wallpaper_thumb_url")

    /**
     * The full-res and thumb URL of the most recently applied wallpaper.
     * The live wallpaper engine observes this to reload its bitmap on rotation.
     * Null when no wallpaper has been persisted yet (fresh install).
     */
    val currentWallpaperUrls: Flow<Pair<String, String>?> = dataStore.data.map { prefs ->
        val full = prefs[currentWallpaperFullUrlKey] ?: return@map null
        full to (prefs[currentWallpaperThumbUrlKey] ?: "")
    }

    suspend fun setCurrentWallpaperUrls(fullUrl: String, thumbUrl: String) {
        dataStore.edit { prefs ->
            prefs[currentWallpaperFullUrlKey] = fullUrl
            prefs[currentWallpaperThumbUrlKey] = thumbUrl
        }
    }

    // ── Gesture & parallax ───────────────────────────────────────────────────
    // Note: "is live wallpaper active" is no longer stored in DataStore — the real source of
    // truth is WallpaperManager.wallpaperInfo.packageName == context.packageName (checked at
    // runtime in NextWallpaperUseCase so rotation never deactivates the live wallpaper).
    private val doubleTapGestureKey = booleanPreferencesKey("double_tap_gesture")
    private val parallaxEnabledKey = booleanPreferencesKey("parallax_enabled")

    val doubleTapGestureEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[doubleTapGestureKey] ?: false  // DEFAULT OFF: most launchers consume the gesture
    }
    val parallaxEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[parallaxEnabledKey] ?: true  // DEFAULT ON per spec
    }

    suspend fun setDoubleTapGesture(enabled: Boolean) =
        dataStore.edit { it[doubleTapGestureKey] = enabled }

    suspend fun setParallaxEnabled(enabled: Boolean) =
        dataStore.edit { it[parallaxEnabledKey] = enabled }

    // ── EditParams (default look for live mode) ───────────────────────────────
    private val editBlurKey = floatPreferencesKey("edit_blur")
    private val editBrightnessKey = floatPreferencesKey("edit_brightness")
    private val editContrastKey = floatPreferencesKey("edit_contrast")
    private val editSaturationKey = floatPreferencesKey("edit_saturation")
    private val editPanXKey = floatPreferencesKey("edit_pan_x")
    private val editPanYKey = floatPreferencesKey("edit_pan_y")

    val defaultEditParams: Flow<EditParams> = dataStore.data.map { prefs ->
        EditParams(
            blur = prefs[editBlurKey] ?: 0f,
            brightness = prefs[editBrightnessKey] ?: 0f,
            contrast = prefs[editContrastKey] ?: 1f,
            saturation = prefs[editSaturationKey] ?: 1f,
            panX = prefs[editPanXKey] ?: 0f,
            panY = prefs[editPanYKey] ?: 0f,
        )
    }

    suspend fun setDefaultEditParams(params: EditParams) = dataStore.edit { prefs ->
        prefs[editBlurKey] = params.blur
        prefs[editBrightnessKey] = params.brightness
        prefs[editContrastKey] = params.contrast
        prefs[editSaturationKey] = params.saturation
        prefs[editPanXKey] = params.panX
        prefs[editPanYKey] = params.panY
    }

    // ── Theme ────────────────────────────────────────────────────────────────
    private val themeKey = stringPreferencesKey("theme")  // "SYSTEM" | "LIGHT" | "DARK"

    val theme: Flow<String> = dataStore.data.map { prefs -> prefs[themeKey] ?: "SYSTEM" }

    suspend fun setTheme(theme: String) = dataStore.edit { it[themeKey] = theme }

    // ── Cache management ─────────────────────────────────────────────────────
    private val cacheTtlMsKey = longPreferencesKey("cache_ttl_ms")

    val cacheTtlMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[cacheTtlMsKey] ?: 3_600_000L
    }

    suspend fun setCacheTtlMs(ms: Long) = dataStore.edit { it[cacheTtlMsKey] = ms }

    // ── User API keys (override BuildConfig at runtime) ──────────────────────
    private val userPexelsKeyKey = stringPreferencesKey("user_pexels_key")
    private val userUnsplashKeyKey = stringPreferencesKey("user_unsplash_key")
    private val userWallhavenKeyKey = stringPreferencesKey("user_wallhaven_key")
    private val userPixabayKeyKey = stringPreferencesKey("user_pixabay_key")
    private val userFlickrKeyKey = stringPreferencesKey("user_flickr_key")
    private val userRedditClientIdKey = stringPreferencesKey("user_reddit_client_id")
    private val redditDeviceIdKey = stringPreferencesKey("reddit_device_id")

    val userPexelsKey: Flow<String> = dataStore.data.map { it[userPexelsKeyKey] ?: "" }
    val userUnsplashKey: Flow<String> = dataStore.data.map { it[userUnsplashKeyKey] ?: "" }
    val userWallhavenKey: Flow<String> = dataStore.data.map { it[userWallhavenKeyKey] ?: "" }
    val userPixabayKey: Flow<String> = dataStore.data.map { it[userPixabayKeyKey] ?: "" }
    val userFlickrKey: Flow<String> = dataStore.data.map { it[userFlickrKeyKey] ?: "" }
    val userRedditClientId: Flow<String> = dataStore.data.map { it[userRedditClientIdKey] ?: "" }

    suspend fun setUserPexelsKey(key: String) = dataStore.edit { it[userPexelsKeyKey] = key }
    suspend fun setUserUnsplashKey(key: String) = dataStore.edit { it[userUnsplashKeyKey] = key }
    suspend fun setUserWallhavenKey(key: String) = dataStore.edit { it[userWallhavenKeyKey] = key }
    suspend fun setUserPixabayKey(key: String) = dataStore.edit { it[userPixabayKeyKey] = key }
    suspend fun setUserFlickrKey(key: String) = dataStore.edit { it[userFlickrKeyKey] = key }
    suspend fun setUserRedditClientId(clientId: String) =
        dataStore.edit { it[userRedditClientIdKey] = clientId }

    /**
     * Returns the persisted per-install device ID for Reddit userless OAuth.
     * Generates and persists a new UUID on first call (atomically via DataStore).
     */
    suspend fun getOrCreateRedditDeviceId(): String {
        val existing = dataStore.data.map { it[redditDeviceIdKey] }.first()
        if (!existing.isNullOrBlank()) return existing
        val newId = java.util.UUID.randomUUID().toString()
        dataStore.edit { it[redditDeviceIdKey] = newId }
        return newId
    }

    // ── User Reddit subreddits ───────────────────────────────────────────────
    private val userSubredditsKey = stringSetPreferencesKey("user_subreddits")

    val userSubreddits: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[userSubredditsKey]?.toList()?.sorted() ?: DEFAULT_SUBREDDITS
    }

    suspend fun setUserSubreddits(subs: Set<String>) =
        dataStore.edit { it[userSubredditsKey] = subs }

    // ── Custom category keywords ─────────────────────────────────────────────
    private val customKeywordsKey = stringSetPreferencesKey("custom_keywords")

    val customKeywords: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[customKeywordsKey] ?: emptySet()
    }

    suspend fun setCustomKeywords(keywords: Set<String>) =
        dataStore.edit { it[customKeywordsKey] = keywords }

    // ── Prefetched next wallpaper (for instant gesture apply) ────────────────
    private val prefetchedFullUrlKey = stringPreferencesKey("prefetched_full_url")
    private val prefetchedThumbUrlKey = stringPreferencesKey("prefetched_thumb_url")

    val prefetchedWallpaperUrls: Flow<Pair<String, String>?> = dataStore.data.map { prefs ->
        val full = prefs[prefetchedFullUrlKey] ?: return@map null
        full to (prefs[prefetchedThumbUrlKey] ?: "")
    }

    suspend fun setPrefetchedWallpaperUrls(fullUrl: String, thumbUrl: String) {
        dataStore.edit { prefs ->
            prefs[prefetchedFullUrlKey] = fullUrl
            prefs[prefetchedThumbUrlKey] = thumbUrl
        }
    }

    suspend fun clearPrefetchedWallpaperUrls() {
        dataStore.edit { prefs ->
            prefs.remove(prefetchedFullUrlKey)
            prefs.remove(prefetchedThumbUrlKey)
        }
    }

    /**
     * Sources enabled on a fresh install: the keyless four (work with no key) plus any key-based
     * source whose key is already baked into the build (the CI release ships all keys). This makes
     * the app "keyless-first" — a great grid with zero setup — while a full release still lights up
     * everything, and user-added keys auto-enable their source (see SettingsViewModel).
     */
    private fun defaultEnabledSourceNames(): Set<String> {
        val keyless = setOf("WALLHAVEN", "OPENVERSE", "NASA", "WIKIMEDIA")
        val keyed = buildSet {
            if (BuildConfig.PEXELS_API_KEY.isNotBlank()) add("PEXELS")
            if (BuildConfig.UNSPLASH_ACCESS_KEY.isNotBlank()) add("UNSPLASH")
            if (BuildConfig.PIXABAY_API_KEY.isNotBlank()) add("PIXABAY")
            if (BuildConfig.FLICKR_API_KEY.isNotBlank()) add("FLICKR")
            if (BuildConfig.REDDIT_CLIENT_ID.isNotBlank()) add("REDDIT")
        }
        return keyless + keyed
    }

    companion object {
        val DEFAULT_SUBREDDITS = listOf("iWallpaper")

        /**
         * Default categories — a spread of distinct subjects and colours (green/blue nature, colourful
         * space, varied cityscapes, saturated vibrant, varied animals, colourful abstract) so the very
         * first grid looks rich and varied. The feed engine rotates and colour-diversifies from here.
         */
        val DEFAULT_CATEGORIES = setOf(
            Category.NATURE,
            Category.SPACE,
            Category.CITY,
            Category.VIBRANT,
            Category.ANIMALS,
            Category.ABSTRACT,
        )
    }
}
