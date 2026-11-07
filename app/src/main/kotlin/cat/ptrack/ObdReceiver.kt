package cat.ptrack

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ObdReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val prefs = context.getSharedPreferences("settings", 0)
        @Suppress("DEPRECATION") val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (device == null || !prefs.getBoolean("auto", false) || device.address != prefs.getString("address", "")) return
        try { context.startForegroundService(Intent(context, RecordingService::class.java).putExtra("background", true)) }
        catch (_: RuntimeException) { RecordingService.state = "permission" }
    }
}
