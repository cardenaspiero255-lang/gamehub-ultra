package com.cardenaspiero255.gamehubultra.platform.compat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.vendor.VendorSocFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDeviceCompatibilityStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val record = DeviceCompatibilityRule(
        schemaVersion = DeviceCompatibilityDatabase.SCHEMA_VERSION,
        ruleId = "verified-rule-001",
        evidenceId = "trusted-evidence-001",
        gamePackage = "com.example.game",
        key = CompatibilityDeviceKey(
            VendorSocFamily.MEDIATEK, CompatibilityGpuFamily.MALI, 35
        ),
        recommendedProfile = PerformanceProfile.BALANCED
    )

    @Before fun resetLocalState() {
        context.getSharedPreferences("car56_device_compatibility_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun validRecordsRoundTripThroughPrivatePersistentStorage() {
        assertTrue(AndroidDeviceCompatibilityStore.save(context, listOf(record)))
        assertEquals(listOf(record), AndroidDeviceCompatibilityStore.load(context))
    }

    @Test fun storingARecordNeverAuthenticatesItsEvidenceOnRead() {
        assertTrue(AndroidDeviceCompatibilityStore.save(context, listOf(record)))
        val restored = AndroidDeviceCompatibilityStore.load(context)
        val untrusted = DeviceCompatibilityDatabase.resolve(
            record.key, record.gamePackage, restored, emptySet()
        )
        assertEquals(CompatibilityStatus.UNVERIFIED, untrusted.status)
        val trusted = DeviceCompatibilityDatabase.resolve(
            record.key, record.gamePackage, restored, setOf(record.evidenceId)
        )
        assertEquals(CompatibilityStatus.KNOWN_GOOD, trusted.status)
    }

    @Test fun futureSchemaAndMalformedPayloadHaveNoTrustedFallback() {
        val prefs = context.getSharedPreferences(
            "car56_device_compatibility_v1", Context.MODE_PRIVATE
        )
        for (payload in listOf("CAR56:99\n", "corrupt", "CAR56:1\nnot-a-record")) {
            prefs.edit().putString("records", payload).commit()
            assertTrue(AndroidDeviceCompatibilityStore.load(context).isEmpty())
        }
    }

    @Test fun attemptedInvalidOverwriteMustPreservePreviousValidSnapshot() {
        assertTrue(AndroidDeviceCompatibilityStore.save(context, listOf(record)))
        assertFalse(AndroidDeviceCompatibilityStore.save(
            context, listOf(record.copy(gamePackage = "../invalid"))
        ))
        assertEquals(listOf(record), AndroidDeviceCompatibilityStore.load(context))
    }

    @Test fun duplicateOrOversizedSnapshotIsRejectedWithoutPartialWrite() {
        assertTrue(AndroidDeviceCompatibilityStore.save(context, listOf(record)))
        assertFalse(AndroidDeviceCompatibilityStore.save(context, listOf(record, record)))
        assertFalse(AndroidDeviceCompatibilityStore.save(context,
            (0..64).map { record.copy(gamePackage = "com.example.game$it") }))
        assertEquals(listOf(record), AndroidDeviceCompatibilityStore.load(context))
    }

    @Test fun storageMustNeverIncludeUniqueDeviceIdentifiersOrSecretValues() {
        assertTrue(AndroidDeviceCompatibilityStore.save(context, listOf(record)))
        val serialized = context.getSharedPreferences(
            "car56_device_compatibility_v1", Context.MODE_PRIVATE
        ).getString("records", null).orEmpty()
        assertTrue(serialized.startsWith("CAR56:1"))
        assertFalse(serialized.contains("ANDROID_ID"))
        assertFalse(serialized.contains("serial"))
        assertFalse(serialized.contains("IMEI"))
        assertFalse(serialized.contains("password"))
    }

    @Test fun explicitClearRemovesAllSavedRules() {
        assertTrue(AndroidDeviceCompatibilityStore.save(context, listOf(record)))
        assertTrue(AndroidDeviceCompatibilityStore.clear(context))
        assertEquals(emptyList(), AndroidDeviceCompatibilityStore.load(context))
    }

    @Test fun readFaultIsHandledSafelyWithoutReturningCachedTrust() {
        val badPrefs = context.getSharedPreferences(
            "car56_device_compatibility_v1", Context.MODE_PRIVATE
        )
        badPrefs.edit().putString("records", "CAR56:1\n" + "bad".repeat(4000)).commit()
        assertEquals(emptyList(), AndroidDeviceCompatibilityStore.load(context))
    }
}
