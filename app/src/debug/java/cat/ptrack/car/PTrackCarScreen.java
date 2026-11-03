package cat.ptrack.car;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Template;
import cat.ptrack.R;

/** Static template rendered by the Android Auto host, independently of MainActivity. */
public final class PTrackCarScreen extends Screen {
    public PTrackCarScreen(@NonNull CarContext carContext) {
        super(carContext);
    }

    @NonNull @Override public Template onGetTemplate() {
        Log.i("PTrackCar", "Minimal template requested by host");
        return new MessageTemplate.Builder(getCarContext().getString(R.string.car_probe_message))
                .setTitle(getCarContext().getString(R.string.car_probe_title))
                .setHeaderAction(Action.APP_ICON)
                .build();
    }
}
