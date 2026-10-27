package com.smyddevstudio.kanako.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smyddevstudio.kanako.model.AnnotationPlateStyle
import com.smyddevstudio.kanako.model.FabDocking
import com.smyddevstudio.kanako.model.FabSize
import com.smyddevstudio.kanako.model.FuriganaColor
import com.smyddevstudio.kanako.model.FuriganaSize
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kanako_preferences")

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.dataStore)

    companion object {
        val FURIGANA_SIZE = stringPreferencesKey("furigana_size")
        val FURIGANA_COLOR = stringPreferencesKey("furigana_color")
        val SCRIPT_TYPE = stringPreferencesKey("script_type")
        val ANNOTATION_PLATE_STYLE = stringPreferencesKey("annotation_plate_style")
        val TOUCH_PASSTHROUGH = booleanPreferencesKey("touch_passthrough")
        val FAB_SIZE = stringPreferencesKey("fab_size")
        val FAB_DOCKING = stringPreferencesKey("fab_docking")
        val FAB_OFFSET_X = intPreferencesKey("fab_offset_x")
        val FAB_OFFSET_Y = intPreferencesKey("fab_offset_y")
        val AUTO_HIDE_ON_KEYBOARD = booleanPreferencesKey("auto_hide_on_keyboard")
        private val AUTO_READ_AFTER_SCROLL = booleanPreferencesKey("auto_read_after_scroll")
        private val AUTO_READ_OFFER_INITIALIZED = booleanPreferencesKey("auto_read_offer_initialized")
        private val AUTO_READ_OFFER_PENDING = booleanPreferencesKey("auto_read_offer_pending")
        private val SNAPSHOT_DISCLOSURE_ACCEPTED = booleanPreferencesKey("snapshot_disclosure_accepted")
    }

    val userPreferencesFlow: Flow<KanakoPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            mapUserPreferences(preferences)
        }

    private fun mapUserPreferences(preferences: Preferences): KanakoPreferences {
        val furiganaSize = preferences[FURIGANA_SIZE]?.let { name ->
            runCatching { FuriganaSize.valueOf(name) }.getOrNull()
        } ?: FuriganaSize.MEDIUM

        val furiganaColor = preferences[FURIGANA_COLOR]?.let { name ->
            runCatching { FuriganaColor.valueOf(name) }.getOrNull()
        } ?: FuriganaColor.MUSHROOM_CRIMSON

        val scriptType = preferences[SCRIPT_TYPE]?.let { name ->
            when (name) {
                "HIRAGANA", "KATAKANA", "KANA" -> ScriptType.KANA
                "ROMAJI" -> ScriptType.ROMAJI
                else -> null
            }
        } ?: ScriptType.KANA

        val annotationPlateStyle = preferences[ANNOTATION_PLATE_STYLE]?.let { name ->
            runCatching { AnnotationPlateStyle.valueOf(name) }.getOrNull()
        } ?: AnnotationPlateStyle.WASHI_CAPSULE

        val touchPassthrough = preferences[TOUCH_PASSTHROUGH] ?: true

        val fabSize = preferences[FAB_SIZE]?.let { name ->
            runCatching { FabSize.valueOf(name) }.getOrNull()
        } ?: FabSize.MEDIUM

        val fabDocking = FabDocking.FLOATING

        val autoHideOnKeyboard = preferences[AUTO_HIDE_ON_KEYBOARD] ?: true
        val fabOffsetX = preferences[FAB_OFFSET_X] ?: 100
        val fabOffsetY = preferences[FAB_OFFSET_Y] ?: 300

        return KanakoPreferences(
            furiganaSize = furiganaSize,
            furiganaColor = furiganaColor,
            scriptType = scriptType,
            annotationPlateStyle = annotationPlateStyle,
            touchPassthrough = touchPassthrough,
            fabSize = fabSize,
            fabDocking = fabDocking,
            fabOffsetX = fabOffsetX,
            fabOffsetY = fabOffsetY,
            hasSavedFloatingPosition = preferences[FAB_OFFSET_X] != null && preferences[FAB_OFFSET_Y] != null,
            autoHideOnKeyboard = autoHideOnKeyboard,
            autoReadAfterScroll = preferences[AUTO_READ_AFTER_SCROLL] ?: false,
            offerAutoRead = preferences[AUTO_READ_OFFER_PENDING] ?: false,
            hasAcceptedSnapshotDisclosure = preferences[SNAPSHOT_DISCLOSURE_ACCEPTED] ?: false
        )
    }

    suspend fun initializeAutoReadOffer(isNewInstallation: Boolean) {
        dataStore.edit { preferences ->
            if (preferences[AUTO_READ_OFFER_INITIALIZED] == null) {
                val untouched = preferences.asMap().isEmpty()
                preferences[AUTO_READ_OFFER_INITIALIZED] = true
                preferences[AUTO_READ_OFFER_PENDING] = isNewInstallation && untouched
            }
        }
    }

    suspend fun updateAutoReadAfterScroll(enabled: Boolean) {
        dataStore.edit {
            it[AUTO_READ_AFTER_SCROLL] = enabled
            it[AUTO_READ_OFFER_PENDING] = false
        }
    }

    suspend fun acceptSnapshotDisclosure() {
        dataStore.edit { it[SNAPSHOT_DISCLOSURE_ACCEPTED] = true }
    }

    suspend fun updateFuriganaSize(size: FuriganaSize) {
        dataStore.edit { preferences ->
            preferences[FURIGANA_SIZE] = size.name
        }
    }

    suspend fun updateFuriganaColor(color: FuriganaColor) {
        dataStore.edit { preferences ->
            preferences[FURIGANA_COLOR] = color.name
        }
    }

    suspend fun updateScriptType(scriptType: ScriptType) {
        dataStore.edit { preferences ->
            preferences[SCRIPT_TYPE] = scriptType.name
        }
    }

    suspend fun updateAnnotationPlateStyle(style: AnnotationPlateStyle) {
        dataStore.edit { preferences ->
            preferences[ANNOTATION_PLATE_STYLE] = style.name
        }
    }

    suspend fun updateTouchPassthrough(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[TOUCH_PASSTHROUGH] = enabled
        }
    }

    suspend fun updateFabSize(size: FabSize) {
        dataStore.edit { preferences ->
            preferences[FAB_SIZE] = size.name
        }
    }

    suspend fun updateFabDocking(docking: FabDocking) {
        dataStore.edit { preferences ->
            preferences[FAB_DOCKING] = docking.name
        }
    }

    suspend fun updateFabOffset(x: Int, y: Int) {
        dataStore.edit { preferences ->
            preferences[FAB_OFFSET_X] = x
            preferences[FAB_OFFSET_Y] = y
        }
    }

    suspend fun updateAutoHideOnKeyboard(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_HIDE_ON_KEYBOARD] = enabled
        }
    }

}
