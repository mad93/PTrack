package cat.ptrack

import android.Manifest
import android.annotation.TargetApi
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

internal object ObdAutoStart {
    const val CAR_ADDRESS = "auto_car_address"
    const val TAG = "PTrackObdAuto"

    fun hasBluetoothPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun updateObservations(context: Context, before: Set<String>, after: Set<String>) {
        val prefs = context.getSharedPreferences("settings", 0)
        val car = prefs.getString(CAR_ADDRESS, null)
        (before - after).filterNot { it == car }.forEach { observe(context, it, false) }
        if (prefs.getBoolean("auto", false)) (after - before).forEach { observe(context, it, true) }
    }

    fun connected(context: Context, address: String, source: String) {
        val prefs = context.getSharedPreferences("settings", 0)
        if ((!prefs.getBoolean("auto", false) && !RecordingService.waitingForObd) || !hasBluetoothPermission(context)) return
        try {
            val mac = VehicleProfiles.normalize(address)
            val isObd = Store(context).use { store ->
                if (store.vehicles.matches(mac).isNotEmpty()) true
                else {
                    if (!mac.equals(prefs.getString(CAR_ADDRESS, null), ignoreCase = true) ||
                        store.vehicles.active().obdAddress == null) return
                    false
                }
            }
            if (RecordingService.pendingObdChoice == mac ||
                (RecordingService.running && (!isObd || !RecordingService.waitingForObd))) return
            Log.d(TAG, "Bluetooth connected: $source (${if (isObd) "OBD" else "car"})")
            val request = Intent(context, RecordingService::class.java)
                .setAction(RecordingService.ACTION_AUTO_OBD).putExtra("background", true)
            if (isObd) request.putExtra(RecordingService.EXTRA_OBD, mac)
            if (RecordingService.running) context.startService(request) else context.startForegroundService(request)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Android rejected automatic OBD start ($source)", error)
        }
    }

    @Suppress("DEPRECATION")
    fun observe(context: Context, address: String, enabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 31 ||
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) return false
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= 36) observeById(manager, address, enabled)
            else if (manager.associations.any { it.equals(address, ignoreCase = true) }) {
                if (enabled) manager.startObservingDevicePresence(address) else manager.stopObservingDevicePresence(address)
                true
            } else false
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not change Bluetooth observation", error)
            false
        }
    }

    @TargetApi(36)
    private fun observeById(manager: CompanionDeviceManager, address: String, enabled: Boolean): Boolean {
        val association = manager.myAssociations.firstOrNull {
            address.equals(it.deviceMacAddress?.toString(), ignoreCase = true)
        } ?: return false
        val request = ObservingDevicePresenceRequest.Builder().setAssociationId(association.id).build()
        if (enabled) manager.startObservingDevicePresence(request) else manager.stopObservingDevicePresence(request)
        return true
    }
}
