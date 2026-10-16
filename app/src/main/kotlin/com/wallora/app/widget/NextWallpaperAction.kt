package com.wallora.app.widget

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.wallora.app.domain.usecase.NextWallpaperResult
import com.wallora.app.domain.usecase.WallpaperTarget
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

/**
 * Glance [ActionCallback] that applies the next wallpaper in-place.
 *
 * Glance callbacks are not Hilt-injected; we use [EntryPointAccessors] to reach
 * [NextWallpaperUseCase].
 *
 * The work runs on [workScope] and the callback waits for it for at most
 * [CALLBACK_BUDGET_MS]. Android kills a process whose broadcast receiver is still busy after
 * ~10 s ("ANR: Broadcast of Intent ... ActionCallbackBroadcastReceiver"), so if the work is
 * slower we return from the callback and let it finish in the background instead.
 */
class NextWallpaperAction : ActionCallback {

    companion object {
        private const val TAG = "NextWallpaperAction"
        private const val CALLBACK_BUDGET_MS = 7_000L
        private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WalloraEntryPoint::class.java,
        )
        val useCase = entryPoint.nextWallpaperUseCase()
        val t0 = System.currentTimeMillis()
        Log.w(TAG, "Widget tapped — applying next wallpaper")
        logNetworkState(context)

        val work = workScope.async { useCase(WallpaperTarget.BOTH) }
        // Cancelling the wait (timeout) does not cancel [work] — it keeps running in workScope.
        val result = withTimeoutOrNull(CALLBACK_BUDGET_MS) { work.await() }
        val elapsed = System.currentTimeMillis() - t0
        when (result) {
            null -> Log.w(TAG, "Still running after $elapsed ms — leaving callback to avoid a broadcast ANR; work continues in background")
            is NextWallpaperResult.Applied -> Log.w(TAG, "Applied: ${result.wallpaper.globalKey} in $elapsed ms")
            is NextWallpaperResult.NoPlaylist -> Log.w(TAG, "No playlist (after $elapsed ms)")
            is NextWallpaperResult.Failure -> Log.e(TAG, "Failed: ${result.message} (after $elapsed ms)")
        }
    }

    /**
     * One diagnostic line describing what could block this (freshly started, background)
     * process from the network: Data Saver, battery restrictions, standby bucket, process
     * importance, plus a real DNS probe. Each item is read independently so one failure
     * doesn't hide the rest.
     */
    private suspend fun logNetworkState(context: Context) {
        fun <T> safe(block: () -> T): String = try { block().toString() } catch (e: Exception) { "n/a(${e.javaClass.simpleName})" }

        val restrictBg = safe {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            when (cm.restrictBackgroundStatus) {
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "ENABLED(app not allowlisted)"
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "ALLOWLISTED"
                else -> "DISABLED"
            }
        }
        val pm = context.getSystemService(PowerManager::class.java)
        val ignoringBatteryOpt = safe { pm.isIgnoringBatteryOptimizations(context.packageName) }
        val powerSave = safe { pm.isPowerSaveMode }
        val doze = safe { pm.isDeviceIdleMode }
        // 10=ACTIVE 20=WORKING_SET 30=FREQUENT 40=RARE 45=RESTRICTED 50=NEVER
        val bucket = safe { context.getSystemService(UsageStatsManager::class.java).appStandbyBucket }
        // 100=FOREGROUND 125=FOREGROUND_SERVICE 200=VISIBLE 230=PERCEPTIBLE 400=CACHED
        val importance = safe { ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }.importance }
        val dns = withContext(Dispatchers.IO) {
            safe { InetAddress.getByName("pixabay.com").hostAddress }
        }
        Log.w(
            TAG,
            "Net state: dnsProbe=$dns restrictBackground=$restrictBg ignoringBatteryOpt=$ignoringBatteryOpt " +
                "powerSave=$powerSave doze=$doze standbyBucket=$bucket importance=$importance",
        )
    }
}
