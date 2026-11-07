package cat.ptrack

import android.annotation.TargetApi
import android.companion.CompanionDeviceService
import android.content.Intent

/** Android 12+ presence monitoring; disabled by resources on Android 8–11. */
@TargetApi(31)
class ObdCompanionService : CompanionDeviceService() {
    override fun onDeviceAppeared(address: String) {
        val prefs = getSharedPreferences("settings", 0)
        if (!prefs.getBoolean("auto", false) || address != prefs.getString("address", "")) return
        try { startForegroundService(Intent(this, RecordingService::class.java).putExtra("background", true)) }
        catch (_: RuntimeException) { RecordingService.state = "permission" }
    }
}
