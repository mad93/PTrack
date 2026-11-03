package cat.ptrack.car;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Shell-only test commands; signature-level DUMP permission is required by the manifest. */
public final class CarTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            String scenario=intent.getStringExtra("scenario");
            CarTestFeed.command(scenario==null?"real":scenario);
            setResultCode(Activity.RESULT_OK);
            setResultData("Car display fixture: "+scenario);
        } catch(IllegalArgumentException failure) { setResultCode(Activity.RESULT_CANCELED); setResultData(failure.getMessage()); }
    }
}
