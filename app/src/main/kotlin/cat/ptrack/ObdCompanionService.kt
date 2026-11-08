package cat.ptrack

import android.annotation.TargetApi
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent

/** Android 12+ presence monitoring; disabled by resources on Android 8–11. */
@TargetApi(31)
class ObdCompanionService : CompanionDeviceService() {
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        ObdAutoStart.connected(this, address, "Companion legacy")
    }

    @TargetApi(36)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (event.event != DevicePresenceEvent.EVENT_BT_CONNECTED) return
        val manager = getSystemService(CompanionDeviceManager::class.java) ?: return
        val address = manager.myAssociations.firstOrNull { it.id == event.associationId }
            ?.deviceMacAddress?.toString() ?: return
        ObdAutoStart.connected(this, address, "Companion")
    }
}
