package com.smyddevstudio.kanako.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.smyddevstudio.kanako.MainActivity
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun quickSettingsTileState(isActive: Boolean): Int =
    if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

class KanakoQuickSettingsTileService : TileService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var preferencesRepository: UserPreferencesRepository
    private var preferenceCollection: Job? = null

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = UserPreferencesRepository(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        preferenceCollection?.cancel()
        preferenceCollection = serviceScope.launch {
            combine(preferencesRepository.userPreferencesFlow, KanakoOverlaySession.isActive) { _, isActive ->
                isActive
            }.collect { isActive ->
                qsTile?.let { tile ->
                    tile.state = quickSettingsTileState(isActive)
                    tile.label = getString(R.string.quick_settings_tile_label)
                    tile.updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        preferenceCollection?.cancel()
        preferenceCollection = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            if (KanakoOverlaySession.isActive.value) {
                KanakoOverlayService.stopService(this@KanakoQuickSettingsTileService)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                KanakoOverlayService.canDrawOverlays(this@KanakoQuickSettingsTileService) &&
                KanakoAccessibilityService.connected.value &&
                preferencesRepository.userPreferencesFlow.first().hasAcceptedSnapshotDisclosure
            ) {
                KanakoOverlayService.startService(this@KanakoQuickSettingsTileService)
            } else {
                val launchIntent = Intent(this@KanakoQuickSettingsTileService, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_START_CAPTURE_FROM_TILE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this@KanakoQuickSettingsTileService,
                        0,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    startActivityAndCollapseLegacy(launchIntent)
                }
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseLegacy(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    override fun onDestroy() {
        preferenceCollection?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
