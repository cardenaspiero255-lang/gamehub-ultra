package com.cardenaspiero255.gamehubultra.voice

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AndroidContinuousVoiceGatewayRobolectricTest {
    @Test
    fun revokedRecordAudioPermissionIsNotMistakenForSuccessfulServiceStart() {
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(context)
        Mockito.`when`(context.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(Mockito.mock(SharedPreferences::class.java))
        Mockito.`when`(
            context.checkPermission(
                Mockito.eq(Manifest.permission.RECORD_AUDIO),
                Mockito.anyInt(),
                Mockito.anyInt()
            )
        ).thenReturn(PackageManager.PERMISSION_DENIED)
        val gateway = AndroidContinuousVoiceGateway(context)

        assertFailsWith<SecurityException> { gateway.startWakeService() }
        Mockito.verify(context, Mockito.never()).startForegroundService(Mockito.any())
    }
}
