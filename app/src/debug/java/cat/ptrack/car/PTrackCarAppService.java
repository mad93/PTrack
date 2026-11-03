package cat.ptrack.car;

import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

/** Debug-only Android Auto transport probe. It never starts or reads OBD recording. */
public final class PTrackCarAppService extends CarAppService {
    @NonNull @Override public HostValidator createHostValidator() {
        // Official development setting: accept test hosts. This class is absent from release.
        // This validates incoming hosts; it cannot bypass Android Auto's app trust checks.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull @Override public Session onCreateSession() {
        Log.i("PTrackCar", "Android Auto session created");
        return new Session() {
            @NonNull @Override public Screen onCreateScreen(@NonNull Intent intent) {
                return new PTrackCarScreen(getCarContext());
            }
        };
    }
}
