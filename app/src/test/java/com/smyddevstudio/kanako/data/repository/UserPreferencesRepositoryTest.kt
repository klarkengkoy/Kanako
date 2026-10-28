package com.smyddevstudio.kanako.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.smyddevstudio.kanako.model.AnnotationPlateStyle
import com.smyddevstudio.kanako.model.FabDocking
import com.smyddevstudio.kanako.model.FabSize
import com.smyddevstudio.kanako.model.FuriganaColor
import com.smyddevstudio.kanako.model.FuriganaSize
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        val testFile = tmpFolder.newFile("test_kanako_preferences.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        repository = UserPreferencesRepository(testDataStore)
    }

    @Test
    fun newInstallationOffersAutoReadWithoutActivatingBeforeConsent() = runTest(testDispatcher) {
        repository.initializeAutoReadOffer(isNewInstallation = true)
        assertTrue(repository.userPreferencesFlow.first().offerAutoRead)
        assertFalse(repository.userPreferencesFlow.first().autoReadAfterScroll)
        assertFalse(repository.userPreferencesFlow.first().hasAcceptedSnapshotDisclosure)
        repository.updateAutoReadAfterScroll(true)
        repository.initializeAutoReadOffer(isNewInstallation = true)
        val restored = UserPreferencesRepository(testDataStore).userPreferencesFlow.first()
        assertFalse(restored.offerAutoRead)
        assertTrue(restored.autoReadAfterScroll)
        assertFalse(restored.hasAcceptedSnapshotDisclosure)
        assertFalse(restored.isOverlayActive)
    }

    @Test
    fun decliningNewInstallOfferPersistsAndDoesNotReappear() = runTest(testDispatcher) {
        repository.initializeAutoReadOffer(isNewInstallation = true)
        repository.updateAutoReadAfterScroll(false)
        UserPreferencesRepository(testDataStore).initializeAutoReadOffer(isNewInstallation = true)
        assertFalse(repository.userPreferencesFlow.first().offerAutoRead)
        assertFalse(repository.userPreferencesFlow.first().autoReadAfterScroll)
    }

    @Test
    fun upgradesAndExistingPreferencesNeverReceiveNewInstallOffer() = runTest(testDispatcher) {
        repository.updateFabOffset(480, 620)
        repository.initializeAutoReadOffer(isNewInstallation = true)
        val prefs = repository.userPreferencesFlow.first()
        assertFalse(prefs.offerAutoRead)
        assertFalse(prefs.autoReadAfterScroll)
        assertEquals(480, prefs.fabOffsetX)
        assertEquals(620, prefs.fabOffsetY)
    }

    @Test
    fun emptyUpgradeAndExplicitExistingAutoReadChoiceArePreserved() = runTest(testDispatcher) {
        repository.initializeAutoReadOffer(isNewInstallation = false)
        assertFalse(repository.userPreferencesFlow.first().offerAutoRead)
        repository.updateAutoReadAfterScroll(true)
        repository.initializeAutoReadOffer(isNewInstallation = true)
        assertTrue(repository.userPreferencesFlow.first().autoReadAfterScroll)
        assertFalse(repository.userPreferencesFlow.first().offerAutoRead)
    }

    @Test
    fun cameraDenialSurvivesRepositoryRecreationWithoutGrantingOtherAccess() = runTest(testDispatcher) {
        assertFalse(repository.userPreferencesFlow.first().hasDeniedCameraPermission)
        repository.updateScriptType(ScriptType.ROMAJI)
        repository.recordCameraPermissionDenial()
        val restored = UserPreferencesRepository(testDataStore).userPreferencesFlow.first()
        assertTrue(restored.hasDeniedCameraPermission)
        assertEquals(ScriptType.ROMAJI, restored.scriptType)
        assertFalse(restored.hasAcceptedSnapshotDisclosure)
        assertFalse(restored.autoReadAfterScroll)
        assertFalse(restored.isOverlayActive)
    }

    @Test
    fun testDefaultPreferences() = runTest(testDispatcher) {
        val prefs = repository.userPreferencesFlow.first()
        assertEquals(KanakoPreferences(), prefs)
        assertEquals(FuriganaSize.MEDIUM, prefs.furiganaSize)
        assertEquals(FuriganaColor.MUSHROOM_CRIMSON, prefs.furiganaColor)
        assertEquals(ScriptType.KANA, prefs.scriptType)
        assertEquals(AnnotationPlateStyle.WASHI_CAPSULE, prefs.annotationPlateStyle)
        assertTrue(prefs.touchPassthrough)
        assertEquals(FabSize.MEDIUM, prefs.fabSize)
        assertEquals(FabDocking.FLOATING, prefs.fabDocking)
        assertTrue(prefs.autoHideOnKeyboard)
        assertFalse(prefs.isOverlayActive)
        assertFalse(prefs.hasAcceptedSnapshotDisclosure)
    }

    @Test
    fun savedPlacementIsDetectedFromExistingCoordinatesWithoutResettingIt() = runTest(testDispatcher) {
        assertFalse(repository.userPreferencesFlow.first().hasSavedFloatingPosition)
        repository.updateFabOffset(480, 620)
        val restored = UserPreferencesRepository(testDataStore).userPreferencesFlow.first()
        assertTrue(restored.hasSavedFloatingPosition)
        assertEquals(480, restored.fabOffsetX)
        assertEquals(620, restored.fabOffsetY)
    }

    @Test
    fun aPartialLegacyCoordinateDoesNotOverrideTheInitialRightEdgePlacement() = runTest(testDispatcher) {
        testDataStore.edit { it[UserPreferencesRepository.FAB_OFFSET_X] = 80 }
        assertFalse(repository.userPreferencesFlow.first().hasSavedFloatingPosition)
    }

    @Test
    fun testPersistedOverlayFlagDoesNotImplyActiveSession() = runTest(testDispatcher) {
        testDataStore.edit { preferences ->
            preferences[booleanPreferencesKey("is_overlay_active")] = true
        }

        assertFalse(repository.userPreferencesFlow.first().isOverlayActive)
    }

    @Test
    fun testUpdateFuriganaSize() = runTest(testDispatcher) {
        repository.updateFuriganaSize(FuriganaSize.LARGE)
        val prefs = repository.userPreferencesFlow.first()
        assertEquals(FuriganaSize.LARGE, prefs.furiganaSize)
    }

    @Test
    fun testUpdateFuriganaColor() = runTest(testDispatcher) {
        repository.updateFuriganaColor(FuriganaColor.PINE_GREEN)
        val prefs = repository.userPreferencesFlow.first()
        assertEquals(FuriganaColor.PINE_GREEN, prefs.furiganaColor)
    }

    @Test
    fun testUpdateScriptType() = runTest(testDispatcher) {
        repository.updateScriptType(ScriptType.ROMAJI)
        val restored = UserPreferencesRepository(testDataStore)
        restored.initializeAutoReadOffer(isNewInstallation = true)
        val prefs = restored.userPreferencesFlow.first()
        assertEquals(ScriptType.ROMAJI, prefs.scriptType)
    }

    @Test
    fun testUpdateAnnotationPlateStyle() = runTest(testDispatcher) {
        repository.updateAnnotationPlateStyle(AnnotationPlateStyle.HIGH_CONTRAST)
        val prefs = repository.userPreferencesFlow.first()
        assertEquals(AnnotationPlateStyle.HIGH_CONTRAST, prefs.annotationPlateStyle)
    }

    @Test
    fun testUpdateTouchPassthrough() = runTest(testDispatcher) {
        repository.updateTouchPassthrough(false)
        val prefs = repository.userPreferencesFlow.first()
        assertFalse(prefs.touchPassthrough)
    }

    @Test
    fun testUpdateFabSize() = runTest(testDispatcher) {
        repository.updateFabSize(FabSize.COMPACT)
        val prefs = repository.userPreferencesFlow.first()
        assertEquals(FabSize.COMPACT, prefs.fabSize)
    }

    @Test
    fun testUpdateFabDocking() = runTest(testDispatcher) {
        repository.updateFabDocking(FabDocking.FLOATING)
        val prefs = repository.userPreferencesFlow.first()
        assertEquals(FabDocking.FLOATING, prefs.fabDocking)
    }

    @Test
    fun testUpdateAutoHideOnKeyboard() = runTest(testDispatcher) {
        repository.updateAutoHideOnKeyboard(false)
        val prefs = repository.userPreferencesFlow.first()
        assertFalse(prefs.autoHideOnKeyboard)
    }

    @Test
    fun testInvalidEnumValueFallback() = runTest(testDispatcher) {
        testDataStore.edit { prefs ->
            prefs[UserPreferencesRepository.FURIGANA_SIZE] = "INVALID_SIZE_NAME"
            prefs[UserPreferencesRepository.FURIGANA_COLOR] = "INVALID_COLOR_NAME"
            prefs[UserPreferencesRepository.SCRIPT_TYPE] = "INVALID_SCRIPT_NAME"
            prefs[UserPreferencesRepository.ANNOTATION_PLATE_STYLE] = "INVALID_STYLE_NAME"
            prefs[UserPreferencesRepository.FAB_SIZE] = "INVALID_FAB_SIZE"
            prefs[UserPreferencesRepository.FAB_DOCKING] = "INVALID_FAB_DOCKING"
        }

        val prefs = repository.userPreferencesFlow.first()
        assertEquals(FuriganaSize.MEDIUM, prefs.furiganaSize)
        assertEquals(FuriganaColor.MUSHROOM_CRIMSON, prefs.furiganaColor)
        assertEquals(ScriptType.KANA, prefs.scriptType)
        assertEquals(AnnotationPlateStyle.WASHI_CAPSULE, prefs.annotationPlateStyle)
        assertEquals(FabSize.MEDIUM, prefs.fabSize)
        assertEquals(FabDocking.FLOATING, prefs.fabDocking)
    }
    @Test
    fun automaticReadingIsOptInAndPersistsWithoutStartingAReader() = runTest(testDispatcher) {
        assertFalse(repository.userPreferencesFlow.first().autoReadAfterScroll)
        repository.updateAutoReadAfterScroll(true)
        val restored = UserPreferencesRepository(testDataStore).userPreferencesFlow.first()
        assertTrue(restored.autoReadAfterScroll)
        assertFalse(restored.isOverlayActive)
        repository.updateAutoReadAfterScroll(false)
        assertFalse(repository.userPreferencesFlow.first().autoReadAfterScroll)
    }

    @Test
    fun screenshotConsentRequiresExplicitAcceptanceAndDoesNotStartAReader() = runTest(testDispatcher) {
        assertFalse(repository.userPreferencesFlow.first().hasAcceptedSnapshotDisclosure)
        repository.acceptSnapshotDisclosure()
        val restored = UserPreferencesRepository(testDataStore).userPreferencesFlow.first()
        assertTrue(restored.hasAcceptedSnapshotDisclosure)
        assertFalse(restored.isOverlayActive)
    }
}
