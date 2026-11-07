package cat.ptrack.car

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The manifest requires the signature-level DUMP permission for shell-only commands. */
class CarTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val scenario = intent.getStringExtra("scenario")
            CarTestFeed.command(scenario ?: "real")
            resultCode = Activity.RESULT_OK; resultData = "Car display fixture: $scenario"
        } catch (failure: IllegalArgumentException) { resultCode = Activity.RESULT_CANCELED; resultData = failure.message }
    }
}
