package com.smyddevstudio.kanako.service

import android.service.quicksettings.Tile
import org.junit.Assert.assertEquals
import org.junit.Test

class KanakoQuickSettingsTileServiceTest {
    @Test
    fun tileStateReflectsOverlayPreference() {
        assertEquals(Tile.STATE_INACTIVE, quickSettingsTileState(isActive = false))
        assertEquals(Tile.STATE_ACTIVE, quickSettingsTileState(isActive = true))
    }
}
