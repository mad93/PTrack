package cat.ptrack;

import android.companion.CompanionDeviceService;
import android.content.Intent;

/** Android 12+ presence monitoring reawakens recording without leaving the activity open. */
@android.annotation.TargetApi(31) // Component is disabled by resources on Android 8–11.
public final class ObdCompanionService extends CompanionDeviceService {
    @Override public void onDeviceAppeared(String address) {
        var prefs=getSharedPreferences("settings",0);
        if(!prefs.getBoolean("auto",false)||!address.equals(prefs.getString("address","")))return;
        try{startForegroundService(new Intent(this,RecordingService.class).putExtra("background",true));}
        catch(RuntimeException e){RecordingService.state="permission";}
    }
}
