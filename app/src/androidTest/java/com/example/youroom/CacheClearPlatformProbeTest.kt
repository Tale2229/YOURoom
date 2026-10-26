package com.example.youroom

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Run each method separately: the real reset deliberately kills instrumentation.
 * Never run this probe against the ordinary application ID or a user's device. */
class CacheClearPlatformProbeTest {
    private fun isolatedContext() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        assumeTrue("Explicitly opt into the destructive isolated probe",
            InstrumentationRegistry.getArguments().getString("allowIsolatedDataReset")=="true")
        check(it.packageName == "com.example.youroom.uitest") { "Isolated test package only" }
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")) {
            "Emulator only"
        }
    }

    @Test fun seedIsolatedData() = runBlocking {
        val context=isolatedContext()
        SecureSessionStore(context).saveLogin("probe-token",RememberedCredentials("probe-account","probe-password"),"probe-account")
        val settings=SettingsStore(context)
        settings.saveAppearance(AppearancePreferences(darkMode=true))
        settings.savePeriodStrip(PeriodStripPreferences(showNumbers=true))
        settings.savePlanningPreferences(PlanningPreferences().withCampus(Campus.XITUCHENG,
            CampusPlanningPreferences(templates=listOf(TimeTemplate("probe","probe-template",listOf(StudyWindow(660,720)))))))
        settings.save(SearchFilters(favoritesOnly=true))
        settings.saveBuildings(Campus.XITUCHENG,setOf("probe-building"))
        settings.addTag("probe-tag")
        settings.saveGuideRecord(GuideHistory.Guest,GuideRecord(12,GuideOutcome.COMPLETED))
        settings.saveGuideRecord(settings.guideAccountIdentity("probe-account"),GuideRecord(15,GuideOutcome.SKIPPED))
        val database=Room.databaseBuilder(context,YOURoomDatabase::class.java,"youroom.db")
            .addMigrations(YOURoomDatabase.MIGRATION_1_2).build()
        try {
            for(campus in Campus.entries) database.dao().saveSnapshot(SnapshotEntity(campus.dataKey,"2026-10-22",1,"{}"))
            database.dao().saveProfile(ProfileEntity("probe","probe-room","probe-building",true,true,false,true,"probe-note","[\"probe-tag\"]"))
        } finally { database.close() }
        listOf(context.filesDir,context.cacheDir,context.noBackupFilesDir,requireNotNull(context.getExternalFilesDir(null)))
            .forEach { File(it,"cache-clear-probe").writeText("isolated dummy data") }
        assertEquals("probe-account",SecureSessionStore(context).currentAccount())
        assertFalse(settings.guideHistory.first().shouldOffer(GuideHistory.Guest))
    }

    @Test fun requestSystemReset() {
        assertTrue(AndroidUserDataReset(isolatedContext()).request())
        // The framework stops this process. Returning is not evidence of completion.
        android.os.SystemClock.sleep(3000)
    }

    @Test fun verifyFreshState() = runBlocking {
        val context=isolatedContext()
        val session=SecureSessionStore(context)
        assertNull(session.read());assertNull(session.credentials());assertNull(session.currentAccount())
        val settings=SettingsStore(context)
        assertEquals(AppearancePreferences(),settings.appearance.first())
        assertFalse(settings.filters.first().favoritesOnly)
        assertEquals(PeriodStripPreferences(),settings.periodStrip.first())
        for(campus in Campus.entries) assertTrue(settings.planningPreferences.first().forCampus(campus).templates.isEmpty())
        assertTrue(settings.buildings.first().values.all { it.isEmpty() })
        assertTrue(settings.guideHistory.first().records.isEmpty())
        assertTrue(settings.guideHistory.first().shouldOffer(GuideHistory.Guest))
        val database=Room.databaseBuilder(context,YOURoomDatabase::class.java,"youroom.db")
            .addMigrations(YOURoomDatabase.MIGRATION_1_2).build()
        try {
            for(campus in Campus.entries) assertNull(database.dao().snapshot(campus.dataKey))
            assertTrue(database.dao().profiles().first().isEmpty())
        } finally { database.close() }
        listOf(context.filesDir,context.cacheDir,context.noBackupFilesDir,requireNotNull(context.getExternalFilesDir(null)))
            .forEach { assertFalse(File(it,"cache-clear-probe").exists()) }
    }
}
