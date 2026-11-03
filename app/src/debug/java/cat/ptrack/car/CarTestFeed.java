package cat.ptrack.car;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import cat.ptrack.Sample;
import cat.ptrack.VehicleTelemetry;

/** Debug-only, memory-only display fixture. Never touches LIVE, Bluetooth or SQLite. */
public final class CarTestFeed {
    public static final VehicleTelemetry SOURCE=new VehicleTelemetry();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static boolean enabled;
    private static String scenario="drive";
    private static long trip, tick, until;
    private static final Runnable UPDATE=new Runnable() {
        @Override public void run() {
            if(SystemClock.elapsedRealtime()>until) { command("real"); return; }
            double current=scenario.equals("regen")?-40:scenario.equals("charge")?-20:30+20*Math.sin(tick++/4.0);
            double speed=scenario.equals("charge")?0:scenario.equals("regen")?40:60;
            SOURCE.accept(trip,new Sample(System.currentTimeMillis(),Math.max(0,70-tick*0.002),400,current,speed,null,null,null,"{\"simulation\":true}"),true,SystemClock.elapsedRealtime());
            SOURCE.cable(scenario.equals("charge")?1.0:null,SystemClock.elapsedRealtime());
            MAIN.postDelayed(this,1000);
        }
    };
    public static VehicleTelemetry.Snapshot snapshot() { return (enabled?SOURCE:VehicleTelemetry.LIVE).snapshot(); }
    public static void command(String value) {
        if(!java.util.Set.of("drive","regen","charge","freeze","disconnect","reconnect","real").contains(value)) throw new IllegalArgumentException("Unknown scenario");
        MAIN.removeCallbacks(UPDATE);
        if(value.equals("real")) { enabled=false; SOURCE.status("disconnected"); return; }
        if(!enabled) {
            enabled=true; trip++; tick=0;
            long end=System.currentTimeMillis();
            for(int i=60;i>=0;i--) SOURCE.accept(trip,new Sample(end-i*1000,70,400,30,60,null,null,null,"{\"simulation\":true}"),true,SystemClock.elapsedRealtime());
        }
        if(value.equals("freeze")) return;
        if(value.equals("disconnect")) { SOURCE.status("missing"); return; }
        scenario=value.equals("reconnect")?"drive":value;
        until=SystemClock.elapsedRealtime()+600_000;
        MAIN.postDelayed(UPDATE,10);
    }
    private CarTestFeed() {}
}
