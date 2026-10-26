package com.wallora.app.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.wallora.app.data.remote.PixabaySource
import com.wallora.app.di.ApplicationScope
import com.wallora.app.domain.model.SourceId
import com.wallora.app.domain.model.Wallpaper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Progress of a "save all favorites to the gallery" run. */
data class SaveProgress(
    val total: Int,
    val done: Int,
    val saved: Int,
    val skipped: Int,
    val failed: Int,
    val running: Boolean,
)

/**
 * Saves full-resolution copies of wallpapers into the device gallery (Pictures/Wallora) via
 * MediaStore — no storage permission needed on Android 10+.
 *
 * - Runs on the application scope, so it keeps going if the user leaves the Favorites screen
 *   (it still stops if Android kills the process).
 * - Images already saved earlier (same file name in Pictures/Wallora) are skipped, so the
 *   action can be repeated safely after a partial run.
 * - Pixabay URLs expire after 24h, so a failed Pixabay download is retried once with a fresh
 *   URL looked up by image ID.
 */
@Singleton
class SaveFavoritesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pixabaySource: PixabaySource,
    private val okHttpClient: OkHttpClient,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "SaveFavorites"
        private const val RELATIVE_DIR = "Pictures/Wallora"
    }

    private enum class Outcome { SAVED, SKIPPED, FAILED }

    private val _progress = MutableStateFlow<SaveProgress?>(null)
    val progress: StateFlow<SaveProgress?> = _progress.asStateFlow()

    @Volatile private var job: Job? = null

    fun start(wallpapers: List<Wallpaper>) {
        if (wallpapers.isEmpty() || job?.isActive == true) return
        job = appScope.launch(Dispatchers.IO) { run(wallpapers) }
    }

    /** Hide the finished-run summary. No effect while a run is active. */
    fun dismiss() {
        if (job?.isActive != true) _progress.value = null
    }

    private suspend fun run(wallpapers: List<Wallpaper>) {
        val total = wallpapers.size
        var saved = 0
        var skipped = 0
        var failed = 0
        _progress.value = SaveProgress(total, 0, 0, 0, 0, running = true)
        for ((index, wallpaper) in wallpapers.withIndex()) {
            try {
                when (saveOne(wallpaper)) {
                    Outcome.SAVED -> saved++
                    Outcome.SKIPPED -> skipped++
                    Outcome.FAILED -> failed++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Saving ${wallpaper.globalKey} failed: $e")
                failed++
            }
            _progress.value = SaveProgress(total, index + 1, saved, skipped, failed, running = true)
        }
        _progress.value = SaveProgress(total, total, saved, skipped, failed, running = false)
        Log.w(TAG, "Save-all finished: total=$total saved=$saved skipped=$skipped failed=$failed")
    }

    private suspend fun saveOne(wallpaper: Wallpaper): Outcome {
        val baseName = "${wallpaper.sourceId.name.lowercase()}_${wallpaper.id}"
        if (alreadySaved(baseName)) return Outcome.SKIPPED

        var response = fetch(wallpaper.fullUrl)
        if (response == null && wallpaper.sourceId == SourceId.PIXABAY) {
            val fresh = pixabaySource.fetchById(wallpaper.id)
            if (fresh != null) response = fetch(fresh.fullUrl)
        }
        if (response == null) return Outcome.FAILED

        return response.use { r ->
            val body = r.body ?: return@use Outcome.FAILED
            val mime = mimeOf(body.contentType()?.let { "${it.type}/${it.subtype}" }, wallpaper.fullUrl)
            val ext = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$baseName.$ext")
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: return@use Outcome.FAILED
            try {
                val out = resolver.openOutputStream(uri) ?: throw IOException("No output stream for $uri")
                out.use { body.byteStream().copyTo(it) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                Outcome.SAVED
            } catch (e: Exception) {
                resolver.delete(uri, null, null) // don't leave a half-written pending file behind
                throw e
            }
        }
    }

    /** Successful response for [url], or null (response closed) on HTTP error / network error. */
    private fun fetch(url: String): Response? =
        try {
            val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
            if (response.isSuccessful) {
                response
            } else {
                Log.w(TAG, "HTTP ${response.code} for ${url.take(80)}")
                response.close()
                null
            }
        } catch (e: IOException) {
            Log.w(TAG, "Download failed for ${url.take(80)}: $e")
            null
        }

    private fun alreadySaved(baseName: String): Boolean =
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf("$baseName.%", "$RELATIVE_DIR/"),
                null,
            )?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            false
        }

    private fun mimeOf(contentType: String?, url: String): String = when {
        contentType == "image/png" || contentType == "image/webp" || contentType == "image/jpeg" -> contentType ?: "image/jpeg"
        url.substringBefore('?').endsWith(".png", ignoreCase = true) -> "image/png"
        url.substringBefore('?').endsWith(".webp", ignoreCase = true) -> "image/webp"
        else -> "image/jpeg"
    }
}
