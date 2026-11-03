package cat.ptrack;

import android.bluetooth.BluetoothDevice;
import android.content.*;

public final class ObdReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if(!BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) return;
        var prefs=context.getSharedPreferences("settings",0);
        BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if(device==null || !prefs.getBoolean("auto",false) || !device.getAddress().equals(prefs.getString("address",""))) return;
        try { context.startForegroundService(new Intent(context,RecordingService.class).putExtra("background",true)); }
        catch(RuntimeException e) { RecordingService.state="permission"; }
    }
}
