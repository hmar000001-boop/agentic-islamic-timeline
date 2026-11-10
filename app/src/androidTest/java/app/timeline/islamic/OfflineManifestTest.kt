package app.timeline.islamic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineManifestTest {
    @Test fun offlinePackageDoesNotRequestInternetPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissions = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertFalse("Offline flavor must not request INTERNET", "android.permission.INTERNET" in permissions)
        assertEquals("app.timeline.islamic", context.packageName)
    }
}
