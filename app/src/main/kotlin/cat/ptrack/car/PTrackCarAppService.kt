package cat.ptrack.car

import android.content.Intent
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import cat.ptrack.BuildConfig

/** Hosts the dashboard and its OBD connection action. */
class PTrackCarAppService : CarAppService() {
    // Host validation does not bypass Android Auto's own app trust checks.
    override fun createHostValidator(): HostValidator = if (BuildConfig.DEBUG) {
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    } else {
        HostValidator.Builder(this)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()
    }
    override fun onCreateSession(): Session {
        Log.i("PTrackCar", "Android Auto session created")
        return object : Session() { override fun onCreateScreen(intent: Intent): Screen = PTrackCarScreen(carContext) }
    }
}
